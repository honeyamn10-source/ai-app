@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ai.byak.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.byak.app.data.*
import kotlinx.coroutines.launch

private enum class Destination(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home), Chats("Chats", Icons.Outlined.ChatBubbleOutline),
    Search("Research", Icons.Outlined.TravelExplore), Files("Files", Icons.Outlined.FolderOpen),
    Projects("Projects", Icons.Outlined.Workspaces), Models("Models", Icons.Outlined.Hub),
    Settings("Settings", Icons.Outlined.Settings)
}

@Composable fun ByakApp(api: ApiClient, sessionStore: SessionStore) {
    val session by sessionStore.session.collectAsState(initial = null)
    if (session == null) AuthScreen(api) else MainShell(api, sessionStore, session!!)
}

@Composable private fun AuthScreen(api: ApiClient) {
    var register by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(.22f), MaterialTheme.colorScheme.background), radius = 1200f)), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp).widthIn(max = 440.dp), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(.96f))) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(12.dp), tint = Color.White) }
                Text("BYAK AI", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Bring Your API Key. Bring Your Intelligence.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (register) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { scope.launch { busy = true; error = null; runCatching { if (register) api.register(name, email, password) else api.login(email, password) }.onFailure { error = it.message }; busy = false } }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (register) "Create BYAK account" else "Sign in") }
                OutlinedButton(onClick = { error = "Add your Google OAuth client ID to enable Google Sign-In." }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Continue with Google") }
                TextButton(onClick = { register = !register; error = null }, Modifier.align(Alignment.CenterHorizontally)) { Text(if (register) "Already registered? Sign in" else "New here? Create account") }
                Text("Your provider keys are encrypted and are never included in the APK.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun MainShell(api: ApiClient, sessions: SessionStore, session: Session) {
    val vm = remember(api) { ByakViewModel(api) }; val state by vm.state.collectAsState(); var destination by remember { mutableStateOf(Destination.Home) }
    LaunchedEffect(Unit) { vm.bootstrap() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp
        Scaffold(
            topBar = { TopAppBar(title = { Column { Text(destination.title, fontWeight = FontWeight.Bold); Text("BYAK AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } }, actions = { if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); IconButton(onClick = vm::bootstrap) { Icon(Icons.Outlined.Refresh, "Refresh") }; Spacer(Modifier.width(8.dp)) }) },
            bottomBar = { if (!useRail) NavigationBar { Destination.entries.take(5).forEach { item -> NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }) } } },
            snackbarHost = { state.error?.let { message -> Snackbar(Modifier.padding(16.dp), action = { TextButton(onClick = vm::clearError) { Text("Dismiss") } }) { Text(message) } } }
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (useRail) NavigationRail(header = { Surface(Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(10.dp), tint = Color.White) } }) { Destination.entries.forEach { item -> NavigationRailItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }) } } }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (destination) {
                        Destination.Home -> HomeScreen(session, state) { destination = it }
                        Destination.Chats -> ChatScreen(state, vm)
                        Destination.Search -> SearchScreen(state, vm)
                        Destination.Files -> FilesScreen(state, vm)
                        Destination.Projects -> ProjectsScreen(state, vm)
                        Destination.Models -> ModelsScreen(state, vm)
                        Destination.Settings -> SettingsScreen(api, sessions, session, state) { destination = Destination.Models }
                    }
                }
            }
        }
    }
}

@Composable private fun HomeScreen(session: Session, state: UiState, navigate: (Destination) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Good to see you, ${session.name.substringBefore(' ')}", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("One intelligent workspace. Your models, your data, your choice.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp), onClick = { navigate(Destination.Chats) }) { Row(Modifier.padding(24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Start creating", fontWeight = FontWeight.Bold, fontSize = 22.sp); Text(if (state.providers.isEmpty()) "Connect a provider to begin" else "Ask anything with ${state.providers.first().name}") }; Icon(Icons.Outlined.ArrowForward, null) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("Chats", state.conversations.size.toString(), Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f)); Metric("Projects", state.projects.size.toString(), Icons.Outlined.Workspaces, Modifier.weight(1f)); Metric("Files", state.files.size.toString(), Icons.Outlined.Description, Modifier.weight(1f)) } }
        item { Text("Quick tools", fontWeight = FontWeight.Bold, fontSize = 19.sp) }
        items(listOf(Destination.Search to "Research the web, GitHub and Reddit", Destination.Files to "Build a private knowledge base", Destination.Models to "Connect OpenAI, Gemini, Claude and more")) { (dest, subtitle) -> ListItem(headlineContent = { Text(dest.title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(subtitle) }, leadingContent = { Icon(dest.icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }, modifier = Modifier.clickable { navigate(dest) }) }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Laptop, null); Spacer(Modifier.width(14.dp)); Column { Text("Local AI", fontWeight = FontWeight.Bold); Text("Ollama and on-device models — coming soon") } } } }
    }
}

@Composable private fun Metric(label: String, value: String, icon: ImageVector, modifier: Modifier) { Card(modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)); Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(label, style = MaterialTheme.typography.labelMedium) } } }

