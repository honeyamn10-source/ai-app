package ai.byak.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Tiny document store for on-device mode: named collections of JSON records plus a "meta" object,
 * persisted atomically to app-private storage. All access is serialised by a mutex.
 */
class LocalStore(context: Context) {
    private val file = File(context.filesDir, "byak_local.json")
    val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
    /** Large per-record payloads (knowledge-file chunks) live outside the main document so it stays small. */
    val blobsDir = File(context.filesDir, "blobs").apply { mkdirs() }
    private val lock = Mutex()
    private var root: JSONObject? = null

    private fun load(): JSONObject = root ?: readFromDisk().also { root = it }
    private fun readFromDisk(): JSONObject {
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrElse {
            // Never overwrite unreadable data with an empty store: keep the file aside so it can be recovered.
            file.copyTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"), overwrite = true)
            JSONObject()
        }
    }
    private fun save(data: JSONObject) {
        val next = File(file.parentFile, "${file.name}.next")
        next.writeText(data.toString()); next.renameTo(file)
    }

    /** Read-only access to the current data. */
    suspend fun <T> read(block: (Db) -> T): T = lock.withLock { withContext(Dispatchers.IO) { block(Db(load())) } }
    /** Mutates and persists. */
    suspend fun <T> write(block: (Db) -> T): T = lock.withLock { withContext(Dispatchers.IO) { val data = load(); val result = block(Db(data)); save(data); result } }

    suspend fun wipe() = lock.withLock { withContext(Dispatchers.IO) { root = JSONObject(); file.delete(); attachmentsDir.listFiles()?.forEach { it.delete() }; blobsDir.listFiles()?.forEach { it.delete() } } }

    fun writeBlob(name: String, text: String) { val target = File(blobsDir, name); val next = File(blobsDir, "$name.next"); next.writeText(text); next.renameTo(target) }
    fun readBlob(name: String): String? = File(blobsDir, name).takeIf { it.exists() }?.readText()
    fun deleteBlob(name: String) { File(blobsDir, name).delete() }

    class Db(private val data: JSONObject) {
        val meta: JSONObject get() = data.optJSONObject("meta") ?: JSONObject().also { data.put("meta", it) }
        fun all(collection: String): MutableList<JSONObject> {
            val array = data.optJSONArray(collection) ?: JSONArray().also { data.put(collection, it) }
            return (0 until array.length()).map { array.getJSONObject(it) }.toMutableList()
        }
        private fun replace(collection: String, rows: List<JSONObject>) { data.put(collection, JSONArray(rows)) }
        fun find(collection: String, id: String): JSONObject? = all(collection).firstOrNull { it.optString("id") == id }
        fun insert(collection: String, row: JSONObject): JSONObject {
            val stamp = now()
            if (!row.has("id")) row.put("id", UUID.randomUUID().toString())
            row.put("createdAt", row.optString("createdAt").ifBlank { stamp }).put("updatedAt", stamp)
            replace(collection, all(collection) + row); return row
        }
        fun update(collection: String, id: String, patch: (JSONObject) -> Unit): JSONObject? {
            val rows = all(collection); val row = rows.firstOrNull { it.optString("id") == id } ?: return null
            patch(row); row.put("updatedAt", now()); replace(collection, rows); return row
        }
        fun remove(collection: String, predicate: (JSONObject) -> Boolean): Int {
            val rows = all(collection); val kept = rows.filterNot(predicate); replace(collection, kept); return rows.size - kept.size
        }
        fun export(): JSONObject = JSONObject(data.toString())
    }

    companion object { fun now(): String = Instant.now().toString() }
}
