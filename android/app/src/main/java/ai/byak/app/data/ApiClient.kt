package ai.byak.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int, val code: String? = null) : Exception(message)

/** The server address comes from [SettingsStore] on every call, so changing it in the app takes effect immediately. */
class ApiClient(private val settings: SettingsStore, private val sessions: SessionStore) {
    private suspend fun base(): String = settings.currentServerUrl().trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val streamClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
    private val refreshLock = Mutex()

    private fun build(url: String, method: String, body: JSONObject?, token: String?, accept: String = "application/json"): Request {
        val builder = Request.Builder().url(url).header("Accept", accept)
        token?.let { builder.header("Authorization", "Bearer $it") }
        val payload = (body ?: JSONObject()).toString().toRequestBody(jsonType)
        when (method) {
            "POST" -> builder.post(payload); "PATCH" -> builder.patch(payload); "PUT" -> builder.put(payload)
            "DELETE" -> if (body != null) builder.delete(payload) else builder.delete()
            else -> builder.get()
        }
        return builder.build()
    }

    private fun failure(response: Response, text: String): ApiException {
        val error = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
        val message = error?.optString("message")?.takeIf { it.isNotBlank() } ?: when (response.code) {
            401 -> "Please sign in again"; 429 -> "Too many requests — try again in a minute"; in 500..599 -> "The server had a problem. Please try again."
            else -> "Request failed (${response.code})"
        }
        return ApiException(message, response.code, error?.optString("code")?.ifBlank { null })
    }

    /** Executes a request; on 401 refreshes the session once and retries. Returns the raw body bytes. */
    private suspend fun executeBytes(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true): ByteArray = withContext(Dispatchers.IO) {
        val url = "${base()}$path"
        suspend fun attempt(token: String?): Pair<Response, ByteArray> {
            val response = try { client.newCall(build(url, method, body, token)).execute() }
                catch (e: IllegalArgumentException) { throw ApiException("The server address \"${base()}\" isn't valid. Change it in Settings → Server.", 0) }
                catch (e: IOException) { throw ApiException("Can't reach the BYAK server at ${base()}. Check your connection or the server address.", 0) }
            return response to response.use { it.body?.bytes() ?: ByteArray(0) }
        }
        val token = if (authenticated) sessions.current()?.accessToken else null
        var (response, bytes) = attempt(token)
        if (authenticated && response.code == 401 && refreshSession(token)) {
            val retried = attempt(sessions.current()?.accessToken); response = retried.first; bytes = retried.second
        }
        if (!response.isSuccessful) throw failure(response, bytes.toString(Charsets.UTF_8))
        bytes
    }

