@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ai.byak.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import ai.byak.app.ByakApplication
import ai.byak.app.data.Session
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

enum class Destination(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home), Chats("Chats", Icons.Outlined.ChatBubbleOutline), Search("Research", Icons.Outlined.TravelExplore),
    Files("Files", Icons.Outlined.FolderOpen), Projects("Projects", Icons.Outlined.Workspaces), Prompts("Prompts", Icons.Outlined.AutoStories), Models("Models", Icons.Outlined.Hub),
    Plan("BYAK Pro", Icons.Outlined.WorkspacePremium), Memory("Memory", Icons.Outlined.Psychology), Usage("Usage", Icons.Outlined.BarChart),
    Devices("Devices", Icons.Outlined.Devices), Settings("Settings", Icons.Outlined.Settings)
}
private val bottomTabs = listOf(Destination.Home, Destination.Chats, Destination.Search, Destination.Files, Destination.Settings)

fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }

@Composable fun ByakApp(app: ByakApplication) {
    val session by app.sessionStore.session.collectAsState(initial = null)
    val current = session
    if (current == null) AuthScreen(app.api, app.settings) else MainShell(app, current)
}

@Composable private fun MainShell(app: ByakApplication, session: Session) {
    // Keyed by account so signing in as someone else never shows the previous account's chats, research or plan.
    val vm: ByakViewModel = viewModel(key = "byak:${session.email}", factory = viewModelFactory { initializer { ByakViewModel(app.api, app.billing) } })
    val state by vm.state.collectAsState()
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val navigate: (Destination) -> Unit = { destination = it; scope.launch { drawer.close() } }

    LaunchedEffect(session.email) { vm.bootstrap() }
    LaunchedEffect(state.error, state.notice) {
        val message = state.error ?: state.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(message, withDismissAction = true); vm.clearMessages()
    }
    BackHandler(enabled = drawer.isOpen || state.activeConversation != null || destination != Destination.Home) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            destination == Destination.Chats && state.activeConversation != null -> vm.closeConversation()
            else -> destination = Destination.Home
        }
    }
    if (state.upgradeSuggested) UpgradeDialog(state, onDismiss = vm::dismissUpgrade) { vm.dismissUpgrade(); destination = Destination.Plan }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = !wide && drawer.isOpen, drawerContent = {
            ModalDrawerSheet { DrawerContent(session, state.subscription.isPro, destination, navigate) }
        }) {
            Scaffold(
                topBar = {
                    val inChat = destination == Destination.Chats && state.activeConversation != null
                    if (!inChat) TopAppBar(
                        navigationIcon = { if (!wide) IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Menu") } },
                        title = { Column { Text(destination.title, fontWeight = FontWeight.Bold); Text(if (state.subscription.isPro) "BYAK AI · Pro" else "BYAK AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } },
                        actions = { if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); IconButton(onClick = { vm.bootstrap() }) { Icon(Icons.Outlined.Refresh, "Refresh") } }
                    )
                },
                bottomBar = {
                    val inChat = destination == Destination.Chats && state.activeConversation != null
                    if (!wide && !inChat) NavigationBar { bottomTabs.forEach { item -> NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }) } }
                },
                snackbarHost = { SnackbarHost(snackbar) }
            ) { padding ->
                Row(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                    if (wide) NavigationRail(header = { Surface(Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(10.dp), tint = Color.White) } }) {
                        Destination.entries.forEach { item -> NavigationRailItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title, maxLines = 1) }) }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (destination) {
                            Destination.Home -> HomeScreen(session, state, vm, navigate)
                            Destination.Chats -> ChatScreen(state, vm) { destination = Destination.Models }
                            Destination.Search -> ResearchScreen(state, vm) { destination = Destination.Chats }
                            Destination.Files -> FilesScreen(state, vm)
                            Destination.Projects -> ProjectsScreen(state, vm) { destination = Destination.Chats }
                            Destination.Prompts -> PromptsScreen(state, vm) { destination = Destination.Chats }
                            Destination.Models -> ModelsScreen(state, vm)
                            Destination.Plan -> PlanScreen(state, vm)
                            Destination.Memory -> MemoryScreen(state, vm)
                            Destination.Usage -> UsageScreen(state, vm)
                            Destination.Devices -> DevicesScreen(state, vm)
                            Destination.Settings -> SettingsScreen(session, state, vm, app.settings, navigate)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DrawerContent(session: Session, pro: Boolean, selected: Destination, navigate: (Destination) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 20.dp)) {
        Text("BYAK AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp))
        Text("${session.name} · ${if (pro) "Pro" else "Free"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Spacer(Modifier.height(12.dp))
        Destination.entries.forEach { item -> NavigationDrawerItem(label = { Text(item.title) }, icon = { Icon(item.icon, null) }, selected = selected == item, onClick = { navigate(item) }) }
    }
}

/** Paywall shown when a free-plan limit is hit: says exactly what was blocked and what Pro unlocks. */
@Composable private fun UpgradeDialog(state: UiState, onDismiss: () -> Unit, onSeePlans: () -> Unit) {
    val perks = state.subscription.highlights.take(4).ifEmpty { defaultHighlights.take(4) }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Unlock more with BYAK Pro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.upgradeReason ?: "You've reached a free plan limit.")
                perks.forEach { perk ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(8.dp))
                        Text("${perk.title}: ", fontWeight = FontWeight.SemiBold); Text(perk.pro)
                    }
                }
                Text("From about $1 a month. Cancel anytime in Google Play.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onSeePlans) { Text("See Pro plans") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
