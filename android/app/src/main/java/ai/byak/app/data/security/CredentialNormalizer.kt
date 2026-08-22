package ai.byak.app.data.security

import ai.byak.app.domain.model.AiProvider

data class NormalizedCredential(
    val value: String,
    val repaired: Boolean,
)

/**
 * Accepts the safe paste formats people commonly copy from dashboards, password
 * managers and .env files. The returned value is never logged or surfaced in UI.
 */
fun normalizeCredential(provider: AiProvider, raw: String): NormalizedCredential {
    val original = raw.trim()
    if (original.isBlank()) return NormalizedCredential("", false)

    val withoutFence = original
        .removePrefix("```")
        .removeSuffix("```")
        .lineSequence()
        .filterNot { it.trim().startsWith("#") }
        .joinToString("\n")
        .trim()

    val knownToken = provider.tokenPattern()?.find(withoutFence)?.value
    val structuredValue = ASSIGNMENT_PATTERN.find(withoutFence)?.groupValues?.getOrNull(1)
    val assignmentValue = withoutFence.lineSequence().mapNotNull { line ->
        val clean = line.trim().removeSuffix(",")
        val separator = when {
            '=' in clean -> '='
            ':' in clean -> ':'
            else -> return@mapNotNull null
        }
        val name = clean.substringBefore(separator)
            .trim().trim('"', '\'', '`').lowercase()
        if (name.contains("key") || name.contains("token")) clean.substringAfter(separator) else null
    }.firstOrNull()

    val candidate = knownToken ?: structuredValue ?: assignmentValue ?: withoutFence
    val cleaned = candidate
        .trim()
        .removeSuffix(",")
        .trim()
        .removeSurrounding("`")
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .removePrefix("Bearer ")
        .removePrefix("bearer ")
        .trim()

    return NormalizedCredential(cleaned, cleaned != original)
}

private fun AiProvider.tokenPattern(): Regex? = when (this) {
    AiProvider.OPENROUTER -> Regex("""sk-or-v1-[A-Za-z0-9_-]{16,}""")
    AiProvider.OPENAI -> Regex("""sk-(?:proj-)?[A-Za-z0-9_-]{16,}""")
    AiProvider.ANTHROPIC -> Regex("""sk-ant-[A-Za-z0-9_-]{16,}""")
    // Google can change key prefixes, so assignment extraction remains the fallback.
    AiProvider.GEMINI -> Regex("""AIza[A-Za-z0-9_-]{20,}""")
    AiProvider.OLLAMA, AiProvider.ON_DEVICE -> null
}

private val ASSIGNMENT_PATTERN = Regex(
    """(?i)(?:api[_-]?key|access[_-]?token|token)\s*[\"']?\s*[:=]\s*[\"'`]?([A-Za-z0-9._~+\-/=]{12,})""",
)
