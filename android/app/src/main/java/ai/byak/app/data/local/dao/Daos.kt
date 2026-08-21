package ai.byak.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ai.byak.app.data.local.entity.AgentRunEntity
import ai.byak.app.data.local.entity.AgentStepEntity
import ai.byak.app.data.local.entity.ConversationEntity
import ai.byak.app.data.local.entity.DocumentChunkEntity
import ai.byak.app.data.local.entity.DocumentEntity
import ai.byak.app.data.local.entity.EntitlementEntity
import ai.byak.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: AgentRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(steps: List<AgentStepEntity>)

    @Query("SELECT * FROM agent_runs ORDER BY createdAt DESC")
    fun observeRuns(): Flow<List<AgentRunEntity>>

    @Query("SELECT * FROM agent_runs WHERE id = :runId LIMIT 1")
    fun observeRun(runId: String): Flow<AgentRunEntity?>

    @Query("SELECT * FROM agent_runs WHERE id = :runId LIMIT 1")
    suspend fun getRun(runId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY sequence")
    fun observeSteps(runId: String): Flow<List<AgentStepEntity>>

    @Query("UPDATE agent_runs SET status = :status, progress = :progress, updatedAt = :now, result = :result, error = :error WHERE id = :runId")
    suspend fun updateRun(runId: String, status: String, progress: Int, result: String, error: String?, now: Long)

    @Query("UPDATE agent_steps SET status = :status, detail = :detail, startedAt = COALESCE(startedAt, :now), completedAt = :completedAt WHERE id = :stepId")
    suspend fun updateStep(stepId: String, status: String, detail: String, now: Long, completedAt: Long?)
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentMessages(conversationId: String, limit: Int = 30): List<MessageEntity>

    @Query("UPDATE messages SET content = :content, streaming = :streaming, failure = :failure, updatedAt = :now WHERE id = :id")
    suspend fun updateMessage(id: String, content: String, streaming: Boolean, failure: String?, now: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun touchConversation(id: String, title: String, now: Long)
}

data class FtsChunkResult(
    val id: String,
    val documentId: String,
    val content: String,
)

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT document_chunks.id, document_chunks.documentId, document_chunks.content FROM document_chunks JOIN document_chunks_fts ON document_chunks.rowId = document_chunks_fts.rowid WHERE document_chunks_fts MATCH :query LIMIT :limit")
    suspend fun lexicalSearch(query: String, limit: Int): List<FtsChunkResult>

    @Query("SELECT * FROM document_chunks WHERE embedding IS NOT NULL")
    suspend fun embeddedChunks(): List<DocumentChunkEntity>

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: String)

    @Transaction
    suspend fun replaceDocument(document: DocumentEntity, chunks: List<DocumentChunkEntity>) {
        upsertDocument(document)
        insertChunks(chunks)
    }
}

@Dao
interface EntitlementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entitlement: EntitlementEntity)

    @Query("SELECT * FROM entitlements WHERE id = 'premium' LIMIT 1")
    fun observePremium(): Flow<EntitlementEntity?>

    @Query("DELETE FROM entitlements")
    suspend fun clear()
}
