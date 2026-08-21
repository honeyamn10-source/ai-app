package ai.byak.app.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.domain.model.ChatMessage
import ai.byak.app.domain.model.Conversation
import ai.byak.app.domain.repository.ChatRepository
import ai.byak.app.data.security.SecureStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val generating: Boolean = false,
    val providerReady: Boolean = false,
    val providerName: String = "Choose AI",
    val model: String = "",
)

sealed interface ChatEffect { data class Error(val message: String) : ChatEffect }

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val secureStore: SecureStore,
) : ViewModel() {
    private val activeId = MutableStateFlow<String?>(null)
    private val generating = MutableStateFlow(false)
    private val mutableEffects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ChatEffect> = mutableEffects.asSharedFlow()
    private var generationJob: Job? = null

    private val messages: Flow<List<ChatMessage>> = activeId.flatMapLatest { id ->
        if (id == null) emptyFlow() else repository.observeMessages(id)
    }

    val state: StateFlow<ChatUiState> = combine(
        repository.observeConversations(), activeId, messages, generating, secureStore.state,
    ) { conversations, selected, items, isGenerating, secure ->
        ChatUiState(
            conversations = conversations,
            activeConversationId = selected,
            messages = items,
            generating = isGenerating,
            providerReady = secureStore.apiKey(secure.selectedProvider).isNotBlank(),
            providerName = secure.selectedProvider.lowercase().replaceFirstChar(Char::uppercase),
            model = secure.selectedModel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun openConversation(id: String?) { activeId.value = id }

    fun newConversation() { activeId.value = null }

    fun send(prompt: String) {
        if (prompt.isBlank() || generating.value) return
        generationJob = viewModelScope.launch {
            generating.value = true
            try {
                val id = activeId.value ?: repository.createConversation(prompt.trim().take(70)).also { activeId.value = it }
                repository.send(id, prompt).collect { }
            } catch (_: CancellationException) {
                // Stopping is a successful user action, not an error.
            } catch (error: Throwable) {
                mutableEffects.emit(ChatEffect.Error(error.message ?: "BYAK could not complete that response"))
            } finally {
                generating.value = false
            }
        }
    }

    fun stop() {
        generationJob?.cancel()
        generating.value = false
    }
}
