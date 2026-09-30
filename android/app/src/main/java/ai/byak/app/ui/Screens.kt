@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ai.byak.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import ai.byak.app.data.Project
import ai.byak.app.data.Provider
import ai.byak.app.data.Session
import ai.byak.app.data.local.Catalog
import ai.byak.app.data.local.LocalModel
import ai.byak.app.data.local.LocalModelState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- Home ----------
@Composable fun HomeScreen(session: Session, state: UiState, vm: ByakViewModel, navigate: (Destination) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Good to see you, ${session.name.substringBefore(' ').takeUnless { it.isBlank() || it == "You" } ?: "there"}", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("One intelligent workspace. Your models, your data, your choice.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            val ready = state.providers.any { it.enabled }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp), onClick = { if (ready) { vm.newConversation(); navigate(Destination.Chats) } else navigate(Destination.Models) }) {
                Row(Modifier.padding(24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(if (ready) "Start a new chat" else "Connect your first model", fontWeight = FontWeight.Bold, fontSize = 22.sp); Text(if (ready) "Ask anything with ${state.providers.first { it.enabled }.name}" else "Add an API key (OpenAI, Claude, Gemini…) or download the free Offline AI") }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("Chats", state.conversations.size.toString(), Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f)) { navigate(Destination.Chats) }; Metric("Projects", state.projects.size.toString(), Icons.Outlined.Workspaces, Modifier.weight(1f)) { navigate(Destination.Projects) }; Metric("Files", state.files.size.toString(), Icons.Outlined.Description, Modifier.weight(1f)) { navigate(Destination.Files) } } }
        if (state.conversations.isNotEmpty()) {
            item { Text("Recent chats", fontWeight = FontWeight.Bold, fontSize = 19.sp) }
            items(state.conversations.take(3), key = { "recent-${it.id}" }) { c -> ListItem(headlineContent = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(c.preview.ifBlank { c.model }, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingContent = { Icon(Icons.Outlined.ChatBubbleOutline, null) }, modifier = Modifier.clickable { vm.openConversation(c); navigate(Destination.Chats) }) }
        }
        item { Text("Quick tools", fontWeight = FontWeight.Bold, fontSize = 19.sp) }
        items(listOf(Destination.Search to "Research the web, GitHub and Reddit", Destination.Files to "Build a private knowledge base", Destination.Projects to "Workspaces with their own instructions", Destination.Prompts to "Templates and saved prompts", Destination.Compare to "Ask two models, compare answers", Destination.Models to "Connect OpenAI, Gemini, Claude and more")) { (dest, subtitle) ->
            ListItem(headlineContent = { Text(dest.title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(subtitle) }, leadingContent = { Icon(dest.icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }, modifier = Modifier.clickable { navigate(dest) })
        }
        if (!state.subscription.isPro) item {
            Card(onClick = { navigate(Destination.Plan) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.WorkspacePremium, null); Spacer(Modifier.width(14.dp)); Column { Text("Upgrade to BYAK Pro", fontWeight = FontWeight.Bold); Text("Web search in chat, photo questions and a longer memory — from $1/month") } }
            }
        }
    }
}

@Composable private fun Metric(label: String, value: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)); Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(label, style = MaterialTheme.typography.labelMedium) } }
}

// ---------- Research ----------
@Composable fun ResearchScreen(state: UiState, vm: ByakViewModel, openChat: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }; var source by remember { mutableStateOf("github") }; val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Deep research", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Every result keeps its source attached.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow { listOf("web", "github", "reddit").forEachIndexed { i, item -> SegmentedButton(selected = source == item, onClick = { source = item }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(item.replaceFirstChar { it.uppercase() }) } } }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("What do you want to research?") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { vm.search(query.trim(), source) }, enabled = query.isNotBlank() && !state.busy) { Icon(Icons.Outlined.Search, "Search") }
        }
        if (state.research.isNotEmpty()) OutlinedButton(onClick = { vm.summarizeResearch(query.trim()); openChat() }, enabled = state.providers.any { it.enabled }, modifier = Modifier.padding(top = 10.dp)) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Summarize these results with AI") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.research.isEmpty()) item { EmptyState(Icons.Outlined.TravelExplore, "Source-first research", "Search GitHub, Reddit or the web. Every result keeps its link.") }
            items(state.research) { result ->
                Card(Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url))) } }) {
                    ListItem(headlineContent = { Text(result.title, fontWeight = FontWeight.Bold) }, supportingContent = { Column { Text(Uri.parse(result.url).host.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(result.summary, maxLines = 4, overflow = TextOverflow.Ellipsis) } }, trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open source") })
                }
            }
        }
    }
}

