package ai.byak.app.ui.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.byak.app.domain.model.AgentRun
import ai.byak.app.domain.model.AgentStatus
import ai.byak.app.domain.model.AgentStep
import ai.byak.app.domain.model.AgentType
import ai.byak.app.domain.model.StepStatus
import ai.byak.app.ui.theme.GlassCard

private data class AgentTemplate(val type: AgentType, val name: String, val description: String, val icon: ImageVector, val tint: Color)

@Composable
fun AgentScreen(viewModel: AgentViewModel, openSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val selected = state.runs.firstOrNull { it.id == state.selectedRunId }
    val context = LocalContext.current
    var launchAfterPermission by remember { mutableStateOf<Pair<AgentType, String>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        launchAfterPermission?.let { (type, goal) ->
            if (granted) viewModel.start(type, goal)
            launchAfterPermission = null
        }
    }
    fun start(type: AgentType, goal: String) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            launchAfterPermission = type to goal
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.start(type, goal)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is AgentEffect.Error) snackbar.showSnackbar(it.message) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) IconButton({ viewModel.select(null) }) { Icon(Icons.Outlined.ArrowBack, "Back") }
                else Spacer(Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (selected == null) "Agents" else selected.type.displayName(), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(if (selected == null) "Give a goal. BYAK handles the steps." else selected.status.displayName(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                if (!state.providerReady) IconButton(openSettings) { Icon(Icons.Outlined.Settings, "Add AI key") }
            }
        },
    ) { padding ->
        if (selected == null) AgentDashboard(state, viewModel, openSettings, ::start, Modifier.padding(padding))
        else AgentRunDetail(selected, state.steps, viewModel, Modifier.padding(padding))
    }
}

@Composable
private fun AgentDashboard(
    state: AgentUiState,
    viewModel: AgentViewModel,
    openSettings: () -> Unit,
    start: (AgentType, String) -> Unit,
    modifier: Modifier,
) {
    val templates = listOf(
        AgentTemplate(AgentType.DEEP_RESEARCH, "Deep Research", "Investigate evidence and deliver a decision-ready report.", Icons.Outlined.Search, Color(0xFF00E5FF)),
        AgentTemplate(AgentType.BUILDER, "Builder", "Turn a product or app idea into a concrete implementation.", Icons.Outlined.Code, Color(0xFFB68CFF)),
        AgentTemplate(AgentType.BUSINESS_PLANNER, "Business Planner", "Test positioning, economics, risks, and next moves.", Icons.Outlined.BusinessCenter, Color(0xFFFFD700)),
    )
    var selectedTemplate by remember { mutableStateOf<AgentTemplate?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Text("Long work, without babysitting", fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
            Text("You can leave the app. Progress stays visible and the result waits here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
        }
        if (!state.providerReady) {
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = openSettings) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.padding(start = 13.dp)) {
                            Text("Connect AI to start an agent", fontWeight = FontWeight.Bold)
                            Text("Your provider key stays encrypted on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        items(templates, key = { it.type.name }) { template ->
            GlassCard(
                Modifier.fillMaxWidth(),
                onClick = { if (state.providerReady) selectedTemplate = template else openSettings() },
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = template.tint.copy(alpha = .13f)) {
                        Icon(template.icon, null, Modifier.padding(12.dp), tint = template.tint)
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(template.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(template.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.runs.isNotEmpty()) {
            item { Text("Your work", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 12.dp)) }
            items(state.runs, key = { it.id }) { run ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { viewModel.select(run.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusIcon(run.status)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(run.goal, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${run.type.displayName()} · ${run.status.displayName()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        if (run.status == AgentStatus.RUNNING) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
    selectedTemplate?.let { template ->
        GoalDialog(template, { selectedTemplate = null }) { goal -> selectedTemplate = null; start(template.type, goal) }
    }
}

@Composable
private fun AgentRunDetail(run: AgentRun, steps: List<AgentStep>, viewModel: AgentViewModel, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(run.goal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator({ run.progress / 100f }, Modifier.fillMaxWidth())
                    Text("${run.progress}% · ${run.status.displayName()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                    if (run.status in listOf(AgentStatus.RUNNING, AgentStatus.QUEUED)) {
                        TextButton({ viewModel.cancel(run.id) }, Modifier.align(Alignment.End)) { Icon(Icons.Outlined.Cancel, null); Text(" Stop agent") }
                    }
                }
            }
        }
        items(steps, key = { it.id }) { step -> StepCard(step) }
        if (run.error != null) item {
            GlassCard(Modifier.fillMaxWidth()) { Text(run.error, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.error) }
        }
        if (run.result.isNotBlank()) item {
            Text("Final result", fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(run.result, Modifier.padding(18.dp), lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun StepCard(step: AgentStep) {
    GlassCard(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (step.status) {
                    StepStatus.COMPLETE -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                    StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    StepStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    StepStatus.PENDING -> Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(step.title, Modifier.padding(start = 11.dp), fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(step.detail.isNotBlank()) {
                Text(step.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 11.dp), maxLines = if (step.status == StepStatus.RUNNING) 8 else 30)
            }
        }
    }
}

@Composable
private fun GoalDialog(template: AgentTemplate, dismiss: () -> Unit, submit: (String) -> Unit) {
    var goal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(template.icon, null, tint = template.tint) },
        title = { Text(template.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Describe the result you want. You can close BYAK after it starts.")
                OutlinedTextField(goal, { goal = it }, label = { Text("Your goal") }, minLines = 4, maxLines = 8, shape = RoundedCornerShape(18.dp))
            }
        },
        confirmButton = { Button({ submit(goal.trim()) }, enabled = goal.trim().length >= 8) { Text("Start") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatusIcon(status: AgentStatus) {
    when (status) {
        AgentStatus.SUCCEEDED -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
        AgentStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        AgentStatus.CANCELLED -> Icon(Icons.Outlined.Cancel, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
    }
}

private fun AgentType.displayName() = when (this) {
    AgentType.DEEP_RESEARCH -> "Deep Research"
    AgentType.BUILDER -> "Builder"
    AgentType.BUSINESS_PLANNER -> "Business Planner"
}

private fun AgentStatus.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)
