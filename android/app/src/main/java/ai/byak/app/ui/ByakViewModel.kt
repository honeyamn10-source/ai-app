package ai.byak.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false, val error: String? = null, val providers: List<Provider> = emptyList(),
    val conversations: List<Conversation> = emptyList(), val messages: List<ChatMessage> = emptyList(),
    val activeConversation: Conversation? = null, val projects: List<Project> = emptyList(),
    val files: List<UserFile> = emptyList(), val research: List<ResearchResult> = emptyList(),
    val agentEvents: List<AgentEvent> = emptyList(), val agentRunning: Boolean = false
)

class ByakViewModel(private val api: ApiClient) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    fun bootstrap() = launch { copy(providers = api.providers(), conversations = api.conversations(), projects = api.projects(), files = api.files()) }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun addProvider(type: String, key: String, model: String, baseUrl: String = "", done: () -> Unit = {}) = launch { api.addProvider(type, key, model, baseUrl); copy(providers = api.providers()); done() }
    fun validateProvider(id: String) = launch { api.validateProvider(id) }
    fun removeProvider(id: String) = launch { api.deleteProvider(id); copy(providers = api.providers()) }
    fun openConversation(item: Conversation) = launch { copy(activeConversation = item, messages = api.messages(item.id)) }
    fun newConversation() = launch { val p = state.value.providers.firstOrNull(); val item = api.createConversation(p?.id, p?.defaultModel.orEmpty()); copy(activeConversation = item, conversations = api.conversations(), messages = emptyList()) }
    fun send(text: String) = launch {
        val current = state.value.activeConversation ?: return@launch; val p = state.value.providers.firstOrNull { it.id == current.providerId } ?: state.value.providers.firstOrNull() ?: throw ApiException("Add an AI provider in Models first", 400)
        val localUser = ChatMessage("local-user", "user", text); val pending = ChatMessage("local-assistant", "assistant", "", true); copy(messages = state.value.messages + localUser + pending)
        var answer = ""; api.streamMessage(current.id, text, p.id, current.model.ifBlank { p.defaultModel }).collect { delta -> answer += delta; copy(messages = state.value.messages.dropLast(1) + pending.copy(content = answer)) }
        copy(messages = api.messages(current.id), conversations = api.conversations())
    }
    fun search(query: String, source: String) = launch { copy(research = api.research(query, source)) }
    fun addProject(name: String, description: String) = launch { api.createProject(name, description); copy(projects = api.projects()) }
    fun removeProject(id: String) = launch { api.deleteProject(id); copy(projects = api.projects()) }
    fun upload(name: String, type: String, text: String) = launch { api.uploadText(name, type, text); copy(files = api.files()) }
    fun removeFile(id: String) = launch { api.deleteFile(id); copy(files = api.files()) }
    fun runAgent(template: String, goal: String) = viewModelScope.launch {
        val provider = state.value.providers.firstOrNull()
        if (provider == null) {
            _state.update { it.copy(error = "Connect one AI provider first", agentRunning = false) }
            return@launch
        }
        _state.update { it.copy(agentEvents = emptyList(), agentRunning = true, error = null) }
        try {
            api.runAgent(template, goal, provider.id, provider.defaultModel).collect { event ->
                _state.update { current -> current.copy(agentEvents = current.agentEvents + event) }
            }
            copy(conversations = api.conversations())
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "Agent run failed") }
        } finally {
            _state.update { it.copy(agentRunning = false) }
        }
    }
    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { _state.update { it.copy(loading = true, error = null) }; try { block() } catch (e: Exception) { _state.update { it.copy(error = e.message ?: "Something went wrong") } } finally { _state.update { it.copy(loading = false) } } }
    private fun copy(providers: List<Provider> = state.value.providers, conversations: List<Conversation> = state.value.conversations, messages: List<ChatMessage> = state.value.messages, activeConversation: Conversation? = state.value.activeConversation, projects: List<Project> = state.value.projects, files: List<UserFile> = state.value.files, research: List<ResearchResult> = state.value.research) { _state.update { it.copy(providers = providers, conversations = conversations, messages = messages, activeConversation = activeConversation, projects = projects, files = files, research = research) } }
}
