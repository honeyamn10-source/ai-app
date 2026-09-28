@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ai.byak.app.ui

import android.content.Intent
import android.net.Uri
import ai.byak.app.BuildConfig
import ai.byak.app.billing.PlanOffer
import ai.byak.app.billing.perMonth
import ai.byak.app.billing.yearlySavingsPercent
import ai.byak.app.data.ProHighlight
import ai.byak.app.data.Session
import ai.byak.app.data.SettingsStore
import ai.byak.app.data.ThemeMode
import ai.byak.app.ui.theme.dynamicColorSupported
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val playBasePlans get() = listOf(BuildConfig.PLAY_YEARLY_BASE_PLAN, BuildConfig.PLAY_MONTHLY_BASE_PLAN)

// ---------- Settings ----------
@Composable fun SettingsScreen(session: Session, state: UiState, vm: ByakViewModel, settings: SettingsStore, navigate: (Destination) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val server by settings.serverUrl.collectAsState(initial = ""); val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.System); val dynamic by settings.dynamicColor.collectAsState(initial = false)
    var editingServer by remember { mutableStateOf(false) }; var appearance by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }; var editingProfile by remember { mutableStateOf(false) }; var changingPassword by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val data = vm.exportData() ?: return@launch
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } != null }.getOrDefault(false) }
            if (!ok) vm.reportError("Couldn't save the export")
        }
    }
    val sub = state.subscription
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.Black) }
        item { Card(Modifier.fillMaxWidth().clickable { editingProfile = true }) { ListItem(headlineContent = { Text(state.profile?.name ?: session.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text(state.profile?.email ?: session.email) }, leadingContent = { Icon(Icons.Outlined.AccountCircle, null) }, trailingContent = { Icon(Icons.Outlined.Edit, "Edit profile") }) } }
        item { SettingsRow(Icons.Outlined.WorkspacePremium, if (sub.isPro) "BYAK Pro (${sub.plan})" else "Free plan", if (sub.isPro) sub.expiresAt?.let { "${if (sub.autoRenewing) "Renews" else "Ends"} ${it.take(10)}" } ?: "Active" else "Upgrade from $1/month") { navigate(Destination.Plan) } }
        item { SettingsRow(Icons.Outlined.Hub, "AI providers", "${state.providers.size} encrypted connection(s)") { navigate(Destination.Models) } }
        item { SettingsRow(Icons.Outlined.Workspaces, "Projects", "${state.projects.size} project(s)") { navigate(Destination.Projects) } }
        item { SettingsRow(Icons.Outlined.AutoStories, "Prompt library", "Templates and your saved prompts") { navigate(Destination.Prompts) } }
        item { SettingsRow(Icons.Outlined.Palette, "Appearance", themeMode.name + if (dynamic && dynamicColorSupported) " · dynamic color" else "") { appearance = true } }
        item { SettingsRow(Icons.Outlined.Psychology, "Memory & instructions", if (state.memoryEnabled) "On · the AI remembers what you save" else "Off · view and clear anytime") { navigate(Destination.Memory) } }
        item { SettingsRow(Icons.Outlined.BarChart, "Usage", "Tokens and requests by model") { navigate(Destination.Usage) } }
        item { SettingsRow(Icons.Outlined.Devices, "Signed-in devices", "Review and sign out other devices") { navigate(Destination.Devices) } }
        if (state.profile?.hasPassword != false) item { SettingsRow(Icons.Outlined.Lock, "Change password", "Signs out your other devices") { changingPassword = true } }
        item { SettingsRow(Icons.Outlined.Dns, "Server", server.removePrefix("https://").removePrefix("http://")) { editingServer = true } }
        item { SettingsRow(Icons.Outlined.Download, "Export my data", "Chats, projects, files and memory as JSON") { exporter.launch("byak-export.json") } }
        item { OutlinedButton(onClick = { vm.logout() }, Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Text(" Sign out") } }
        item { TextButton(onClick = { deleting = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete account and data") } }
        item { Text("BYAK AI ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
    }
    if (deleting) ConfirmDialog("Delete your account?", "This permanently removes sessions, provider keys, chats, files, projects and memory." + if (sub.isPro) " Also cancel your subscription in Google Play to stop renewals." else "", "Delete permanently", onDismiss = { deleting = false }) { deleting = false; vm.deleteAccount() }
    if (editingProfile) ProfileDialog(state.profile?.name ?: session.name, state.profile?.customInstructions.orEmpty(), onDismiss = { editingProfile = false }) { name, instructions -> vm.saveProfile(name, instructions); editingProfile = false }
    if (editingServer) ServerDialog(server, settings, onDismiss = { editingServer = false }) { changed -> editingServer = false; if (changed) vm.logout() }
    if (appearance) AppearanceDialog(themeMode, dynamic, onDismiss = { appearance = false }) { mode, dyn -> scope.launch { settings.setThemeMode(mode); settings.setDynamicColor(dyn) }; appearance = false }
    if (changingPassword) PasswordDialog(onDismiss = { changingPassword = false }) { current, new -> vm.changePassword(current, new) { changingPassword = false } }
}

@Composable fun SettingsRow(icon: ImageVector, title: String, detail: String, click: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = click)) { ListItem(headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(detail) }, leadingContent = { Icon(icon, null) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }) }
}

@Composable private fun ProfileDialog(name: String, instructions: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(name) }; var i by remember { mutableStateOf(instructions) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Profile") }, confirmButton = { Button(onClick = { onSave(n.trim(), i.trim()) }, enabled = n.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true); OutlinedTextField(i, { i = it }, label = { Text("Custom instructions for every chat") }, placeholder = { Text("e.g. I'm a data engineer; prefer concise answers with code") }, minLines = 3, maxLines = 8) } })
}

@Composable private fun PasswordDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && confirm != new
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Change password") }, confirmButton = { Button(onClick = { onSave(current, new) }, enabled = current.isNotEmpty() && new.length >= 10 && new == confirm) { Text("Change") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(current, { current = it }, label = { Text("Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(new, { new = it }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), supportingText = { Text("At least 10 characters") })
            OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm new password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), isError = mismatch, supportingText = { if (mismatch) Text("Passwords don't match") })
        } })
}

