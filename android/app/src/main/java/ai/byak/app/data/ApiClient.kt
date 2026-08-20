package ai.byak.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int) : Exception(message)

class ApiClient(baseUrl: String, private val sessions: SessionStore) {
    private val base = baseUrl.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(100, TimeUnit.SECONDS).build()

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("$base$path").header("Accept", "application/json")
        if (authenticated) sessions.session.first()?.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        val payload = body?.toString()?.toRequestBody(jsonType)
        when (method) { "POST" -> builder.post(payload ?: "{}".toRequestBody(jsonType)); "PATCH" -> builder.patch(payload ?: "{}".toRequestBody(jsonType)); "PUT" -> builder.put(payload ?: "{}".toRequestBody(jsonType)); "DELETE" -> if (payload != null) builder.delete(payload) else builder.delete(); else -> builder.get() }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty(); val result = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (!response.isSuccessful) throw ApiException(result.optJSONObject("error")?.optString("message") ?: "Request failed", response.code)
            result
        }
    }

    suspend fun register(name: String, email: String, password: String): Session = auth("/v1/auth/register", JSONObject().put("name", name).put("email", email).put("password", password))
    suspend fun login(email: String, password: String): Session = auth("/v1/auth/login", JSONObject().put("email", email).put("password", password))
    suspend fun google(idToken: String): Session = auth("/v1/auth/google", JSONObject().put("idToken", idToken))
    private suspend fun auth(path: String, body: JSONObject): Session { val data = request(path, "POST", body, false); val user = data.getJSONObject("user"); return Session(data.getString("accessToken"), data.getString("refreshToken"), user.getString("name"), user.getString("email")).also { sessions.save(it) } }
    suspend fun logout() { runCatching { request("/v1/auth/logout", "POST", JSONObject()) }; sessions.clear() }
    suspend fun deleteAccount() { request("/v1/me", "DELETE"); sessions.clear() }

    suspend fun providers(): List<Provider> = request("/v1/providers").getJSONArray("items").objects().map { Provider(it.getString("id"), it.getString("provider"), it.getString("name"), it.optString("maskedKey"), it.optString("defaultModel")) }
    suspend fun addProvider(type: String, apiKey: String, model: String, baseUrl: String = ""): Provider { val data = request("/v1/providers", "POST", JSONObject().put("provider", type).put("apiKey", apiKey).put("defaultModel", model).put("baseUrl", baseUrl)); return Provider(data.getString("id"), data.getString("provider"), data.getString("name"), data.getString("maskedKey"), data.optString("defaultModel")) }
    suspend fun validateProvider(id: String) { request("/v1/providers/$id/validate", "POST", JSONObject()) }
    suspend fun deleteProvider(id: String) { request("/v1/providers/$id", "DELETE") }

    suspend fun conversations(): List<Conversation> = request("/v1/conversations").getJSONArray("items").objects().map { Conversation(it.getString("id"), it.getString("title"), it.optString("providerId").ifBlank { null }, it.optString("model")) }
    suspend fun createConversation(providerId: String?, model: String): Conversation { val data = request("/v1/conversations", "POST", JSONObject().put("title", "New conversation").put("providerId", providerId).put("model", model)); return Conversation(data.getString("id"), data.getString("title"), providerId, model) }
    suspend fun messages(id: String): List<ChatMessage> = request("/v1/conversations/$id").getJSONArray("messages").objects().map { ChatMessage(it.getString("id"), it.getString("role"), it.getString("content")) }
    fun streamMessage(conversationId: String, content: String, providerId: String, model: String): Flow<String> = flow {
        val token = sessions.session.first()?.accessToken ?: throw ApiException("Sign in required", 401)
        val body = JSONObject().put("content", content).put("providerId", providerId).put("model", model).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$base/v1/conversations/$conversationId/stream").header("Authorization", "Bearer $token").header("Accept", "text/event-stream").post(body).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().use { response -> if (!response.isSuccessful) throw ApiException("Generation failed", response.code); val source = response.body?.source() ?: return@use; while (!source.exhausted()) { val line = source.readUtf8Line().orEmpty(); if (line.startsWith("data:")) { val data = JSONObject(line.removePrefix("data:").trim()); data.optString("delta").takeIf { it.isNotEmpty() }?.let { emit(it) } } } } }
    }

    suspend fun projects(): List<Project> = request("/v1/projects").getJSONArray("items").objects().map { Project(it.getString("id"), it.getString("name"), it.optString("description")) }
    suspend fun createProject(name: String, description: String): Project { val it = request("/v1/projects", "POST", JSONObject().put("name", name).put("description", description)); return Project(it.getString("id"), it.getString("name"), it.optString("description")) }
    suspend fun deleteProject(id: String) { request("/v1/projects/$id", "DELETE") }

    suspend fun research(query: String, source: String): List<ResearchResult> = request("/v1/research", "POST", JSONObject().put("query", query).put("source", source)).getJSONArray("items").objects().map { ResearchResult(it.getString("title"), it.getString("url"), it.optString("summary")) }
    suspend fun files(): List<UserFile> = request("/v1/files").getJSONArray("items").objects().map { UserFile(it.getString("id"), it.getString("name"), it.getString("mimeType"), it.optInt("chunkCount")) }
    suspend fun uploadText(name: String, mimeType: String, content: String): UserFile { val it = request("/v1/files", "POST", JSONObject().put("name", name).put("mimeType", mimeType).put("content", content)); return UserFile(it.getString("id"), it.getString("name"), it.getString("mimeType"), it.getInt("chunkCount")) }
    suspend fun deleteFile(id: String) { request("/v1/files/$id", "DELETE") }
    suspend fun subscription(): JSONObject = request("/v1/subscription")
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

