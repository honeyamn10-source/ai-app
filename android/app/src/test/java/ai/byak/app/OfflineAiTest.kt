package ai.byak.app

import ai.byak.app.data.local.Catalog
import ai.byak.app.data.local.LocalModel
import ai.byak.app.data.local.Turn
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
        assertEquals("pollinations", Catalog.detect("sk_abc123"))
        assertNull(Catalog.detect("sk-abc")) // plain sk- keys are shared by several providers
    }

    @Test fun offlineModelIsInTheCatalogWithoutAKey() {
        val local = Catalog.asCatalog().single { it.id == Catalog.LOCAL }
        assertTrue(local.localOnly && local.keyOptional)
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-4-5"), Catalog.entry("anthropic")!!.models)
    }

    @Test fun pastedKeysAndEndpointsAreCleaned() {
        assertEquals("sk-proj-abc123", Catalog.cleanKey("  Bearer sk-proj-abc\n123 "))
        assertEquals("AIzaXYZ", Catalog.cleanKey("\"AIzaXYZ\""))
        assertEquals("https://api.example.com/v1", Catalog.cleanBaseUrl("https://api.example.com/v1/chat/completions/"))
        assertEquals("https://api.example.com/v1", Catalog.cleanBaseUrl(" https://api.example.com/v1 "))
    }

    @Test fun offlinePromptKeepsContextAndTheLatestQuestion() {
        val system = listOf(
            "You are BYAK AI, a helpful, accurate assistant.",
            "The user's standing instructions:\nAnswer in French.",
            "Relevant excerpts from the user's documents. Cite them like [Document source 1]:\n[Document source 1: a.md, chunk 0]\nThe codename is BLUEBIRD.\n\n[Document source 2: b.md, chunk 1]\nIt ships in March."
        ).joinToString("\n\n")
        val turns = List(30) { Turn(if (it % 2 == 0) "user" else "assistant", "message $it " + "x".repeat(400)) } + Turn("user", "What is the codename?")
        val prompt = LocalModel.prompt(system, turns)
        assertTrue(prompt.contains("Answer in French"))
        assertTrue(prompt.contains("BLUEBIRD") && prompt.contains("ships in March"))
        assertTrue(prompt.contains("User: What is the codename?"))
        assertTrue(prompt.endsWith("/no_think"))
        assertTrue(prompt.length <= 6_000)
    }
}
