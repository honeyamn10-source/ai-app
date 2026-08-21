package ai.byak.app.ui.settings

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.byak.app.billing.BillingManager
import ai.byak.app.domain.model.AiProvider
import ai.byak.app.ui.components.ByakLogo
import ai.byak.app.ui.theme.GlassCard
import ai.byak.app.ui.theme.PremiumGold
import coil3.compose.AsyncImage

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, openLibrary: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showProvider by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ByakLogo(62.dp)
                    Column(Modifier.padding(start = 11.dp)) {
                        Text(state.session?.name ?: "You", fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (state.session?.localOnly == true) "Private on-device session" else state.session?.email.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = { showProvider = true }) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text("AI connection", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${state.provider.name.pretty()} · ${state.model}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.Key, null)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = openLibrary) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 13.dp)) {
                            Text("Private library", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Give chats and agents your own reference material.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WorkspacePremium, null, tint = PremiumGold)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(if (state.billing.active) "BYAK Pro active" else "BYAK Pro", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Server-verified Google Play subscription", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (state.billing.verifying) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("  Verifying with Google Play…")
                            }
                        }
                        state.billing.billingChoiceImageUrl?.let { url ->
                            AsyncImage(model = url, contentDescription = "Google Play Billing choice", modifier = Modifier.fillMaxWidth().height(72.dp))
                        }
                        state.billing.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        state.billing.offers.forEach { offer ->
                            OutlinedButton(
                                onClick = { (context as? Activity)?.let { viewModel.purchase(it, offer.productId) } },
                                enabled = state.billing.ready && !state.billing.verifying && !state.billing.active,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text("${offer.title} · ${offer.price} ${offer.period}") }
                        }
                        TextButton(viewModel::restorePurchases, Modifier.align(Alignment.End)) {
                            Icon(Icons.Outlined.Refresh, null)
                            Text(" Restore purchases")
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.Logout, null)
                    Text(" Sign out")
                }
                Text(
                    "BYAK never bundles provider secrets in the APK. Keys you add are encrypted with Android Keystore.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
    if (showProvider) ProviderDialog(state, { showProvider = false }) { provider, key, model ->
        viewModel.saveProvider(provider, key, model)
        showProvider = false
    }
}

@Composable
private fun ProviderDialog(state: SettingsUiState, dismiss: () -> Unit, save: (AiProvider, String, String) -> Unit) {
    var provider by remember { mutableStateOf(state.provider) }
    var key by remember { mutableStateOf("") }
    var model by remember(provider) { mutableStateOf(provider.defaultModel()) }
    val hasExisting = when (provider) {
        AiProvider.OPENAI -> state.hasOpenAi
        AiProvider.ANTHROPIC -> state.hasAnthropic
        AiProvider.GEMINI -> state.hasGemini
    }
    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Outlined.Key, null) },
        title = { Text("Connect your AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose one provider. You can change it anytime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AiProvider.entries.forEach { item ->
                        AssistChip(
                            onClick = { provider = item; model = item.defaultModel(); key = "" },
                            label = { Text(item.name.pretty()) },
                            leadingIcon = if (provider == item) ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(17.dp)) }) else null,
                        )
                    }
                }
                AnimatedVisibility(hasExisting) {
                    Text("A key is already stored. Leave the field blank to keep it.", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasExisting) "Replace API key (optional)" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Text("The key is sent only to ${provider.name.pretty()}'s API from this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        },
        confirmButton = { Button({ save(provider, key, model) }, enabled = model.isNotBlank() && (hasExisting || key.isNotBlank())) { Text("Save") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}

private fun String.pretty() = lowercase().replaceFirstChar(Char::uppercase)
private fun AiProvider.defaultModel() = when (this) {
    AiProvider.OPENAI -> "gpt-5-mini"
    AiProvider.ANTHROPIC -> "claude-sonnet-4-5"
    AiProvider.GEMINI -> "gemini-2.5-flash"
}
