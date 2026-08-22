package ai.byak.app.ui.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.domain.model.LibraryDocument
import ai.byak.app.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class LibraryUiState(val documents: List<LibraryDocument> = emptyList(), val importing: Boolean = false)
sealed interface LibraryEffect { data class Message(val value: String) : LibraryEffect }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
) : ViewModel() {
    val documents: StateFlow<List<LibraryDocument>> = repository.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableImporting = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = mutableImporting
    private val mutableEffects = MutableSharedFlow<LibraryEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<LibraryEffect> = mutableEffects.asSharedFlow()

    fun import(uri: Uri) {
        viewModelScope.launch {
            mutableImporting.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val type = resolver.getType(uri) ?: "text/plain"
                    require(type in SUPPORTED_TYPES || type.startsWith("text/")) { "Choose a TXT, Markdown, CSV, or JSON document" }
                    val title = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "Imported document"
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val output = StringBuilder()
                        val buffer = CharArray(8_192)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            output.append(buffer, 0, count)
                            if (output.length > 5_000_000) throw IOException("Document exceeds the 5 MB text limit")
                        }
                        output.toString()
                    } ?: error("The selected document could not be opened")
                    repository.importText(title, type, text)
                }
            }.onSuccess {
                mutableEffects.emit(LibraryEffect.Message("Document is ready for private retrieval"))
            }.onFailure {
                mutableEffects.emit(LibraryEffect.Message(it.message ?: "Document import failed"))
            }
            mutableImporting.value = false
        }
    }

    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

    private companion object {
        val SUPPORTED_TYPES = setOf("application/json", "text/markdown", "text/csv", "text/plain")
    }
}
