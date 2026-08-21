package ai.byak.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int) : Exception(message)

class ApiClient(context: Context) {
    private val store = LocalStore(context)
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun register(name: String, email: String, password: String) = Session("local", "", name.ifBlank { "You" }, email)
    suspend fun login(email: String, password: String) = Session("local", "", "You", email)
    suspend fun google(idToken: String) = Session("local", "", "You", "")
    suspend fun logout() = Unit
    suspend fun deleteAccount() = withContext(Dispatchers.IO) { store.clearAll() }

    suspend fun providers(): List<Provider> = withContext(Dispatchers.IO) { store.providers() }

    suspend fun addProvider(type: String, apiKey: String, model: String, baseUrl: String = ""): Provider = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key is required" }
        if (type == "custom" && !baseUrl.startsWith("https://")) throw ApiException("Custom provider must use HTTPS", 400)
        store.addProvider(type, apiKey.trim(), model.trim(), baseUrl.trim())
    }

    suspend fun validateProvider(id: String) = withContext(Dispatchers.IO) {
        val provider = store.storedProvider(id)
        val request = when (provider.provider) {
            "gemini" -> Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?key=${encode(provider.apiKey)}").get().build()
            "anthropic" -> Request.Builder().url("https://api.anthropic.com/v1/models").header("x-api-key", provider.apiKey).header("anthropic-version", "2023-06-01").get().build()
            else -> Request.Builder().url("${openAiBase(provider)}/models").header("Authorization", "Bearer ${provider.apiKey}").get().build()
        }
        execute(request)
    }

    suspend fun deleteProvider(id: String) = withContext(Dispatchers.IO) { store.deleteProvider(id) }
    suspend fun conversations(): List<Conversation> = withContext(Dispatchers.IO) { store.conversations() }
    suspend fun createConversation(providerId: String?, model: String): Conversation = withContext(Dispatchers.IO) { store.createConversation(providerId, model) }
    suspend fun messages(id: String): List<ChatMessage> = withContext(Dispatchers.IO) { store.messages(id) }

    fun streamMessage(conversationId: String, content: String, providerId: String, model: String): Flow<String> = flow {
        store.addMessage(conversationId, "user", content)
        val provider = store.storedProvider(providerId)
        val history = store.messages(conversationId).takeLast(30)
        val context = store.fileContext()
        val answer = withContext(Dispatchers.IO) { complete(provider, model.ifBlank { provider.defaultModel }, history, context) }
        store.addMessage(conversationId, "assistant", answer)
        for (chunk in answer.chunked(64)) {
            emit(chunk)
            delay(8)
        }
    }

    suspend fun projects(): List<Project> = withContext(Dispatchers.IO) { store.projects() }
    suspend fun createProject(name: String, description: String): Project = withContext(Dispatchers.IO) { store.createProject(name, description) }
    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) { store.deleteProject(id) }
    suspend fun files(): List<UserFile> = withContext(Dispatchers.IO) { store.files() }
    suspend fun uploadText(name: String, mimeType: String, content: String): UserFile = withContext(Dispatchers.IO) {
        if (content.length > 1_000_000) throw ApiException("File is too large for local mode (1 MB maximum)", 413)
        store.addFile(name, mimeType, content)
    }
    suspend fun deleteFile(id: String) = withContext(Dispatchers.IO) { store.deleteFile(id) }
    suspend fun subscription(): JSONObject = JSONObject().put("plan", "local").put("status", "active")

    suspend fun research(query: String, source: String): List<ResearchResult> = withContext(Dispatchers.IO) {
        when (source) {
            "github" -> githubResearch(query)
            "reddit" -> redditResearch(query)
            else -> webResearch(query)
        }
    }

    private fun complete(provider: StoredProvider, model: String, history: List<ChatMessage>, documentContext: String): String {
        val system = buildString {
            append("You are BYAK AI, a helpful multi-provider assistant running in private device-only mode.")
            if (documentContext.isNotBlank()) append("\nUse this user-provided document context when relevant:\n").append(documentContext)
        }
        return when (provider.provider) {
            "anthropic" -> completeAnthropic(provider, model, system, history)
            "gemini" -> completeGemini(provider, model, system, history)
            else -> completeOpenAi(provider, model, system, history)
        }
    }

    private fun completeOpenAi(provider: StoredProvider, model: String, system: String, history: List<ChatMessage>): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.6)
        val builder = Request.Builder().url("${openAiBase(provider)}/chat/completions")
            .header("Authorization", "Bearer ${provider.apiKey}")
            .header("Content-Type", "application/json")
        if (provider.provider == "openrouter") builder.header("X-Title", "BYAK AI")
        val json = execute(builder.post(body.toString().toRequestBody(jsonType)).build())
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").ifBlank { "The provider returned an empty response." }
    }

    private fun completeAnthropic(provider: StoredProvider, model: String, system: String, history: List<ChatMessage>): String {
        val messages = JSONArray()
        history.filter { it.role == "user" || it.role == "assistant" }.forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        val body = JSONObject().put("model", model).put("system", system).put("max_tokens", 4096).put("messages", messages)
        val request = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", provider.apiKey).header("anthropic-version", "2023-06-01").header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType)).build()
        val content = execute(request).getJSONArray("content")
        return (0 until content.length()).joinToString("") { content.getJSONObject(it).optString("text") }.ifBlank { "The provider returned an empty response." }
    }

    private fun completeGemini(provider: StoredProvider, model: String, system: String, history: List<ChatMessage>): String {
        val contents = JSONArray()
        history.forEach {
            contents.put(JSONObject().put("role", if (it.role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", it.content))))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", 4096))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${encode(model)}:generateContent?key=${encode(provider.apiKey)}"
        val request = Request.Builder().url(url).header("Content-Type", "application/json").post(body.toString().toRequestBody(jsonType)).build()
        val parts = execute(request).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.ifBlank { "The provider returned an empty response." }
    }

    private fun githubResearch(query: String): List<ResearchResult> {
        val request = Request.Builder().url("https://api.github.com/search/repositories?q=${encode(query)}&sort=stars&per_page=10").header("Accept", "application/vnd.github+json").build()
        return execute(request).getJSONArray("items").objects().map { ResearchResult(it.getString("full_name"), it.getString("html_url"), it.optString("description")) }
    }

    private fun redditResearch(query: String): List<ResearchResult> {
        val request = Request.Builder().url("https://www.reddit.com/search.json?q=${encode(query)}&sort=relevance&limit=10&raw_json=1").header("User-Agent", "BYAK-AI/0.2 Android").build()
        return execute(request).getJSONObject("data").getJSONArray("children").objects().map { child ->
            val item = child.getJSONObject("data")
            ResearchResult(item.optString("title"), "https://www.reddit.com${item.optString("permalink")}", item.optString("selftext"))
        }
    }

    private fun webResearch(query: String): List<ResearchResult> {
        val request = Request.Builder().url("https://api.duckduckgo.com/?q=${encode(query)}&format=json&no_html=1&skip_disambig=1").build()
        val json = execute(request)
        val results = mutableListOf<ResearchResult>()
        json.optString("AbstractText").takeIf { it.isNotBlank() }?.let { results += ResearchResult(json.optString("Heading", query), json.optString("AbstractURL"), it) }
        json.optJSONArray("RelatedTopics")?.objects()?.forEach { item ->
            if (item.has("Text") && item.has("FirstURL")) results += ResearchResult(item.optString("Text").substringBefore(" - "), item.optString("FirstURL"), item.optString("Text"))
        }
        return results.take(10)
    }

    private fun openAiBase(provider: StoredProvider): String = when (provider.provider) {
        "openai" -> "https://api.openai.com/v1"
        "openrouter" -> "https://openrouter.ai/api/v1"
        "groq" -> "https://api.groq.com/openai/v1"
        "mistral" -> "https://api.mistral.ai/v1"
        "deepseek" -> "https://api.deepseek.com/v1"
        else -> provider.baseUrl.trimEnd('/')
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { if (text.isBlank()) JSONObject() else JSONObject(text) }.getOrElse { JSONObject().put("raw", text) }
            if (!response.isSuccessful) {
                val message = json.optJSONObject("error")?.optString("message")
                    ?: json.optString("message").ifBlank { "Provider request failed (${response.code})" }
                throw ApiException(message, response.code)
            }
            return json
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
