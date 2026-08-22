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
    val selectedProvider: String = "ON_DEVICE",
    val selectedModel: String = "Gemini Nano",
    val openAiModel: String = "gpt-5-mini",
    val openRouterModel: String = "openrouter/auto",
    val anthropicModel: String = "claude-sonnet-4-5",
    val geminiModel: String = "gemini-3.1-flash-lite",
    val openRouterImageModel: String = "openai/gpt-image-2",
    val geminiImageModel: String = "gemini-3.1-flash-image",
)

fun SecureState.hasValidSession(nowEpochSeconds: Long): Boolean =
    accessToken.isNotBlank() && (localSession || tokenExpiresAtEpochSeconds > nowEpochSeconds + 30)
