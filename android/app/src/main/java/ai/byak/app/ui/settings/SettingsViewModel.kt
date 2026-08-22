package ai.byak.app.ui.settings

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.billing.BillingManager
import ai.byak.app.billing.BillingState
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
    val provider: AiProvider = AiProvider.ON_DEVICE,
    val model: String = "Gemini Nano",
    val openAiModel: String = "gpt-5-mini",
    val openRouterModel: String = "openrouter/auto",
    val anthropicModel: String = "claude-sonnet-4-5",
    val geminiModel: String = "gemini-3.1-flash-lite",
    val hasOpenAi: Boolean = false,
    val hasOpenRouter: Boolean = false,
    val hasAnthropic: Boolean = false,
    val hasGemini: Boolean = false,
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
) : ViewModel() {
    private val connection = MutableStateFlow(ConnectionUiState())

    val state: StateFlow<SettingsUiState> = combine(
        secureStore.state,
        authRepository.session,
        billingManager.state,
        connection,
    ) { secure, session, billing, connectionState ->
        SettingsUiState(
            session = session,
            provider = runCatching { AiProvider.valueOf(secure.selectedProvider) }.getOrDefault(AiProvider.ON_DEVICE),
            model = secure.selectedModel,
            openAiModel = secure.openAiModel,
            openRouterModel = secure.openRouterModel,
            anthropicModel = secure.anthropicModel,
            geminiModel = secure.geminiModel,
            hasOpenAi = secure.openAiKey.isNotBlank(),
            hasOpenRouter = secure.openRouterKey.isNotBlank(),
            hasAnthropic = secure.anthropicKey.isNotBlank(),
            hasGemini = secure.geminiKey.isNotBlank(),
            testingConnection = connectionState.testing,
            connectionMessage = connectionState.message,
            connectionSucceeded = connectionState.succeeded,
            availableModels = connectionState.availableModels,
            billing = billing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun saveAndTestProvider(provider: AiProvider, newKey: String, model: String) {
        if (connection.value.testing) return
        viewModelScope.launch {
            connection.value = ConnectionUiState(testing = true, message = "Checking the secure connection…")
            val existing = secureStore.apiKey(provider.name)
            val effectiveKey = newKey.trim().ifBlank { existing }
            connectionTester.test(provider, effectiveKey, model.trim())
                .onSuccess { report ->
                    secureStore.saveProviderKey(
                        provider = provider.name,
                        key = report.normalizedCredential.ifBlank { effectiveKey },
                        model = report.resolvedModel,
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
