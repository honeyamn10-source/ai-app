package ai.byak.app.ui

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

@Composable
fun ByakApp(api: ApiClient) {
    MainShell(api, Session("local", "", "You", "Device-only mode"))
}

@Composable
private fun AuthScreen(api: ApiClient) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(MaterialTheme.colorScheme.primary.copy(alpha = .22f), MaterialTheme.colorScheme.background),
                radius = 1200f
            )
        ), contentAlignment = Alignment.Center
    ) {
        Card(Modifier.padding(24.dp).widthIn(max = 440.dp), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(12.dp), tint = Color.White)
                }
                Text("BYAK AI", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Bring Your API Key. Bring Your Intelligence.")
                if (register) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") })
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") })
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            runCatching { if (register) api.register(name, email, password) else api.login(email, password) }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy
                ) { Text(if (register) "Create BYAK account" else "Sign in") }
                OutlinedButton(
                    onClick = { error = "Configure Google OAuth to enable Google Sign-In." },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Outlined.AccountCircle, null); Text(" Continue with Google") }
                TextButton(onClick = { register = !register }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (register) "Already registered? Sign in" else "New here? Create account")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(api: ApiClient, session: Session) {
    val vm = remember(api) { ByakViewModel(api) }
    val state by vm.state.collectAsState()
    var destination by remember { mutableStateOf(Destination.Home) }
    LaunchedEffect(Unit) { vm.bootstrap() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(destination.title, fontWeight = FontWeight.Bold); Text("BYAK AI", style = MaterialTheme.typography.labelSmall) } },
                actions = { if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); IconButton(onClick = vm::bootstrap) { Icon(Icons.Outlined.Refresh, "Refresh") } }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding()) {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item, onClick = { destination = item },
                            icon = { Icon(item.icon, item.title) }, label = { Text(item.title) },
                            modifier = Modifier.width(88.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (destination) {
                Destination.Home -> HomeScreen(session, state) { destination = it }
                Destination.Chats -> ChatScreen(state, vm)
                Destination.Search -> SearchScreen(state, vm)
                Destination.Files -> FilesScreen(state, vm)
                Destination.Projects -> ProjectsScreen(state, vm)
                Destination.Models -> ModelsScreen(state, vm)
                Destination.Settings -> SettingsScreen(api, session, state) { destination = Destination.Models }
            }
            state.error?.let { message ->
                Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp), action = { TextButton(onClick = vm::clearError) { Text("Dismiss") } }) { Text(message) }
            }
        }
    }
}

@Composable
private fun HomeScreen(session: Session, state: UiState, navigate: (Destination) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Good to see you, ${session.name.substringBefore(' ')}", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Your models. Your data. Your choice.") }
        item {
            Card(onClick = { navigate(Destination.Chats) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Start creating", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(if (state.providers.isEmpty()) "Connect a provider first" else "Use ${state.providers.first().name}") }
                    Icon(Icons.Outlined.ArrowForward, null)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Chats", state.conversations.size, Modifier.weight(1f)); Metric("Projects", state.projects.size, Modifier.weight(1f)); Metric("Files", state.files.size, Modifier.weight(1f)) } }
        item { QuickTool(Destination.Search, "Web, GitHub and Reddit research", navigate) }
        item { QuickTool(Destination.Files, "Private document knowledge", navigate) }
        item { QuickTool(Destination.Models, "Connect your AI providers", navigate) }
        item { ListItem(headlineContent = { Text("Standalone BYOK mode") }, supportingContent = { Text("Provider keys and app data stay encrypted on this device") }, leadingContent = { Icon(Icons.Outlined.Security, null) }) }
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.Black); Text(label) } }
}