@Composable private fun ChatScreen(state: UiState, vm: ByakViewModel) {
    var draft by remember { mutableStateOf("") }
    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(state.activeConversation == null) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Button(vm::newConversation, enabled = state.providers.isNotEmpty()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("New chat") }; if (state.providers.isEmpty()) Text("Connect a model first from Models.", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(state.conversations) { item -> Card(Modifier.fillMaxWidth().clickable { vm.openConversation(item) }) { ListItem(headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(item.model.ifBlank { "Choose model" }) }, leadingContent = { Icon(Icons.Outlined.ChatBubbleOutline, null) }) } } } }
        state.activeConversation?.let { conversation -> Column(Modifier.fillMaxSize()) { Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { /* list is restored when screen is reopened */ }) { Icon(Icons.Outlined.ChatBubbleOutline, "Chats") }; Column { Text(conversation.title, fontWeight = FontWeight.Bold, maxLines = 1); Text(conversation.model, style = MaterialTheme.typography.labelSmall) } }; LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { if (state.messages.isEmpty()) item { EmptyState(Icons.Outlined.AutoAwesome, "A fresh conversation", "Ask, create, research or analyze a document.") }; items(state.messages) { MessageBubble(it) } }; Surface(tonalElevation = 4.dp) { Row(Modifier.padding(12.dp).navigationBarsPadding(), verticalAlignment = Alignment.Bottom) { OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Message BYAK AI") }, maxLines = 6, shape = RoundedCornerShape(22.dp)); Spacer(Modifier.width(8.dp)); FilledIconButton(onClick = { val value = draft.trim(); if (value.isNotEmpty()) { draft = ""; vm.send(value) } }, enabled = draft.isNotBlank() && !state.loading) { Icon(Icons.Outlined.ArrowUpward, "Send") } } } } }
    }
}

@Composable private fun MessageBubble(message: ChatMessage) { val user = message.role == "user"; Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) { Surface(color = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 680.dp)) { Column(Modifier.padding(14.dp)) { Text(if (user) "You" else "BYAK AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(message.content.ifEmpty { "Thinking…" }); if (message.pending) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) } } } }

@Composable private fun SearchScreen(state: UiState, vm: ByakViewModel) {
    var query by remember { mutableStateOf("") }; var source by remember { mutableStateOf("github") }; val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp)) { Text("Deep research", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Every result keeps its source attached.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); SingleChoiceSegmentedButtonRow { listOf("web","github","reddit").forEachIndexed { i, item -> SegmentedButton(selected = source == item, onClick = { source = item }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(item.replaceFirstChar { it.uppercase() }) } } }; Spacer(Modifier.height(12.dp)); Row { OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("What do you want to research?") }, singleLine = true); Spacer(Modifier.width(8.dp)); FilledIconButton(onClick = { vm.search(query, source) }, enabled = query.isNotBlank() && !state.loading) { Icon(Icons.Outlined.Search, "Search") } }; Spacer(Modifier.height(12.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { if (state.research.isEmpty()) item { EmptyState(Icons.Outlined.TravelExplore, "Source-first research", "Search GitHub and Reddit now. Web search uses your configured server connector.") }; items(state.research) { result -> Card(Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url))) }) { ListItem(headlineContent = { Text(result.title, fontWeight = FontWeight.Bold) }, supportingContent = { Text(result.summary, maxLines = 4, overflow = TextOverflow.Ellipsis) }, trailingContent = { Icon(Icons.Outlined.OpenInNew, "Open source") }) } } } }
}

@Composable private fun FilesScreen(state: UiState, vm: ByakViewModel) {
    val context = LocalContext.current; val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { val name = it.lastPathSegment?.substringAfterLast('/') ?: "document.txt"; val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty(); val type = context.contentResolver.getType(it).let { mime -> if (mime in listOf("text/plain","text/markdown","text/csv","application/json")) mime!! else "text/plain" }; vm.upload(name, type, text) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Knowledge files", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Private project context with source chunks", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Button(onClick = { picker.launch("text/*") }) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Upload") } } }; if (state.files.isEmpty()) item { EmptyState(Icons.Outlined.FolderOpen, "No files yet", "Upload TXT, Markdown, CSV or JSON. PDF and DOCX run through the production document worker.") }; items(state.files) { file -> Card { ListItem(headlineContent = { Text(file.name, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("${file.mimeType} · ${file.chunkCount} searchable chunks") }, leadingContent = { Icon(Icons.Outlined.Description, null) }, trailingContent = { IconButton(onClick = { vm.removeFile(file.id) }) { Icon(Icons.Outlined.Delete, "Delete") } }) } } }
}

@Composable private fun ProjectsScreen(state: UiState, vm: ByakViewModel) { var show by remember { mutableStateOf(false) }; LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Projects", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Isolated chats, files and instructions", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Button(onClick = { show = true }) { Icon(Icons.Outlined.Add, null); Text(" New") } } }; if (state.projects.isEmpty()) item { EmptyState(Icons.Outlined.Workspaces, "Build a workspace", "Group research, knowledge and chats by goal.") }; items(state.projects) { project -> Card { ListItem(headlineContent = { Text(project.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text(project.description.ifBlank { "No description" }) }, leadingContent = { Icon(Icons.Outlined.Workspaces, null) }, trailingContent = { IconButton(onClick = { vm.removeProject(project.id) }) { Icon(Icons.Outlined.Delete, "Delete") } }) } } }; if (show) ProjectDialog({ show = false }) { n, d -> vm.addProject(n, d); show = false } }
}