/** Lets people point the app at their own BYAK server. Changing it while signed in signs out, because sessions belong to a server. */
@Composable fun ServerDialog(current: String, settings: SettingsStore, onDismiss: () -> Unit, onSaved: (changed: Boolean) -> Unit) {
    val scope = rememberCoroutineScope(); var value by remember { mutableStateOf(current) }
    val error = SettingsStore.validate(value, allowHttp = BuildConfig.DEBUG)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("BYAK server") },
        confirmButton = { Button(onClick = { scope.launch { val next = value.trim().trimEnd('/'); settings.setServerUrl(next); onSaved(next != current.trimEnd('/')) } }, enabled = error == null && value.isNotBlank()) { Text("Save") } },
        dismissButton = { Row { TextButton(onClick = { scope.launch { settings.setServerUrl(""); onSaved(BuildConfig.API_BASE_URL != current) } }) { Text("Reset") }; TextButton(onClick = onDismiss) { Text("Cancel") } } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The address of the BYAK API this app talks to. Use your hosted API, or your own self-hosted server.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value, { value = it }, label = { Text("Server URL") }, singleLine = true, placeholder = { Text("https://api.example.com") }, isError = error != null, supportingText = { error?.let { Text(it) } })
        } })
}

@Composable private fun AppearanceDialog(mode: ThemeMode, dynamic: Boolean, onDismiss: () -> Unit, onSave: (ThemeMode, Boolean) -> Unit) {
    var selected by remember { mutableStateOf(mode) }; var useDynamic by remember { mutableStateOf(dynamic) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Appearance") }, confirmButton = { Button(onClick = { onSave(selected, useDynamic) }) { Text("Apply") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Column {
            ThemeMode.entries.forEach { option -> Row(Modifier.fillMaxWidth().clickable { selected = option }, verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == option, { selected = option }); Text(when (option) { ThemeMode.System -> "Follow system"; ThemeMode.Light -> "Light"; ThemeMode.Dark -> "Dark" }) } }
            if (dynamicColorSupported) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Dynamic color"); Text("Match your wallpaper (Material You)", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = useDynamic, onCheckedChange = { useDynamic = it })
            }
        } })
}

// ---------- Plan / payments ----------
/** "Title · value" as one wrapping paragraph with a bold title (two side-by-side Texts wrap into ragged columns). */
fun perkLine(title: String, value: String, separator: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)); append(title); pop(); append(separator); append(value)
}
/** Used until the server's list arrives (and by older servers that don't send one). */
val defaultHighlights = listOf(
    ProHighlight("webSearchesPerDay", "Live web search in chat", "10 per day", "200 per day"),
    ProHighlight("imagesPerDay", "Ask about photos", "12 images per day", "500 per day"),
    ProHighlight("historyMessages", "Conversation memory", "Last 20 messages", "Last 100 messages"),
    ProHighlight("ragChunks", "Document search depth", "4 excerpts per answer", "10 excerpts per answer"),
    ProHighlight("projects", "Projects", "3", "200"),
    ProHighlight("files", "Knowledge files", "25", "2,000"),
    ProHighlight("savedPrompts", "Saved prompts", "5", "200"),
    ProHighlight("researchPerDay", "Research searches", "50 per day", "1,000 per day")
)

