package ai.byak.app.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.byak.app.domain.model.ChatMessage
import ai.byak.app.domain.model.MessageRole
import ai.byak.app.ui.components.ByakLogo
import ai.byak.app.ui.theme.GlassCard

@Composable
fun ChatScreen(viewModel: ChatViewModel, openSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is ChatEffect.Error) snackbar.showSnackbar(it.message) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ByakLogo(42.dp)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("BYAK", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text("${state.providerName} · ${state.model}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                }
                Surface(onClick = openSettings, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.providerReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error))
                        Text(if (state.providerReady) " Ready" else " Add AI", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
                IconButton(viewModel::newConversation) { Icon(Icons.Outlined.Add, "New chat") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.activeConversationId == null) {
                Welcome(state, viewModel, openSettings, Modifier.weight(1f))
            } else {
                Conversation(state, viewModel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Welcome(state: ChatUiState, viewModel: ChatViewModel, openSettings: () -> Unit, modifier: Modifier) {
    val prompts = listOf(
        Triple("Research a hard question", "Compare evidence and uncertainty", Icons.Outlined.Search),
        Triple("Build something", "Turn an idea into a working plan", Icons.Outlined.Build),
        Triple("Think it through", "Explore options with a clear recommendation", Icons.Outlined.AutoAwesome),
    )
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("What are we working on?", fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
            Text("Ask naturally. BYAK keeps the interface quiet and does the complicated work underneath.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
        }
        if (!state.providerReady) {
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = openSettings) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text("Connect your preferred AI", fontWeight = FontWeight.Bold)
                            Text("One encrypted key powers chat and agents.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.Settings, null)
                    }
                }
            }
        }
        items(prompts, key = { it.first }) { prompt ->
            OutlinedCard(
                onClick = { if (state.providerReady) viewModel.send(prompt.first) else openSettings() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(prompt.third, null, Modifier.padding(11.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(prompt.first, fontWeight = FontWeight.Bold)
                        Text(prompt.second, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.conversations.isNotEmpty()) {
            item { Text("Recent", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 14.dp)) }
            items(state.conversations.take(8), key = { it.id }) { conversation ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { viewModel.openConversation(conversation.id) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(conversation.title, Modifier.padding(start = 13.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Conversation(state: ChatUiState, viewModel: ChatViewModel, modifier: Modifier) {
    var draft by remember { mutableStateOf("") }
    Column(modifier) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            items(state.messages, key = { it.id }) { message -> MessageBubble(message) }
        }
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message BYAK…") },
                    minLines = 1,
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (state.generating) viewModel.stop()
                        else draft.trim().takeIf(String::isNotEmpty)?.let { text -> draft = ""; viewModel.send(text) }
                    },
                    enabled = state.generating || (draft.isNotBlank() && state.providerReady),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(if (state.generating) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward, if (state.generating) "Stop" else "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.role == MessageRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        if (!user) {
            ByakLogo(32.dp)
            Spacer(Modifier.width(7.dp))
        }
        Surface(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(if (user) .86f else .92f).animateContentSize(),
            shape = RoundedCornerShape(22.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                if (message.content.isBlank() && message.streaming) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(message.content, lineHeight = 22.sp)
                    if (message.streaming) Text("●", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                }
            }
        }
    }
}