@Composable private fun ProjectDialog(close: () -> Unit, create: (String,String) -> Unit) { var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, confirmButton = { Button(onClick = { create(name, description) }, enabled = name.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton(close) { Text("Cancel") } }, title = { Text("New project") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Name") }); OutlinedTextField(description, { description = it }, label = { Text("Description") }) } }) }

@Composable private fun ModelsScreen(state: UiState, vm: ByakViewModel) { var show by remember { mutableStateOf(false) }; LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("AI providers", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Your keys are encrypted and always masked", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Button(onClick = { show = true }) { Icon(Icons.Outlined.Add, null); Text(" Connect") } } }; if (state.providers.isEmpty()) item { EmptyState(Icons.Outlined.Key, "Bring your API key", "OpenAI, Claude, Gemini, OpenRouter, Groq, Mistral, DeepSeek or custom endpoints.") }; items(state.providers) { p -> Card { ListItem(headlineContent = { Text(p.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${p.defaultModel}\n${p.maskedKey}") }, leadingContent = { Icon(Icons.Outlined.Hub, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Row { IconButton(onClick = { vm.validateProvider(p.id) }) { Icon(Icons.Outlined.Verified, "Validate") }; IconButton(onClick = { vm.removeProvider(p.id) }) { Icon(Icons.Outlined.Delete, "Remove") } } }) } }; item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { ListItem(headlineContent = { Text("Local AI", fontWeight = FontWeight.Bold) }, supportingContent = { Text("Ollama · llama.cpp · On-device\nComing soon — no fake functionality") }, leadingContent = { Icon(Icons.Outlined.Laptop, null) }) } } }; if (show) ProviderDialog({ show = false }) { type, key, model, base -> vm.addProvider(type, key, model, base) { show = false } } }
}

@Composable private fun ProviderDialog(close: () -> Unit, save: (String,String,String,String) -> Unit) { val types = listOf("openai","anthropic","gemini","openrouter","groq","mistral","deepseek","custom"); var type by remember { mutableStateOf("openai") }; var key by remember { mutableStateOf("") }; var model by remember { mutableStateOf("gpt-4.1-mini") }; var base by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, confirmButton = { Button(onClick = { save(type,key,model,base) }, enabled = key.isNotBlank() && model.isNotBlank()) { Text("Save securely") } }, dismissButton = { TextButton(close) { Text("Cancel") } }, title = { Text("Connect provider") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Provider", style = MaterialTheme.typography.labelMedium); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { types.forEach { FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it) }) } }; OutlinedTextField(key, { key = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(model, { model = it }, label = { Text("Default model") }); if (type == "custom") OutlinedTextField(base, { base = it }, label = { Text("HTTPS endpoint") }); Text("The full key cannot be viewed again after saving.", style = MaterialTheme.typography.bodySmall) } }) }

@Composable private fun SettingsScreen(api: ApiClient, sessions: SessionStore, session: Session, state: UiState, models: () -> Unit) { val scope = rememberCoroutineScope(); var delete by remember { mutableStateOf(false) }; LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Account", fontSize = 25.sp, fontWeight = FontWeight.Black) }; item { Card { ListItem(headlineContent = { Text(session.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text(session.email) }, leadingContent = { Icon(Icons.Outlined.AccountCircle, null) }) } }; item { SettingsRow(Icons.Outlined.Key, "Provider security", "${state.providers.size} encrypted connection(s)", models) }; item { SettingsRow(Icons.Outlined.Payments, "Free plan", "Upgrade architecture ready for $1 monthly / $10 annual") {} }; item { SettingsRow(Icons.Outlined.Memory, "Memory", "Off by default · view and clear anytime") {} }; item { SettingsRow(Icons.Outlined.PrivacyTip, "Privacy & data", "Export or delete your account data") {} }; item { OutlinedButton(onClick = { scope.launch { api.logout() } }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Logout, null); Text(" Sign out") } }; item { TextButton(onClick = { delete = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete account and data") } } }; if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete your account?") }, text = { Text("This permanently removes sessions, provider connections, chats, files, projects and memory.") }, confirmButton = { Button(onClick = { scope.launch { api.deleteAccount() } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete permanently") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } }) }
}

@Composable private fun SettingsRow(icon: ImageVector, title: String, detail: String, click: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = click)) { ListItem(headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(detail) }, leadingContent = { Icon(icon, null) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }) } }
@Composable private fun EmptyState(icon: ImageVector, title: String, detail: String) { Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(14.dp)); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
