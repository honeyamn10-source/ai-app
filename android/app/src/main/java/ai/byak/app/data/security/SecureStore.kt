package ai.byak.app.data.security

import androidx.datastore.core.DataStore
import ai.byak.app.core.di.ApplicationScope
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class SecureStore @Inject constructor(
    private val dataStore: DataStore<SecureState>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val cached = AtomicReference(SecureState())

    init {
        scope.launch { dataStore.data.collect(cached::set) }
    }

    val state: Flow<SecureState> = dataStore.data
    val hasSession: Flow<Boolean> = state
        .map { it.hasValidSession(System.currentTimeMillis() / 1_000) }
        .distinctUntilChanged()

    fun snapshot(): SecureState = cached.get()

    suspend fun update(transform: (SecureState) -> SecureState): SecureState {
        // DataStore writes are serialized, but its collector may resume later. Update the
        // in-process snapshot before returning so Chat can use a newly saved key immediately.
        val updated = dataStore.updateData(transform)
        cached.set(updated)
        return updated
    }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        expiresAtEpochSeconds: Long,
        userId: String,
        name: String,
        email: String,
        localOnly: Boolean = false,
    ) = update {
        it.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenExpiresAtEpochSeconds = expiresAtEpochSeconds,
            userId = userId,
            userName = name,
            userEmail = email,
            localSession = localOnly,
        )
    }

    suspend fun clearSession() = update {
        it.copy(
            accessToken = "",
            refreshToken = "",
            tokenExpiresAtEpochSeconds = 0,
            userId = "",
            userName = "",
            userEmail = "",
            localSession = false,
        )
    }

    suspend fun saveProviderKey(provider: String, key: String, model: String) = update {
        when (provider.uppercase()) {
            "OPENAI" -> it.copy(
                openAiKey = normalizeCredential(ai.byak.app.domain.model.AiProvider.OPENAI, key).value,
                selectedProvider = "OPENAI",
                selectedModel = model.trim(),
                openAiModel = model.trim(),
            )
            "OPENROUTER" -> it.copy(
                openRouterKey = normalizeCredential(ai.byak.app.domain.model.AiProvider.OPENROUTER, key).value,
                selectedProvider = "OPENROUTER",
                selectedModel = model.trim(),
                openRouterModel = model.trim(),
            )
            "ANTHROPIC" -> it.copy(
                anthropicKey = normalizeCredential(ai.byak.app.domain.model.AiProvider.ANTHROPIC, key).value,
                selectedProvider = "ANTHROPIC",
                selectedModel = model.trim(),
                anthropicModel = model.trim(),
            )
            "GEMINI" -> it.copy(
                geminiKey = normalizeCredential(ai.byak.app.domain.model.AiProvider.GEMINI, key).value,
                selectedProvider = "GEMINI",
                selectedModel = model.trim(),
                geminiModel = model.trim(),
            )
            "ON_DEVICE" -> it.copy(selectedProvider = "ON_DEVICE", selectedModel = ON_DEVICE_MODEL)
            else -> error("Unsupported AI provider: $provider")
        }
    }

    suspend fun saveImageModel(provider: String, model: String) = update {
        when (provider.uppercase()) {
            "OPENROUTER" -> it.copy(openRouterImageModel = model.trim())
            "GEMINI" -> it.copy(geminiImageModel = model.trim())
            else -> error("Unsupported image provider: $provider")
        }
    }

    fun apiKey(provider: String): String = when (provider.uppercase()) {
        "OPENAI" -> snapshot().openAiKey
        "OPENROUTER" -> snapshot().openRouterKey
        "ANTHROPIC" -> snapshot().anthropicKey
        "GEMINI" -> snapshot().geminiKey
        "ON_DEVICE" -> ON_DEVICE_READY_SENTINEL
        else -> ""
    }

    fun model(provider: String): String = snapshot().let { state ->
        when (provider.uppercase()) {
            "OPENAI" -> state.openAiModel
            "OPENROUTER" -> state.openRouterModel
            "ANTHROPIC" -> state.anthropicModel
            "GEMINI" -> state.geminiModel
            "ON_DEVICE" -> ON_DEVICE_MODEL
            else -> state.selectedModel
        }
    }

    companion object {
        const val ON_DEVICE_MODEL = "Gemini Nano"
        private const val ON_DEVICE_READY_SENTINEL = "device"
    }
}
