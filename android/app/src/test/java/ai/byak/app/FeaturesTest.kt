package ai.byak.app

import ai.byak.app.billing.describePeriod
import ai.byak.app.data.SavedPrompt
import ai.byak.app.data.Subscription
import ai.byak.app.data.SettingsStore
import ai.byak.app.data.SseParser
import ai.byak.app.data.StreamEvent
import ai.byak.app.ui.speakableChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    @Test fun serverUrlValidation() {
        assertNull(SettingsStore.validate("https://api.example.com", allowHttp = false))
        assertNotNull(SettingsStore.validate("http://api.example.com", allowHttp = false))
        assertNull(SettingsStore.validate("http://10.0.2.2:8787", allowHttp = true))
        assertNotNull(SettingsStore.validate("ftp://example.com", allowHttp = true))
        assertNotNull(SettingsStore.validate("https://", allowHttp = false))
        assertNotNull(SettingsStore.validate("not a url", allowHttp = false))
    }

    @Test fun promptTemplatesDropTheInputMarker() {
        assertEquals("Summarize:\n", SavedPrompt("t", "Summarize", "Summarize:\n\n{{input}}").composerText)
        assertEquals("Be concise", SavedPrompt("t", "Plain", "Be concise").composerText)
    }

    @Test fun speechSkipsMarkdownAndCodeAndRespectsLimit() {
        val chunks = speakableChunks("# Title\n\nSee **this** [link](https://x.y). ```kotlin\nval x = 1\n``` Done.", 1000)
        assertEquals(listOf("Title See this link. Code block omitted. Done."), chunks)
        val long = speakableChunks("One sentence here. ".repeat(50), 60)
        assertTrue(long.size > 1); assertTrue(long.all { it.length <= 60 })
        assertTrue(speakableChunks("```\ncode only\n```", 100).all { it == "Code block omitted." })
    }

    @Test fun statusEventsAreParsed() {
        val parser = SseParser()
        val events = "event: status\ndata: {\"message\":\"Searching the web…\"}\n\n".split("\n").mapNotNull(parser::feed)
        assertEquals(listOf(StreamEvent.Status("Searching the web…")), events)
    }

    @Test fun trialPeriodsAreReadable() {
        assertEquals("7-day", describePeriod("P7D")); assertEquals("1-week", describePeriod("P1W")); assertEquals("1-month", describePeriod("P1M"))
        assertNull(describePeriod("P1Y2M"))
    }

    @Test fun dailyAllowancesNeverGoNegative() {
        val sub = Subscription.FREE.copy(limits = mapOf("webSearchesPerDay" to 3, "imagesPerDay" to 5), webSearchesToday = 4, imagesToday = 2)
        assertEquals(0, sub.webSearchesLeft); assertEquals(3, sub.imagesLeft)
    }
}