@Composable fun PlanScreen(state: UiState, vm: ByakViewModel) {
    val context = LocalContext.current; val sub = state.subscription
    val highlights = sub.highlights.ifEmpty { defaultHighlights }
    LaunchedEffect(Unit) { vm.loadPlans(BuildConfig.PLAY_PRODUCT_ID, playBasePlans) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Icon(Icons.Outlined.WorkspacePremium, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (sub.isPro) "You're on BYAK Pro" else "Get more from every chat", fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                    if (sub.isPro) Text("${sub.plan.replaceFirstChar { it.uppercase() }} · ${if (sub.autoRenewing) "renews" else "ends"} ${sub.expiresAt?.take(10) ?: ""}" + if (sub.status == "grace") " · payment issue — update your payment method in Google Play" else "")
                    else {
                        Text("Pro gives your AI live web access, eyes for your photos and a longer memory.", modifier = Modifier.padding(vertical = 6.dp))
                        highlights.take(4).forEach { perk ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                                Text(perkLine(perk.title, perk.pro, " · "), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        if (!sub.isPro && sub.limits.isNotEmpty()) item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Today on the Free plan", fontWeight = FontWeight.Bold)
                LimitBar("Web searches", sub.webSearchesToday, sub.limits["webSearchesPerDay"] ?: 0)
                LimitBar("Photo questions", sub.imagesToday, sub.limits["imagesPerDay"] ?: 0)
            } }
        }
        val monthlyOffer = state.offers.firstOrNull { it.basePlanId == BuildConfig.PLAY_MONTHLY_BASE_PLAN }
        val yearlyOffer = state.offers.firstOrNull { it.basePlanId == BuildConfig.PLAY_YEARLY_BASE_PLAN }
        val savings = yearlySavingsPercent(monthlyOffer, yearlyOffer)
        if (!sub.isPro) {
            if (state.offers.isEmpty()) item {
                Text(if (state.busy) "Loading prices from Google Play…" else "Plans are unavailable right now. Make sure this app was installed from Google Play and you're signed in to the Play Store.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.offers, key = { it.basePlanId }) { offer -> OfferCard(offer, offer == yearlyOffer, savings) { context.findActivity()?.let { vm.buy(it, offer) } } }
            if (!sub.verificationAvailable) item { Text("Note: this server hasn't enabled purchase verification yet, so a purchase can't be activated until it does.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        } else {
            // Monthly subscribers can move to yearly; Google Play credits the unused part of the month.
            if (sub.basePlanId == BuildConfig.PLAY_MONTHLY_BASE_PLAN && yearlyOffer != null) item {
                OfferCard(yearlyOffer, true, savings, switching = true) { context.findActivity()?.let { vm.buy(it, yearlyOffer) } }
            }
            item {
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=${sub.productId ?: BuildConfig.PLAY_PRODUCT_ID}&package=${context.packageName}"))) } }, Modifier.fillMaxWidth()) { Text("Manage subscription in Google Play") }
            }
        }
        item { PlanComparison(highlights) }
        item { TextButton(onClick = { vm.restorePurchases() }, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Restore purchases") } }
        item { Text("Payment is charged to your Google Play account. Subscriptions renew automatically until cancelled in Google Play. Free trials convert to a paid subscription unless cancelled before they end. Your provider API usage is billed separately by each provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun OfferCard(offer: PlanOffer, yearly: Boolean, savings: Int?, switching: Boolean = false, onBuy: () -> Unit) {
    val period = if (yearly) "year" else "month"
    Card(onClick = onBuy, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (yearly) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (switching) "Switch to yearly" else if (yearly) "Yearly" else "Monthly", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (yearly) { Spacer(Modifier.width(8.dp)); Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) { Text(savings?.let { "SAVE $it%" } ?: "BEST VALUE", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) } }
            }
            Text(if (offer.trial != null && !switching) "${offer.trial}, then ${offer.price} per $period" else "${offer.price} per $period")
            if (yearly) perMonth(offer)?.let { Text("Just $it a month, billed yearly", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = onBuy, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(when { switching -> "Switch and save"; offer.trial != null -> "Start ${offer.trial.removeSuffix(" free trial")} free trial"; else -> "Subscribe" })
            }
        }
    }
}

@Composable private fun PlanComparison(highlights: List<ProHighlight>) {
    Card { Column(Modifier.padding(16.dp)) {
        Row { Text("Compare plans", Modifier.weight(1.4f), fontWeight = FontWeight.Bold); Text("Free", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("Pro", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        highlights.forEach { row -> HorizontalDivider(Modifier.padding(vertical = 8.dp)); Row { Text(row.title, Modifier.weight(1.4f)); Text(row.free, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(row.pro, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) } }
    } }
}

// ---------- Memory ----------
@Composable fun MemoryScreen(state: UiState, vm: ByakViewModel) {
    var draft by remember { mutableStateOf("") }; var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadMemory() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Memory", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Facts the AI should always know about you. Only used when memory is on.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Card { ListItem(headlineContent = { Text("Use memory in chats", fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(if (state.memoryEnabled) "On" else "Off") }, trailingContent = { Switch(checked = state.memoryEnabled, onCheckedChange = { vm.setMemoryEnabled(it) }) }) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("e.g. I live in Berlin and work in fintech") }, maxLines = 3)
                Spacer(Modifier.width(8.dp)); FilledIconButton(onClick = { vm.addMemory(draft.trim()); draft = "" }, enabled = draft.isNotBlank()) { Icon(Icons.Outlined.Add, "Add memory") }
            }
        }
        state.subscription.limits["memories"]?.let { limit -> item { LimitBar("Memories", state.memories.size, limit) } }
        if (state.memories.isEmpty()) item { EmptyState(Icons.Outlined.Psychology, "Nothing remembered yet", "Add preferences, context about your work, or anything you don't want to repeat.") }
        items(state.memories, key = { it.id }) { memory -> Card { ListItem(headlineContent = { Text(memory.content) }, trailingContent = { IconButton(onClick = { vm.deleteMemory(memory.id) }) { Icon(Icons.Outlined.Delete, "Forget") } }) } }
        if (state.memories.isNotEmpty()) item { TextButton(onClick = { clearing = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Clear all memory") } }
    }
    if (clearing) ConfirmDialog("Clear all memory?", "Every saved memory will be permanently deleted.", "Clear", onDismiss = { clearing = false }) { vm.clearMemory(); clearing = false }
}

// ---------- Usage ----------
@Composable fun UsageScreen(state: UiState, vm: ByakViewModel) {
    LaunchedEffect(Unit) { vm.loadUsage() }
    val usage = state.usage
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Usage · last 30 days", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Token counts reported by your providers. Costs are billed by each provider.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (usage == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else {
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("Requests", usage.requests.toString(), Modifier.weight(1f)); Stat("Input tokens", compact(usage.inputTokens), Modifier.weight(1f)); Stat("Output tokens", compact(usage.outputTokens), Modifier.weight(1f)) } }
            if (usage.byDay.isNotEmpty()) item {
                val max = usage.byDay.maxOf { it.tokens }.coerceAtLeast(1)
                Card { Column(Modifier.padding(16.dp)) {
                    Text("Tokens per day", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        usage.byDay.takeLast(30).forEach { day -> Surface(Modifier.weight(1f).fillMaxHeight((day.tokens.toFloat() / max).coerceIn(0.02f, 1f)), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)) {} }
                    }
                } }
            }
            if (usage.byModel.isEmpty()) item { EmptyState(Icons.Outlined.BarChart, "No usage yet", "Start chatting to see your token usage here.") }
            items(usage.byModel) { m -> Card { ListItem(headlineContent = { Text(m.model, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("${m.provider} · ${m.requests} requests") }, trailingContent = { Text("${compact(m.inputTokens + m.outputTokens)} tokens") }) } }
        }
    }
}

@Composable private fun Stat(label: String, value: String, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(14.dp)) { Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(label, style = MaterialTheme.typography.labelMedium) } } }
private fun compact(value: Long): String = when { value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1e6); value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1e3); else -> value.toString() }

// ---------- Devices ----------
@Composable fun DevicesScreen(state: UiState, vm: ByakViewModel) {
    LaunchedEffect(Unit) { vm.loadDevices() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Signed-in devices", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Sign out anything you don't recognise.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.devices, key = { it.id }) { device ->
            Card { ListItem(headlineContent = { Text(if (device.current) "This device" else device.device.take(60), fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("Last active ${device.lastUsedAt.take(16).replace('T', ' ')}") }, leadingContent = { Icon(Icons.Outlined.Devices, null) },
                trailingContent = { if (!device.current) TextButton(onClick = { vm.revokeDevice(device.id) }) { Text("Sign out") } }) }
        }
    }
}
