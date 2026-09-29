@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ai.byak.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import ai.byak.app.data.ChatMessage
import ai.byak.app.data.Conversation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var choosingPrompt by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val provider = state.providers.firstOrNull { it.id == conversation.providerId } ?: state.providers.firstOrNull { it.enabled }
    val model = conversation.model.ifBlank { provider?.defaultModel.orEmpty() }
    val lastAssistant = state.messages.lastOrNull()?.takeIf { it.role == "assistant" && !it.pending }
    val lastUser = state.messages.lastOrNull { it.role == "user" && !it.id.startsWith("local-") }
    val speaker = rememberSpeaker()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex) }
    LaunchedEffect(state.composerPrefill) { state.composerPrefill?.let { draft = it; vm.consumePrefill() } }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { prepareImage(context, uri)?.let(vm::addDraft) ?: vm.reportError("Couldn't read that image") }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken -> draft = listOf(draft.trimEnd(), spoken).filter { it.isNotBlank() }.joinToString(" ") }
    }

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
                EmptyState(Icons.Outlined.AutoAwesome, "A fresh conversation", "Ask anything, attach a photo, or turn on Web for live results. Your files are searched automatically.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Explain a concept simply", "Draft a professional email", "Review my code", "Summarize my documents").forEach { SuggestionChip(onClick = { draft = it }, label = { Text(it) }) }
                }
            }
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(
                    message, vm, speaker,
                    canRegenerate = message == lastAssistant && !state.streaming, canEdit = message == lastUser && !state.streaming,
                    status = if (message.pending) state.generationStatus else null
                )
            }
        }
        Surface(tonalElevation = 4.dp) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                state.editing?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp))
                        Text("Editing your message — the answer after it will be replaced", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.cancelEdit() }) { Text("Cancel") }
                    }
                }
                if (state.drafts.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.drafts.forEach { image ->
                        Box {
                            ImageBytes(image.bytes, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                            IconButton(onClick = { vm.removeDraft(image) }, Modifier.align(Alignment.TopEnd).size(24.dp)) { Icon(Icons.Outlined.Cancel, "Remove image", tint = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sub = state.subscription; val metered = !sub.isPro && sub.limits.isNotEmpty()
                    IconButton(onClick = {
                        if (metered && sub.imagesLeft <= state.drafts.size) vm.showUpgrade("You've used today's ${sub.limits["imagesPerDay"]} free photo questions. They reset at midnight UTC.")
                        else photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, enabled = state.editing == null && state.drafts.size < 4) {
                        if (metered) BadgedBox(badge = { Badge { Text("${(sub.imagesLeft - state.drafts.size).coerceAtLeast(0)}") } }) { Icon(Icons.Outlined.AddPhotoAlternate, "Attach image, ${sub.imagesLeft} free today") }
                        else Icon(Icons.Outlined.AddPhotoAlternate, "Attach image")
                    }
                    FilterChip(selected = state.webSearch, onClick = vm::toggleWebSearch, label = { Text(if (metered) "Web · ${sub.webSearchesLeft} left" else "Web") }, leadingIcon = { Icon(Icons.Outlined.Language, null, Modifier.size(16.dp)) })
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { vm.loadPrompts(); choosingPrompt = true }) { Icon(Icons.Outlined.AutoStories, "Prompt library") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")) }
                        catch (e: ActivityNotFoundException) { vm.reportError("Voice input isn't available on this device") }
                    }) { Icon(Icons.Outlined.Mic, "Voice input") }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text(if (state.drafts.isNotEmpty()) "Ask about the image" else "Message BYAK AI") }, maxLines = 6, shape = RoundedCornerShape(22.dp))
                    Spacer(Modifier.width(8.dp))
                    val canSend = draft.isNotBlank() || (state.drafts.isNotEmpty() && state.editing == null)
                    if (state.streaming) FilledTonalIconButton(onClick = vm::stopGeneration) { Icon(Icons.Outlined.Stop, "Stop generating") }
                    else FilledIconButton(onClick = { val value = draft.trim(); if (canSend) { draft = ""; vm.send(value) } }, enabled = canSend) { Icon(Icons.AutoMirrored.Outlined.Send, "Send") }
                }
            }
        }
    }
    if (picking) ModelPickerDialog(state, vm, conversation.providerId ?: provider?.id, model, onDismiss = { picking = false }) { providerId, chosen -> vm.setConversationModel(providerId, chosen); picking = false }
    if (renaming) TextInputDialog("Rename chat", conversation.title, "Title", onDismiss = { renaming = false }) { vm.renameConversation(conversation.id, it); renaming = false }
    if (choosingPrompt) PromptPickerDialog(state, onDismiss = { choosingPrompt = false }) { prompt -> draft = prompt.composerText; choosingPrompt = false }
}

