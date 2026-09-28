package ai.byak.app.data

data class Session(val accessToken: String, val refreshToken: String, val name: String, val email: String)
data class Provider(val id: String, val provider: String, val name: String, val maskedKey: String, val defaultModel: String, val enabled: Boolean = true, val lastValidatedAt: String? = null)
data class CatalogProvider(val id: String, val name: String, val models: List<String>, val localOnly: Boolean, val keyOptional: Boolean)
data class Conversation(
    val id: String, val title: String, val providerId: String? = null, val model: String = "",
    val projectId: String? = null, val pinned: Boolean = false, val preview: String = "", val updatedAt: String = ""
)
data class Citation(val id: Int, val title: String, val chunk: Int, val kind: String = "document", val url: String? = null)
data class AttachmentRef(val index: Int, val mimeType: String)
/** An image picked in the composer, already downscaled and encoded for upload. */
class ImageDraft(val mimeType: String, val bytes: ByteArray)
data class ChatMessage(
    val id: String, val role: String, val content: String, val pending: Boolean = false,
    val status: String = "complete", val model: String? = null, val citations: List<Citation> = emptyList(),
    val attachments: List<AttachmentRef> = emptyList(), val localImages: List<ImageDraft> = emptyList()
)
data class Project(val id: String, val name: String, val description: String = "", val instructions: String = "", val conversationCount: Int = 0, val fileCount: Int = 0)
data class ResearchResult(val title: String, val url: String, val summary: String)
data class UserFile(val id: String, val name: String, val mimeType: String, val chunkCount: Int, val size: Long = 0, val projectId: String? = null)
data class Memory(val id: String, val content: String)
data class SavedPrompt(val id: String, val title: String, val content: String, val category: String = "", val builtIn: Boolean = false) {
    /** Template text with the {{input}} marker removed, ready for the composer. */
    val composerText: String get() = content.replace("{{input}}", "").trimEnd() + if (content.contains("{{input}}")) "\n" else ""
}
/** One row of the Free vs Pro comparison, served by the backend so limits stay in one place. */
data class ProHighlight(val key: String, val title: String, val free: String, val pro: String)
/** One model's answer in a side-by-side comparison. */
data class ComparisonResult(val providerName: String, val model: String, val content: String, val error: String?, val ms: Long, val outputTokens: Long)
data class Device(val id: String, val device: String, val lastUsedAt: String, val current: Boolean)
data class ModelUsage(val provider: String, val model: String, val requests: Int, val inputTokens: Long, val outputTokens: Long)
data class DayUsage(val day: String, val requests: Int, val tokens: Long)
data class Usage(val requests: Int, val inputTokens: Long, val outputTokens: Long, val byModel: List<ModelUsage>, val byDay: List<DayUsage>)
data class Profile(val name: String, val email: String, val memoryEnabled: Boolean, val customInstructions: String, val hasPassword: Boolean)
data class Subscription(
    val plan: String, val tier: String, val status: String, val expiresAt: String?, val autoRenewing: Boolean,
    val productId: String?, val billingAccountId: String, val verificationAvailable: Boolean, val limits: Map<String, Int>,
    val webSearchesToday: Int = 0, val imagesToday: Int = 0, val highlights: List<ProHighlight> = emptyList(),
    val basePlanId: String? = null, val comparisonsToday: Int = 0
) {
    val isPro get() = tier == "pro"
    val webSearchesLeft get() = ((limits["webSearchesPerDay"] ?: 0) - webSearchesToday).coerceAtLeast(0)
    val imagesLeft get() = ((limits["imagesPerDay"] ?: 0) - imagesToday).coerceAtLeast(0)
    val comparisonsLeft get() = ((limits["comparisonsPerDay"] ?: 0) - comparisonsToday).coerceAtLeast(0)
    companion object { val FREE = Subscription("free", "free", "active", null, false, null, "", false, emptyMap()) }
}

/** Events emitted while an answer streams in. */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Complete(val message: ChatMessage) : StreamEvent
    data class Failed(val message: String) : StreamEvent
    data class Status(val message: String) : StreamEvent
}
