package ai.byak.app.ui.settings

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.billing.BillingManager
import ai.byak.app.billing.BillingState
import ai.byak.app.data.localai.PortableLocalModelManager
import ai.byak.app.data.localai.PortableModelState
import ai.byak.app.data.localai.PortableModelStatus
import ai.byak.app.data.repository.ProviderConnectionTester
import ai.byak.app.data.security.SecureStore
import ai.byak.app.domain.model.AiProvider
import ai.byak.app.domain.model.Session
import ai.byak.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val session: Session? = null,
    val provider: AiProvider = AiProvider.AUTO,
    val model: String = SecureStore.AUTO_MODEL,
    val openAiModel: String = "gpt-5.6-luna",
    val openRouterModel: String = "openrouter/auto",
    val anthropicModel: String = "claude-sonnet-5",
    val geminiModel: String = "gemini-3.8-flash",
    val nvidiaModel: String = "meta/llama-3.1-70b-instruct",
    val groqModel: String = "llama-3.3-70b-versatile",
    val mistralModel: String = "mistral-small-latest",
    val deepSeekModel: String = "deepseek-v4-flash",
    val customModel: String = "",
    val customBaseUrl: String = "",
    val hasOpenAi: Boolean = false,
    val hasOpenRouter: Boolean = false,
    val hasAnthropic: Boolean = false,
    val hasGemini: Boolean = false,
    val hasNvidia: Boolean = false,
    val hasGroq: Boolean = false,
    val hasMistral: Boolean = false,
    val hasDeepSeek: Boolean = false,
    val hasCustom: Boolean = false,
    val portableStatus: PortableModelStatus = PortableModelStatus.MISSING,
    val portableProgress: Int = 0,
    val portableMessage: String = "",
    val testingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val connectionSucceeded: Boolean? = null,
    val availableModels: List<String> = emptyList(),
    val billing: BillingState = BillingState(),
)

@Immutable
private data class ConnectionUiState(
    val testing: Boolean = false,
    val message: String? = null,
    val succeeded: Boolean? = null,
    val availableModels: List<String> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStore: SecureStore,
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager,
    private val connectionTester: ProviderConnectionTester,
    private val portableLocal: PortableLocalModelManager,
) : ViewModel() {
    private val connection = MutableStateFlow(ConnectionUiState())
    private val portable = portableLocal.observeState()
        .stateIn(viewModelScope, SharingStarted.Eagerly, portableLocal.state())

    val state: StateFlow<SettingsUiState> = combine(
        secureStore.state,
        authRepository.session,
        billingManager.state,
        connection,
        portable,
    ) { secure, session, billing, connectionState, portableState ->
        SettingsUiState(
            session = session,
            provider = runCatching { AiProvider.valueOf(secure.selectedProvider) }
                .getOrDefault(AiProvider.AUTO),
            model = secure.selectedModel,
            openAiModel = secure.openAiModel,
            openRouterModel = secure.openRouterModel,
            anthropicModel = secure.anthropicModel,
            geminiModel = secure.geminiModel,
            nvidiaModel = secure.nvidiaModel,
            groqModel = secure.groqModel,
            mistralModel = secure.mistralModel,
            deepSeekModel = secure.deepSeekModel,
            customModel = secure.customModel,
            customBaseUrl = secure.customBaseUrl,
            hasOpenAi = secure.openAiKey.isNotBlank(),
            hasOpenRouter = secure.openRouterKey.isNotBlank(),
            hasAnthropic = secure.anthropicKey.isNotBlank(),
            hasGemini = secure.geminiKey.isNotBlank(),
            hasNvidia = secure.nvidiaKey.isNotBlank(),
            hasGroq = secure.groqKey.isNotBlank(),
            hasMistral = secure.mistralKey.isNotBlank(),
            hasDeepSeek = secure.deepSeekKey.isNotBlank(),
            hasCustom = secure.customKey.isNotBlank() && secure.customBaseUrl.isNotBlank(),
            portableStatus = portableState.status,
            portableProgress = portableState.progressPercent,
            portableMessage = portableState.message,
            testingConnection = connectionState.testing,
            connectionMessage = connectionState.message,
            connectionSucceeded = connectionState.succeeded,
            availableModels = connectionState.availableModels,
            billing = billing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun saveAndTestProvider(
        provider: AiProvider,
        newKey: String,
        model: String,
        baseUrl: String,
    ) {
        if (connection.value.testing) return
        viewModelScope.launch {
            connection.value = ConnectionUiState(testing = true, message = when (provider) {
                AiProvider.PORTABLE_LOCAL -> "Checking the local model…"
                AiProvider.ON_DEVICE -> "Checking Android AICore…"
                else -> "Checking the secure connection…"
            })
            val existing = secureStore.apiKey(provider.name)
            val effectiveKey = newKey.trim().ifBlank { existing }
            connectionTester.test(provider, effectiveKey, model.trim(), baseUrl.trim())
                .onSuccess { report ->
                    secureStore.saveProviderKey(
                        provider = provider.name,
                        key = report.normalizedCredential.ifBlank { effectiveKey },
                        model = report.resolvedModel,
                        baseUrl = report.normalizedBaseUrl.ifBlank { baseUrl },
                    )
                    connection.value = ConnectionUiState(
                        message = report.message,
                        succeeded = true,
                        availableModels = report.availableModels,
                    )
                }
                .onFailure { error ->
                    connection.value = ConnectionUiState(
                        message = error.message ?: "Connection test failed. Check the key and try again.",
                        succeeded = false,
                    )
                }
        }
    }

    fun clearConnectionMessage() = connection.update { ConnectionUiState() }
    fun purchase(activity: Activity, productId: String) = billingManager.purchase(activity, productId)
    fun restorePurchases() = billingManager.restore()
    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}