// ---------- Files ----------
private const val MAX_UPLOAD_BYTES = 8L * 1024 * 1024
private val uploadTypes = setOf("text/plain", "text/markdown", "text/x-markdown", "text/csv", "application/json", "text/html", "text/xml", "application/xml")

private fun Context.describe(uri: Uri): Pair<String, Long> {
    var name = "document.txt"; var size = -1L
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { cursor.getString(it) }?.let { name = it }
            cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
        }
    }
    return name to size
}

private fun mimeFor(name: String, reported: String?): String = when {
    reported in uploadTypes -> reported!!
    name.endsWith(".md", true) || name.endsWith(".markdown", true) -> "text/markdown"
    name.endsWith(".csv", true) -> "text/csv"; name.endsWith(".json", true) -> "application/json"
    name.endsWith(".html", true) || name.endsWith(".htm", true) -> "text/html"; name.endsWith(".xml", true) -> "text/xml"
    else -> "text/plain"
}

@Composable fun FilesScreen(state: UiState, vm: ByakViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<ai.byak.app.data.UserFile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val (name, size) = context.describe(uri)
            if (size > MAX_UPLOAD_BYTES) { vm.reportError("$name is larger than 8 MB"); return@launch }
            val text = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            when {
                text == null -> vm.reportError("Couldn't read $name")
                text.size > MAX_UPLOAD_BYTES -> vm.reportError("$name is larger than 8 MB")
                else -> vm.upload(name, mimeFor(name, context.contentResolver.getType(uri)), text.toString(Charsets.UTF_8))
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Knowledge files", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Chats search these automatically and cite them", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = { picker.launch(arrayOf("text/*", "application/json", "application/xml")) }, enabled = !state.busy) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Upload") }
            }
        }
        state.subscription.limits["files"]?.let { limit -> item { LimitBar("Files", state.files.size, limit) } }
        if (state.files.isEmpty()) item { EmptyState(Icons.Outlined.FolderOpen, "No files yet", "Upload TXT, Markdown, CSV, JSON, HTML or XML (up to 8 MB).") }
        items(state.files, key = { it.id }) { file ->
            Card { ListItem(headlineContent = { Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("${formatBytes(file.size)} · ${file.chunkCount} searchable chunks" + (state.projects.firstOrNull { it.id == file.projectId }?.let { " · ${it.name}" } ?: "")) }, leadingContent = { Icon(Icons.Outlined.Description, null) }, trailingContent = { IconButton(onClick = { deleting = file }) { Icon(Icons.Outlined.Delete, "Delete") } }) }
        }
    }
    deleting?.let { file -> ConfirmDialog("Delete ${file.name}?", "It will no longer be used as context in your chats.", "Delete", onDismiss = { deleting = null }) { vm.removeFile(file.id); deleting = null } }
}

fun formatBytes(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "${bytes / 1024} KB"; else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0) }

@Composable fun LimitBar(label: String, used: Int, limit: Int) {
    Column(Modifier.fillMaxWidth()) {
        Text("$label: $used of $limit on your plan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { (used.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

// ---------- Projects ----------
@Composable fun ProjectsScreen(state: UiState, vm: ByakViewModel, openChat: () -> Unit) {
    var editing by remember { mutableStateOf<Project?>(null) }; var creating by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf<Project?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Projects", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Chats with their own instructions and files", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = { creating = true }) { Icon(Icons.Outlined.Add, null); Text(" New") }
            }
        }
        state.subscription.limits["projects"]?.let { limit -> item { LimitBar("Projects", state.projects.size, limit) } }
        if (state.projects.isEmpty()) item { EmptyState(Icons.Outlined.Workspaces, "Build a workspace", "Give each project instructions like “answer as a senior lawyer” — every chat inside follows them.") }
        items(state.projects, key = { it.id }) { project ->
            Card(Modifier.fillMaxWidth().clickable { editing = project }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Workspaces, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(project.name, fontWeight = FontWeight.Bold); Text(project.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        IconButton(onClick = { deleting = project }) { Icon(Icons.Outlined.Delete, "Delete") }
                    }
                    Text("${project.conversationCount} chats · ${project.fileCount} files" + if (project.instructions.isNotBlank()) " · custom instructions" else "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    FilledTonalButton(onClick = { vm.newConversation(projectId = project.id); openChat() }, enabled = state.providers.any { it.enabled }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.ChatBubbleOutline, null); Spacer(Modifier.width(8.dp)); Text("New chat in project") }
                }
            }
        }
    }
    if (creating) ProjectDialog(null, onDismiss = { creating = false }) { n, d, i -> vm.addProject(n, d, i); creating = false }
    editing?.let { project -> ProjectDialog(project, onDismiss = { editing = null }) { n, d, i -> vm.editProject(project.id, n, d, i); editing = null } }
    deleting?.let { project -> ConfirmDialog("Delete ${project.name}?", "Its chats and files are kept but no longer grouped under this project.", "Delete", onDismiss = { deleting = null }) { vm.removeProject(project.id); deleting = null } }
}

