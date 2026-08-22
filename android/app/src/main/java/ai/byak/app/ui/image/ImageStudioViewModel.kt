package ai.byak.app.ui.image

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.data.security.SecureStore
import ai.byak.app.domain.model.GeneratedImage
import ai.byak.app.domain.model.ImageGenerationRequest
import ai.byak.app.domain.model.ImageProvider
import ai.byak.app.domain.model.ImageQuality
import ai.byak.app.domain.model.ImageResolution
import ai.byak.app.domain.repository.ImageGenerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ImageStudioUiState(
    val provider: ImageProvider = ImageProvider.OPENROUTER,
    val model: String = "openai/gpt-image-2",
    val models: List<String> = emptyList(),
    val hasOpenRouter: Boolean = false,
    val hasGemini: Boolean = false,
    val loadingModels: Boolean = false,
    val generating: Boolean = false,
    val generated: GeneratedImage? = null,
    val message: String? = null,
)

@Immutable
private data class ImageOperationState(
    val provider: ImageProvider = ImageProvider.OPENROUTER,
    val model: String = "openai/gpt-image-2",
    val models: List<String> = emptyList(),
    val loadingModels: Boolean = false,
    val generating: Boolean = false,
    val generated: GeneratedImage? = null,
    val message: String? = null,
)

@HiltViewModel
class ImageStudioViewModel @Inject constructor(
    private val repository: ImageGenerationRepository,
    private val secureStore: SecureStore,
) : ViewModel() {
    private val operation = MutableStateFlow(ImageOperationState())

    val state: StateFlow<ImageStudioUiState> = combine(secureStore.state, operation) { secure, current ->
        ImageStudioUiState(
            provider = current.provider,
            model = current.model,
            models = current.models,
            hasOpenRouter = secure.openRouterKey.isNotBlank(),
            hasGemini = secure.geminiKey.isNotBlank(),
            loadingModels = current.loadingModels,
            generating = current.generating,
            generated = current.generated,
            message = current.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImageStudioUiState())

    init {
        viewModelScope.launch {
            val secure = secureStore.state.first()
            val initial = when {
                secure.openRouterKey.isNotBlank() -> ImageProvider.OPENROUTER
                secure.geminiKey.isNotBlank() -> ImageProvider.GEMINI
                else -> ImageProvider.OPENROUTER
            }
            operation.update {
                it.copy(
                    provider = initial,
                    model = if (initial == ImageProvider.OPENROUTER) secure.openRouterImageModel else secure.geminiImageModel,
                )
            }
            refreshModels()
        }
    }

    fun selectProvider(provider: ImageProvider) {
        val secure = secureStore.snapshot()
        operation.update {
            it.copy(
                provider = provider,
                model = if (provider == ImageProvider.OPENROUTER) secure.openRouterImageModel else secure.geminiImageModel,
                models = emptyList(),
                message = null,
            )
        }
        refreshModels()
    }

    fun selectModel(model: String) {
        if (model.isNotBlank()) operation.update { it.copy(model = model.trim()) }
    }

    fun refreshModels() {
        if (operation.value.loadingModels) return
        viewModelScope.launch {
            operation.update { it.copy(loadingModels = true, message = null) }
            val provider = operation.value.provider
            repository.discoverModels(provider)
                .onSuccess { models ->
                    operation.update { current ->
                        val selected = current.model.takeIf { it in models } ?: models.firstOrNull() ?: current.model
                        current.copy(loadingModels = false, models = models, model = selected)
                    }
                }
                .onFailure { error ->
                    operation.update {
                        it.copy(loadingModels = false, message = error.message ?: "Could not load image models.")
                    }
                }
        }
    }

    fun generate(
        prompt: String,
        styleDirection: String,
        aspectRatio: String,
        resolution: ImageResolution,
        quality: ImageQuality,
    ) {
        if (operation.value.generating) return
        viewModelScope.launch {
            val current = operation.value
            val fullPrompt = buildString {
                append(prompt.trim())
                if (styleDirection.isNotBlank()) {
                    append("\n\nCreative direction: ")
                    append(styleDirection)
                }
            }
            operation.update { it.copy(generating = true, message = "Creating your image…") }
            val result = repository.generate(
                ImageGenerationRequest(
                    provider = current.provider,
                    model = current.model,
                    prompt = fullPrompt,
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                    quality = quality,
                ),
            )
            val image = result.getOrNull()
            if (image != null) {
                secureStore.saveImageModel(current.provider.name, current.model)
                operation.update { it.copy(generating = false, generated = image, message = "Image ready") }
            } else {
                val error = result.exceptionOrNull()
                operation.update {
                    it.copy(generating = false, message = error?.message ?: "Image generation failed.")
                }
            }
        }
    }

    fun saveToGallery() {
        val image = operation.value.generated ?: return
        viewModelScope.launch {
            repository.saveToGallery(image)
                .onSuccess { operation.update { it.copy(message = "Saved to Pictures/BYAK AI") } }
                .onFailure { error ->
                    operation.update { it.copy(message = error.message ?: "Could not save this image.") }
                }
        }
    }

    fun clearMessage() = operation.update { it.copy(message = null) }
}
