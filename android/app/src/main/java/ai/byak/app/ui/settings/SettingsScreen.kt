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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.byak.app.billing.PlanOffer
import ai.byak.app.billing.PlayCatalogStatus
import ai.byak.app.data.security.SecureStore
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

            val requiresCloudAccount = state.session?.localOnly != false
            if (requiresCloudAccount && plans.isNotEmpty()) {
                Text(
                    "Sign in before subscribing so Google Play can securely verify and restore Pro on your account.",
                    color = PremiumGold,
                    fontSize = 12.sp,
                )
            }
            plans.forEach { plan ->
                val available = state.billing.offers.any { it.planId == plan.planId }
                PlanRow(plan, available, state.billing.active, requiresCloudAccount) {
                    if (requiresCloudAccount) {
                        viewModel.signOut()
                    } else {
                        activity?.let { viewModel.purchase(it, plan.planId) }
                    }
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
private fun PlanRow(
    plan: PlanOffer,
    available: Boolean,
    active: Boolean,
    requiresCloudAccount: Boolean,
    purchase: () -> Unit,
) {
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
                requiresCloudAccount -> "Sign in"
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
    save: (AiProvider, String, String, String) -> Unit,
) {
    var provider by remember { mutableStateOf(state.provider) }
    var key by remember { mutableStateOf("") }
    var model by remember(provider) { mutableStateOf(state.modelFor(provider)) }
    var baseUrl by remember(provider) { mutableStateOf(state.baseUrlFor(provider)) }
    val uriHandler = LocalUriHandler.current
    val needsKey = provider !in setOf(AiProvider.AUTO, AiProvider.PORTABLE_LOCAL, AiProvider.ON_DEVICE)
    val needsModel = needsKey
    val hasExisting = needsKey && state.hasKeyFor(provider)
    AlertDialog(
        onDismissRequest = { if (!state.testingConnection) dismiss() },
        icon = { Icon(Icons.Outlined.Key, null) },
        title = { Text("Choose your AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Auto uses private local AI when available and otherwise uses your first connected cloud provider. You can also choose any provider directly.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(AiProvider.entries, key = { it.name }) { item ->
                        AssistChip(
                            onClick = {
                                provider = item
                                model = state.modelFor(item).ifBlank { item.defaultModel() }
                                baseUrl = state.baseUrlFor(item)
                                key = ""
                            },
                            label = { Text(item.displayName()) },
                            leadingIcon = if (provider == item) ({
                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(17.dp))
                            }) else null,
                        )
                    }
                }
                AnimatedVisibility(provider == AiProvider.AUTO) {
                    Text(
                        "Recommended. BYAK automatically skips unsupported AICore hardware and uses Portable Local AI or an encrypted cloud connection.",
                        color = CyberTeal,
                        fontSize = 12.sp,
                    )
                }
                AnimatedVisibility(provider == AiProvider.PORTABLE_LOCAL) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            state.portableMessage.ifBlank {
                                "Download Qwen3 0.6B once, then chat privately without internet or an API key."
                            },
                            color = if (state.portableStatus == ai.byak.app.data.localai.PortableModelStatus.READY) {
                                CyberTeal
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 12.sp,
                        )
                        if (state.portableStatus == ai.byak.app.data.localai.PortableModelStatus.DOWNLOADING) {
                            Text(
                                "Download progress: ${state.portableProgress}%. Tap Check status after Android finishes.",
                                color = PremiumGold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                AnimatedVisibility(provider == AiProvider.ON_DEVICE) {
                    Text(
                        "Gemini Nano uses Android AICore and appears as Ready or Downloadable only on supported chipsets. Auto skips it when unsupported.",
                        color = CyberTeal,
                        fontSize = 12.sp,
                    )
                }
                AnimatedVisibility(hasExisting) {
                    Text(
                        "A key is already encrypted on this phone. Leave the key blank to keep and retest it.",
                        color = CyberTeal,
                        fontSize = 12.sp,
                    )
                }
                AnimatedVisibility(provider == AiProvider.CUSTOM) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("HTTPS API base URL") },
                        supportingText = { Text("Example: https://example.com/v1") },
                        singleLine = true,
                        enabled = !state.testingConnection,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                AnimatedVisibility(needsKey) {
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
                }
                AnimatedVisibility(needsModel) {
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
                }
                AnimatedVisibility(state.availableModels.isNotEmpty() && needsModel) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Models available to this key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.availableModels.take(40), key = { it }) { available ->
                                AssistChip(
                                    onClick = { model = available },
                                    label = {
                                        Text(
                                            available,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
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
                AnimatedVisibility(state.connectionSucceeded == false && provider.keyHelpUrl() != null) {
                    TextButton(onClick = { provider.keyHelpUrl()?.let(uriHandler::openUri) }) {
                        Text(provider.keyHelpLabel())
                    }
                }
                Text(
                    when {
                        needsKey -> "The key is sent only to ${provider.displayName()}'s API and remains encrypted on this device."
                        provider == AiProvider.PORTABLE_LOCAL ->
                            "The model is stored in BYAK's private app folder. Prompts and responses remain on this phone."
                        provider == AiProvider.ON_DEVICE ->
                            "Android AICore owns Gemini Nano availability and download. BYAK cannot force-enable it on unsupported hardware."
                        else ->
                            "Auto removes the AICore dead end: local when possible, connected provider when necessary."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            val keyReady = !needsKey || hasExisting || key.isNotBlank()
            val modelReady = !needsModel || model.isNotBlank()
            val urlReady = provider != AiProvider.CUSTOM || baseUrl.isNotBlank()
            Button(
                onClick = { save(provider, key, model, baseUrl) },
                enabled = !state.testingConnection && keyReady && modelReady && urlReady,
            ) {
                if (state.testingConnection) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        when (provider) {
                            AiProvider.AUTO -> "Use Auto"
                            AiProvider.PORTABLE_LOCAL -> when (state.portableStatus) {
                                ai.byak.app.data.localai.PortableModelStatus.READY -> "Use offline"
                                ai.byak.app.data.localai.PortableModelStatus.DOWNLOADING -> "Check status"
                                ai.byak.app.data.localai.PortableModelStatus.FAILED -> "Retry download"
                                ai.byak.app.data.localai.PortableModelStatus.MISSING -> "Download local AI"
                            }
                            AiProvider.ON_DEVICE -> "Check Phone AI"
                            else -> if (hasExisting) "Update & test" else "Connect & test"
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(dismiss, enabled = !state.testingConnection) { Text("Done") }
        },
    )
}

private fun SettingsUiState.hasKeyFor(provider: AiProvider): Boolean = when (provider) {
    AiProvider.OPENAI -> hasOpenAi
    AiProvider.OPENROUTER -> hasOpenRouter
    AiProvider.ANTHROPIC -> hasAnthropic
    AiProvider.GEMINI -> hasGemini
    AiProvider.NVIDIA -> hasNvidia
    AiProvider.GROQ -> hasGroq
    AiProvider.MISTRAL -> hasMistral
    AiProvider.DEEPSEEK -> hasDeepSeek
    AiProvider.CUSTOM -> hasCustom
    AiProvider.PORTABLE_LOCAL ->
        portableStatus == ai.byak.app.data.localai.PortableModelStatus.READY
    AiProvider.AUTO, AiProvider.ON_DEVICE -> true
}

private fun AiProvider.displayName(): String = when (this) {
    AiProvider.AUTO -> "Auto"
    AiProvider.PORTABLE_LOCAL -> "Local download"
    AiProvider.ON_DEVICE -> "Gemini Nano"
    AiProvider.OPENROUTER -> "OpenRouter"
    AiProvider.GEMINI -> "Gemini"
    AiProvider.NVIDIA -> "NVIDIA"
    AiProvider.GROQ -> "Groq"
    AiProvider.MISTRAL -> "Mistral"
    AiProvider.DEEPSEEK -> "DeepSeek"
    AiProvider.OPENAI -> "OpenAI"
    AiProvider.ANTHROPIC -> "Claude"
    AiProvider.CUSTOM -> "Universal API"
}

private fun AiProvider.defaultModel(): String = when (this) {
    AiProvider.AUTO -> SecureStore.AUTO_MODEL
    AiProvider.PORTABLE_LOCAL -> SecureStore.PORTABLE_LOCAL_MODEL
    AiProvider.ON_DEVICE -> SecureStore.ON_DEVICE_MODEL
    AiProvider.OPENAI -> "gpt-5.6-luna"
    AiProvider.OPENROUTER -> "openrouter/auto"
    AiProvider.ANTHROPIC -> "claude-sonnet-5"
    AiProvider.GEMINI -> "gemini-3.8-flash"
    AiProvider.NVIDIA -> "meta/llama-3.1-70b-instruct"
    AiProvider.GROQ -> "llama-3.3-70b-versatile"
    AiProvider.MISTRAL -> "mistral-small-latest"
    AiProvider.DEEPSEEK -> "deepseek-v4-flash"
    AiProvider.CUSTOM -> ""
}

private fun AiProvider.modelHint(): String = when (this) {
    AiProvider.OPENROUTER -> "Use openrouter/auto or any model ID returned by OpenRouter."
    AiProvider.GEMINI -> "Stable default: gemini-3.8-flash"
    AiProvider.NVIDIA -> "Use a model ID available to your NVIDIA API Catalog key."
    AiProvider.GROQ -> "Use a model returned by your Groq model catalog."
    AiProvider.MISTRAL -> "Example: mistral-small-latest"
    AiProvider.DEEPSEEK -> "Example: deepseek-v4-flash"
    AiProvider.OPENAI -> "Example: gpt-5.6-luna"
    AiProvider.ANTHROPIC -> "Example: claude-sonnet-5"
    AiProvider.CUSTOM -> "Enter the OpenAI-compatible model ID."
    AiProvider.AUTO, AiProvider.PORTABLE_LOCAL, AiProvider.ON_DEVICE -> "Managed automatically."
}

private fun SettingsUiState.modelFor(provider: AiProvider): String = when (provider) {
    AiProvider.AUTO -> SecureStore.AUTO_MODEL
    AiProvider.PORTABLE_LOCAL -> SecureStore.PORTABLE_LOCAL_MODEL
    AiProvider.ON_DEVICE -> SecureStore.ON_DEVICE_MODEL
    AiProvider.OPENAI -> openAiModel
    AiProvider.OPENROUTER -> openRouterModel
    AiProvider.ANTHROPIC -> anthropicModel
    AiProvider.GEMINI -> geminiModel
    AiProvider.NVIDIA -> nvidiaModel
    AiProvider.GROQ -> groqModel
    AiProvider.MISTRAL -> mistralModel
    AiProvider.DEEPSEEK -> deepSeekModel
    AiProvider.CUSTOM -> customModel
}

private fun SettingsUiState.baseUrlFor(provider: AiProvider): String =
    if (provider == AiProvider.CUSTOM) customBaseUrl else ""

private fun AiProvider.keyHelpUrl(): String? = when (this) {
    AiProvider.OPENROUTER -> "https://openrouter.ai/settings/keys"
    AiProvider.GEMINI -> "https://aistudio.google.com/app/apikey"
    AiProvider.NVIDIA -> "https://build.nvidia.com/"
    AiProvider.GROQ -> "https://console.groq.com/keys"
    AiProvider.MISTRAL -> "https://console.mistral.ai/api-keys/"
    AiProvider.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
    AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
    AiProvider.ANTHROPIC -> "https://console.anthropic.com/settings/keys"
    AiProvider.CUSTOM, AiProvider.AUTO, AiProvider.PORTABLE_LOCAL, AiProvider.ON_DEVICE -> null
}

private fun AiProvider.keyHelpLabel(): String = when (this) {
    AiProvider.GEMINI -> "Create a Google AI Studio key"
    AiProvider.NVIDIA -> "Create an NVIDIA API key"
    else -> "Open provider key settings"
}
