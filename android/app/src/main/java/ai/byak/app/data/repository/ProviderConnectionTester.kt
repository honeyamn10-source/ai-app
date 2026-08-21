package ai.byak.app.data.repository

import ai.byak.app.domain.model.AiProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderConnectionTester @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun test(provider: AiProvider, apiKey: String, model: String): Result<String> = runCatching {
        require(apiKey.isNotBlank()) { "Enter an API key first." }
        require(model.isNotBlank()) { "Choose a model first." }

        val response = when (provider) {
            AiProvider.OPENAI -> client.get("https://api.openai.com/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            AiProvider.OPENROUTER -> client.get("https://openrouter.ai/api/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("HTTP-Referer", "https://byak.ai")
                header("X-OpenRouter-Title", "BYAK AI")
            }
            AiProvider.ANTHROPIC -> client.get("https://api.anthropic.com/v1/models") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
            }
            AiProvider.GEMINI -> client.get(
                "https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.urlEncode()}",
            )
        }

        val detail = response.bodyAsText().take(2_000)
        if (!response.status.isSuccess()) {
            error(connectionError(provider, response.status.value, detail))
        }
        "${provider.displayName()} is connected. ${model.trim()} is ready for chat and agent runs."
    }

    private fun connectionError(provider: AiProvider, status: Int, detail: String): String {
        val name = provider.displayName()
        return when (status) {
            401, 403 -> "$name rejected this key. Copy a current key from your provider account and try again."
            402 -> "$name accepted the key but the account needs credits."
            404 -> "$name's model catalog could not be reached. Check the model name and try again."
            429 -> "$name is rate-limiting this account. Wait briefly, then test again."
            in 500..599 -> "$name is temporarily unavailable. Your key remains encrypted on this device."
            else -> "$name connection failed (HTTP $status): ${detail.lineSequence().firstOrNull().orEmpty().take(240)}"
        }
    }
}

private fun AiProvider.displayName(): String = when (this) {
    AiProvider.OPENAI -> "OpenAI"
    AiProvider.OPENROUTER -> "OpenRouter"
    AiProvider.ANTHROPIC -> "Anthropic"
    AiProvider.GEMINI -> "Gemini"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
