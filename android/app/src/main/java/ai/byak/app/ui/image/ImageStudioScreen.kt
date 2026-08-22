package ai.byak.app.ui.image

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.byak.app.domain.model.GeneratedImage
import ai.byak.app.domain.model.ImageProvider
import ai.byak.app.domain.model.ImageQuality
import ai.byak.app.domain.model.ImageResolution
import ai.byak.app.ui.theme.CyberTeal
import ai.byak.app.ui.theme.GlassCard
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(
    viewModel: ImageStudioViewModel,
    openSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("Photorealistic, refined lighting, professional composition") }
    var aspect by remember { mutableStateOf("1:1") }
    var resolution by remember { mutableStateOf(ImageResolution.ONE_K) }
    var quality by remember { mutableStateOf(ImageQuality.AUTO) }
    val providerReady = when (state.provider) {
        ImageProvider.OPENROUTER -> state.hasOpenRouter
        ImageProvider.GEMINI -> state.hasGemini
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        if (!state.generating) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Create", fontWeight = FontWeight.Black)
                        Text("Professional AI image studio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Image engine", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ImageProvider.entries, key = { it.name }) { provider ->
                                AssistChip(
                                    onClick = { viewModel.selectProvider(provider) },
                                    label = { Text(provider.displayName()) },
                                    leadingIcon = if (provider == state.provider) {
                                        { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(17.dp), tint = CyberTeal) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        if (!providerReady) {
                            OutlinedButton(onClick = openSettings, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Settings, null)
                                Text(" Connect " + state.provider.displayName())
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Describe your image") },
                    placeholder = { Text("A premium product photograph of…") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(20.dp),
                    enabled = !state.generating,
                )
            }

            item {
                Text("Creative direction", fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp),
                ) {
                    items(STYLES, key = { it.first }) { option ->
                        AssistChip(
                            onClick = { style = option.second },
                            label = { Text(option.first) },
                            leadingIcon = if (style == option.second) {
                                { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.model,
                    onValueChange = viewModel::selectModel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Image model") },
                    supportingText = {
                        Text(
                            if (state.loadingModels) "Loading current models…" else "BYAK checks the provider's live image catalog.",
                        )
                    },
                    trailingIcon = {
                        if (state.loadingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    },
                    singleLine = true,
                    enabled = !state.generating,
                    shape = RoundedCornerShape(18.dp),
                )
                if (state.models.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(top = 8.dp),
                    ) {
                        items(state.models.take(8), key = { it }) { model ->
                            AssistChip(
                                onClick = { viewModel.selectModel(model) },
                                label = {
                                    Text(model.substringAfter('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                            )
                        }
                    }
                }
            }

            item {
                Text("Format", fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(top = 8.dp),
                ) {
                    items(ASPECTS, key = { it }) { value ->
                        AssistChip(onClick = { aspect = value }, label = { Text(value) })
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(top = 8.dp),
                ) {
                    items(ImageResolution.entries, key = { it.name }) { value ->
                        AssistChip(onClick = { resolution = value }, label = { Text(value.label()) })
                    }
                    items(ImageQuality.entries, key = { "quality-" + it.name }) { value ->
                        AssistChip(onClick = { quality = value }, label = { Text(value.label()) })
                    }
                }
                Text(
                    aspect + " · " + resolution.label() + " · " + quality.label(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            item {
                Button(
                    onClick = { viewModel.generate(prompt, style, aspect, resolution, quality) },
                    enabled = providerReady && prompt.trim().length >= 3 && !state.generating && state.model.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    if (state.generating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  Creating…")
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, null)
                        Text("  Generate image")
                    }
                }
            }

            item {
                AnimatedVisibility(state.generated != null) {
                    state.generated?.let { image ->
                        GeneratedImageCard(
                            image = image,
                            save = viewModel::saveToGallery,
                            regenerate = { viewModel.generate(prompt, style, aspect, resolution, quality) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratedImageCard(
    image: GeneratedImage,
    save: () -> Unit,
    regenerate: () -> Unit,
) {
    val context = LocalContext.current
    val legacyGalleryPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) save()
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = File(image.filePath),
                contentDescription = image.prompt,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Fit,
            )
            Text(image.model, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            legacyGalleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            save()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Download, null)
                    Text(" Save")
                }
                OutlinedButton(
                    onClick = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".files",
                            File(image.filePath),
                        )
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = image.mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                "Share BYAK image",
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Share, null)
                    Text(" Share")
                }
            }
            OutlinedButton(onClick = regenerate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, null)
                Text(" Regenerate")
            }
        }
    }
}

private fun ImageProvider.displayName(): String = when (this) {
    ImageProvider.OPENROUTER -> "OpenRouter"
    ImageProvider.GEMINI -> "Gemini"
}

private fun ImageResolution.label(): String = when (this) {
    ImageResolution.ONE_K -> "1K"
    ImageResolution.TWO_K -> "2K"
    ImageResolution.FOUR_K -> "4K"
}

private fun ImageQuality.label(): String = when (this) {
    ImageQuality.AUTO -> "Auto quality"
    ImageQuality.MEDIUM -> "Medium"
    ImageQuality.HIGH -> "High"
}

private val ASPECTS = listOf("1:1", "16:9", "9:16", "4:3", "3:4")
private val STYLES = listOf(
    "Photo" to "Photorealistic, refined lighting, professional composition",
    "Product" to "Premium commercial product photography, controlled studio lighting, clean background",
    "Illustration" to "Editorial illustration, sophisticated color system, crisp intentional details",
    "Logo" to "Minimal vector-style brand mark, balanced geometry, no mockup, no extra text",
)
