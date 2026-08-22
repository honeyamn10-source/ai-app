package ai.byak.app.data.security

import kotlinx.serialization.Serializable

@Serializable
data class SecureState(
    val accessToken: String = "",
    val refreshToken: String = "",
    val tokenExpiresAtEpochSeconds: Long = 0,
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val localSession: Boolean = false,
    val openAiKey: String = "",
    val openRouterKey: String = "",
    val anthropicKey: String = "",
    val geminiKey: String = "",
    val selectedProvider: String = "OPENAI",
    val selectedModel: String = "gpt-5-mini",
    val openAiModel: String = "gpt-5-mini",
    val openRouterModel: String = "openrouter/auto",
    val anthropicModel: String = "claude-sonnet-4-5",
    val geminiModel: String = "gemini-3.1-flash-lite",
    val ollamaEndpoint: String = "http://192.168.1.20:11434",
    val ollamaModel: String = "deepseek-coder:6.7b",
    val openRouterImageModel: String = "openai/gpt-image-2",
    val geminiImageModel: String = "gemini-3.1-flash-image",
)

fun SecureState.hasValidSession(nowEpochSeconds: Long): Boolean =
    accessToken.isNotBlank() && (localSession || tokenExpiresAtEpochSeconds > nowEpochSeconds + 30)
