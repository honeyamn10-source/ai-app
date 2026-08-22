package ai.byak.app.data.repository

import ai.byak.app.data.local.VectorCodec
import ai.byak.app.data.local.dao.DocumentDao
import ai.byak.app.data.local.entity.DocumentChunkEntity
import ai.byak.app.data.local.entity.DocumentEntity
import ai.byak.app.domain.model.LibraryDocument
import ai.byak.app.domain.model.RetrievedChunk
import ai.byak.app.domain.repository.DocumentRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<LibraryDocument>> = dao.observeDocuments().map { documents ->
        documents.map { LibraryDocument(it.id, it.title, it.mimeType, it.chunkCount, it.createdAt) }
    }

    override suspend fun importText(title: String, mimeType: String, content: String): String {
        val normalized = content.replace("\u0000", "").trim()
        require(normalized.isNotEmpty()) { "The document is empty" }
        require(normalized.length <= MAX_DOCUMENT_CHARS) { "Document exceeds the 5 MB text limit" }
        val id = UUID.randomUUID().toString()
        val chunks = chunk(normalized).map { text ->
            DocumentChunkEntity(
                id = UUID.randomUUID().toString(),
                documentId = id,
                content = text,
                embedding = VectorCodec.encode(LocalEmbedding.embed(text)),
                tokenCount = text.length / 4,
            )
        }
        dao.replaceDocument(
            DocumentEntity(
                id = id,
                title = title.trim().ifBlank { "Untitled document" }.take(180),
                mimeType = mimeType.take(100),
                sourceUri = null,
                chunkCount = chunks.size,
                createdAt = System.currentTimeMillis(),
            ),
            chunks,
        )
        return id
    }

    override suspend fun search(query: String, limit: Int): List<RetrievedChunk> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        val lexicalQuery = clean.split(Regex("[^\\p{L}\\p{N}_]+"))
            .filter { it.length > 1 }
            .take(12)
            .joinToString(" OR ") { "${it.replace("\"", "") }*" }
        val lexical = if (lexicalQuery.isBlank()) emptyList() else runCatching {
            dao.lexicalSearch(lexicalQuery, limit * 2)
        }.getOrDefault(emptyList())
        val lexicalScores = lexical.mapIndexed { index, item -> item.id to (1f - index / (lexical.size + 1f)) }.toMap()

        val queryVector = LocalEmbedding.embed(clean)
        return dao.embeddedChunks().asSequence()
            .map { chunk ->
                val vectorScore = chunk.embedding?.let(VectorCodec::decode)
                    ?.let { VectorCodec.cosineSimilarity(queryVector, it) } ?: 0f
                val lexicalScore = lexicalScores[chunk.id] ?: 0f
                RetrievedChunk(chunk.id, chunk.documentId, chunk.content, vectorScore * 0.65f + lexicalScore * 0.35f)
            }
            .filter { it.score > 0.04f }
            .sortedByDescending(RetrievedChunk::score)
            .take(limit.coerceIn(1, 30))
            .toList()
    }

    override suspend fun contextFor(query: String, limit: Int): String = search(query, limit)
        .mapIndexed { index, chunk -> "[Local source ${index + 1}]\n${chunk.content}" }
        .joinToString("\n\n")

    override suspend fun delete(documentId: String) = dao.deleteDocument(documentId)

    private fun chunk(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + CHUNK_SIZE).coerceAtMost(text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOfAny(charArrayOf('\n', '.', '!', '?'), end - 1)
                if (boundary > start + CHUNK_SIZE / 2) end = boundary + 1
            }
            result += text.substring(start, end).trim()
            if (end >= text.length) break
            start = (end - CHUNK_OVERLAP).coerceAtLeast(start + 1)
        }
        return result.filter(String::isNotEmpty)
    }

    private object LocalEmbedding {
        private const val DIMENSIONS = 384
        fun embed(text: String): FloatArray {
            val vector = FloatArray(DIMENSIONS)
            text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 1 }
                .forEach { token ->
                    val hash = token.hashCode()
                    val index = (hash and Int.MAX_VALUE) % DIMENSIONS
                    vector[index] += if (hash and 1 == 0) 1f else -1f
                }
            val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
            if (norm > 0f) for (index in vector.indices) vector[index] /= norm
            return vector
        }
    }

    private companion object {
        const val CHUNK_SIZE = 1_200
        const val CHUNK_OVERLAP = 180
        const val MAX_DOCUMENT_CHARS = 5_000_000
    }
}
