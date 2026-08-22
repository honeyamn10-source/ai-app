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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.byak.app.billing.PlanOffer
import ai.byak.app.billing.PlayCatalogStatus
import ai.byak.app.domain.model.AiProvider
import ai.byak.app.ui.components.ByakLogo
import ai.byak.app.ui.theme.CyberTeal
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
                            if (state.session?.localOnly == true) "Private on-device workspace" else state.session?.email.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = {
                    viewModel.clearConnectionMessage()
                    showProvider = true
                }) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text("AI connection", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "${state.provider.displayName()} · ${state.model}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            if (state.hasKeyFor(state.provider)) Icons.Outlined.CloudDone else Icons.Outlined.Key,
                            null,
                            tint = if (state.hasKeyFor(state.provider)) CyberTeal else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = openLibrary) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 13.dp)) {
                            Text("Private library", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Ground chats and agents in your own documents.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { ProCard(state, context as? Activity, viewModel) }
            item {
                OutlinedButton(
                    onClick = viewModel::signOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.Logout, null)
                    Text(" Sign out")
                }
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Security, null, Modifier.size(16.dp), tint = CyberTeal)
                    Text(
                        "  Provider keys are encrypted by Android Keystore and never bundled inside BYAK.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
    if (showProvider) {
        ProviderDialog(
            state = state,
            dismiss = {
                viewModel.clearConnectionMessage()
                showProvider = false
            },
            save = viewModel::saveAndTestProvider,
        )
    }
}

@Composable
private fun ProCard(state: SettingsUiState, activity: Activity?, viewModel: SettingsViewModel) {
    // Prices and availability must come from Google Play. Never show invented fallback
    // prices: they confuse sideload testers and can violate store price transparency.
    val plans = state.billing.offers
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WorkspacePremium, null, tint = PremiumGold)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (state.billing.active) "BYAK Pro active" else "BYAK Pro", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("More agent capacity. Longer context. Priority workflows.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.billing.active) Icon(Icons.Outlined.CheckCircle, null, tint = CyberTeal)
            }

            Text("✓ More background agent runs   ✓ Larger document context\n✓ Premium workflows              ✓ Future Pro upgrades", fontSize = 13.sp)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            if (state.billing.loading || state.billing.verifying) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (state.billing.verifying) "  Verifying securely…" else "  Connecting to Google Play…")
                }
            }

            state.billing.billingChoiceImageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Google Play Billing choice",
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                )
            }

            plans.forEach { plan ->
                val available = state.billing.offers.any { it.productId == plan.productId }
                PlanRow(plan, available, state.billing.active) {
                    activity?.let { viewModel.purchase(it, plan.productId) }
                }
            }

            state.billing.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            if (state.billing.catalogStatus == PlayCatalogStatus.NOT_PUBLISHED) {
                Text(
                    "Checkout is intentionally disabled in sideloaded APKs. The signed AAB must be installed from a Play testing track with both subscription base plans active.",
                    color = PremiumGold,
                    fontSize = 12.sp,
                )
            }
            TextButton(
                onClick = viewModel::restorePurchases,
                enabled = state.billing.ready && !state.billing.verifying,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Outlined.Refresh, null)
                Text(" Restore purchases")
            }
        }
    }
}

@Composable
private fun PlanRow(plan: PlanOffer, available: Boolean, active: Boolean, purchase: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(plan.title.removePrefix("BYAK Pro "), fontWeight = FontWeight.Bold)
            Text("${plan.price} ${plan.period}", color = PremiumGold, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = purchase,
            enabled = available && !active,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(when {
                active -> "Active"
                available -> "Choose"
                else -> "Play only"
            })
        }
    }
}

@Composable
private fun ProviderDialog(
    state: SettingsUiState,
    dismiss: () -> Unit,
    save: (AiProvider, String, String) -> Unit,
) {
    var provider by remember { mutableStateOf(state.provider) }
    var key by remember { mutableStateOf("") }
    var model by remember(provider) { mutableStateOf(provider.defaultModel()) }
    val hasExisting = state.hasKeyFor(provider)
    AlertDialog(
        onDismissRequest = { if (!state.testingConnection) dismiss() },
        icon = { Icon(Icons.Outlined.Key, null) },
        title = { Text("Connect your AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a provider and model. BYAK tests the same generation route used by Chat before saving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(AiProvider.entries, key = { it.name }) { item ->
                        AssistChip(
                            onClick = { provider = item; model = item.defaultModel(); key = "" },
                            label = { Text(item.displayName()) },
                            leadingIcon = if (provider == item) ({
                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(17.dp))
                            }) else null,
                        )
                    }
                }
                AnimatedVisibility(hasExisting) {
                    Text("A key is already stored. Leave this blank to test and keep it.", color = CyberTeal, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasExisting) "Replace API key (optional)" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.testingConnection,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    supportingText = { Text(provider.modelHint()) },
                    singleLine = true,
                    enabled = !state.testingConnection,
                    shape = RoundedCornerShape(16.dp),
                )
                state.connectionMessage?.let { message ->
                    Row(verticalAlignment = Alignment.Top) {
                        if (state.testingConnection) {
                            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (state.connectionSucceeded == true) Icons.Outlined.CheckCircle else Icons.Outlined.Key,
                                null,
                                Modifier.size(18.dp),
                                tint = if (state.connectionSucceeded == true) CyberTeal else MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            "  $message",
                            color = when (state.connectionSucceeded) {
                                true -> CyberTeal
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 12.sp,
                        )
                    }
                }
                Text(
                    "The key is sent only to ${provider.displayName()}'s official API and remains encrypted on this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { save(provider, key, model) },
                enabled = !state.testingConnection && model.isNotBlank() && (hasExisting || key.isNotBlank()),
            ) {
                if (state.testingConnection) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else Text(if (hasExisting) "Update & test" else "Connect & test")
            }
        },
        dismissButton = { TextButton(dismiss, enabled = !state.testingConnection) { Text("Done") } },
    )
}

private fun SettingsUiState.hasKeyFor(provider: AiProvider): Boolean = when (provider) {
    AiProvider.OPENAI -> hasOpenAi
    AiProvider.OPENROUTER -> hasOpenRouter
    AiProvider.ANTHROPIC -> hasAnthropic
    AiProvider.GEMINI -> hasGemini
}

private fun AiProvider.displayName(): String = when (this) {
    AiProvider.OPENAI -> "OpenAI"
    AiProvider.OPENROUTER -> "OpenRouter"
    AiProvider.ANTHROPIC -> "Claude"
    AiProvider.GEMINI -> "Gemini"
}

private fun AiProvider.defaultModel(): String = when (this) {
    AiProvider.OPENAI -> "gpt-5-mini"
    AiProvider.OPENROUTER -> "openrouter/auto"
    AiProvider.ANTHROPIC -> "claude-sonnet-4-5"
    AiProvider.GEMINI -> "gemini-2.5-flash"
}

private fun AiProvider.modelHint(): String = when (this) {
    AiProvider.OPENROUTER -> "Use openrouter/auto or a provider/model ID from OpenRouter."
    AiProvider.OPENAI -> "Example: gpt-5-mini"
    AiProvider.ANTHROPIC -> "Example: claude-sonnet-4-5"
    AiProvider.GEMINI -> "Example: gemini-2.5-flash"
}
