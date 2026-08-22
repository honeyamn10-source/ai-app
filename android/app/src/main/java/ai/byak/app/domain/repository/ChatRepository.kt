package ai.byak.app.domain.repository

import ai.byak.app.domain.model.ChatMessage
import ai.byak.app.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun createConversation(title: String = "New conversation"): String
    fun send(conversationId: String, prompt: String): Flow<StreamChunk>
}
