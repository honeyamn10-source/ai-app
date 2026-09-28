@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ai.byak.app.ui

import ai.byak.app.data.ComparisonResult
import ai.byak.app.data.Provider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ask two models the same question and read the answers side by side (stacked on phones). */
@Composable fun CompareScreen(state: UiState, vm: ByakViewModel, openModels: () -> Unit) {
    val enabled = state.providers.filter { it.enabled }
    var question by rememberSaveable { mutableStateOf("") }
    var firstProvider by rememberSaveable { mutableStateOf(enabled.getOrNull(0)?.id.orEmpty()) }
    var secondProvider by rememberSaveable { mutableStateOf((enabled.getOrNull(1) ?: enabled.getOrNull(0))?.id.orEmpty()) }
    var firstModel by rememberSaveable { mutableStateOf(enabled.firstOrNull { it.id == firstProvider }?.defaultModel.orEmpty()) }
    var secondModel by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(secondProvider, state.modelOptions[secondProvider]) {
        // Default the second slot to a different model so the comparison is meaningful.
        if (secondModel.isBlank()) {
            val provider = enabled.firstOrNull { it.id == secondProvider }
            secondModel = if (secondProvider == firstProvider) state.modelOptions[secondProvider].orEmpty().firstOrNull { it != firstModel } ?: provider?.defaultModel.orEmpty() else provider?.defaultModel.orEmpty()
        }
    }
    val sub = state.subscription; val metered = !sub.isPro && sub.limits.isNotEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Compare models", fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("One question, two models, answers side by side. Great for picking the best model for a job.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled.isEmpty()) item {
            Card(onClick = openModels) { ListItem(headlineContent = { Text("Connect a model first", fontWeight = FontWeight.Bold) }, supportingContent = { Text("Add at least one provider; you can compare two of its models.") }, leadingContent = { Icon(Icons.Outlined.Key, null) }) }
        } else {
            item { ModelSlot("Model A", enabled, firstProvider, firstModel, state, vm, { firstProvider = it; firstModel = enabled.first { p -> p.id == it }.defaultModel }) { firstModel = it } }
            item { ModelSlot("Model B", enabled, secondProvider, secondModel, state, vm, { secondProvider = it; secondModel = enabled.first { p -> p.id == it }.defaultModel }) { secondModel = it } }
            item {
                OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), label = { Text("Your question") }, minLines = 2, maxLines = 6, shape = RoundedCornerShape(16.dp))
                if (metered) Text("${sub.comparisonsLeft} of ${sub.limits["comparisonsPerDay"]} free comparisons left today · Pro gets 100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Button(
                    onClick = {
                        if (metered && sub.comparisonsLeft == 0) vm.showUpgrade("You've used today's ${sub.limits["comparisonsPerDay"]} free model comparisons. BYAK Pro gives you 100 a day.")
                        else vm.compare(question.trim(), firstProvider to firstModel.trim(), secondProvider to secondModel.trim())
                    },
                    enabled = question.isNotBlank() && firstModel.isNotBlank() && secondModel.isNotBlank() && !state.comparing,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { if (state.comparing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.CompareArrows, null); Spacer(Modifier.width(8.dp)); Text("Compare") } }
            }
        }
        if (state.comparing) item { Text("Asking both models…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.comparison.isNotEmpty()) item {
            BoxWithConstraints {
                // Side by side on tablets, stacked on phones.
                if (maxWidth >= 640.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { state.comparison.forEachIndexed { i, r -> ResultCard(i, r, Modifier.weight(1f)) } }
                else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { state.comparison.forEachIndexed { i, r -> ResultCard(i, r, Modifier.fillMaxWidth()) } }
            }
        }
    }
}

@Composable private fun ModelSlot(label: String, providers: List<Provider>, providerId: String, model: String, state: UiState, vm: ByakViewModel, onProvider: (String) -> Unit, onModel: (String) -> Unit) {
    LaunchedEffect(providerId) { if (providerId.isNotBlank()) vm.loadModels(providerId) }
    val suggestions = state.modelOptions[providerId].orEmpty().filter { it != model && (model.isBlank() || it.contains(model.substringBefore('-'), ignoreCase = true)) }.take(6)
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { providers.forEach { p -> FilterChip(selected = p.id == providerId, onClick = { onProvider(p.id) }, label = { Text(p.name) }) } }
        OutlinedTextField(model, onModel, Modifier.fillMaxWidth(), label = { Text("Model id") }, singleLine = true)
        if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { suggestions.forEach { s -> SuggestionChip(onClick = { onModel(s) }, label = { Text(s) }) } }
    } }
}

@Composable private fun ResultCard(index: Int, result: ComparisonResult, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    Card(modifier) { Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)) { Text(if (index == 0) "A" else "B", Modifier.padding(horizontal = 7.dp, vertical = 1.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) { Text(result.model, fontWeight = FontWeight.Bold); Text("${result.providerName} · ${String.format(java.util.Locale.US, "%.1f", result.ms / 1000.0)}s" + if (result.outputTokens > 0) " · ${result.outputTokens} tokens" else "", style = MaterialTheme.typography.labelSmall) }
            if (result.error == null) IconButton(onClick = { clipboard.setText(AnnotatedString(result.content)) }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
        }
        Spacer(Modifier.height(8.dp))
        if (result.error != null) Text(result.error, color = MaterialTheme.colorScheme.error) else MarkdownText(result.content)
    } }
}
