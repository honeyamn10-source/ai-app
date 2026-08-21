package ai.byak.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class StoredProvider(
    val id: String,
    val provider: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val apiKey: String
)

internal class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("byak_local_data_v1", Context.MODE_PRIVATE)
    private val lock = Any()

    fun providers(): List<Provider> = synchronized(lock) {
        array(PROVIDERS).objects().map {
            Provider(
                id = it.getString("id"),
                provider = it.getString("provider"),
                name = it.getString("name"),
                maskedKey = it.getString("maskedKey"),
                defaultModel = it.getString("defaultModel")
            )
        }
    }

    fun storedProvider(id: String): StoredProvider = synchronized(lock) {
        val item = array(PROVIDERS).objects().firstOrNull { it.getString("id") == id }
            ?: throw ApiException("Provider not found", 404)
        StoredProvider(
            id = id,
            provider = item.getString("provider"),
            name = item.getString("name"),
            baseUrl = item.optString("baseUrl"),
            defaultModel = item.getString("defaultModel"),
            apiKey = decrypt(item.getString("secret"), id)
        )
    }

    fun addProvider(type: String, apiKey: String, model: String, baseUrl: String): Provider = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        val name = providerName(type)
        val masked = if (apiKey.length <= 8) "••••••••" else "${apiKey.take(4)}••••${apiKey.takeLast(4)}"
        val item = JSONObject()
            .put("id", id)
            .put("provider", type)
            .put("name", name)
            .put("baseUrl", baseUrl.trimEnd('/'))
            .put("defaultModel", model)
            .put("maskedKey", masked)
            .put("secret", encrypt(apiKey, id))
        save(PROVIDERS, JSONArray().put(item))
        Provider(id, type, name, masked, model)
    }

    fun deleteProvider(id: String) = synchronized(lock) { remove(PROVIDERS) { it.getString("id") == id } }

    fun conversations(): List<Conversation> = synchronized(lock) {
        array(CONVERSATIONS).objects().reversed().map {
            Conversation(it.getString("id"), it.getString("title"), it.optString("providerId").ifBlank { null }, it.optString("model"))
        }
    }

    fun createConversation(providerId: String?, model: String): Conversation = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        val item = JSONObject().put("id", id).put("title", "New conversation").put("providerId", providerId ?: "").put("model", model)
        save(CONVERSATIONS, array(CONVERSATIONS).put(item))
        Conversation(id, "New conversation", providerId, model)
    }

    fun messages(conversationId: String): List<ChatMessage> = synchronized(lock) {
        array(MESSAGES).objects().filter { it.getString("conversationId") == conversationId }.map {
            ChatMessage(it.getString("id"), it.getString("role"), it.getString("content"))
        }
    }

    fun addMessage(conversationId: String, role: String, content: String) = synchronized(lock) {
        val item = JSONObject().put("id", UUID.randomUUID().toString()).put("conversationId", conversationId).put("role", role).put("content", content)
        save(MESSAGES, array(MESSAGES).put(item))
        if (role == "user") {
            val conversations = array(CONVERSATIONS)
            for (index in 0 until conversations.length()) {
                val conversation = conversations.getJSONObject(index)
                if (conversation.getString("id") == conversationId && conversation.getString("title") == "New conversation") {
                    conversation.put("title", content.take(70))
                    save(CONVERSATIONS, conversations)
                    break
                }
            }
        }
    }

    fun projects(): List<Project> = synchronized(lock) {
        array(PROJECTS).objects().map { Project(it.getString("id"), it.getString("name"), it.optString("description")) }
    }

    fun createProject(name: String, description: String): Project = synchronized(lock) {
        val item = JSONObject().put("id", UUID.randomUUID().toString()).put("name", name).put("description", description)
        save(PROJECTS, array(PROJECTS).put(item))
        Project(item.getString("id"), name, description)
    }

    fun deleteProject(id: String) = synchronized(lock) { remove(PROJECTS) { it.getString("id") == id } }

    fun files(): List<UserFile> = synchronized(lock) {
        array(FILES).objects().map { UserFile(it.getString("id"), it.getString("name"), it.getString("mimeType"), it.optInt("chunkCount", 1)) }
    }

    fun fileContext(maxCharacters: Int = 10_000): String = synchronized(lock) {
        array(FILES).objects().joinToString("\n\n") { "Document: ${it.getString("name")}\n${it.optString("content")}" }.take(maxCharacters)
    }

    fun addFile(name: String, mimeType: String, content: String): UserFile = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        val chunks = maxOf(1, (content.length + 1499) / 1500)
        val item = JSONObject().put("id", id).put("name", name).put("mimeType", mimeType).put("content", content.take(1_000_000)).put("chunkCount", chunks)
        save(FILES, array(FILES).put(item))
        UserFile(id, name, mimeType, chunks)
    }

    fun deleteFile(id: String) = synchronized(lock) { remove(FILES) { it.getString("id") == id } }

    fun clearAll() = synchronized(lock) { preferences.edit().clear().apply() }

    private fun array(key: String): JSONArray = runCatching { JSONArray(preferences.getString(key, "[]")) }.getOrDefault(JSONArray())
    private fun save(key: String, value: JSONArray) { preferences.edit().putString(key, value.toString()).apply() }
    private fun remove(key: String, predicate: (JSONObject) -> Boolean) {
        val kept = JSONArray()
        array(key).objects().filterNot(predicate).forEach { kept.put(it) }
        save(key, kept)
    }

    private fun encrypt(value: String, aad: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(aad.toByteArray())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String, aad: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad.toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun providerName(type: String) = when (type) {
        "openai" -> "OpenAI"
        "anthropic" -> "Anthropic"
        "gemini" -> "Google Gemini"
        "openrouter" -> "OpenRouter"
        "groq" -> "Groq"
        "mistral" -> "Mistral"
        "deepseek" -> "DeepSeek"
        else -> "Custom provider"
    }

    companion object {
        private const val PROVIDERS = "providers"
        private const val CONVERSATIONS = "conversations"
        private const val MESSAGES = "messages"
        private const val PROJECTS = "projects"
        private const val FILES = "files"
        private const val KEY_ALIAS = "byak_local_provider_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
