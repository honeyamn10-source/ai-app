package ai.byak.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ai.byak.app.ui.agent.AgentScreen
import ai.byak.app.ui.agent.AgentViewModel
import ai.byak.app.ui.auth.AuthScreen
import ai.byak.app.ui.auth.AuthViewModel
import ai.byak.app.ui.chat.ChatScreen
import ai.byak.app.ui.chat.ChatViewModel
import ai.byak.app.ui.library.LibraryScreen
import ai.byak.app.ui.library.LibraryViewModel
import ai.byak.app.ui.image.ImageStudioScreen
import ai.byak.app.ui.image.ImageStudioViewModel
import ai.byak.app.ui.settings.SettingsScreen
import ai.byak.app.ui.settings.SettingsViewModel
import ai.byak.app.ui.theme.CyberTeal
import ai.byak.app.ui.theme.NeonPurple
import ai.byak.app.ui.theme.ObsidianElevated

private enum class MainDestination(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "Chat", Icons.Outlined.ChatBubbleOutline),
    Agents("agents", "Agents", Icons.Outlined.AutoAwesome),
    Create("create", "Create", Icons.Outlined.Image),
    You("you", "You", Icons.Outlined.PersonOutline),
}

@Composable
fun ByakApp(authViewModel: AuthViewModel = hiltViewModel()) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    when {
        !auth.initialized -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        auth.session == null -> AuthScreen(authViewModel)
        else -> AuthenticatedApp()
    }
}

@Composable
private fun AuthenticatedApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    fun navigate(destination: MainDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route != "library") {
                NavigationBar(containerColor = ObsidianElevated, tonalElevation = 0.dp) {
                    MainDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = { navigate(destination) },
                            icon = { androidx.compose.material3.Icon(destination.icon, destination.label) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberTeal,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = NeonPurple.copy(alpha = 0.2f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { outerPadding ->
        NavHost(navController, startDestination = MainDestination.Chat.route, modifier = Modifier.fillMaxSize().padding(outerPadding)) {
            composable(MainDestination.Chat.route) {
                ChatScreen(
                    viewModel = hiltViewModel<ChatViewModel>(),
                    openSettings = { navigate(MainDestination.You) },
                )
            }
            composable(MainDestination.Agents.route) {
                AgentScreen(
                    viewModel = hiltViewModel<AgentViewModel>(),
                    openSettings = { navigate(MainDestination.You) },
                )
            }
            composable(MainDestination.Create.route) {
                ImageStudioScreen(
                    viewModel = hiltViewModel<ImageStudioViewModel>(),
                    openSettings = { navigate(MainDestination.You) },
                )
            }
            composable(MainDestination.You.route) {
                SettingsScreen(
                    viewModel = hiltViewModel<SettingsViewModel>(),
                    openLibrary = { navController.navigate("library") },
                )
            }
            composable("library") {
                LibraryScreen(
                    viewModel = hiltViewModel<LibraryViewModel>(),
                    navigateBack = navController::popBackStack,
                )
            }
        }
    }
}
