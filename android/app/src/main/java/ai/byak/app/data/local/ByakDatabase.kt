package ai.byak.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ai.byak.app.data.local.dao.AgentDao
import ai.byak.app.data.local.dao.ChatDao
import ai.byak.app.data.local.dao.DocumentDao
import ai.byak.app.data.local.dao.EntitlementDao
import ai.byak.app.data.local.entity.AgentRunEntity
import ai.byak.app.data.local.entity.AgentStepEntity
import ai.byak.app.data.local.entity.ConversationEntity
import ai.byak.app.data.local.entity.DocumentChunkEntity
import ai.byak.app.data.local.entity.DocumentChunkFts
import ai.byak.app.data.local.entity.DocumentEntity
import ai.byak.app.data.local.entity.EntitlementEntity
import ai.byak.app.data.local.entity.MessageEntity

@Database(
    entities = [
        AgentRunEntity::class,
        AgentStepEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class,
        DocumentChunkFts::class,
        EntitlementEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ByakDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun chatDao(): ChatDao
    abstract fun documentDao(): DocumentDao
    abstract fun entitlementDao(): EntitlementDao
}
