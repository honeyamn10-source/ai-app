@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ai.byak.app.ui

import android.content.Intent
import ai.byak.app.data.ChatMessage
import ai.byak.app.data.Conversation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable fun ChatScreen(state: UiState, vm: ByakViewModel, openModels: () -> Unit) {
    val conversation = state.activeConversation
    if (conversation == null) ConversationList(state, vm, openModels) else ConversationView(conversation, state, vm)
}

@Composable private fun ConversationList(state: UiState, vm: ByakViewModel, openModels: () -> Unit) {
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(state.conversationQuery, vm::searchConversations, Modifier.weight(1f), placeholder = { Text("Search chats") }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.width(10.dp))
                Button(onClick = { vm.newConversation() }, enabled = state.providers.any { it.enabled }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("New") }
            }
        }
        if (state.providers.none { it.enabled }) item {
            Card(Modifier.fillMaxWidth().clickable(onClick = openModels), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                ListItem(headlineContent = { Text("Connect a model to start chatting", fontWeight = FontWeight.Bold) }, supportingContent = { Text("Add your OpenAI, Claude, Gemini or other API key") }, leadingContent = { Icon(Icons.Outlined.Key, null) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) })
            }
        }
        if (state.conversations.isEmpty()) item { EmptyState(Icons.Outlined.ChatBubbleOutline, if (state.conversationQuery.isBlank()) "No conversations yet" else "No matches", if (state.conversationQuery.isBlank()) "Start a new chat to ask, write, code or analyze your documents." else "Try a different search.") }
        items(state.conversations, key = { it.id }) { item ->
            var menu by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { vm.openConversation(item) }) {
                ListItem(
                    headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(item.preview.ifBlank { item.model.ifBlank { "Choose a model" } }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(if (item.pinned) Icons.Outlined.PushPin else Icons.Outlined.ChatBubbleOutline, null, tint = if (item.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Options") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(if (item.pinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) }, onClick = { menu = false; vm.togglePin(item) })
                                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = item })
                                DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { menu = false; vm.archiveConversation(item.id) })
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; deleting = item })
                            }
                        }
                    }
                )
            }
        }
    }
    renaming?.let { item -> TextInputDialog("Rename chat", item.title, "Title", onDismiss = { renaming = null }) { vm.renameConversation(item.id, it); renaming = null } }
    deleting?.let { item -> ConfirmDialog("Delete this chat?", "“${item.title}” and all its messages will be permanently deleted.", "Delete", onDismiss = { deleting = null }) { vm.deleteConversation(item.id); deleting = null } }
}

@Composable private fun ConversationView(conversation: Conversation, state: UiState, vm: ByakViewModel) {
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val provider = state.providers.firstOrNull { it.id == conversation.providerId } ?: state.providers.firstOrNull { it.enabled }
    val model = conversation.model.ifBlank { provider?.defaultModel.orEmpty() }
    val lastAssistant = state.messages.lastOrNull()?.takeIf { it.role == "assistant" && !it.pending }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::closeConversation) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to chats") }
                Column(Modifier.weight(1f)) {
                    Text(conversation.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AssistChip(onClick = { picking = true }, label = { Text(listOfNotNull(provider?.name, model.ifBlank { null }).joinToString(" · ").ifBlank { "Choose model" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp)) }, modifier = Modifier.height(30.dp))
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Share as Markdown") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                            menu = false
                            scope.launch { vm.exportConversation(conversation.id)?.let { text -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_SUBJECT, conversation.title).putExtra(Intent.EXTRA_TEXT, text), "Share conversation")) } }
                        })
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { menu = false; vm.archiveConversation(conversation.id) })
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.messages.isEmpty() && !state.busy) item {
                EmptyState(Icons.Outlined.AutoAwesome, "A fresh conversation", "Ask anything. Files in your knowledge base are searched automatically and cited.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Explain a concept simply", "Draft a professional email", "Review my code", "Summarize my documents").forEach { SuggestionChip(onClick = { draft = it }, label = { Text(it) }) }
                }
            }
            items(state.messages, key = { it.id }) { message -> MessageBubble(message, canRegenerate = message == lastAssistant && !state.streaming, onRegenerate = vm::regenerate) }
        }
        Surface(tonalElevation = 4.dp) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Message BYAK AI") }, maxLines = 6, shape = RoundedCornerShape(22.dp))
                Spacer(Modifier.width(8.dp))
                if (state.streaming) FilledTonalIconButton(onClick = vm::stopGeneration) { Icon(Icons.Outlined.Stop, "Stop generating") }
                else FilledIconButton(onClick = { val value = draft.trim(); if (value.isNotEmpty()) { draft = ""; vm.send(value) } }, enabled = draft.isNotBlank()) { Icon(Icons.AutoMirrored.Outlined.Send, "Send") }
            }
        }
    }
    if (picking) ModelPickerDialog(state, vm, conversation.providerId ?: provider?.id, model, onDismiss = { picking = false }) { providerId, chosen -> vm.setConversationModel(providerId, chosen); picking = false }
    if (renaming) TextInputDialog("Rename chat", conversation.title, "Title", onDismiss = { renaming = false }) { vm.renameConversation(conversation.id, it); renaming = false }
}

@Composable private fun MessageBubble(message: ChatMessage, canRegenerate: Boolean, onRegenerate: () -> Unit) {
    val user = message.role == "user"; val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(color = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 680.dp)) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = if (user) 12.dp else 4.dp)) {
                Text(if (user) "You" else listOfNotNull("BYAK AI", message.model).joinToString(" · "), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                when {
                    message.pending && message.content.isEmpty() -> Text("Thinking…")
                    user -> Text(message.content)
                    else -> MarkdownText(message.content)
                }
                if (message.pending) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                if (message.status == "stopped") Text("Stopped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                if (message.citations.isNotEmpty()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.citations.forEach { AssistChip(onClick = {}, label = { Text("[${it.id}] ${it.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(14.dp)) }) }
                }
                if (!user && !message.pending) Row {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }, Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(18.dp)) }
                    if (canRegenerate) IconButton(onClick = onRegenerate, Modifier.size(36.dp)) { Icon(Icons.Outlined.Replay, "Regenerate", Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable private fun ModelPickerDialog(state: UiState, vm: ByakViewModel, initialProvider: String?, initialModel: String, onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    var providerId by remember { mutableStateOf(initialProvider ?: state.providers.firstOrNull()?.id.orEmpty()) }
    var model by remember { mutableStateOf(initialModel) }
    LaunchedEffect(providerId) { if (providerId.isNotBlank()) vm.loadModels(providerId) }
    val options = state.modelOptions[providerId].orEmpty()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose model") },
        confirmButton = { Button(onClick = { onPick(providerId, model.trim()) }, enabled = providerId.isNotBlank() && model.isNotBlank()) { Text("Use model") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { state.providers.filter { it.enabled }.forEach { p -> FilterChip(selected = providerId == p.id, onClick = { providerId = p.id; model = p.defaultModel }, label = { Text(p.name) }) } }
                OutlinedTextField(model, { model = it }, label = { Text("Model id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (options.isEmpty()) Text("Loading available models…", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items((if (model.isBlank() || model in options) options else options.filter { it.contains(model, ignoreCase = true) }).take(150)) { option ->
                        ListItem(headlineContent = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingContent = { RadioButton(selected = option == model, onClick = { model = option }) }, modifier = Modifier.clickable { model = option })
                    }
                }
            }
        })
}
