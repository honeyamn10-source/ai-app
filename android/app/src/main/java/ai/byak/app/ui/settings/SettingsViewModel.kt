package ai.byak.app.ui.settings

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.billing.BillingManager
import ai.byak.app.billing.BillingState
import ai.byak.app.data.security.SecureStore
import ai.byak.app.domain.model.AiProvider
import ai.byak.app.domain.model.Session
import ai.byak.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val session: Session? = null,
    val provider: AiProvider = AiProvider.OPENAI,
    val model: String = "gpt-5-mini",
    val hasOpenAi: Boolean = false,
    val hasAnthropic: Boolean = false,
    val hasGemini: Boolean = false,
    val billing: BillingState = BillingState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStore: SecureStore,
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        secureStore.state, authRepository.session, billingManager.state,
    ) { secure, session, billing ->
        SettingsUiState(
            session = session,
            provider = runCatching { AiProvider.valueOf(secure.selectedProvider) }.getOrDefault(AiProvider.OPENAI),
            model = secure.selectedModel,
            hasOpenAi = secure.openAiKey.isNotBlank(),
            hasAnthropic = secure.anthropicKey.isNotBlank(),
            hasGemini = secure.geminiKey.isNotBlank(),
            billing = billing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun saveProvider(provider: AiProvider, newKey: String, model: String) {
        viewModelScope.launch {
            val existing = secureStore.apiKey(provider.name)
            secureStore.saveProviderKey(provider.name, newKey.trim().ifBlank { existing }, model.trim())
        }
    }

    fun purchase(activity: Activity, productId: String) = billingManager.purchase(activity, productId)
    fun restorePurchases() = billingManager.restore()
    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}
