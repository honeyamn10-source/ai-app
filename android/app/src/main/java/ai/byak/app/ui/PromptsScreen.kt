package ai.byak.app.ui

import ai.byak.app.data.SavedPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun PromptsScreen(state: UiState, vm: ByakViewModel, openChat: () -> Unit) {
    var editing by remember { mutableStateOf<SavedPrompt?>(null) }; var creating by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf<SavedPrompt?>(null) }
    LaunchedEffect(Unit) { vm.loadPrompts() }
    val use: (SavedPrompt) -> Unit = { prompt -> if (state.providers.none { it.enabled }) vm.reportError("Connect an AI provider in Models first") else { vm.usePrompt(prompt); openChat() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Prompt library", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Reusable instructions — tap one to start a chat with it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = { creating = true }) { Icon(Icons.Outlined.Add, null); Text(" New") }
            }
        }
        item { Text("My prompts", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (state.prompts.isEmpty()) item { Text("Save prompts you use often — a weekly report format, a code review checklist, a tone of voice.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.prompts, key = { it.id }) { prompt ->
            PromptCard(prompt, onUse = { use(prompt) }, onEdit = { editing = prompt }, onDelete = { deleting = prompt })
        }
        item { Text("Templates", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
        items(state.templates, key = { it.id }) { prompt -> PromptCard(prompt, onUse = { use(prompt) }, onEdit = null, onDelete = null) }
    }
    if (creating) PromptDialog(null, onDismiss = { creating = false }) { title, content -> vm.savePrompt(null, title, content); creating = false }
    editing?.let { prompt -> PromptDialog(prompt, onDismiss = { editing = null }) { title, content -> vm.savePrompt(prompt.id, title, content); editing = null } }
    deleting?.let { prompt -> ConfirmDialog("Delete “${prompt.title}”?", "This saved prompt will be removed.", "Delete", onDismiss = { deleting = null }) { vm.deletePrompt(prompt.id); deleting = null } }
}

@Composable private fun PromptCard(prompt: SavedPrompt, onUse: () -> Unit, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onUse)) {
        ListItem(
            headlineContent = { Text(prompt.title, fontWeight = FontWeight.SemiBold) },
            overlineContent = if (prompt.category.isNotBlank()) { { Text(prompt.category) } } else null,
            supportingContent = { Text(prompt.content.replace("{{input}}", "…"), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            leadingContent = { Icon(if (prompt.builtIn) Icons.Outlined.AutoAwesome else Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = if (onEdit != null && onDelete != null) { { Row { IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }; IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") } } } } else null
        )
    }
}

@Composable private fun PromptDialog(prompt: SavedPrompt?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(prompt?.title.orEmpty()) }; var content by remember { mutableStateOf(prompt?.content.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (prompt == null) "New prompt" else "Edit prompt") },
        confirmButton = { Button(onClick = { onSave(title.trim(), content.trim()) }, enabled = title.isNotBlank() && content.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
            OutlinedTextField(content, { content = it }, label = { Text("Prompt") }, minLines = 4, maxLines = 10, supportingText = { Text("Tip: put {{input}} where your text should go") })
        } })
}

/** Compact picker used from the chat composer. */
@Composable fun PromptPickerDialog(state: UiState, onDismiss: () -> Unit, onPick: (SavedPrompt) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Insert a prompt") }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (state.prompts.isEmpty() && state.templates.isEmpty()) item { Text("Loading…") }
                items(state.prompts + state.templates, key = { it.id }) { prompt ->
                    ListItem(headlineContent = { Text(prompt.title) }, supportingContent = { Text(prompt.content.replace("{{input}}", "…"), maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.clickable { onPick(prompt) })
                }
            }
        })
}
