package ai.byak.app.core.di

import ai.byak.app.data.repository.AgentRepositoryImpl
import ai.byak.app.data.repository.AuthRepositoryImpl
import ai.byak.app.data.repository.ChatRepositoryImpl
import ai.byak.app.data.repository.DocumentRepositoryImpl
import ai.byak.app.data.repository.KtorStreamingRepository
import ai.byak.app.data.repository.ImageGenerationRepositoryImpl
import ai.byak.app.domain.repository.AgentRepository
import ai.byak.app.domain.repository.AuthRepository
import ai.byak.app.domain.repository.ChatRepository
import ai.byak.app.domain.repository.DocumentRepository
import ai.byak.app.domain.repository.StreamingRepository
import ai.byak.app.domain.repository.ImageGenerationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAuth(implementation: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindStreaming(implementation: KtorStreamingRepository): StreamingRepository
    @Binds abstract fun bindChat(implementation: ChatRepositoryImpl): ChatRepository
    @Binds abstract fun bindDocuments(implementation: DocumentRepositoryImpl): DocumentRepository
    @Binds abstract fun bindAgents(implementation: AgentRepositoryImpl): AgentRepository
    @Binds abstract fun bindImages(implementation: ImageGenerationRepositoryImpl): ImageGenerationRepository
}
