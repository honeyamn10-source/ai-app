package ai.byak.app

import ai.byak.app.data.local.Rag
import ai.byak.app.data.local.stripHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEngineTest {
    @Test fun chunksAreBoundedAndPreferSentenceEnds() {
        val chunks = Rag.chunk("One sentence here. ".repeat(200), size = 200, overlap = 20)
        assertTrue(chunks.size > 5); assertTrue(chunks.all { it.text.length <= 200 })
        assertTrue(chunks.dropLast(1).all { it.text.endsWith(".") })
    }

    @Test fun bm25RanksTheRelevantChunkFirst() {
        val candidates = listOf(
            Triple("f1", "garden.md", Rag.Chunk(0, "Notes about tomatoes and watering the garden")),
            Triple("f2", "launch.md", Rag.Chunk(0, "The launch codename is BLUEBIRD and ships in March")),
            Triple("f3", "misc.md", Rag.Chunk(0, "Le projet utilise la sécurité renforcée"))
        )
        assertEquals("launch.md", Rag.retrieve("what is the launch codename?", candidates, 4).first().fileName)
        assertEquals("misc.md", Rag.retrieve("sécurité", candidates, 4).first().fileName)
        assertTrue(Rag.retrieve("", candidates, 4).isEmpty())
    }

    @Test fun htmlIsReducedToText() {
        assertEquals("Hi Fish & chips", "<html><title>Hi</title><script>evil()</script><p>Fish &amp; chips</p></html>".stripHtml())
    }
}
