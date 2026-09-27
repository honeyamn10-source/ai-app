package ai.byak.app.data

import org.json.JSONObject

/** Incremental parser for the backend's `event:`/`data:` server-sent events. Feed it one line at a time. */
class SseParser {
    private var event = "message"
    private val data = StringBuilder()

    fun feed(line: String): StreamEvent? {
        when {
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.removePrefix("data:").trim()) }
            line.isEmpty() -> return dispatch()
        }
        return null
    }

    /** Flushes a trailing event if the stream ended without a blank line. */
    fun finish(): StreamEvent? = dispatch()

    private fun dispatch(): StreamEvent? {
        if (data.isEmpty()) { event = "message"; return null }
        val payload = runCatching { JSONObject(data.toString()) }.getOrNull()
        val name = event
        data.clear(); event = "message"
        payload ?: return null
        return when (name) {
            "content_delta" -> payload.optString("delta").takeIf { it.isNotEmpty() }?.let { StreamEvent.Delta(it) }
            "message_complete" -> StreamEvent.Complete(payload.toMessage())
            "error" -> StreamEvent.Failed(payload.optString("message").ifBlank { "Generation failed" })
            else -> null
        }
    }
}

internal fun JSONObject.toMessage(): ChatMessage {
    val citations = optJSONArray("citations")?.let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }.map { Citation(it.optInt("id"), it.optString("title"), it.optInt("chunk")) }
    }.orEmpty()
    return ChatMessage(
        id = getString("id"), role = getString("role"), content = optString("content"),
        status = optString("status", "complete"), model = optString("model").ifBlank { null }, citations = citations
    )
}
