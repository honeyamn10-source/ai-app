package ai.byak.app.data.local

import kotlin.math.ln

/** Knowledge-file search for on-device mode (a port of backend/src/rag.mjs). */
object Rag {
    data class Chunk(val index: Int, val text: String)
    data class Match(val fileId: String, val fileName: String, val index: Int, val text: String, val score: Double)

    fun chunk(text: String, size: Int = 1200, overlap: Int = 150): List<Chunk> {
        val clean = text.replace("\u0000", "").replace("\r", "")
        val chunks = mutableListOf<Chunk>(); var start = 0
        while (start < clean.length) {
            var end = minOf(start + size, clean.length)
            if (end < clean.length) {
                val windowStart = start + (size * 0.6).toInt()
                val window = clean.substring(windowStart, end)
                val cut = maxOf(window.lastIndexOf("\n\n"), window.lastIndexOf(". "), window.lastIndexOf('\n'))
                if (cut > 0) end = windowStart + cut + 1
            }
            clean.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let { chunks += Chunk(chunks.size, it) }
            if (end >= clean.length) break
            start = maxOf(end - overlap, start + 1)
        }
        return chunks
    }

    private val stopWords = setOf("the", "and", "for", "are", "but", "not", "you", "all", "can", "was", "one", "our", "has", "have", "this", "that", "with", "what", "from", "they", "will", "would", "there", "their", "about", "which", "when", "your", "how", "does", "into", "than", "then", "them", "these", "some")
    fun tokens(text: String): List<String> = Regex("[\\p{L}\\p{N}]{2,}").findAll(text.lowercase()).map { it.value }.filterNot { it in stopWords }.toList()

    /** BM25 over (fileId, fileName, chunk) candidates; best match scores 1.0. */
    fun retrieve(query: String, candidates: List<Triple<String, String, Chunk>>, limit: Int): List<Match> {
        val wanted = tokens(query).distinct()
        if (wanted.isEmpty() || candidates.isEmpty()) return emptyList()
        val docs = candidates.map { (id, name, chunk) -> val t = tokens(chunk.text); Triple(Triple(id, name, chunk), t.groupingBy { it }.eachCount(), t.size) }
        val avg = docs.sumOf { it.third }.toDouble() / docs.size
        val df = wanted.associateWith { w -> docs.count { it.second.containsKey(w) } }
        val scored = docs.map { (meta, tf, length) ->
            val score = wanted.sumOf { w ->
                val f = tf[w] ?: return@sumOf 0.0
                val idf = ln(1 + (docs.size - df.getValue(w) + 0.5) / (df.getValue(w) + 0.5))
                idf * (f * 2.4) / (f + 1.4 * (0.25 + 0.75 * length / avg.coerceAtLeast(1.0)))
            }
            Match(meta.first, meta.second, meta.third.index, meta.third.text, score)
        }.filter { it.score > 0 }.sortedByDescending { it.score }.take(limit)
        val top = scored.firstOrNull()?.score ?: 1.0
        return scored.map { it.copy(score = it.score / top) }
    }
}
