package ai.byak.app

import ai.byak.app.data.SseParser
import ai.byak.app.data.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    private fun parse(raw: String): List<StreamEvent> { val parser = SseParser(); return raw.split("\n").mapNotNull(parser::feed) + listOfNotNull(parser.finish()) }

    @Test fun parsesDeltasAndCompletion() {
        val events = parse(
            "event: message_start\ndata: {\"userMessageId\":\"u1\"}\n\n" +
            "event: content_delta\ndata: {\"delta\":\"Hel\"}\n\n" +
            "event: content_delta\ndata: {\"delta\":\"lo\"}\n\n" +
            "event: message_complete\ndata: {\"id\":\"m1\",\"role\":\"assistant\",\"content\":\"Hello\",\"status\":\"complete\",\"citations\":[{\"id\":1,\"title\":\"notes.md\",\"chunk\":0}]}\n\n"
        )
        assertEquals(listOf(StreamEvent.Delta("Hel"), StreamEvent.Delta("lo")), events.take(2))
        val complete = events[2] as StreamEvent.Complete
        assertEquals("Hello", complete.message.content); assertEquals("notes.md", complete.message.citations.single().title)
    }

    @Test fun surfacesServerErrors() {
        val events = parse("event: error\ndata: {\"message\":\"Anthropic: the provider rejected your API key\"}\n\n")
        assertEquals(StreamEvent.Failed("Anthropic: the provider rejected your API key"), events.single())
    }

    @Test fun ignoresMalformedAndUnknownEvents() {
        assertTrue(parse("event: usage\ndata: {\"inputTokens\":3}\n\nevent: content_delta\ndata: {not json\n\n").isEmpty())
    }

    @Test fun flushesTrailingEventWithoutBlankLine() {
        assertEquals(listOf(StreamEvent.Delta("tail")), parse("event: content_delta\ndata: {\"delta\":\"tail\"}"))
    }
}
