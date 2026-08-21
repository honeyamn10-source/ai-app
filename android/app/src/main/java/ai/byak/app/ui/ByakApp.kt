package ai.byak.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.byak.app.billing.BillingManager
import ai.byak.app.billing.BillingState
import ai.byak.app.data.*
import kotlinx.coroutines.launch

private enum class AppTab(val label: String, val icon: ImageVector) {
    Chat("Chat", Icons.Outlined.ChatBubbleOutline),
    Agents("Agents", Icons.Outlined.AutoAwesome),
    Library("Library", Icons.Outlined.FolderOpen),
    You("You", Icons.Outlined.PersonOutline)
}

private data class AgentTemplate(val name: String, val detail: String, val icon: ImageVector, val tint: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByakApp(api: ApiClient, billingManager: BillingManager) {
    val vm = remember(api) { ByakViewModel(api) }
    val state by vm.state.collectAsState()
    val billing by billingManager.state.collectAsState()
    var tab by remember { mutableStateOf(AppTab.Chat) }
    var showModels by remember { mutableStateOf(false) }
    var showPlans by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.bootstrap() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ByakMark(34.dp)
                        Text("BYAK", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                },
                navigationIcon = { Spacer(Modifier.width(48.dp)) },
                actions = {
                    Surface(
                        onClick = { showPlans = true },
                        shape = RoundedCornerShape(20.dp),
                        color = if (billing.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WorkspacePremium, null, Modifier.size(17.dp))
                            Text(if (billing.active) " Pro" else " Upgrade", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.Chat -> FriendlyChatScreen(state, vm, openModels = { showModels = true })
                AppTab.Agents -> AgentsScreen(state, vm, openModels = { showModels = true })
                AppTab.Library -> LibraryScreen(state, vm)
                AppTab.You -> ProfileScreen(state, api, billing, openModels = { showModels = true }, openPlans = { showPlans = true })
            }
            state.error?.let { message ->
                Snackbar(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = vm::clearError) { Text("Dismiss") } }
                ) { Text(message) }
            }
        }
    }

    if (showModels) ModelSheet(state, vm, close = { showModels = false })
    if (showPlans) SubscriptionSheet(billing, billingManager, close = { showPlans = false })
}

@Composable
private fun ByakMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * .32f)).background(
            Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF9B8AFB), Color(0xFFFFB86B)))
        ),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Outlined.AutoAwesome, "BYAK", tint = Color.White, modifier = Modifier.size(size * .58f)) }
}

@Composable
private fun FriendlyChatScreen(state: UiState, vm: ByakViewModel, openModels: () -> Unit) {
    val conversation = state.activeConversation
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(onClick = openModels, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bolt, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(" ${state.providers.firstOrNull()?.name ?: "Choose AI"}", fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::newConversation, enabled = state.providers.isNotEmpty()) { Icon(Icons.Outlined.AddComment, "New chat") }
        }

        if (conversation == null) ChatWelcome(state, vm, openModels) else ConversationView(state, vm)
    }
}