    private suspend fun execute(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true): String =
        executeBytes(path, method, body, authenticated).toString(Charsets.UTF_8)

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true): JSONObject =
        execute(path, method, body, authenticated).let { if (it.isBlank()) JSONObject() else JSONObject(it) }

    /** Serialised so parallel 401s trigger one refresh; returns false (and signs out) when the refresh token is no longer valid. */
    private suspend fun refreshSession(staleAccessToken: String?): Boolean = refreshLock.withLock {
        val current = sessions.current() ?: return false
        if (current.accessToken != staleAccessToken) return true
        val response = try { client.newCall(build("${base()}/v1/auth/refresh", "POST", JSONObject().put("refreshToken", current.refreshToken), null)).execute() } catch (e: Exception) { return false }
        val text = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) { if (response.code == 401) sessions.clear(); return false }
        val data = JSONObject(text)
        sessions.updateTokens(data.getString("accessToken"), data.getString("refreshToken"))
        true
    }

    // ---------- auth & account ----------
    suspend fun register(name: String, email: String, password: String): Session = auth("/v1/auth/register", JSONObject().put("name", name).put("email", email).put("password", password))
    suspend fun login(email: String, password: String): Session = auth("/v1/auth/login", JSONObject().put("email", email).put("password", password))
    suspend fun google(idToken: String): Session = auth("/v1/auth/google", JSONObject().put("idToken", idToken))
    private suspend fun auth(path: String, body: JSONObject): Session {
        val data = request(path, "POST", body, false); val user = data.getJSONObject("user")
        return Session(data.getString("accessToken"), data.getString("refreshToken"), user.getString("name"), user.getString("email")).also { sessions.save(it) }
    }
    suspend fun logout() { runCatching { request("/v1/auth/logout", "POST", JSONObject()) }; sessions.clear() }
    suspend fun deleteAccount(): String? { val result = request("/v1/me", "DELETE"); sessions.clear(); return result.optString("notice").ifBlank { null } }
    suspend fun profile(): Pair<Profile, Subscription> {
        val data = request("/v1/me")
        val profile = Profile(data.getString("name"), data.getString("email"), data.optBoolean("memoryEnabled"), data.optString("customInstructions"), data.optBoolean("hasPassword"))
        sessions.updateProfile(profile.name, profile.email)
        return profile to (data.optJSONObject("subscription")?.toSubscription() ?: Subscription.FREE)
    }
    suspend fun updateProfile(name: String, customInstructions: String) { request("/v1/me", "PATCH", JSONObject().put("name", name).put("customInstructions", customInstructions)) }
    suspend fun changePassword(current: String, new: String) { request("/v1/auth/password", "POST", JSONObject().put("currentPassword", current).put("newPassword", new)) }
    suspend fun exportData(): String = execute("/v1/me/export")
    suspend fun devices(): List<Device> = request("/v1/devices").getJSONArray("items").objects().map { Device(it.getString("id"), it.optString("device"), it.optString("lastUsedAt"), it.optBoolean("current")) }
    suspend fun revokeDevice(id: String) { request("/v1/devices/${id.enc()}", "DELETE") }

    // ---------- providers ----------
    suspend fun catalog(): List<CatalogProvider> = request("/v1/models/catalog", authenticated = false).getJSONArray("providers").objects().map {
        CatalogProvider(it.getString("id"), it.getString("name"), it.optJSONArray("models")?.strings().orEmpty(), it.optBoolean("localOnly"), it.optBoolean("keyOptional"))
    }
    suspend fun providers(): List<Provider> = request("/v1/providers").getJSONArray("items").objects().map { it.toProvider() }
    suspend fun addProvider(type: String, apiKey: String, model: String, baseUrl: String = ""): Provider =
        request("/v1/providers", "POST", JSONObject().put("provider", type).put("apiKey", apiKey).put("defaultModel", model).put("baseUrl", baseUrl)).toProvider()
    suspend fun updateProvider(id: String, defaultModel: String? = null, enabled: Boolean? = null, apiKey: String? = null): Provider {
        val body = JSONObject(); defaultModel?.let { body.put("defaultModel", it) }; enabled?.let { body.put("enabled", it) }; apiKey?.let { body.put("apiKey", it) }
        return request("/v1/providers/${id.enc()}", "PATCH", body).toProvider()
    }
    suspend fun validateProvider(id: String) { request("/v1/providers/${id.enc()}/validate", "POST", JSONObject()) }
    suspend fun providerModels(id: String): List<String> = request("/v1/providers/${id.enc()}/models").getJSONArray("items").strings()
    suspend fun deleteProvider(id: String) { request("/v1/providers/${id.enc()}", "DELETE") }

    // ---------- conversations ----------
    suspend fun conversations(query: String = "", archived: Boolean = false): List<Conversation> =
        request("/v1/conversations?archived=$archived" + (if (query.isNotBlank()) "&q=${query.enc()}" else "")).getJSONArray("items").objects().map { it.toConversation() }
    suspend fun createConversation(providerId: String?, model: String, projectId: String? = null): Conversation {
        val body = JSONObject().put("title", "New conversation").put("model", model)
        providerId?.let { body.put("providerId", it) }; projectId?.let { body.put("projectId", it) }
        return request("/v1/conversations", "POST", body).toConversation()
    }
    suspend fun updateConversation(id: String, title: String? = null, pinned: Boolean? = null, archived: Boolean? = null, providerId: String? = null, model: String? = null): Conversation {
        val body = JSONObject(); title?.let { body.put("title", it) }; pinned?.let { body.put("pinned", it) }; archived?.let { body.put("archived", it) }
        providerId?.let { body.put("providerId", it) }; model?.let { body.put("model", it) }
        return request("/v1/conversations/${id.enc()}", "PATCH", body).toConversation()
    }
    suspend fun deleteConversation(id: String) { request("/v1/conversations/${id.enc()}", "DELETE") }
    suspend fun messages(id: String): List<ChatMessage> = request("/v1/conversations/${id.enc()}").getJSONArray("messages").objects().map { it.toMessage() }
    suspend fun exportConversation(id: String): String = execute("/v1/exports/conversations/${id.enc()}?format=markdown")

    fun streamMessage(conversationId: String, content: String, providerId: String?, model: String?, images: List<ImageDraft> = emptyList(), webSearch: Boolean = false): Flow<StreamEvent> {
        val body = generationBody(providerId, model, webSearch).put("content", content)
        if (images.isNotEmpty()) body.put("attachments", JSONArray(images.map { JSONObject().put("type", "image").put("mimeType", it.mimeType).put("data", Base64.getEncoder().encodeToString(it.bytes)) }))
        return stream("/v1/conversations/${conversationId.enc()}/stream", body)
    }
    fun regenerate(conversationId: String, providerId: String?, model: String?, webSearch: Boolean = false): Flow<StreamEvent> =
        stream("/v1/conversations/${conversationId.enc()}/regenerate", generationBody(providerId, model, webSearch))
    fun editMessage(conversationId: String, messageId: String, content: String, providerId: String?, model: String?, webSearch: Boolean = false): Flow<StreamEvent> =
        stream("/v1/conversations/${conversationId.enc()}/edit", generationBody(providerId, model, webSearch).put("messageId", messageId).put("content", content))
    private fun generationBody(providerId: String?, model: String?, webSearch: Boolean): JSONObject {
        val body = JSONObject(); providerId?.let { body.put("providerId", it) }; model?.takeIf { it.isNotBlank() }?.let { body.put("model", it) }
        if (webSearch) body.put("webSearch", true)
        return body
    }
    suspend fun attachment(conversationId: String, messageId: String, index: Int): ByteArray =
        executeBytes("/v1/conversations/${conversationId.enc()}/messages/${messageId.enc()}/attachments/$index")

    /** Streams SSE events. Cancelling the collector cancels the HTTP call, which tells the server to stop generating. */
    private fun stream(path: String, body: JSONObject): Flow<StreamEvent> = flow {
        var token = sessions.current()?.accessToken ?: throw ApiException("Sign in required", 401)
        var response: Response? = null
        for (attempt in 0..1) {
            val call = streamClient.newCall(build("${base()}$path", "POST", body, token, "text/event-stream"))
            val job = currentCoroutineContext()[Job]
            val handle = job?.invokeOnCompletion { call.cancel() }
            val current = try { call.execute() } catch (e: IOException) { handle?.dispose(); throw ApiException("Can't reach the BYAK server. Check your connection.", 0) }
            if (current.code == 401 && attempt == 0 && refreshSession(token)) { current.close(); handle?.dispose(); token = sessions.current()?.accessToken ?: break; continue }
            response = current; break
        }
        val active = response ?: throw ApiException("Please sign in again", 401)
        active.use { res ->
            if (!res.isSuccessful) throw failure(res, res.body?.string().orEmpty())
            val source = res.body?.source() ?: return@use
            val parser = SseParser()
            while (true) {
                val line = source.readUtf8Line() ?: break
                parser.feed(line)?.let { emit(it) }
            }
            parser.finish()?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    // ---------- projects, files, research ----------
    suspend fun projects(): List<Project> = request("/v1/projects").getJSONArray("items").objects().map { it.toProject() }
    suspend fun createProject(name: String, description: String, instructions: String = ""): Project =
        request("/v1/projects", "POST", JSONObject().put("name", name).put("description", description).put("instructions", instructions)).toProject()
    suspend fun updateProject(id: String, name: String, description: String, instructions: String): Project =
        request("/v1/projects/${id.enc()}", "PATCH", JSONObject().put("name", name).put("description", description).put("instructions", instructions)).toProject()
    suspend fun deleteProject(id: String) { request("/v1/projects/${id.enc()}", "DELETE") }

    suspend fun research(query: String, source: String): List<ResearchResult> =
        request("/v1/research", "POST", JSONObject().put("query", query).put("source", source)).getJSONArray("items").objects().map { ResearchResult(it.getString("title"), it.getString("url"), it.optString("summary")) }
    suspend fun files(): List<UserFile> = request("/v1/files").getJSONArray("items").objects().map { it.toFile() }
    suspend fun uploadText(name: String, mimeType: String, content: String, projectId: String? = null): UserFile {
        val body = JSONObject().put("name", name).put("mimeType", mimeType).put("content", content); projectId?.let { body.put("projectId", it) }
        return request("/v1/files", "POST", body).toFile()
    }
    suspend fun deleteFile(id: String) { request("/v1/files/${id.enc()}", "DELETE") }

    // ---------- compare ----------
    suspend fun compare(content: String, targets: List<Pair<String, String>>): List<ComparisonResult> {
        val body = JSONObject().put("content", content).put("targets", JSONArray(targets.map { (providerId, model) -> JSONObject().put("providerId", providerId).put("model", model) }))
        return request("/v1/compare", "POST", body).getJSONArray("results").objects().map {
            ComparisonResult(it.optString("providerName"), it.optString("model"), it.optString("content"), it.nullableString("error"), it.optLong("ms"), it.optJSONObject("usage")?.optLong("outputTokens") ?: 0)
        }
    }

    // ---------- prompt library ----------
    suspend fun prompts(): Pair<List<SavedPrompt>, List<SavedPrompt>> {
        val data = request("/v1/prompts")
        val mine = data.getJSONArray("items").objects().map { SavedPrompt(it.getString("id"), it.getString("title"), it.getString("content")) }
        val templates = data.optJSONArray("templates")?.objects().orEmpty().map { SavedPrompt(it.getString("id"), it.getString("title"), it.getString("content"), it.optString("category"), builtIn = true) }
        return mine to templates
    }
    suspend fun savePrompt(id: String?, title: String, content: String) {
        val body = JSONObject().put("title", title).put("content", content)
        if (id == null) request("/v1/prompts", "POST", body) else request("/v1/prompts/${id.enc()}", "PATCH", body)
    }
    suspend fun deletePrompt(id: String) { request("/v1/prompts/${id.enc()}", "DELETE") }

    // ---------- memory, usage, billing ----------
    suspend fun memory(): Pair<Boolean, List<Memory>> { val data = request("/v1/memory"); return data.optBoolean("enabled") to data.getJSONArray("items").objects().map { Memory(it.getString("id"), it.getString("content")) } }
    suspend fun setMemoryEnabled(enabled: Boolean) { request("/v1/memory/settings", "PUT", JSONObject().put("enabled", enabled)) }
    suspend fun addMemory(content: String) { request("/v1/memory", "POST", JSONObject().put("content", content)) }
    suspend fun deleteMemory(id: String) { request("/v1/memory/${id.enc()}", "DELETE") }
    suspend fun clearMemory() { request("/v1/memory", "DELETE") }
    suspend fun usage(days: Int = 30): Usage {
        val data = request("/v1/usage?days=$days")
        return Usage(
            data.optInt("requests"), data.optLong("inputTokens"), data.optLong("outputTokens"),
            data.getJSONArray("byModel").objects().map { ModelUsage(it.optString("provider"), it.optString("model"), it.optInt("requests"), it.optLong("inputTokens"), it.optLong("outputTokens")) },
            data.getJSONArray("byDay").objects().map { DayUsage(it.getString("day"), it.optInt("requests"), it.optLong("tokens")) }
        )
    }
    suspend fun subscription(): Subscription = request("/v1/subscription").toSubscription()
    suspend fun verifyPurchases(tokens: List<String>): Subscription = request("/v1/billing/google/verify", "POST", JSONObject().put("purchaseTokens", JSONArray(tokens))).toSubscription()
}

private fun String.enc(): String = URLEncoder.encode(this, "UTF-8")
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
private fun JSONObject.toProvider() = Provider(getString("id"), getString("provider"), getString("name"), optString("maskedKey"), optString("defaultModel"), optBoolean("enabled", true), nullableString("lastValidatedAt"))
private fun JSONObject.toConversation() = Conversation(getString("id"), optString("title", "New conversation"), nullableString("providerId"), optString("model"), nullableString("projectId"), optBoolean("pinned"), optString("preview"), optString("updatedAt"))
private fun JSONObject.toProject() = Project(getString("id"), getString("name"), optString("description"), optString("instructions"), optInt("conversationCount"), optInt("fileCount"))
private fun JSONObject.toFile() = UserFile(getString("id"), getString("name"), optString("mimeType"), optInt("chunkCount"), optLong("size"), nullableString("projectId"))
internal fun JSONObject.toSubscription(): Subscription {
    val limits = optJSONObject("limits")?.let { json -> json.keys().asSequence().associateWith { json.optInt(it) } }.orEmpty()
    val usage = optJSONObject("usageToday")
    val highlights = optJSONArray("highlights")?.objects().orEmpty().map { ProHighlight(it.optString("key"), it.optString("title"), it.optString("free"), it.optString("pro")) }
    return Subscription(optString("plan", "free"), optString("tier", "free"), optString("status", "active"), nullableString("expiresAt"), optBoolean("autoRenewing"), nullableString("productId"), optString("billingAccountId"), optBoolean("verificationAvailable"), limits,
        usage?.optInt("webSearches") ?: 0, usage?.optInt("images") ?: 0, highlights, nullableString("basePlanId"), usage?.optInt("comparisons") ?: 0)
}
