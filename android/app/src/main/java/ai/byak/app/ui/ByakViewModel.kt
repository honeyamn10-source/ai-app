package ai.byak.app.ui

import android.app.Activity
import ai.byak.app.billing.BillingManager
import ai.byak.app.billing.PlanOffer
import ai.byak.app.billing.PurchaseOutcome
import ai.byak.app.data.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val busy: Boolean = false, val streaming: Boolean = false, val error: String? = null, val notice: String? = null,
    val catalog: List<CatalogProvider> = emptyList(), val providers: List<Provider> = emptyList(),
    val conversations: List<Conversation> = emptyList(), val conversationQuery: String = "",
    val activeConversation: Conversation? = null, val messages: List<ChatMessage> = emptyList(),
    val projects: List<Project> = emptyList(), val files: List<UserFile> = emptyList(), val research: List<ResearchResult> = emptyList(),
    val profile: Profile? = null, val subscription: Subscription = Subscription.FREE, val offers: List<PlanOffer> = emptyList(),
    val memoryEnabled: Boolean = false, val memories: List<Memory> = emptyList(), val usage: Usage? = null, val devices: List<Device> = emptyList(),
    val modelOptions: Map<String, List<String>> = emptyMap(), val upgradeSuggested: Boolean = false
)

class ByakViewModel(private val api: ApiClient, private val billing: BillingManager) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var generation: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            billing.purchases.collect { outcome ->
                when (outcome) {
                    is PurchaseOutcome.Purchased -> task("Activating your plan…") { val sub = api.verifyPurchases(outcome.tokens); update { copy(subscription = sub, upgradeSuggested = false) }; notice(if (sub.isPro) "Welcome to BYAK Pro!" else "Purchase received — activation is pending") }
                    PurchaseOutcome.Pending -> notice("Payment pending. Your plan activates once Google Play confirms it.")
                    PurchaseOutcome.Cancelled -> Unit
                    is PurchaseOutcome.Failed -> update { copy(error = outcome.message) }
                }
            }
        }
    }

    private fun update(block: UiState.() -> UiState) = _state.update(block)
    private fun notice(message: String) = update { copy(notice = message) }
    fun clearMessages() = update { copy(error = null, notice = null) }
    fun dismissUpgrade() = update { copy(upgradeSuggested = false) }

    private fun task(success: String? = null, block: suspend () -> Unit) = viewModelScope.launch {
        update { copy(busy = true, error = null) }
        try { block(); success?.let { if (state.value.notice == null) notice(it) } }
        catch (e: CancellationException) { throw e }
        catch (e: ApiException) { update { copy(error = e.message, upgradeSuggested = e.code == "plan_limit" || upgradeSuggested) } }
        catch (e: Exception) { update { copy(error = e.message ?: "Something went wrong") } }
        finally { update { copy(busy = false) } }
    }

    fun bootstrap() = task {
        val catalog = runCatching { api.catalog() }.getOrDefault(state.value.catalog)
        val (profile, subscription) = api.profile()
        update { copy(catalog = catalog, profile = profile, subscription = subscription) }
        val providers = api.providers(); val conversations = api.conversations(state.value.conversationQuery); val projects = api.projects(); val files = api.files()
        update { copy(providers = providers, conversations = conversations, projects = projects, files = files, memoryEnabled = profile.memoryEnabled) }
    }

    // ---------- providers ----------
    fun addProvider(type: String, key: String, model: String, baseUrl: String = "", done: () -> Unit = {}) = task("Provider connected") {
        api.addProvider(type, key.trim(), model.trim(), baseUrl.trim())
        val list = api.providers(); update { copy(providers = list) }; done()
    }
    fun validateProvider(id: String) = task("Key works — you're ready to chat") { api.validateProvider(id); val list = api.providers(); update { copy(providers = list) } }
    fun removeProvider(id: String) = task("Provider removed") { api.deleteProvider(id); val list = api.providers(); update { copy(providers = list) } }
    fun setProviderEnabled(id: String, enabled: Boolean) = task { api.updateProvider(id, enabled = enabled); val list = api.providers(); update { copy(providers = list) } }
    fun setDefaultModel(id: String, model: String) = task("Default model updated") { api.updateProvider(id, defaultModel = model); val list = api.providers(); update { copy(providers = list) } }
    fun loadModels(providerId: String) = viewModelScope.launch {
        if (state.value.modelOptions.containsKey(providerId)) return@launch
        val models = runCatching { api.providerModels(providerId) }.getOrElse { emptyList() }
        update { copy(modelOptions = modelOptions + (providerId to models)) }
    }

    // ---------- conversations ----------
    fun searchConversations(query: String) {
        update { copy(conversationQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(300); runCatching { api.conversations(query) }.onSuccess { list -> update { copy(conversations = list) } } }
    }
    private suspend fun refreshConversations() { val list = api.conversations(state.value.conversationQuery); update { copy(conversations = list) } }

    fun openConversation(item: Conversation) = task { update { copy(activeConversation = item, messages = emptyList()) }; val messages = api.messages(item.id); update { copy(messages = messages) } }
    fun closeConversation() { stopGeneration(); update { copy(activeConversation = null, messages = emptyList()) }; viewModelScope.launch { runCatching { refreshConversations() } } }

    fun newConversation(projectId: String? = null, firstMessage: String? = null) = task {
        val provider = state.value.providers.firstOrNull { it.enabled } ?: throw ApiException("Connect an AI provider in Models first", 400)
        val item = api.createConversation(provider.id, provider.defaultModel, projectId)
        update { copy(activeConversation = item, messages = emptyList()) }
        refreshConversations()
        firstMessage?.let { send(it) }
    }

    fun renameConversation(id: String, title: String) = task { val updated = api.updateConversation(id, title = title); update { copy(activeConversation = if (activeConversation?.id == id) updated else activeConversation) }; refreshConversations() }
    fun togglePin(item: Conversation) = task { api.updateConversation(item.id, pinned = !item.pinned); refreshConversations() }
    fun archiveConversation(id: String) = task("Conversation archived") { api.updateConversation(id, archived = true); if (state.value.activeConversation?.id == id) update { copy(activeConversation = null, messages = emptyList()) }; refreshConversations() }
    fun deleteConversation(id: String) = task("Conversation deleted") { api.deleteConversation(id); if (state.value.activeConversation?.id == id) update { copy(activeConversation = null, messages = emptyList()) }; refreshConversations() }
    fun setConversationModel(providerId: String, model: String) = task {
        val current = state.value.activeConversation ?: return@task
        val updated = api.updateConversation(current.id, providerId = providerId, model = model); update { copy(activeConversation = updated) }
    }

    private fun activeProvider(conversation: Conversation): Provider? =
        state.value.providers.firstOrNull { it.id == conversation.providerId && it.enabled } ?: state.value.providers.firstOrNull { it.enabled }

    fun send(text: String) {
        val conversation = state.value.activeConversation ?: return
        val provider = activeProvider(conversation) ?: run { update { copy(error = "Connect an AI provider in Models first") }; return }
        val local = ChatMessage("local-user-${System.nanoTime()}", "user", text)
        streamInto(conversation, listOf(local)) { api.streamMessage(conversation.id, text, provider.id, conversation.model.ifBlank { provider.defaultModel }) }
    }

    fun regenerate() {
        val conversation = state.value.activeConversation ?: return
        val provider = activeProvider(conversation) ?: return
        val trimmed = state.value.messages.dropLastWhile { it.role == "assistant" }
        update { copy(messages = trimmed) }
        streamInto(conversation, emptyList()) { api.regenerate(conversation.id, provider.id, conversation.model.ifBlank { provider.defaultModel }) }
    }

    private fun streamInto(conversation: Conversation, prefix: List<ChatMessage>, source: () -> Flow<StreamEvent>) {
        if (generation?.isActive == true) return
        val pending = ChatMessage("local-assistant", "assistant", "", pending = true)
        update { copy(messages = messages + prefix + pending, streaming = true, error = null) }
        generation = viewModelScope.launch {
            var answer = ""
            try {
                source().collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> { answer += event.text; update { copy(messages = messages.dropLast(1) + pending.copy(content = answer)) } }
                        is StreamEvent.Complete -> update { copy(messages = messages.dropLast(1) + event.message) }
                        is StreamEvent.Failed -> update { copy(error = event.message) }
                    }
                }
            } catch (e: CancellationException) { /* stopped by the user; the server keeps the partial answer */ }
            catch (e: ApiException) { update { copy(error = e.message, upgradeSuggested = e.code == "plan_limit" || upgradeSuggested) } }
            catch (e: Exception) { update { copy(error = e.message ?: "Generation failed") } }
            finally {
                update { copy(streaming = false) }
                viewModelScope.launch {
                    delay(250) // let the server persist a stopped/partial answer
                    runCatching { api.messages(conversation.id) }.onSuccess { list -> if (state.value.activeConversation?.id == conversation.id) update { copy(messages = list) } }
                    runCatching { refreshConversations() }
                }
            }
        }
    }

    fun stopGeneration() { generation?.cancel(); generation = null }

    suspend fun exportConversation(id: String): String? = runCatching { api.exportConversation(id) }.onFailure { update { copy(error = it.message) } }.getOrNull()

    // ---------- research ----------
    fun search(query: String, source: String) = task { val results = api.research(query, source); update { copy(research = results) }; if (results.isEmpty()) notice("No results found") }
    fun summarizeResearch(query: String) {
        val results = state.value.research.take(10); if (results.isEmpty()) return
        val prompt = buildString {
            append("Summarize what these sources say about \"$query\". Compare them, highlight disagreements, and cite each source by number.\n\n")
            results.forEachIndexed { i, r -> append("[${i + 1}] ${r.title}\n${r.url}\n${r.summary.take(600)}\n\n") }
        }
        newConversation(firstMessage = prompt)
    }

    // ---------- projects & files ----------
    fun addProject(name: String, description: String, instructions: String) = task("Project created") { api.createProject(name, description, instructions); val list = api.projects(); update { copy(projects = list) } }
    fun editProject(id: String, name: String, description: String, instructions: String) = task("Project saved") { api.updateProject(id, name, description, instructions); val list = api.projects(); update { copy(projects = list) } }
    fun removeProject(id: String) = task("Project deleted") { api.deleteProject(id); val list = api.projects(); update { copy(projects = list) } }
    fun upload(name: String, type: String, text: String) = task("$name added to your knowledge base") { api.uploadText(name, type, text); val list = api.files(); update { copy(files = list) } }
    fun removeFile(id: String) = task("File deleted") { api.deleteFile(id); val list = api.files(); update { copy(files = list) } }
    fun reportError(message: String) = update { copy(error = message) }

    // ---------- memory, usage, account ----------
    fun loadMemory() = task { val (enabled, items) = api.memory(); update { copy(memoryEnabled = enabled, memories = items) } }
    fun setMemoryEnabled(enabled: Boolean) = task { api.setMemoryEnabled(enabled); update { copy(memoryEnabled = enabled) } }
    fun addMemory(content: String) = task { api.addMemory(content); val (_, items) = api.memory(); update { copy(memories = items) } }
    fun deleteMemory(id: String) = task { api.deleteMemory(id); update { copy(memories = memories.filterNot { it.id == id }) } }
    fun clearMemory() = task("Memory cleared") { api.clearMemory(); update { copy(memories = emptyList()) } }
    fun loadUsage() = task { val usage = api.usage(); update { copy(usage = usage) } }
    fun loadDevices() = task { val devices = api.devices(); update { copy(devices = devices) } }
    fun revokeDevice(id: String) = task("Device signed out") { api.revokeDevice(id); val devices = api.devices(); update { copy(devices = devices) } }
    fun saveProfile(name: String, instructions: String) = task("Saved") { api.updateProfile(name, instructions); val (profile, _) = api.profile(); update { copy(profile = profile) } }
    fun changePassword(current: String, new: String, done: () -> Unit) = task("Password changed. Other devices were signed out.") { api.changePassword(current, new); done() }
    suspend fun exportData(): String? = runCatching { api.exportData() }.onFailure { update { copy(error = it.message) } }.getOrNull()
    fun logout() = viewModelScope.launch { stopGeneration(); api.logout() }
    fun deleteAccount() = task { api.deleteAccount()?.let { message -> notice(message) } }

    // ---------- billing ----------
    fun loadPlans(productId: String, basePlanIds: List<String>) = task {
        val sub = api.subscription(); update { copy(subscription = sub) }
        val offers = runCatching { billing.offers(productId, basePlanIds) }.getOrDefault(emptyList()); update { copy(offers = offers) }
    }
    fun buy(activity: Activity, offer: PlanOffer) {
        val accountId = state.value.subscription.billingAccountId
        billing.launch(activity, offer, accountId)?.let { message -> update { copy(error = message) } }
    }
    fun restorePurchases() = task {
        val tokens = billing.ownedPurchaseTokens()
        if (tokens.isEmpty()) { notice("No active Google Play subscription found for this account"); return@task }
        val sub = api.verifyPurchases(tokens); update { copy(subscription = sub) }
        notice(if (sub.isPro) "Your Pro plan is active" else "Purchase found but not active")
    }

    override fun onCleared() { billing.close() }
}