@Composable
private fun ChatWelcome(state: UiState, vm: ByakViewModel, openModels: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Hello, I’m BYAK", fontSize = 31.sp, fontWeight = FontWeight.Black)
            Text("What would you like to make or solve today?", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
        if (state.providers.isEmpty()) {
            item {
                Card(
                    onClick = openModels,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ByakMark(46.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Connect your AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("A simple one-time setup. Your key stays encrypted on this phone.")
                        }
                        Icon(Icons.Outlined.ArrowForward, null)
                    }
                }
            }
        }
        item { Text("Try asking", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(
            listOf(
                "Create a business plan" to Icons.Outlined.Lightbulb,
                "Research a difficult topic" to Icons.Outlined.TravelExplore,
                "Help me build an app" to Icons.Outlined.Code,
                "Explain something simply" to Icons.Outlined.School
            )
        ) { suggestion ->
            SuggestionCard(suggestion.first, suggestion.second, enabled = state.providers.isNotEmpty(), onClick = vm::newConversation)
        }
        if (state.conversations.isNotEmpty()) {
            item { Text("Recent", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp)) }
            items(state.conversations.take(6)) { item ->
                ListItem(
                    headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(item.model.ifBlank { "BYAK conversation" }) },
                    leadingContent = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { vm.openConversation(item) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(text, Modifier.weight(1f).padding(horizontal = 14.dp), fontWeight = FontWeight.Medium)
            Icon(Icons.Outlined.ArrowOutward, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ConversationView(state: UiState, vm: ByakViewModel) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.messages.isEmpty()) item {
                Column(Modifier.fillParentMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    ByakMark(58.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("A fresh conversation", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Ask anything. I’ll help you work through it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.messages) { MessageBubble(it) }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Message BYAK…") }, maxLines = 6, shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { val text = draft.trim(); if (text.isNotEmpty()) { draft = ""; vm.send(text) } },
                    enabled = draft.isNotBlank() && !state.loading,
                    modifier = Modifier.size(52.dp)
                ) { Icon(Icons.Outlined.ArrowUpward, "Send") }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        if (!user) { ByakMark(30.dp); Spacer(Modifier.width(8.dp)) }
        Surface(
            color = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(if (user) .86f else .92f)
        ) {
            Text(message.content.ifEmpty { "Thinking…" }, Modifier.padding(horizontal = 17.dp, vertical = 14.dp), lineHeight = 22.sp)
        }
    }
}

@Composable
private fun AgentsScreen(state: UiState, vm: ByakViewModel, openModels: () -> Unit) {
    val templates = listOf(
        AgentTemplate("Deep Research", "Search, compare sources and create a clear report", Icons.Outlined.TravelExplore, Color(0xFF6C5CE7)),
        AgentTemplate("Builder", "Plan products, apps and complete implementation tasks", Icons.Outlined.Handyman, Color(0xFF007F73)),
        AgentTemplate("Business Planner", "Validate ideas, pricing, risks and next steps", Icons.Outlined.QueryStats, Color(0xFFE17A32)),
        AgentTemplate("Study Coach", "Learn step by step with examples and practice", Icons.Outlined.School, Color(0xFF3478C0)),
        AgentTemplate("Custom Agent", "Give BYAK a goal and let it plan the work", Icons.Outlined.AutoMode, Color(0xFF9B59B6))
    )
    var selected by remember { mutableStateOf<AgentTemplate?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Agents", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Give BYAK a goal. It will plan, research, reason and deliver.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
        }
        if (state.providers.isEmpty()) item {
            Card(onClick = openModels, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
                ListItem(
                    headlineContent = { Text("Connect an AI first", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("One provider powers chat and every agent") },
                    leadingContent = { ByakMark(42.dp) }, trailingContent = { Icon(Icons.Outlined.ArrowForward, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
        items(templates) { template ->
            Card(onClick = { selected = template }, shape = RoundedCornerShape(22.dp), enabled = !state.agentRunning && state.providers.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = template.tint.copy(alpha = .14f)) {
                        Icon(template.icon, null, Modifier.padding(13.dp), tint = template.tint)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(template.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(template.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
        if (state.agentEvents.isNotEmpty()) {
            item { Text(if (state.agentRunning) "Agent working…" else "Latest result", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
            items(state.agentEvents) { event -> AgentEventCard(event, state.agentRunning) }
        }
    }
    selected?.let { template ->
        AgentGoalDialog(template, close = { selected = null }) { goal ->
            vm.runAgent(template.name, goal)
            selected = null
        }
    }
}

@Composable
private fun AgentEventCard(event: AgentEvent, running: Boolean) {
    val final = event.completed
    Surface(shape = RoundedCornerShape(18.dp), color = if (final) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            if (final) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            else if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Check, null)
            Column(Modifier.padding(start = 12.dp)) {
                Text(event.stage, fontWeight = FontWeight.Bold)
                Text(event.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AgentGoalDialog(template: AgentTemplate, close: () -> Unit, run: (String) -> Unit) {
    var goal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(template.icon, null, tint = template.tint) },
        title = { Text(template.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Describe the outcome you want. BYAK will choose the steps.")
                OutlinedTextField(goal, { goal = it }, label = { Text("Your goal") }, minLines = 4, maxLines = 8, shape = RoundedCornerShape(18.dp))
            }
        },
        confirmButton = { Button(onClick = { run(goal.trim()) }, enabled = goal.isNotBlank()) { Text("Start agent") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
private fun LibraryScreen(state: UiState, vm: ByakViewModel) {
    val context = LocalContext.current
    var showProject by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.txt"
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val type = context.contentResolver.getType(uri) ?: "text/plain"
        vm.upload(name, type, text)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Library", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Everything BYAK can use to help you.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                Button(onClick = { picker.launch("text/*") }, Modifier.weight(1f)) { Icon(Icons.Outlined.UploadFile, null); Text(" Add file") }
                OutlinedButton(onClick = { showProject = true }, Modifier.weight(1f)) { Icon(Icons.Outlined.CreateNewFolder, null); Text(" Project") }
            }
        }
        item { SectionTitle("Files", state.files.size) }
        if (state.files.isEmpty()) item { FriendlyEmpty(Icons.Outlined.Description, "No files yet", "Add notes or documents so BYAK can use them in answers.") }
        items(state.files) { file ->
            ListItem(
                headlineContent = { Text(file.name) }, supportingContent = { Text("${file.chunkCount} knowledge sections") },
                leadingContent = { Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { IconButton(onClick = { vm.removeFile(file.id) }) { Icon(Icons.Outlined.DeleteOutline, "Delete") } },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        item { SectionTitle("Projects", state.projects.size) }
        if (state.projects.isEmpty()) item { FriendlyEmpty(Icons.Outlined.FolderOpen, "No projects yet", "Group your work into simple project spaces.") }
        items(state.projects) { project ->
            ListItem(
                headlineContent = { Text(project.name, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(project.description.ifBlank { "BYAK project" }) },
                leadingContent = { Icon(Icons.Outlined.Folder, null) },
                trailingContent = { IconButton(onClick = { vm.removeProject(project.id) }) { Icon(Icons.Outlined.DeleteOutline, "Delete") } }
            )
        }
    }
    if (showProject) ProjectDialog(close = { showProject = false }) { name, detail -> vm.addProject(name, detail); showProject = false }
}

@Composable
private fun SectionTitle(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Text(count.toString(), Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
    }
}

@Composable
private fun FriendlyEmpty(icon: ImageVector, title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProjectDialog(close: () -> Unit, create: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close, title = { Text("New project") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Project name") }); OutlinedTextField(detail, { detail = it }, label = { Text("What is it about?") }) } },
        confirmButton = { Button(onClick = { create(name.trim(), detail.trim()) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
private fun ProfileScreen(state: UiState, api: ApiClient, billing: BillingState, openModels: () -> Unit, openPlans: () -> Unit) {
    val scope = rememberCoroutineScope()
    var clear by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("You", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Your private BYAK space", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Card(onClick = openPlans, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WorkspacePremium, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(if (billing.active) "BYAK Pro" else "Unlock BYAK Pro", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(if (billing.active) "Your subscription is active" else "More agents, longer work and premium tools")
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
        item { SettingsRow(Icons.Outlined.Bolt, "AI model", state.providers.firstOrNull()?.name ?: "Connect a provider", openModels) }
        item { SettingsRow(Icons.Outlined.Security, "Privacy", "Keys encrypted by Android Keystore") {} }
        item { SettingsRow(Icons.Outlined.CloudOff, "Device-only mode", "No BYAK server required") {} }
        item { SettingsRow(Icons.Outlined.Info, "Version", "BYAK AI 0.3.0") {} }
        item { TextButton(onClick = { clear = true }, Modifier.fillMaxWidth()) { Text("Clear all local data", color = MaterialTheme.colorScheme.error) } }
    }
    if (clear) AlertDialog(
        onDismissRequest = { clear = false }, title = { Text("Clear everything?") },
        text = { Text("This permanently removes API keys, chats, files and projects from this phone.") },
        confirmButton = { Button(onClick = { scope.launch { api.deleteAccount(); clear = false } }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, detail: String, click: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, null) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = click)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(state: UiState, vm: ByakViewModel, close: () -> Unit) {
    var add by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Choose your AI", fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("One provider powers chat and agents. You can change it anytime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            state.providers.forEach { provider ->
                ListItem(
                    headlineContent = { Text(provider.name, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("${provider.defaultModel} · ${provider.maskedKey}") },
                    leadingContent = { Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { IconButton(onClick = { vm.removeProvider(provider.id) }) { Icon(Icons.Outlined.DeleteOutline, "Remove") } }
                )
            }
            Button(onClick = { add = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.Add, null); Text(" Connect AI provider")
            }
            Text("Your key is encrypted on this device and sent only to the provider you choose.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
    if (add) ProviderDialog(close = { add = false }) { type, key, model, base -> vm.addProvider(type, key, model, base) { add = false; close() } }
}

@Composable
private fun ProviderDialog(close: () -> Unit, save: (String, String, String, String) -> Unit) {
    val choices = listOf("openrouter" to "OpenRouter", "gemini" to "Gemini", "openai" to "OpenAI", "anthropic" to "Claude", "groq" to "Groq", "mistral" to "Mistral", "deepseek" to "DeepSeek", "custom" to "Other")
    val defaults = mapOf("openrouter" to "google/gemini-2.5-flash", "gemini" to "gemini-2.5-flash", "openai" to "gpt-4.1-mini", "anthropic" to "claude-sonnet-4-20250514", "groq" to "llama-3.3-70b-versatile", "mistral" to "mistral-small-latest", "deepseek" to "deepseek-chat", "custom" to "")
    var type by remember { mutableStateOf("openrouter") }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(defaults.getValue(type)) }
    var base by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close, title = { Text("Connect your AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose a provider, paste your key once, and BYAK handles the rest.")
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        FilterChip(selected = type == choice.first, onClick = { type = choice.first; model = defaults[choice.first].orEmpty() }, label = { Text(choice.second) })
                        Spacer(Modifier.width(7.dp))
                    }
                }
                OutlinedTextField(key, { key = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
                if (type == "custom") OutlinedTextField(base, { base = it }, label = { Text("HTTPS address") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { save(type, key.trim(), model.trim(), base.trim()) }, enabled = key.isNotBlank() && model.isNotBlank()) { Text("Connect") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionSheet(state: BillingState, manager: BillingManager, close: () -> Unit) {
    val activity = LocalContext.current as? Activity
    ModalBottomSheet(onDismissRequest = close) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ByakMark(54.dp)
                Spacer(Modifier.height(12.dp))
                Text("BYAK Pro", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("For bigger goals and longer autonomous work.", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Benefit("More autonomous agent runs")
                    Benefit("Longer chats and document context")
                    Benefit("Premium agent templates")
                    Benefit("Priority new features")
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(state.offers) { offer ->
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(offer.title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("${offer.price} ${offer.period}") }
                        Button(onClick = { activity?.let { manager.purchase(it, offer.productId) } }) { Text("Choose") }
                    }
                }
            }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item {
                OutlinedButton(onClick = manager::restore, Modifier.fillMaxWidth()) { Text("Restore purchases") }
                Text("Subscriptions are processed by Google Play. Provider usage charges remain with your chosen AI provider.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Text(text, Modifier.padding(start = 10.dp)) }
}
