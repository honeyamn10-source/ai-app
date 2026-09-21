package ai.byak.app.data.localai

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.core.content.getSystemService
import ai.byak.app.data.security.SecureStore
import ai.byak.app.domain.model.MessageRole
import ai.byak.app.domain.repository.StreamChunk
import ai.byak.app.domain.repository.StreamingRequest
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PortableModelStatus { MISSING, DOWNLOADING, READY, FAILED }

data class PortableModelState(
    val status: PortableModelStatus,
    val progressPercent: Int = 0,
    val message: String,
)

@Singleton
class PortableLocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val engineMutex = Mutex()
    @Volatile private var engine: Engine? = null
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun state(): PortableModelState {
        val model = modelFile()
        if (model.isFile && model.length() >= MIN_VALID_MODEL_BYTES) {
            return PortableModelState(
                PortableModelStatus.READY,
                100,
                "${SecureStore.PORTABLE_LOCAL_MODEL} is active and works without internet or an API key.",
            )
        }
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) {
            return PortableModelState(
                PortableModelStatus.MISSING,
                message = "Download the 586 MB local model once. It then runs privately and offline on this phone.",
            )
        }
        val manager = context.getSystemService<DownloadManager>()
            ?: return PortableModelState(PortableModelStatus.FAILED, message = "Android Download Manager is unavailable.")
        val cursor = manager.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) {
                preferences.edit().remove(KEY_DOWNLOAD_ID).apply()
                return PortableModelState(PortableModelStatus.MISSING, message = "The local model download was removed. Start it again.")
            }
            val status = it.int(DownloadManager.COLUMN_STATUS)
            val downloaded = it.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val total = it.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val progress = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 99) else 0
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (model.isFile && model.length() >= MIN_VALID_MODEL_BYTES) {
                        PortableModelState(PortableModelStatus.READY, 100, "Local AI is downloaded and active.")
                    } else {
                        PortableModelState(PortableModelStatus.FAILED, progress, "The downloaded model is incomplete. Delete it and download again.")
                    }
                }
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED ->
                    PortableModelState(PortableModelStatus.DOWNLOADING, progress, "Downloading local AI… $progress%")
                else -> PortableModelState(
                    PortableModelStatus.FAILED,
                    progress,
                    "The local model download failed. Check storage and internet, then try again.",
                )
            }
        }
    }

    fun observeState(): Flow<PortableModelState> = flow {
        while (currentCoroutineContext().isActive) {
            val current = state()
            emit(current)
            delay(if (current.status == PortableModelStatus.DOWNLOADING) 750L else 3_000L)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun startDownload(): PortableModelState {
        val current = state()
        if (current.status == PortableModelStatus.READY || current.status == PortableModelStatus.DOWNLOADING) return current
        val target = modelFile()
        target.parentFile?.mkdirs()
        val available = StatFs(target.parentFile?.absolutePath ?: context.filesDir.absolutePath).availableBytes
        require(available >= REQUIRED_FREE_BYTES) {
            "Local AI needs about 1 GB of free storage for download and runtime preparation."
        }
        target.delete()
        val manager = context.getSystemService<DownloadManager>()
            ?: error("Android Download Manager is unavailable on this phone.")
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("BYAK Portable Local AI")
            .setDescription("Downloading Qwen3 0.6B for private offline chat")
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, MODEL_FILE_NAME)
        val id = manager.enqueue(request)
        preferences.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        return PortableModelState(
            PortableModelStatus.DOWNLOADING,
            0,
            "Local AI download started. Android will notify you when the 586 MB model is ready.",
        )
    }

    fun retryDownload(): PortableModelState {
        deleteModel()
        return startDownload()
    }

    fun deleteModel() {
        engine?.close()
        engine = null
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0L) context.getSystemService<DownloadManager>()?.remove(id)
        preferences.edit().remove(KEY_DOWNLOAD_ID).apply()
        modelFile().delete()
    }

    fun stream(request: StreamingRequest): Flow<StreamChunk> = flow {
        val current = state()
        require(current.status == PortableModelStatus.READY) {
            when (current.status) {
                PortableModelStatus.MISSING -> "Download Portable Local AI from You → AI connection first."
                PortableModelStatus.DOWNLOADING -> current.message
                PortableModelStatus.FAILED -> current.message
                PortableModelStatus.READY -> "Local AI is ready."
            }
        }
        engineMutex.withLock {
            val activeEngine = engine ?: Engine(
                EngineConfig(
                    modelPath = modelFile().absolutePath,
                    backend = Backend.CPU(),
                ),
            ).also {
                it.initialize()
                engine = it
            }
            val fullText = StringBuilder()
            activeEngine.createConversation().use { conversation ->
                conversation.sendMessageAsync(buildPrompt(request)).collect { message ->
                    val received = message.toString()
                    val delta = when {
                        received.isBlank() -> ""
                        received.startsWith(fullText.toString()) -> received.removePrefix(fullText.toString())
                        else -> received
                    }
                    if (delta.isNotEmpty()) {
                        fullText.append(delta)
                        emit(StreamChunk.Delta(delta))
                    }
                }
            }
            emit(StreamChunk.Completed(fullText.toString()))
        }
    }.flowOn(Dispatchers.Default)

    private fun modelFile(): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "models")
        return File(directory, MODEL_FILE_NAME)
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
                append("Instructions: ").append(it).append("\n\n")
            }
            append(history).append("\n\nAssistant:")
        }.takeLast(MAX_PROMPT_CHARS)
    }

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private companion object {
        const val PREFERENCES = "portable_local_ai"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val MODEL_FILE_NAME = "Qwen3-0.6B.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true"
        const val MIN_VALID_MODEL_BYTES = 500_000_000L
        const val REQUIRED_FREE_BYTES = 1_000_000_000L
        const val MAX_PROMPT_CHARS = 8_000
    }
}