@Composable
private fun QuickTool(destination: Destination, detail: String, navigate: (Destination) -> Unit) {
    ListItem(
        headlineContent = { Text(destination.title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(detail) },
        leadingContent = { Icon(destination.icon, null) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        modifier = Modifier.clickable { navigate(destination) }
    )
}

@Composable
private fun ChatScreen(state: UiState, vm: ByakViewModel) {
    var draft by remember { mutableStateOf("") }
    val conversation = state.activeConversation
    if (conversation == null) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Button(onClick = vm::newConversation, enabled = state.providers.isNotEmpty()) { Icon(Icons.Outlined.Add, null); Text(" New chat") }; if (state.providers.isEmpty()) Text("Connect a provider from Models first.") }
            items(state.conversations) { item ->
                Card(Modifier.fillMaxWidth().clickable { vm.openConversation(item) }) {
                    ListItem(headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(item.model.ifBlank { "Choose model" }) })
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            ListItem(headlineContent = { Text(conversation.title) }, supportingContent = { Text(conversation.model) })
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.messages.isEmpty()) item { EmptyState(Icons.Outlined.AutoAwesome, "A fresh conversation", "Ask, create or research anything.") }
                items(state.messages) { MessageBubble(it) }
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Message BYAK AI") }, maxLines = 5)
                FilledIconButton(onClick = { val value = draft.trim(); if (value.isNotEmpty()) { draft = ""; vm.send(value) } }, enabled = draft.isNotBlank() && !state.loading) { Icon(Icons.Outlined.ArrowUpward, "Send") }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 680.dp)
        ) { Column(Modifier.padding(14.dp)) { Text(if (user) "You" else "BYAK AI", fontWeight = FontWeight.Bold); Text(message.content.ifEmpty { "Thinking…" }) } }
    }
}

@Composable
private fun SearchScreen(state: UiState, vm: ByakViewModel) {
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("github") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Source-first research", fontSize = 24.sp, fontWeight = FontWeight.Black)
        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("web", "github", "reddit").forEach { item -> FilterChip(selected = source == item, onClick = { source = item }, label = { Text(item) }); Spacer(Modifier.width(8.dp)) } }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Research query") }); IconButton(onClick = { vm.search(query, source) }, enabled = query.isNotBlank()) { Icon(Icons.Outlined.Search, "Search") } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.research) { result ->
                Card(Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url))) }) {
                    ListItem(headlineContent = { Text(result.title) }, supportingContent = { Text(result.summary, maxLines = 3) }, trailingContent = { Icon(Icons.Outlined.OpenInNew, null) })
                }
            }
        }
    }
}

@Composable
private fun FilesScreen(state: UiState, vm: ByakViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.txt"
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val allowed = setOf("text/plain", "text/markdown", "text/csv", "application/json")
        val type = context.contentResolver.getType(uri).takeIf { it in allowed } ?: "text/plain"
        vm.upload(name, type, text)
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { picker.launch("text/*") }) { Icon(Icons.Outlined.UploadFile, null); Text(" Upload") } }
        if (state.files.isEmpty()) item { EmptyState(Icons.Outlined.FolderOpen, "No files", "Upload TXT, Markdown, CSV or JSON.") }
        items(state.files) { file -> ListItem(headlineContent = { Text(file.name) }, supportingContent = { Text("${file.chunkCount} searchable chunks") }, trailingContent = { IconButton(onClick = { vm.removeFile(file.id) }) { Icon(Icons.Outlined.Delete, "Delete") } }) }
    }
}

@Composable
private fun ProjectsScreen(state: UiState, vm: ByakViewModel) {
    var show by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { show = true }) { Icon(Icons.Outlined.Add, null); Text(" New project") } }
        if (state.projects.isEmpty()) item { EmptyState(Icons.Outlined.Workspaces, "No projects", "Group chats and files by goal.") }
        items(state.projects) { project -> ListItem(headlineContent = { Text(project.name) }, supportingContent = { Text(project.description) }, trailingContent = { IconButton(onClick = { vm.removeProject(project.id) }) { Icon(Icons.Outlined.Delete, "Delete") } }) }
    }
    if (show) ProjectDialog({ show = false }) { name, description -> vm.addProject(name, description); show = false }
}

