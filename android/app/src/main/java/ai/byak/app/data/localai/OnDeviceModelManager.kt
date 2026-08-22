package ai.byak.app.data.localai

import ai.byak.app.domain.model.MessageRole
import ai.byak.app.domain.repository.StreamChunk
import ai.byak.app.domain.repository.StreamingRequest
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

enum class OnDeviceAvailability { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNSUPPORTED }

data class OnDevicePreparation(
    val availability: OnDeviceAvailability,
    val modelName: String,
    val message: String,
)

@Singleton
class OnDeviceModelManager @Inject constructor() {
    private val model by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Generation.getClient() }

    suspend fun status(): OnDeviceAvailability = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> OnDeviceAvailability.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> OnDeviceAvailability.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> OnDeviceAvailability.DOWNLOADING
            else -> OnDeviceAvailability.UNSUPPORTED
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        throw IllegalStateException(phoneAiFailure(error), error)
    }

    suspend fun prepare(): OnDevicePreparation {
        return when (val current = status()) {
            OnDeviceAvailability.AVAILABLE -> ready()
            OnDeviceAvailability.DOWNLOADABLE -> {
                var failure: Throwable? = null
                try {
                    model.download().collect { download ->
                        when (download) {
                            is DownloadStatus.DownloadFailed -> failure = download.e
                            else -> Unit
                        }
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    failure = error
                }
                failure?.let {
                    error("Phone AI could not be prepared. Keep Google Play services and Android AICore updated, check storage and internet access, then try again. ${it.message.orEmpty()}".trim())
                }
                if (status() == OnDeviceAvailability.AVAILABLE) ready()
                else error("Gemini Nano is still preparing. Keep the phone online and try again shortly.")
            }
            OnDeviceAvailability.DOWNLOADING -> error(
                "Gemini Nano is downloading through Android. Keep the phone online, then check again when it finishes.",
            )
            OnDeviceAvailability.UNSUPPORTED -> error(
                "Phone AI is not supported by this device yet. It requires Android AICore, a supported chipset, current Google Play services, and a locked bootloader. You can still connect a cloud provider from this phone.",
            )
        }
    }

    fun stream(request: StreamingRequest): Flow<StreamChunk> = flow {
        if (status() != OnDeviceAvailability.AVAILABLE) {
            // First-use setup stays entirely on the phone. AICore owns the model
            // download and lifecycle; BYAK never downloads arbitrary executable code.
            prepare()
        }
        val prompt = buildPrompt(request)
        val full = StringBuilder()
        model.generateContentStream(prompt).collect { response ->
            val chunk = response.candidates.firstOrNull()?.text.orEmpty()
            if (chunk.isNotEmpty()) {
                full.append(chunk)
                emit(StreamChunk.Delta(chunk))
            }
        }
        emit(StreamChunk.Completed(full.toString()))
    }

    private suspend fun ready(): OnDevicePreparation {
        val baseModel = runCatching { model.getBaseModelName() }.getOrDefault("Gemini Nano")
        return OnDevicePreparation(
            availability = OnDeviceAvailability.AVAILABLE,
            modelName = baseModel,
            message = "$baseModel is ready. Prompts stay on this phone and no API key is required.",
        )
    }

    private fun buildPrompt(request: StreamingRequest): String {
        val history = request.messages.takeLast(12).joinToString("\n\n") { message ->
            val label = when (message.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.SYSTEM -> "System"
            }
            "$label: ${message.content}"
        }
        return buildString {
            request.systemPrompt?.takeIf(String::isNotBlank)?.let {
                append("Instructions: ")
                append(it)
                append("\n\n")
            }
            append(history)
            append("\n\nAssistant:")
        }.takeLast(MAX_PROMPT_CHARS)
    }

    private companion object {
        const val MAX_PROMPT_CHARS = 12_000
    }

    private fun phoneAiFailure(error: Throwable): String {
        val detail = error.message.orEmpty().take(180)
        return buildString {
            append("Android could not start Phone AI. Update Google Play services and the Android AICore system component, restart the phone, and try again.")
            if (detail.isNotBlank()) append(" ").append(detail)
        }
    }
}
