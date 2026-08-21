package ai.byak.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "agent_runs", indices = [Index("status"), Index("createdAt")])
data class AgentRunEntity(
    @PrimaryKey val id: String,
    val type: String,
    val goal: String,
    val status: String,
    val progress: Int = 0,
    val result: String = "",
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Immutable
@Entity(
    tableName = "agent_steps",
    foreignKeys = [ForeignKey(
        entity = AgentRunEntity::class,
        parentColumns = ["id"],
        childColumns = ["runId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("runId"), Index(value = ["runId", "sequence"], unique = true)],
)
data class AgentStepEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val stage: String,
    val title: String,
    val detail: String = "",
    val status: String,
    val sequence: Int,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)

@Immutable
@Entity(tableName = "conversations", indices = [Index("updatedAt")])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Immutable
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId"), Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val streaming: Boolean = false,
    val failure: String? = null,
)

@Immutable
@Entity(tableName = "documents", indices = [Index("createdAt")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mimeType: String,
    val sourceUri: String?,
    val chunkCount: Int,
    val createdAt: Long,
)

@Immutable
@Entity(
    tableName = "document_chunks",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["id"], unique = true), Index("documentId")],
)
data class DocumentChunkEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val id: String,
    val documentId: String,
    val content: String,
    val embedding: ByteArray? = null,
    val tokenCount: Int = 0,
)

@Fts4(contentEntity = DocumentChunkEntity::class)
@Entity(tableName = "document_chunks_fts")
data class DocumentChunkFts(
    val content: String,
)

@Immutable
@Entity(tableName = "entitlements")
data class EntitlementEntity(
    @PrimaryKey val id: String = "premium",
    val productId: String,
    val purchaseTokenHash: String,
    val active: Boolean,
    val verifiedAt: Long,
    val expiresAt: Long?,
)