@Composable
private fun ProjectDialog(close: () -> Unit, create: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close, title = { Text("New project") },
        text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Name") }); OutlinedTextField(description, { description = it }, label = { Text("Description") }) } },
        confirmButton = { Button(onClick = { create(name, description) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
private fun ModelsScreen(state: UiState, vm: ByakViewModel) {
    var show by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { show = true }) { Icon(Icons.Outlined.Add, null); Text(" Connect provider") } }
        if (state.providers.isEmpty()) item { EmptyState(Icons.Outlined.Key, "Bring your API key", "Connect OpenAI, Claude, Gemini or another provider.") }
        items(state.providers) { provider ->
            ListItem(
                headlineContent = { Text(provider.name) }, supportingContent = { Text("${provider.defaultModel}\n${provider.maskedKey}") },
                trailingContent = { Row { IconButton(onClick = { vm.validateProvider(provider.id) }) { Icon(Icons.Outlined.Verified, "Validate") }; IconButton(onClick = { vm.removeProvider(provider.id) }) { Icon(Icons.Outlined.Delete, "Delete") } } }
            )
        }
    }
    if (show) ProviderDialog({ show = false }) { type, key, model, base -> vm.addProvider(type, key, model, base) { show = false } }
}

@Composable
private fun ProviderDialog(close: () -> Unit, save: (String, String, String, String) -> Unit) {
    val types = listOf("openai", "anthropic", "gemini", "openrouter", "groq", "mistral", "deepseek", "custom")
    val defaults = mapOf(
        "openai" to "gpt-4.1-mini", "anthropic" to "claude-sonnet-4-20250514",
        "gemini" to "gemini-2.5-flash", "openrouter" to "openai/gpt-4.1-mini",
        "groq" to "llama-3.3-70b-versatile", "mistral" to "mistral-small-latest",
        "deepseek" to "deepseek-chat", "custom" to ""
    )
    var type by remember { mutableStateOf("openai") }; var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("gpt-4.1-mini") }; var base by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close, title = { Text("Connect provider") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("The key is encrypted by Android Keystore and is sent only to the selected provider.", style = MaterialTheme.typography.bodySmall); Row(Modifier.horizontalScroll(rememberScrollState())) { types.forEach { item -> FilterChip(selected = type == item, onClick = { type = item; model = defaults[item].orEmpty() }, label = { Text(item) }); Spacer(Modifier.width(6.dp)) } }; OutlinedTextField(key, { key = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(model, { model = it }, label = { Text("Default model") }); if (type == "custom") OutlinedTextField(base, { base = it }, label = { Text("HTTPS endpoint") }) } },
        confirmButton = { Button(onClick = { save(type, key, model, base) }, enabled = key.isNotBlank() && model.isNotBlank()) { Text("Save securely") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsScreen(api: ApiClient, session: Session, state: UiState, openModels: () -> Unit) {
    val scope = rememberCoroutineScope(); var delete by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ListItem(headlineContent = { Text("Private device mode") }, supportingContent = { Text("No BYAK server or account required") }, leadingContent = { Icon(Icons.Outlined.Smartphone, null) }) }
        item { ListItem(headlineContent = { Text("Provider security") }, supportingContent = { Text("${state.providers.size} key(s) encrypted by Android Keystore") }, modifier = Modifier.clickable(onClick = openModels)) }
        item { ListItem(headlineContent = { Text("Local storage") }, supportingContent = { Text("Chats, projects and files stay on this device") }) }
        item { TextButton(onClick = { delete = true }, Modifier.fillMaxWidth()) { Text("Clear all local data") } }
    }
    if (delete) AlertDialog(
        onDismissRequest = { delete = false }, title = { Text("Clear all local data?") },
        text = { Text("This permanently removes encrypted API keys, chats, files and projects from this phone.") },
        confirmButton = { Button(onClick = { scope.launch { api.deleteAccount(); delete = false } }) { Text("Clear permanently") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
