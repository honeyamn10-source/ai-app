package ai.byak.app.ui.agent

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.domain.model.AgentRun
import ai.byak.app.domain.model.AgentStep
import ai.byak.app.domain.model.AgentType
import ai.byak.app.domain.repository.AgentRepository
import ai.byak.app.data.security.SecureStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AgentUiState(
    val runs: List<AgentRun> = emptyList(),
    val selectedRunId: String? = null,
    val steps: List<AgentStep> = emptyList(),
    val submitting: Boolean = false,
    val providerReady: Boolean = false,
)

sealed interface AgentEffect { data class Error(val message: String) : AgentEffect }

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val secureStore: SecureStore,
) : ViewModel() {
    private val selectedRunId = MutableStateFlow<String?>(null)
    private val submitting = MutableStateFlow(false)
    private val mutableEffects = MutableSharedFlow<AgentEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<AgentEffect> = mutableEffects.asSharedFlow()

    private val steps = selectedRunId.flatMapLatest { id ->
        if (id == null) emptyFlow() else repository.observeSteps(id)
    }

    val state: StateFlow<AgentUiState> = combine(
        repository.observeRuns(), selectedRunId, steps, submitting, secureStore.state,
    ) { runs, selected, stageItems, busy, secure ->
        AgentUiState(runs, selected, stageItems, busy, secureStore.apiKey(secure.selectedProvider).isNotBlank())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentUiState())

    fun select(runId: String?) { selectedRunId.value = runId }

    fun start(type: AgentType, goal: String) {
        if (submitting.value) return
        viewModelScope.launch {
            submitting.value = true
            runCatching { repository.enqueue(type, goal) }
                .onSuccess { selectedRunId.value = it }
                .onFailure { mutableEffects.emit(AgentEffect.Error(it.message ?: "Agent could not start")) }
            submitting.value = false
        }
    }

    fun cancel(runId: String) = viewModelScope.launch { repository.cancel(runId) }
}
