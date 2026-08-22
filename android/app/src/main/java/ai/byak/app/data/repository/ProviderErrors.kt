package ai.byak.app.data.repository

import ai.byak.app.domain.model.AiProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun providerFailure(provider: AiProvider, status: Int, body: String): String {
    val detail = providerMessage(body)
    val name = provider.displayName()
    return when (status) {
        400 -> name + " could not use this request or model" + detail.suffix() + "."
        401 -> name + " says this credential is missing, invalid, expired, or revoked" + detail.suffix() + "."
        402 -> name + " accepted the key, but this account or key has no available credits" + detail.suffix() + "."
        403 -> name + " received the key but denied this request. Check API restrictions, key permissions, region, or provider guardrails" + detail.suffix() + "."
        404 -> name + " could not find the selected model or endpoint" + detail.suffix() + "."
        408 -> name + " timed out before generation started" + detail.suffix() + "."
        429 -> name + " accepted the connection but is rate-limiting this account" + detail.suffix() + "."
        in 500..599 -> name + " is temporarily unavailable" + detail.suffix() + "."
        else -> name + " returned HTTP " + status + detail.suffix() + "."
    }
}

internal fun providerMessage(body: String): String {
    val parsed = runCatching { Json.parseToJsonElement(body) }.getOrNull()
    val root = parsed as? JsonObject
    val error = root?.get("error")
    val raw = when (error) {
        is JsonObject -> error.string("message") ?: error.string("status") ?: error.string("code")
        else -> root?.string("message")
    } ?: body.lineSequence().firstOrNull { it.isNotBlank() }
    return raw.orEmpty()
        .replace(Regex("""(?i)Bearer\s+[A-Za-z0-9._~+/\-=]+"""), "Bearer ••••")
        .replace(Regex("""(?i)(sk|AIza)[A-Za-z0-9_\-]{8,}"""), "••••")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(360)
}

internal fun AiProvider.displayName(): String = when (this) {
    AiProvider.OPENAI -> "OpenAI"
    AiProvider.OPENROUTER -> "OpenRouter"
    AiProvider.ANTHROPIC -> "Anthropic"
    AiProvider.GEMINI -> "Gemini"
    AiProvider.ON_DEVICE -> "Gemini Nano"
}

private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun String.suffix(): String = if (isBlank()) "" else ": " + this
