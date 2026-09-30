package ai.byak.app.data.local

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import ai.byak.app.data.ApiException
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Where the offline model is: not downloaded, downloading (with percent), ready, or failed. */
data class LocalModelState(val status: Status, val progress: Int = 0, val message: String) {
    enum class Status { Missing, Downloading, Ready, Failed }
    val ready get() = status == Status.Ready
}

/**
 * Offline AI: downloads Qwen3 0.6B (LiteRT-LM format) once, then answers on the phone's CPU
 * with no internet and no API key. Same engine and model the 2.9 release used.
 */
class LocalModel(private val context: Context) {
    private val prefs = context.getSharedPreferences("byak_local_model", Context.MODE_PRIVATE)
    private val lock = Mutex()
    @Volatile private var engine: Engine? = null

    private fun file(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "models"), FILE_NAME)
    private fun complete(f: File) = f.isFile && f.length() >= MIN_BYTES

    fun state(): LocalModelState {
        val model = file()
        val id = prefs.getLong(KEY_DOWNLOAD, -1L)
        if (id <= 0L) return if (complete(model)) ready() else LocalModelState(LocalModelState.Status.Missing, message = "Download once ($SIZE_LABEL). Then it works with no internet and no API key.")
        val manager = context.getSystemService(DownloadManager::class.java) ?: return LocalModelState(LocalModelState.Status.Failed, message = "Android's download manager isn't available on this phone.")
        manager.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) { prefs.edit().remove(KEY_DOWNLOAD).apply(); return if (complete(model)) ready() else LocalModelState(LocalModelState.Status.Missing, message = "The download was cancelled. Start it again.") }
            val done = c.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR); val total = c.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
            return when (c.int(DownloadManager.COLUMN_STATUS)) {
                DownloadManager.STATUS_SUCCESSFUL -> if (complete(model)) { prefs.edit().remove(KEY_DOWNLOAD).apply(); ready() } else LocalModelState(LocalModelState.Status.Failed, percent, "The download is incomplete. Delete it and download again.")
                DownloadManager.STATUS_FAILED -> LocalModelState(LocalModelState.Status.Failed, percent, "The download failed. Check your internet and free storage, then try again.")
                DownloadManager.STATUS_PAUSED -> LocalModelState(LocalModelState.Status.Downloading, percent, "Download paused — waiting for a connection… $percent%")
                else -> LocalModelState(LocalModelState.Status.Downloading, percent, "Downloading offline AI… $percent%")
            }
        }
    }
    private fun ready() = LocalModelState(LocalModelState.Status.Ready, 100, "Ready. Runs on this phone with no internet and no API key.")

    fun startDownload(): LocalModelState {
        val current = state()
        if (current.status == LocalModelState.Status.Ready || current.status == LocalModelState.Status.Downloading) return current
        val target = file(); target.parentFile?.mkdirs()
        if (StatFs(target.parentFile?.absolutePath ?: context.filesDir.absolutePath).availableBytes < FREE_BYTES_NEEDED) throw ApiException("Offline AI needs about 1 GB of free storage.", 507)
        prefs.getLong(KEY_DOWNLOAD, -1L).takeIf { it > 0 }?.let { context.getSystemService(DownloadManager::class.java)?.remove(it) }
        target.delete()
        val manager = context.getSystemService(DownloadManager::class.java) ?: throw ApiException("Android's download manager isn't available on this phone.", 500)
        val id = manager.enqueue(DownloadManager.Request(Uri.parse(URL))
            .setTitle("BYAK offline AI").setDescription("Qwen3 0.6B for private offline chat")
            .setMimeType("application/octet-stream").setAllowedOverMetered(true).setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, FILE_NAME))
        prefs.edit().putLong(KEY_DOWNLOAD, id).apply()
        return LocalModelState(LocalModelState.Status.Downloading, 0, "Download started. You'll get a notification when it's ready.")
    }

    suspend fun delete() = lock.withLock {
        runCatching { engine?.close() }; engine = null
        prefs.getLong(KEY_DOWNLOAD, -1L).takeIf { it > 0 }?.let { context.getSystemService(DownloadManager::class.java)?.remove(it) }
        prefs.edit().remove(KEY_DOWNLOAD).apply()
        file().delete()
    }

    /** Generates one answer on the phone. [onDelta] receives visible text as it is produced. */
    suspend fun generate(system: String, turns: List<Turn>, onDelta: (String) -> Unit): Completion = withContext(Dispatchers.Default) {
        val current = state()
        if (!current.ready) throw ApiException(if (current.status == LocalModelState.Status.Missing) "Download the offline AI first: Models → Offline AI → Download." else current.message, 400)
        lock.withLock {
            val active = engine ?: try {
                Engine(EngineConfig(modelPath = file().absolutePath, backend = Backend.CPU())).also { it.initialize(); engine = it }
            } catch (e: Throwable) {
                throw ApiException("The offline AI couldn't start on this phone (${e.message ?: e.javaClass.simpleName}). Delete it and download again, or use an API key.", 500)
            }
            val filter = ThinkFilter(); val raw = StringBuilder(); val visible = StringBuilder()
            active.createConversation().use { conversation ->
                conversation.sendMessageAsync(prompt(system, turns)).collect { message ->
                    val received = message.toString()
                    // The engine may send each new piece or the whole text so far; handle both.
                    val delta = when { received.isEmpty() -> ""; received.startsWith(raw) -> received.substring(raw.length); else -> received }
                    if (delta.isNotEmpty()) { raw.append(delta); filter.accept(delta).takeIf { it.isNotEmpty() }?.let { visible.append(it); onDelta(it) } }
                }
            }
            filter.flush().takeIf { it.isNotEmpty() }?.let { visible.append(it); onDelta(it) }
            Completion(visible.toString().trim(), 0, 0, "stop")
        }
    }

    companion object {
        const val MODEL_ID = "qwen3-0.6b"
        const val FILE_NAME = "Qwen3-0.6B.litertlm"
        const val URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true"
        const val SIZE_LABEL = "614 MB"
        private const val KEY_DOWNLOAD = "download_id"
        private const val MIN_BYTES = 500_000_000L
        private const val FREE_BYTES_NEEDED = 1_000_000_000L
        private const val MAX_PROMPT_CHARS = 6_000

        /**
         * A small model does best with a short, plain prompt. Keeps the useful parts of the full system prompt
         * (standing instructions, memory, document excerpts, web results) within a budget, then as many recent
         * turns as fit. "/no_think" turns off Qwen3's slow reasoning mode.
         */
        fun prompt(system: String, turns: List<Turn>): String {
            // Split only where a known section starts, so multi-paragraph sections (several excerpts) stay whole.
            val keep = listOf("The user's standing instructions", "Project \"", "Things the user asked you to remember", "Relevant excerpts", "Live web results")
            val starts = keep + listOf("You are BYAK AI", "Retrieved documents", "Notes about web access")
            val sections = system.split(Regex("\n\n(?=(" + starts.joinToString("|") { Regex.escape(it) } + "))"))
            val context = sections.filter { part -> keep.any { part.startsWith(it) } }.joinToString("\n\n") { it.take(1_200) }.take(3_000)
            val header = "You are BYAK AI, a helpful assistant running offline on the user's phone. Answer clearly and briefly." + if (context.isNotBlank()) "\n\n$context" else ""
            val budget = (MAX_PROMPT_CHARS - header.length - 120).coerceAtLeast(1_000)
            val lines = ArrayDeque<String>(); var used = 0
            for (t in turns.asReversed().take(8)) {
                val note = if (t.images.isNotEmpty()) " [a photo was attached; the offline AI can't see photos]" else ""
                val line = "${if (t.role == "assistant") "Assistant" else "User"}: ${t.content}$note"
                if (used + line.length > budget) { if (lines.isEmpty()) lines.addFirst(line.takeLast(budget)); break }
                lines.addFirst(line); used += line.length + 2
            }
            return "$header\n\nConversation:\n${lines.joinToString("\n\n")}\n\nReply as the Assistant to the last User message. /no_think"
        }
    }
}

/** Drops Qwen3 `<think>…</think>` reasoning from streamed text, even when a tag is split across pieces. */
class ThinkFilter {
    private var inside = false; private var pending = ""; private var started = false
    fun accept(piece: String): String {
        var text = pending + piece; pending = ""; val out = StringBuilder()
        while (text.isNotEmpty()) {
            val tag = if (inside) "</think>" else "<think>"
            val at = text.indexOf(tag)
            if (at >= 0) { if (!inside) out.append(text, 0, at); inside = !inside; text = text.substring(at + tag.length); continue }
            // Hold back a possible partial tag at the end until the next piece arrives.
            val keep = (tag.length - 1 downTo 1).firstOrNull { text.endsWith(tag.substring(0, it)) } ?: 0
            if (!inside) out.append(text, 0, text.length - keep)
            pending = text.takeLast(keep); text = ""
        }
        return visible(out.toString())
    }
    fun flush(): String = if (inside) "" else visible(pending).also { pending = "" }
    // The answer starts at the first non-blank character (after any reasoning block).
    private fun visible(text: String): String {
        if (started) return text
        val trimmed = text.trimStart(); if (trimmed.isNotEmpty()) started = true
        return trimmed
    }
}

private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