@Composable private fun ProjectDialog(project: Project?, onDismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(project?.name.orEmpty()) }; var description by remember { mutableStateOf(project?.description.orEmpty()) }; var instructions by remember { mutableStateOf(project?.instructions.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (project == null) "New project" else "Edit project") },
        confirmButton = { Button(onClick = { save(name.trim(), description.trim(), instructions.trim()) }, enabled = name.isNotBlank()) { Text(if (project == null) "Create" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true); OutlinedTextField(description, { description = it }, label = { Text("Description") }); OutlinedTextField(instructions, { instructions = it }, label = { Text("Instructions for the AI") }, minLines = 3, maxLines = 8) } })
}

// ---------- Models ----------
@Composable fun ModelsScreen(state: UiState, vm: ByakViewModel) {
    var adding by remember { mutableStateOf(false) }; var removing by remember { mutableStateOf<Provider?>(null) }; var choosing by remember { mutableStateOf<Provider?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("AI providers", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Keys are encrypted and always masked", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = { adding = true }) { Icon(Icons.Outlined.Add, null); Text(" Connect") }
            }
        }
        if (vm.offlineAvailable) item { OfflineAiCard(state, vm) }
        if (state.providers.isEmpty()) item { EmptyState(Icons.Outlined.Key, "Bring your API key", "OpenAI, Claude, Gemini, OpenRouter, Groq, Mistral, DeepSeek, NVIDIA or any OpenAI-compatible HTTPS endpoint. Or use the free Offline AI above — no key needed.") }
        items(state.providers, key = { it.id }) { p ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Hub, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(p.name, fontWeight = FontWeight.Bold); Text(p.maskedKey, style = MaterialTheme.typography.bodySmall) }
                        Switch(checked = p.enabled, onCheckedChange = { vm.setProviderEnabled(p.id, it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        AssistChip(onClick = { choosing = p }, label = { Text(p.defaultModel.ifBlank { "Choose default model" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp)) }, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.weight(1f))
                        if (p.lastValidatedAt != null) Icon(Icons.Outlined.CheckCircle, "Verified", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        TextButton(onClick = { vm.validateProvider(p.id) }) { Text("Test key") }
                        IconButton(onClick = { removing = p }) { Icon(Icons.Outlined.Delete, "Remove") }
                    }
                }
            }
        }
    }
    if (adding) ProviderDialog(state, onDismiss = { adding = false; vm.clearProviderError() }) { type, key, model, base -> vm.addProvider(type, key, model, base) { adding = false } }
    removing?.let { p -> ConfirmDialog("Remove ${p.name}?", if (p.provider == Catalog.LOCAL) "Chats using offline AI will ask you to pick another model. The downloaded model stays until you delete it on the Offline AI card." else "The encrypted key is deleted from this phone. Chats using it will ask you to pick another model.", "Remove", onDismiss = { removing = null }) { vm.removeProvider(p.id); removing = null } }
    choosing?.let { p -> DefaultModelDialog(p, state, vm, onDismiss = { choosing = null }) { vm.setDefaultModel(p.id, it); choosing = null } }
}

