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
    val nvidiaKey: String = "",
    val groqKey: String = "",
    val mistralKey: String = "",
    val deepSeekKey: String = "",
    val customKey: String = "",
    val customBaseUrl: String = "",
    val selectedProvider: String = "AUTO",
    val selectedModel: String = "Best available",
    val openAiModel: String = "gpt-5-mini",
    val openRouterModel: String = "openrouter/auto",
    val anthropicModel: String = "claude-sonnet-4-5",
    val geminiModel: String = "gemini-2.5-flash-lite",
    val nvidiaModel: String = "meta/llama-3.1-70b-instruct",
    val groqModel: String = "llama-3.3-70b-versatile",
    val mistralModel: String = "mistral-small-latest",
    val deepSeekModel: String = "deepseek-chat",
    val customModel: String = "",
    val openRouterImageModel: String = "openai/gpt-image-2",
    val geminiImageModel: String = "gemini-3.1-flash-image",
)

fun SecureState.hasValidSession(nowEpochSeconds: Long): Boolean =
    accessToken.isNotBlank() && (localSession || tokenExpiresAtEpochSeconds > nowEpochSeconds + 30)
