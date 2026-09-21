package ai.byak.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Session(
    val userId: String,
    val name: String,
    val email: String,
    val expiresAtEpochSeconds: Long,
    val localOnly: Boolean,
)

@Immutable
data class AgentRun(
    val id: String,
    val type: AgentType,
    val goal: String,
    val status: AgentStatus,
    val progress: Int,
    val result: String,
    val error: String?,
    val createdAt: Long,
)

@Immutable
data class AgentStep(
    val id: String,
    val runId: String,
    val stage: AgentStage,
    val title: String,
    val detail: String,
    val status: StepStatus,
    val sequence: Int,
)

enum class AgentType {
    DEEP_RESEARCH,
    BUILDER,
    BUSINESS_PLANNER,
    CONTENT_STUDIO,
    STUDY_COACH,
    CAREER_COACH,
    DATA_ANALYST,
}

enum class AgentStage { PLAN, RESEARCH, SYNTHESIZE, RESULT }
enum class AgentStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }
enum class StepStatus { PENDING, RUNNING, COMPLETE, FAILED }
enum class MessageRole { USER, ASSISTANT, SYSTEM }
enum class AiProvider {
    AUTO,
    PORTABLE_LOCAL,
    ON_DEVICE,
    OPENROUTER,
    GEMINI,
    NVIDIA,
    GROQ,
    MISTRAL,
    DEEPSEEK,
    OPENAI,
    ANTHROPIC,
    CUSTOM,
}

enum class ImageProvider { OPENROUTER, GEMINI }
enum class ImageResolution { ONE_K, TWO_K, FOUR_K }
enum class ImageQuality { AUTO, MEDIUM, HIGH }

@Immutable
data class ImageGenerationRequest(
    val provider: ImageProvider,
    val model: String,
    val prompt: String,
    val aspectRatio: String = "1:1",
    val resolution: ImageResolution = ImageResolution.ONE_K,
    val quality: ImageQuality = ImageQuality.AUTO,
)

@Immutable
data class GeneratedImage(
    val id: String,
    val filePath: String,
    val mimeType: String,
    val prompt: String,
    val provider: ImageProvider,
    val model: String,
)

@Immutable
data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

@Immutable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    val streaming: Boolean = false,
)

@Immutable
data class LibraryDocument(
    val id: String,
    val title: String,
    val mimeType: String,
    val chunkCount: Int,
    val createdAt: Long,
)

@Immutable
data class RetrievedChunk(
    val id: String,
    val documentId: String,
    val content: String,
    val score: Float,
)
