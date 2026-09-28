package ai.byak.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Everything the app's screens need. Two implementations:
 *  - [ai.byak.app.data.local.LocalApi]: runs entirely on the phone (default, no server needed)
 *  - [ApiClient]: talks to a self-hosted BYAK server
 */
interface ByakApi {
    /** True when data lives on this device and AI providers are called directly. */
    val isLocal: Boolean

    suspend fun logout()
    suspend fun deleteAccount(): String?
    suspend fun profile(): Pair<Profile, Subscription>
    suspend fun updateProfile(name: String, customInstructions: String)
    suspend fun changePassword(current: String, new: String)
    suspend fun exportData(): String
    suspend fun devices(): List<Device>
    suspend fun revokeDevice(id: String)

    suspend fun catalog(): List<CatalogProvider>
    suspend fun providers(): List<Provider>
    suspend fun addProvider(type: String, apiKey: String, model: String, baseUrl: String = ""): Provider
    suspend fun updateProvider(id: String, defaultModel: String? = null, enabled: Boolean? = null, apiKey: String? = null): Provider
    suspend fun validateProvider(id: String)
    suspend fun providerModels(id: String): List<String>
    suspend fun deleteProvider(id: String)

    suspend fun conversations(query: String = "", archived: Boolean = false): List<Conversation>
    suspend fun createConversation(providerId: String?, model: String, projectId: String? = null): Conversation
    suspend fun updateConversation(id: String, title: String? = null, pinned: Boolean? = null, archived: Boolean? = null, providerId: String? = null, model: String? = null): Conversation
    suspend fun deleteConversation(id: String)
    suspend fun messages(id: String): List<ChatMessage>
    suspend fun exportConversation(id: String): String
    fun streamMessage(conversationId: String, content: String, providerId: String?, model: String?, images: List<ImageDraft> = emptyList(), webSearch: Boolean = false): Flow<StreamEvent>
    fun regenerate(conversationId: String, providerId: String?, model: String?, webSearch: Boolean = false): Flow<StreamEvent>
    fun editMessage(conversationId: String, messageId: String, content: String, providerId: String?, model: String?, webSearch: Boolean = false): Flow<StreamEvent>
    suspend fun attachment(conversationId: String, messageId: String, index: Int): ByteArray
    suspend fun compare(content: String, targets: List<Pair<String, String>>): List<ComparisonResult>

    suspend fun projects(): List<Project>
    suspend fun createProject(name: String, description: String, instructions: String = ""): Project
    suspend fun updateProject(id: String, name: String, description: String, instructions: String): Project
    suspend fun deleteProject(id: String)
    suspend fun research(query: String, source: String): List<ResearchResult>
    suspend fun files(): List<UserFile>
    suspend fun uploadText(name: String, mimeType: String, content: String, projectId: String? = null): UserFile
    suspend fun deleteFile(id: String)

    suspend fun prompts(): Pair<List<SavedPrompt>, List<SavedPrompt>>
    suspend fun savePrompt(id: String?, title: String, content: String)
    suspend fun deletePrompt(id: String)
    suspend fun memory(): Pair<Boolean, List<Memory>>
    suspend fun setMemoryEnabled(enabled: Boolean)
    suspend fun addMemory(content: String)
    suspend fun deleteMemory(id: String)
    suspend fun clearMemory()
    suspend fun usage(days: Int = 30): Usage

    suspend fun subscription(): Subscription
    suspend fun verifyPurchases(tokens: List<String>): Subscription
    /** Remembers which base plan (monthly/yearly) the user just bought; only the on-device mode needs it. */
    suspend fun rememberPlanChoice(basePlanId: String) {}
}