@Composable private fun MessageBubble(message: ChatMessage, vm: ByakViewModel, speaker: Speaker, canRegenerate: Boolean, canEdit: Boolean, status: String?) {
    val user = message.role == "user"; val clipboard = LocalClipboardManager.current; val context = LocalContext.current
    if (message.status == "failed") { FailedBubble(message, vm); return }
    var reporting by remember { mutableStateOf(false) }
    if (reporting) ReportDialog(message, onDismiss = { reporting = false })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(color = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 680.dp)) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = if (user && !canEdit) 12.dp else 4.dp)) {
                Text(if (user) "You" else listOfNotNull("BYAK AI", message.model).joinToString(" · "), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (message.localImages.isNotEmpty() || message.attachments.isNotEmpty()) Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.localImages.forEach { ImageBytes(it.bytes, Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))) }
                    message.attachments.forEach { ref ->
                        val bytes by produceState<ByteArray?>(null, message.id, ref.index) { value = vm.attachment(message.id, ref.index) }
                        val loaded = bytes
                        if (loaded != null) ImageBytes(loaded, Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)))
                        else Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, "Image") }
                    }
                }
                when {
                    message.pending && message.content.isEmpty() -> Text(status ?: "Thinking…")
                    user -> if (message.content.isNotBlank()) Text(message.content)
                    else -> MarkdownText(message.content)
                }
                if (message.pending) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                if (message.status == "stopped") Text("Stopped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                if (message.citations.isNotEmpty()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.citations.forEach { citation ->
                        val web = citation.kind == "web" && citation.url != null
                        AssistChip(
                            onClick = { if (web) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(citation.url))) } },
                            label = { Text("${if (web) "Web" else "Doc"} ${citation.id} · ${citation.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(if (web) Icons.Outlined.Language else Icons.Outlined.Description, null, Modifier.size(14.dp)) }
                        )
                    }
                }
                if (!user && !message.pending) Row {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }, Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(18.dp)) }
                    IconButton(onClick = { speaker.toggle(message.id, message.content) }, Modifier.size(36.dp)) {
                        Icon(if (speaker.speakingId == message.id) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, if (speaker.speakingId == message.id) "Stop reading" else "Read aloud", Modifier.size(18.dp))
                    }
                    if (canRegenerate) IconButton(onClick = vm::regenerate, Modifier.size(36.dp)) { Icon(Icons.Outlined.Replay, "Regenerate", Modifier.size(18.dp)) }
                    IconButton(onClick = { reporting = true }, Modifier.size(36.dp)) { Icon(Icons.Outlined.Flag, "Report this response", Modifier.size(18.dp)) }
                }
                if (canRegenerate) FollowUps(message, vm)
                if (user && canEdit) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { vm.startEdit(message) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Edit, "Edit message", Modifier.size(16.dp)) }
                }
            }
        }
    }
}

/** Shown when an answer couldn't be produced: the reason stays on screen with a Retry button. */
@Composable private fun FailedBubble(message: ChatMessage, vm: ByakViewModel) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 680.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("No answer", fontWeight = FontWeight.Bold) }
            Text(message.content)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::regenerate) { Icon(Icons.Outlined.Replay, null, Modifier.size(16.dp)); Text(" Retry") }
            }
        }
    }
}

/**
 * Lets people flag an AI response (required by Google Play's AI-generated content policy).
 * The report opens the user's email app addressed to the developer, so nothing is sent silently.
 */
@Composable private fun ReportDialog(message: ChatMessage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val reasons = listOf("Offensive or harmful", "Sexual or violent content", "Dangerous advice", "Inaccurate or misleading", "Other")
    var reason by remember { mutableStateOf(reasons[0]) }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Flag, null) }, title = { Text("Report this response") },
        text = { Column {
            reasons.forEach { r -> Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) { RadioButton(reason == r, { reason = r }); Text(r) } }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Details (optional)") }, maxLines = 4)
            Text("Opens your email app with the response attached. Answers come from the AI provider you chose.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        } },
        confirmButton = { Button(onClick = {
            val body = "Reason: $reason\nDetails: ${note.ifBlank { "-" }}\nModel: ${message.model ?: "unknown"}\nApp version: ${ai.byak.app.BuildConfig.VERSION_NAME}\n\n--- Reported response ---\n${message.content.take(4000)}"
            val email = ai.byak.app.BuildConfig.SUPPORT_EMAIL
            val intent = if (email.isNotBlank()) Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(email)) else Intent(Intent.ACTION_SEND).setType("text/plain")
            runCatching { context.startActivity(Intent.createChooser(intent.putExtra(Intent.EXTRA_SUBJECT, "BYAK AI response report: $reason").putExtra(Intent.EXTRA_TEXT, body), "Send report")) }
            onDismiss()
        }) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** One-tap follow-ups under the latest answer. */
@Composable private fun FollowUps(message: ChatMessage, vm: ByakViewModel) {
    var translating by remember { mutableStateOf(false) }
    val deviceLanguage = remember { java.util.Locale.getDefault().displayLanguage.takeUnless { it.isBlank() || it == "English" } ?: "Spanish" }
    FlowRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.status == "stopped") SuggestionChip(onClick = { vm.send("Continue exactly where you left off.") }, label = { Text("Continue") })
        SuggestionChip(onClick = { vm.send("Summarize your last answer in 3 short bullet points.") }, label = { Text("Summarize") })
        SuggestionChip(onClick = { vm.send("Explain your last answer more simply, as if I'm new to the topic.") }, label = { Text("Simplify") })
        SuggestionChip(onClick = { vm.send("Go deeper: expand your last answer with more detail and an example.") }, label = { Text("More detail") })
        SuggestionChip(onClick = { translating = true }, label = { Text("Translate") })
    }
    if (translating) TextInputDialog("Translate the answer into", deviceLanguage, "Language", onDismiss = { translating = false }) { language -> translating = false; vm.send("Translate your last answer into $language. Keep the formatting.") }
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
