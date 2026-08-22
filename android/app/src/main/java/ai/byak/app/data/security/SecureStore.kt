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
            "OPENAI" -> it.copy(openAiKey = key.trim(), selectedProvider = "OPENAI", selectedModel = model.trim())
            "OPENROUTER" -> it.copy(openRouterKey = key.trim(), selectedProvider = "OPENROUTER", selectedModel = model.trim())
            "ANTHROPIC" -> it.copy(anthropicKey = key.trim(), selectedProvider = "ANTHROPIC", selectedModel = model.trim())
            "GEMINI" -> it.copy(geminiKey = key.trim(), selectedProvider = "GEMINI", selectedModel = model.trim())
            else -> error("Unsupported AI provider: $provider")
        }
    }

    fun apiKey(provider: String): String = when (provider.uppercase()) {
        "OPENAI" -> snapshot().openAiKey
        "OPENROUTER" -> snapshot().openRouterKey
        "ANTHROPIC" -> snapshot().anthropicKey
        "GEMINI" -> snapshot().geminiKey
        else -> ""
    }
}
