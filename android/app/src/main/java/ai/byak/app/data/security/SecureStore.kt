package ai.byak.app.data.security

import androidx.datastore.core.DataStore
import ai.byak.app.core.di.ApplicationScope
import ai.byak.app.domain.model.AiProvider
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ProviderSelection(
    val provider: AiProvider,
    val model: String,
)

@Singleton
class SecureStore @Inject constructor(
    private val dataStore: DataStore<SecureState>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val cached = AtomicReference(SecureState())

    init {
        scope.launch {
            dataStore.data.collect { stored ->
                val supported = stored.withSupportedProvider()
                cached.set(supported)
                if (supported != stored) {
                    dataStore.updateData { current -> current.withSupportedProvider() }
                }
            }
        }
    }

    val state: Flow<SecureState> = dataStore.data.map { it.withSupportedProvider() }
    val hasSession: Flow<Boolean> = state
        .map { it.hasValidSession(System.currentTimeMillis() / 1_000) }
        .distinctUntilChanged()

    fun snapshot(): SecureState = cached.get()

    suspend fun update(transform: (SecureState) -> SecureState): SecureState {
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

    suspend fun saveProviderKey(
        provider: String,
        key: String,
        model: String,
        baseUrl: String = "",
    ) = update {
        val selected = provider.uppercase()
        val cleanModel = model.trim()
        when (selected) {
            "AUTO" -> it.copy(selectedProvider = "AUTO", selectedModel = AUTO_MODEL)
            "OPENAI" -> it.copy(
                openAiKey = normalizeCredential(AiProvider.OPENAI, key).value,
                selectedProvider = selected,
                selectedModel = cleanModel,
                openAiModel = cleanModel,
            )
            "OPENROUTER" -> it.copy(
                openRouterKey = normalizeCredential(AiProvider.OPENROUTER, key).value,
                selectedProvider = selected,
                selectedModel = cleanModel,
                openRouterModel = cleanModel,
            )
            "ANTHROPIC" -> it.copy(
                anthropicKey = normalizeCredential(AiProvider.ANTHROPIC, key).value,
                selectedProvider = selected,
                selectedModel = cleanModel,
                anthropicModel = cleanModel,
            )
            "GEMINI" -> it.copy(
                geminiKey = normalizeCredential(AiProvider.GEMINI, key).value,
                selectedProvider = selected,
                selectedModel = cleanModel,
                geminiModel = cleanModel,
            )
            "NVIDIA" -> it.copy(
                nvidiaKey = normalizeCredential(AiProvider.NVIDIA, key).value,
                selectedProvider = selected,
                selectedModel = cleanModel,
                nvidiaModel = cleanModel,
            )
            "GROQ" -> it.copy(
                groqKey = normalizeCredential(AiProvider.GROQ, key).value,
                selectedProvider = selected,
                selectedModel = cleanModel,
                groqModel = cleanModel,
            )
            "MISTRAL" -> it.copy(
                mistralKey = normalizeCredential(AiProvider.MISTRAL, key).value,
                selectedProvider = selected,
                selectedModel = cleanModel,
                mistralModel = cleanModel,
            )
            "DEEPSEEK" -> it.copy(
                deepSeekKey = normalizeCredential(AiProvider.DEEPSEEK, key).value,
                selectedProvider = selected,
                selectedModel = cleanModel,
                deepSeekModel = cleanModel,
            )
            "CUSTOM" -> it.copy(
                customKey = normalizeCredential(AiProvider.CUSTOM, key).value,
                customBaseUrl = normalizeCompatibleBaseUrl(baseUrl),
                selectedProvider = selected,
                selectedModel = cleanModel,
                customModel = cleanModel,
            )
            "ON_DEVICE" -> it.copy(selectedProvider = selected, selectedModel = ON_DEVICE_MODEL)
            else -> error("Unsupported AI provider: $provider")
        }
    }

    suspend fun selectAuto() = update {
        it.copy(selectedProvider = AiProvider.AUTO.name, selectedModel = AUTO_MODEL)
    }

    suspend fun saveImageModel(provider: String, model: String) = update {
        when (provider.uppercase()) {
            "OPENROUTER" -> it.copy(openRouterImageModel = model.trim())
            "GEMINI" -> it.copy(geminiImageModel = model.trim())
            else -> error("Unsupported image provider: $provider")
        }
    }

    fun apiKey(provider: String): String = snapshot().let { state ->
        when (provider.uppercase()) {
            "OPENAI" -> state.openAiKey
            "OPENROUTER" -> state.openRouterKey
            "ANTHROPIC" -> state.anthropicKey
            "GEMINI" -> state.geminiKey
            "NVIDIA" -> state.nvidiaKey
            "GROQ" -> state.groqKey
            "MISTRAL" -> state.mistralKey
            "DEEPSEEK" -> state.deepSeekKey
            "CUSTOM" -> state.customKey
            "AUTO", "ON_DEVICE" -> ON_DEVICE_READY_SENTINEL
            else -> ""
        }
    }

    fun model(provider: String): String = snapshot().let { state ->
        when (provider.uppercase()) {
            "OPENAI" -> state.openAiModel
            "OPENROUTER" -> state.openRouterModel
            "ANTHROPIC" -> state.anthropicModel
            "GEMINI" -> state.geminiModel
            "NVIDIA" -> state.nvidiaModel
            "GROQ" -> state.groqModel
            "MISTRAL" -> state.mistralModel
            "DEEPSEEK" -> state.deepSeekModel
            "CUSTOM" -> state.customModel
            "AUTO" -> AUTO_MODEL
            "ON_DEVICE" -> ON_DEVICE_MODEL
            else -> state.selectedModel
        }
    }

    fun baseUrl(provider: AiProvider): String = when (provider) {
        AiProvider.OPENAI -> "https://api.openai.com/v1"
        AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
        AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1"
        AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
        AiProvider.NVIDIA -> "https://integrate.api.nvidia.com/v1"
        AiProvider.GROQ -> "https://api.groq.com/openai/v1"
        AiProvider.MISTRAL -> "https://api.mistral.ai/v1"
        AiProvider.DEEPSEEK -> "https://api.deepseek.com/v1"
        AiProvider.CUSTOM -> snapshot().customBaseUrl
        AiProvider.AUTO, AiProvider.ON_DEVICE -> ""
    }

    fun configuredCloudProvider(): ProviderSelection? = snapshot().let { state ->
        listOf(
            Triple(AiProvider.OPENROUTER, state.openRouterKey, state.openRouterModel),
            Triple(AiProvider.GEMINI, state.geminiKey, state.geminiModel),
            Triple(AiProvider.NVIDIA, state.nvidiaKey, state.nvidiaModel),
            Triple(AiProvider.GROQ, state.groqKey, state.groqModel),
            Triple(AiProvider.MISTRAL, state.mistralKey, state.mistralModel),
            Triple(AiProvider.DEEPSEEK, state.deepSeekKey, state.deepSeekModel),
            Triple(AiProvider.OPENAI, state.openAiKey, state.openAiModel),
            Triple(AiProvider.ANTHROPIC, state.anthropicKey, state.anthropicModel),
            Triple(AiProvider.CUSTOM, state.customKey, state.customModel),
        ).firstOrNull { (provider, key, model) ->
            key.isNotBlank() && model.isNotBlank() &&
                (provider != AiProvider.CUSTOM || state.customBaseUrl.isNotBlank())
        }?.let { (provider, _, model) -> ProviderSelection(provider, model) }
    }

    fun hasConfiguredCloudProvider(): Boolean = configuredCloudProvider() != null

    companion object {
        const val AUTO_MODEL = "Best available"
        const val ON_DEVICE_MODEL = "Gemini Nano"
        private const val ON_DEVICE_READY_SENTINEL = "device"
    }
}

internal fun SecureState.withSupportedProvider(): SecureState {
    val supported = AiProvider.entries.any { it.name == selectedProvider }
    return if (supported) this else copy(
        selectedProvider = AiProvider.AUTO.name,
        selectedModel = SecureStore.AUTO_MODEL,
    )
}

internal fun normalizeCompatibleBaseUrl(value: String): String {
    val clean = value.trim().trimEnd('/')
    require(clean.isNotBlank()) { "Enter the provider base URL." }
    val uri = runCatching { URI(clean) }.getOrNull()
        ?: error("Enter a valid HTTPS provider URL.")
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
        "Universal providers must use a valid HTTPS URL."
    }
    return clean.removeSuffix("/chat/completions").removeSuffix("/models").trimEnd('/')
}