/** Download, use or delete the offline model (Qwen3 0.6B). Polls while a download runs. */
@Composable private fun OfflineAiCard(state: UiState, vm: ByakViewModel) {
    val model = state.localModel
    val downloading = model?.status == LocalModelState.Status.Downloading
    LaunchedEffect(downloading) { while (true) { vm.refreshLocalModel(); delay(if (downloading) 1_000 else 4_000) } }
    var confirmDelete by remember { mutableStateOf(false) }
    val connected = state.providers.any { it.provider == Catalog.LOCAL && it.enabled }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Offline AI · Qwen3 0.6B", fontWeight = FontWeight.Bold); Text("Free · private · works without internet or an API key", style = MaterialTheme.typography.bodySmall) }
            }
            Text(model?.message ?: "Checking…", style = MaterialTheme.typography.bodySmall)
            if (downloading) LinearProgressIndicator(progress = { (model?.progress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (model?.status) {
                    LocalModelState.Status.Ready -> {
                        if (connected) AssistChip(onClick = {}, label = { Text("Connected") }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp)) })
                        else Button(onClick = { vm.useLocalModel() }) { Text("Use offline AI") }
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    }
                    LocalModelState.Status.Downloading -> TextButton(onClick = { confirmDelete = true }) { Text("Cancel download") }
                    LocalModelState.Status.Failed -> { Button(onClick = { vm.downloadLocalModel() }, enabled = !state.busy) { Text("Try again") }; TextButton(onClick = { confirmDelete = true }) { Text("Delete") } }
                    LocalModelState.Status.Missing -> Button(onClick = { vm.downloadLocalModel() }, enabled = !state.busy) { Icon(Icons.Outlined.Download, null); Text(" Download (${LocalModel.SIZE_LABEL})") }
                    null -> Unit
                }
            }
            Text("Small and fast, good for everyday questions. For harder tasks and photos, add an API key below.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmDelete) ConfirmDialog("Delete offline AI?", "Frees about ${LocalModel.SIZE_LABEL}. You can download it again anytime.", "Delete", onDismiss = { confirmDelete = false }) { confirmDelete = false; vm.deleteLocalModel() }
}

@Composable private fun DefaultModelDialog(provider: Provider, state: UiState, vm: ByakViewModel, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var model by remember { mutableStateOf(provider.defaultModel) }
    LaunchedEffect(provider.id) { vm.loadModels(provider.id) }
    val options = state.modelOptions[provider.id].orEmpty()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Default model for ${provider.name}") },
        confirmButton = { Button(onClick = { onPick(model.trim()) }, enabled = model.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(model, { model = it }, label = { Text("Model id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 280.dp)) { items((if (model.isBlank() || model in options) options else options.filter { it.contains(model, true) }).take(150)) { option -> ListItem(headlineContent = { Text(option) }, leadingContent = { RadioButton(option == model, { model = option }) }, modifier = Modifier.clickable { model = option }) } }
            }
        })
}

@Composable private fun ProviderDialog(state: UiState, onDismiss: () -> Unit, save: (String, String, String, String) -> Unit) {
    val catalog = state.catalog.filter { !it.localOnly }
    val types = catalog.map { it.id } + "custom"
    var type by remember { mutableStateOf(types.first()) }
    var key by remember { mutableStateOf("") }; var base by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(catalog.firstOrNull()?.models?.firstOrNull().orEmpty()) }
    val suggestions = catalog.firstOrNull { it.id == type }?.models.orEmpty()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Connect provider") },
        confirmButton = { Button(onClick = { save(type, key, model, base) }, enabled = !state.busy && key.isNotBlank() && model.isNotBlank() && (type != "custom" || base.startsWith("https://"))) { if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test & save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Provider", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { types.forEach { id -> FilterChip(selected = type == id, onClick = { type = id; model = catalog.firstOrNull { it.id == id }?.models?.firstOrNull().orEmpty() }, label = { Text(catalog.firstOrNull { it.id == id }?.name ?: "Custom") }) } }
                OutlinedTextField(key, { value -> key = value
                    // Pasting a key picks the matching provider, so a Gemini key is never sent to OpenAI.
                    Catalog.detect(value)?.takeIf { it != type && it in types && !(it == "openai" && type in setOf("openrouter", "deepseek")) }?.let { guess -> type = guess; model = catalog.firstOrNull { it.id == guess }?.models?.firstOrNull().orEmpty() }
                }, label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                state.providerError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(model, { model = it }, label = { Text("Default model") }, singleLine = true)
                if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { suggestions.forEach { s -> SuggestionChip(onClick = { model = s }, label = { Text(s) }) } }
                if (type == "custom") OutlinedTextField(base, { base = it }, label = { Text("HTTPS endpoint (OpenAI-compatible)") }, singleLine = true, placeholder = { Text("https://example.com/v1") })
                Text("The key is tested before it's saved, then encrypted on this phone. You can pick any model the provider offers later.", style = MaterialTheme.typography.bodySmall)
            }
        })
}

// ---------- shared ----------
@Composable fun EmptyState(icon: ImageVector, title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable fun TextInputDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
