package ai.byak.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.byak.app.ui.theme.GlassCard

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, navigateBack: () -> Unit) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is LibraryEffect.Message) snackbar.showSnackbar(it.value) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(navigateBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Private library", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("Local retrieval for chats and agents", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Button(
                    onClick = { picker.launch(arrayOf("text/*", "application/json")) },
                    enabled = !importing,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (importing) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.UploadFile, null)
                    Text(" Add")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.padding(start = 13.dp)) {
                            Text("Indexed on your phone", fontWeight = FontWeight.Bold)
                            Text("FTS4 and local vector matching keep retrieval fast and private.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (documents.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No documents yet", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 13.dp))
                        Text("Add notes, Markdown, CSV, or JSON.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(documents, key = { it.id }) { document ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(document.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${document.chunkCount} searchable chunks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton({ viewModel.delete(document.id) }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                    }
                }
            }
        }
    }
}
