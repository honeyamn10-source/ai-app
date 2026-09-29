package ai.byak.app

import ai.byak.app.data.local.Catalog
import ai.byak.app.data.local.ThinkFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAiTest {
    private fun run(vararg pieces: String): String { val f = ThinkFilter(); return pieces.joinToString("") { f.accept(it) } + f.flush() }

    @Test fun reasoningIsHiddenEvenWhenTagsAreSplit() {
        assertEquals("Paris.", run("<think>The user asks", " about France</think>\n\nParis."))
        assertEquals("Paris.", run("<thi", "nk>hmm</th", "ink>Paris."))
        assertEquals("Hello there", run("Hello ", "there"))
        assertEquals("", run("<think>never closed"))
        assertEquals("a < b", run("a <", " b"))
    }

    @Test fun pastedKeysPickTheRightProvider() {
        assertEquals("anthropic", Catalog.detect("sk-ant-api03-abc"))
        assertEquals("gemini", Catalog.detect(" AIzaSyABC "))
        assertEquals("openrouter", Catalog.detect("sk-or-v1-abc"))
        assertEquals("groq", Catalog.detect("gsk_abc"))
        assertEquals("nvidia", Catalog.detect("nvapi-abc"))
        assertEquals("openai", Catalog.detect("sk-proj-abc"))
        assertNull(Catalog.detect("sk-abc")) // plain sk- keys are shared by several providers
    }

    @Test fun offlineModelIsInTheCatalogWithoutAKey() {
        val local = Catalog.asCatalog().single { it.id == Catalog.LOCAL }
        assertTrue(local.localOnly && local.keyOptional)
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-4-5"), Catalog.entry("anthropic")!!.models)
    }
}
