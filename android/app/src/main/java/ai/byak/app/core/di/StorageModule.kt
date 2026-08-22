package ai.byak.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import ai.byak.app.data.local.ByakDatabase
import ai.byak.app.data.local.dao.AgentDao
import ai.byak.app.data.local.dao.ChatDao
import ai.byak.app.data.local.dao.DocumentDao
import ai.byak.app.data.local.dao.EntitlementDao
import ai.byak.app.data.security.EncryptedStateSerializer
import ai.byak.app.data.security.KeystoreCipher
import ai.byak.app.data.security.SecureState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun provideSecureDataStore(
        @ApplicationContext context: Context,
        cipher: KeystoreCipher,
        json: Json,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<SecureState> = DataStoreFactory.create(
        serializer = EncryptedStateSerializer(cipher, json),
        corruptionHandler = ReplaceFileCorruptionHandler { SecureState() },
        scope = scope,
        produceFile = { context.dataStoreFile("byak_secure_v2.bin") },
    )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ByakDatabase =
        Room.databaseBuilder(context, ByakDatabase::class.java, "byak-v2.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides fun provideAgentDao(database: ByakDatabase): AgentDao = database.agentDao()
    @Provides fun provideChatDao(database: ByakDatabase): ChatDao = database.chatDao()
    @Provides fun provideDocumentDao(database: ByakDatabase): DocumentDao = database.documentDao()
    @Provides fun provideEntitlementDao(database: ByakDatabase): EntitlementDao = database.entitlementDao()
}
