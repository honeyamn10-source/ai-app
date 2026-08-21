package ai.byak.app.domain.repository

import androidx.compose.runtime.Immutable
import ai.byak.app.domain.model.AiProvider
import ai.byak.app.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow

@Immutable
data class PromptMessage(val role: MessageRole, val content: String)

@Immutable
data class StreamingRequest(
    val provider: AiProvider,
    val model: String,
    val messages: List<PromptMessage>,
    val systemPrompt: String? = null,
    val maxTokens: Int = 8_192,
)

sealed interface StreamChunk {
    @Immutable data class Delta(val text: String) : StreamChunk
    @Immutable data class Completed(val fullText: String) : StreamChunk
}

interface StreamingRepository {
    fun stream(request: StreamingRequest): Flow<StreamChunk>
}
