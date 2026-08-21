package ai.byak.app.domain.repository

import ai.byak.app.domain.model.LibraryDocument
import ai.byak.app.domain.model.RetrievedChunk
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<LibraryDocument>>
    suspend fun importText(title: String, mimeType: String, content: String): String
    suspend fun search(query: String, limit: Int = 8): List<RetrievedChunk>
    suspend fun contextFor(query: String, limit: Int = 6): String
    suspend fun delete(documentId: String)
}
