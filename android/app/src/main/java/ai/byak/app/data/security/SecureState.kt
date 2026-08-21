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
)

fun SecureState.hasValidSession(nowEpochSeconds: Long): Boolean =
    accessToken.isNotBlank() && (localSession || tokenExpiresAtEpochSeconds > nowEpochSeconds + 30)
