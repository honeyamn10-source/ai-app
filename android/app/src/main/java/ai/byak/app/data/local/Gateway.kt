package ai.byak.app.data.local

import ai.byak.app.data.ApiException
import ai.byak.app.data.CatalogProvider
import ai.byak.app.data.ImageDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Provider definitions for on-device mode (mirrors backend/src/providers.mjs). */
object Catalog {
    data class Entry(val id: String, val name: String, val baseUrl: String, val kind: String, val models: List<String>, val usageOption: Boolean = false)
    val entries = listOf(
        Entry("openai", "OpenAI", "https://api.openai.com/v1", "openai", listOf("gpt-4.1-mini", "gpt-4.1", "o4-mini"), usageOption = true),
        Entry("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1", "anthropic", listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-4-5")),
        Entry("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini", listOf("gemini-2.5-flash", "gemini-2.5-pro")),
        Entry("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai", listOf("openai/gpt-4.1-mini", "google/gemini-2.5-flash", "anthropic/claude-sonnet-5"), usageOption = true),
        Entry("groq", "Groq", "https://api.groq.com/openai/v1", "openai", listOf("llama-3.3-70b-versatile"), usageOption = true),
        Entry("mistral", "Mistral", "https://api.mistral.ai/v1", "openai", listOf("mistral-small-latest", "mistral-large-latest")),
        Entry("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "openai", listOf("deepseek-chat", "deepseek-reasoner"), usageOption = true),
        Entry("nvidia", "NVIDIA", "https://integrate.api.nvidia.com/v1", "openai", listOf("meta/llama-3.3-70b-instruct", "deepseek-ai/deepseek-r1")),
        Entry(LOCAL, "Offline AI (Qwen3 0.6B)", "", "local", listOf(LocalModel.MODEL_ID))
    )
    const val LOCAL = "local"
    fun entry(id: String): Entry? = entries.firstOrNull { it.id == id }
    /** Keys are often pasted with "Bearer ", quotes, spaces or a line break; none of those are ever part of a key. */
    fun cleanKey(key: String): String = key.trim().removePrefix("Bearer ").removePrefix("bearer ").trim('"', '\'', ' ').filterNot { it.isWhitespace() }
    /** Accepts a pasted full endpoint such as https://host/v1/chat/completions and keeps just the base URL. */
    fun cleanBaseUrl(url: String): String = url.trim().trimEnd('/').removeSuffix("/chat/completions").removeSuffix("/completions").removeSuffix("/models").trimEnd('/')
    /** Guesses the provider from a pasted key's prefix, so a Gemini key never gets sent to OpenAI. */
    fun detect(key: String): String? = key.trim().let { k -> when {
        k.startsWith("sk-ant-") -> "anthropic"; k.startsWith("AIza") -> "gemini"; k.startsWith("sk-or-") -> "openrouter"
        k.startsWith("gsk_") -> "groq"; k.startsWith("nvapi-") -> "nvidia"; k.startsWith("sk-proj-") || k.startsWith("sk-svcacct-") -> "openai"
        else -> null } }
    fun asCatalog(): List<CatalogProvider> = entries.map { CatalogProvider(it.id, it.name, it.models, localOnly = it.kind == "local", keyOptional = it.kind == "local") }
}

data class Connection(val provider: String, val name: String, val baseUrl: String, val apiKey: String)
data class Turn(val role: String, val content: String, val images: List<ImageDraft> = emptyList())
data class Completion(val text: String, val inputTokens: Long, val outputTokens: Long, val stopReason: String?)

/** Calls AI providers directly from the phone with the user's own key. */
// Reasoning models can think silently for a while before the first token, so the idle read timeout is generous.
class Gateway(private val localModel: LocalModel? = null, private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()) {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val quick = client.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()

    private fun kind(c: Connection) = Catalog.entry(c.provider)?.kind ?: "openai"
    private fun base(c: Connection): String = (if (c.provider == "custom") c.baseUrl else Catalog.entry(c.provider)?.baseUrl ?: c.baseUrl).trimEnd('/')
    private fun Request.Builder.auth(c: Connection): Request.Builder = when (kind(c)) {
        "anthropic" -> header("x-api-key", c.apiKey).header("anthropic-version", "2023-06-01")
        "gemini" -> header("x-goog-api-key", c.apiKey)
        else -> apply { if (c.apiKey.isNotBlank()) header("Authorization", "Bearer ${c.apiKey}"); if (c.provider == "openrouter") header("X-Title", "BYAK AI") }
    }

    private fun failure(label: String, response: Response): ApiException {
        val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val detail = runCatching { JSONObject(body).let { it.optJSONObject("error")?.optString("message") ?: it.optString("message") } }.getOrNull().orEmpty()
        val hint = when {
            response.code == 401 || response.code == 403 || detail.contains("API key", true) -> "the provider rejected your API key (Models → Change key)"
            response.code == 402 -> "your account at the provider is out of credit"
            response.code == 404 -> "this model isn't available with your key — pick another in Models"
            response.code == 429 -> "rate limited or out of credit at the provider — wait a moment and retry"
            response.code >= 500 -> "the provider is having problems (error ${response.code}) — try again shortly"
            else -> "error ${response.code}"
        }
        return ApiException("$label: $hint${if (detail.isNotBlank()) " — ${detail.take(300)}" else ""}", response.code)
    }

    private suspend fun execute(http: OkHttpClient, request: Request): Response = withContext(Dispatchers.IO) {
        val call = http.newCall(request)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try { call.execute() } catch (e: IOException) { handle?.dispose(); currentCoroutineContext().ensureActive(); throw ApiException(if (e is java.net.SocketTimeoutException) "${request.url.host} took too long to respond. Try again." else "Couldn't reach ${request.url.host}. Check your internet connection.", 0) }
    }

    suspend fun listModels(c: Connection): List<String> = withContext(Dispatchers.IO) {
        if (kind(c) == "local") return@withContext listOf(LocalModel.MODEL_ID)
        val path = if (kind(c) == "gemini") "/models?pageSize=200" else "/models"
        execute(quick, Request.Builder().url(base(c) + path).auth(c).get().build()).use { res ->
            if (!res.isSuccessful) throw failure(c.name, res)
            val data = JSONObject(res.body?.string().orEmpty())
            if (kind(c) == "gemini") data.optJSONArray("models").objects().filter { m -> m.optJSONArray("supportedGenerationMethods")?.let { a -> (0 until a.length()).any { a.getString(it) == "generateContent" } } == true }.map { it.optString("name").removePrefix("models/") }
            else (data.optJSONArray("data") ?: data.optJSONArray("models")).objects().map { it.optString("id").ifBlank { it.optString("name") } }.filter { it.isNotBlank() }.sorted()
        }.take(300)
    }

    /** Proves the key works. OpenRouter lists models without a key, so it is checked on its key endpoint instead. */
    suspend fun verify(c: Connection): List<String> = withContext(Dispatchers.IO) {
        if (c.provider == "openrouter") execute(quick, Request.Builder().url(base(c) + "/key").auth(c).get().build()).use { res -> if (!res.isSuccessful) throw failure(c.name, res) }
        listModels(c)
    }

    /** Streams one answer; [onDelta] receives text as it arrives. Cancelling the coroutine cancels the HTTP call. */
    suspend fun stream(c: Connection, model: String, system: String, turns: List<Turn>, onDelta: (String) -> Unit): Completion = withContext(Dispatchers.IO) {
        if (model.isBlank()) throw ApiException("Choose a model for this provider", 400)
        if (kind(c) == "local") return@withContext (localModel ?: throw ApiException("Offline AI isn't available", 500)).generate(system, turns, onDelta).also { if (it.text.isBlank()) throw ApiException("The offline AI gave an empty answer. Try asking again in different words.", 502) }
        val merged = merge(turns)
        val (url, body) = when (kind(c)) {
            "anthropic" -> "${base(c)}/messages" to JSONObject().put("model", model).put("max_tokens", 16000).put("stream", true).apply { if (system.isNotBlank()) put("system", system) }
                .put("messages", JSONArray(merged.map { t -> JSONObject().put("role", t.role).put("content", if (t.images.isEmpty()) t.content else JSONArray(t.images.map { i -> JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", i.mimeType).put("data", b64(i))) } + JSONObject().put("type", "text").put("text", t.content.ifBlank { "Describe this image." }))) }))
            "gemini" -> "${base(c)}/models/${java.net.URLEncoder.encode(model, "UTF-8")}:streamGenerateContent?alt=sse" to JSONObject()
                .put("contents", JSONArray(merged.map { t -> JSONObject().put("role", if (t.role == "assistant") "model" else "user").put("parts", JSONArray(t.images.map { i -> JSONObject().put("inline_data", JSONObject().put("mime_type", i.mimeType).put("data", b64(i))) } + JSONObject().put("text", t.content.ifBlank { "Describe this image." }))) }))
                .apply { if (system.isNotBlank()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))) }
            else -> "${base(c)}/chat/completions" to JSONObject().put("model", model).put("stream", true).apply { if (Catalog.entry(c.provider)?.usageOption == true) put("stream_options", JSONObject().put("include_usage", true)) }
                .put("messages", JSONArray((if (system.isNotBlank()) listOf(JSONObject().put("role", "system").put("content", system)) else emptyList()) + merged.map { t ->
                    JSONObject().put("role", t.role).put("content", if (t.images.isEmpty()) t.content else JSONArray(listOf(JSONObject().put("type", "text").put("text", t.content.ifBlank { "Describe this image." })) + t.images.map { i -> JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:${i.mimeType};base64,${b64(i)}")) }))
                }))
        }
        val request = Request.Builder().url(url).auth(c).header("Accept", "text/event-stream").post(body.toString().toRequestBody(json)).build()
        var text = ""; var input = 0L; var output = 0L; var stop: String? = null
        val emit = { delta: String -> if (delta.isNotEmpty()) { text += delta; onDelta(delta) } }
        try { execute(client, request).use { res ->
            if (!res.isSuccessful) throw failure(c.name, res)
            val source = res.body?.source() ?: return@use
            val other = StringBuilder(); var events = 0
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) { if (events == 0 && other.length < 200_000) other.append(line).append('\n'); continue }
                events++
                val raw = line.removePrefix("data:").trim(); if (raw == "[DONE]") break
                val event = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                when (kind(c)) {
                    "anthropic" -> when (event.optString("type")) {
                        "message_start" -> input = event.optJSONObject("message")?.optJSONObject("usage")?.optLong("input_tokens") ?: 0
                        "content_block_delta" -> event.optJSONObject("delta")?.takeIf { it.optString("type") == "text_delta" }?.let { emit(it.optString("text")) }
                        "message_delta" -> { output = event.optJSONObject("usage")?.optLong("output_tokens") ?: output; stop = event.optJSONObject("delta")?.optString("stop_reason")?.ifBlank { null } ?: stop }
                        "error" -> throw ApiException("Anthropic: ${event.optJSONObject("error")?.optString("message") ?: "stream error"}", 502)
                    }
                    "gemini" -> {
                        event.optJSONObject("error")?.let { throw ApiException("Gemini: ${it.optString("message")}", 502) }
                        val candidate = event.optJSONArray("candidates")?.optJSONObject(0)
                        candidate?.optJSONObject("content")?.optJSONArray("parts").objects().filterNot { it.optBoolean("thought") }.forEach { emit(it.optString("text")) }
                        candidate?.optString("finishReason")?.ifBlank { null }?.let { stop = it }
                        event.optJSONObject("usageMetadata")?.let { input = it.optLong("promptTokenCount"); output = it.optLong("candidatesTokenCount") }
                    }
                    else -> {
                        event.optJSONObject("error")?.let { throw ApiException("${c.name}: ${it.optString("message")}", 502) }
                        val choice = event.optJSONArray("choices")?.optJSONObject(0)
                        choice?.optJSONObject("delta")?.optString("content")?.takeIf { it != "null" }?.let(emit)
                        choice?.optString("finish_reason")?.takeIf { it.isNotBlank() && it != "null" }?.let { stop = it }
                        event.optJSONObject("usage")?.let { input = it.optLong("prompt_tokens"); output = it.optLong("completion_tokens") }
                    }
                }
            }
            // Some OpenAI-compatible servers ignore "stream": true and send one JSON answer instead.
            if (events == 0 && text.isEmpty()) runCatching { JSONObject(other.toString()) }.getOrNull()?.let { whole ->
                whole.optJSONObject("error")?.let { throw ApiException("${c.name}: ${it.optString("message")}", 502) }
                val parsed = when (kind(c)) {
                    "anthropic" -> whole.optJSONArray("content").objects().filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
                    "gemini" -> whole.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts").objects().filterNot { it.optBoolean("thought") }.joinToString("") { it.optString("text") }
                    else -> whole.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.takeIf { it != "null" }.orEmpty()
                }
                emit(parsed)
            }
        } } catch (e: IOException) {
            currentCoroutineContext().ensureActive() // a user stop surfaces as cancellation, not as an error
            throw ApiException(if (e is java.net.SocketTimeoutException) "${c.name} stopped responding. Try again." else "The connection to ${c.name} was interrupted. Try again.", 0)
        }
        if (stop == "refusal" && text.isEmpty()) emit("The model declined to answer this request.")
        // Never finish silently: an empty answer is reported, with the likely reason.
        if (text.isBlank()) throw ApiException(when (stop) {
            "max_tokens", "length", "MAX_TOKENS" -> "$model used its whole output limit before answering. Try a shorter question or another model."
            "SAFETY", "content_filter", "PROHIBITED_CONTENT" -> "${c.name} blocked this answer (safety filter). Try rephrasing."
            else -> "${c.name} returned an empty answer with $model. Check the model name in Models, or try another model."
        }, 502)
        Completion(text, input, output, stop)
    }

    private fun merge(turns: List<Turn>): List<Turn> = turns.filter { it.content.isNotBlank() || it.images.isNotEmpty() }.fold(mutableListOf()) { acc, t ->
        val last = acc.lastOrNull()
        if (last != null && last.role == t.role) acc[acc.lastIndex] = Turn(t.role, listOf(last.content, t.content).filter { it.isNotBlank() }.joinToString("\n\n"), last.images + t.images) else acc += t
        acc
    }
    private fun b64(image: ImageDraft) = Base64.getEncoder().encodeToString(image.bytes)

    // ---------- web ----------
    data class WebSource(val title: String, val url: String, val text: String)

    /** Web search: Brave when the user added a key, otherwise Wikipedia (free, no key). */
    suspend fun webSearch(query: String, braveKey: String?): List<WebSource> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query.take(300), "UTF-8")
        if (!braveKey.isNullOrBlank()) {
            execute(quick, Request.Builder().url("https://api.search.brave.com/res/v1/web/search?q=$q&count=8").header("Accept", "application/json").header("X-Subscription-Token", braveKey).get().build()).use { res ->
                if (!res.isSuccessful) throw failure("Brave Search", res)
                return@withContext JSONObject(res.body?.string().orEmpty()).optJSONObject("web")?.optJSONArray("results").objects().map { WebSource(it.optString("title"), it.optString("url"), it.optString("description").stripHtml()) }
            }
        }
        execute(quick, Request.Builder().url("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&srlimit=6&format=json&utf8=1").header("User-Agent", "BYAK-AI/0.5 (Android)").get().build()).use { res ->
            if (!res.isSuccessful) throw failure("Wikipedia", res)
            JSONObject(res.body?.string().orEmpty()).optJSONObject("query")?.optJSONArray("search").objects().map {
                val title = it.optString("title")
                WebSource(title, "https://en.wikipedia.org/wiki/${java.net.URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}", it.optString("snippet").stripHtml())
            }
        }
    }

    suspend fun readUrl(url: String, maxChars: Int = 8000): WebSource = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) throw ApiException("Only https links can be read", 400)
        execute(quick, Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) BYAK-AI").get().build()).use { res ->
            if (!res.isSuccessful) throw ApiException("Couldn't read the page (${res.code})", res.code)
            val type = res.header("Content-Type").orEmpty()
            if (!type.contains("text") && !type.contains("json")) throw ApiException("Only text pages can be read", 415)
            val raw = res.body?.source()?.let { src -> src.request(2L * 1024 * 1024); src.buffer.readUtf8(minOf(src.buffer.size, 2L * 1024 * 1024)) }.orEmpty()
            val title = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.trim().orEmpty()
            WebSource(title.ifBlank { res.request.url.host }, url, (if (type.contains("html")) raw.stripHtml() else raw).take(maxChars))
        }
    }

    suspend fun githubSearch(query: String): List<WebSource> = withContext(Dispatchers.IO) {
        execute(quick, Request.Builder().url("https://api.github.com/search/repositories?q=${java.net.URLEncoder.encode(query, "UTF-8")}&sort=stars&per_page=10").header("Accept", "application/vnd.github+json").header("User-Agent", "BYAK-AI").get().build()).use { res ->
            if (!res.isSuccessful) throw failure("GitHub search", res)
            JSONObject(res.body?.string().orEmpty()).optJSONArray("items").objects().map { WebSource(it.optString("full_name"), it.optString("html_url"), it.optString("description").takeIf { d -> d != "null" }.orEmpty() + " ★${it.optInt("stargazers_count")}") }
        }
    }

    suspend fun redditSearch(query: String): List<WebSource> = withContext(Dispatchers.IO) {
        execute(quick, Request.Builder().url("https://www.reddit.com/search.json?q=${java.net.URLEncoder.encode(query, "UTF-8")}&sort=relevance&limit=10&raw_json=1").header("User-Agent", "android:ai.byak.app:v0.5 (research)").get().build()).use { res ->
            if (!res.isSuccessful) throw failure("Reddit search", res)
            JSONObject(res.body?.string().orEmpty()).optJSONObject("data")?.optJSONArray("children").objects().mapNotNull { it.optJSONObject("data") }.map { WebSource(it.optString("title"), "https://www.reddit.com${it.optString("permalink")}", "r/${it.optString("subreddit")} · ${it.optString("selftext").take(500)}") }
        }
    }
}

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

internal fun String.stripHtml(): String = replace(Regex("<(script|style|noscript|svg)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
    .replace(Regex("\\s+"), " ").trim()
