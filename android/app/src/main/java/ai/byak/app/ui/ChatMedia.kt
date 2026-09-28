package ai.byak.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import ai.byak.app.data.ImageDraft
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_IMAGE_SIDE = 1568 // vision models downscale beyond this anyway

/** Decodes, orients (via ImageDecoder on API 28+), downscales and JPEG-encodes a picked image so uploads stay small. */
suspend fun prepareImage(context: Context, uri: Uri): ImageDraft? = withContext(Dispatchers.IO) {
    runCatching {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = MAX_IMAGE_SIDE.toFloat() / max(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
            val scale = MAX_IMAGE_SIDE.toFloat() / max(decoded.width, decoded.height)
            if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true) else decoded
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        ImageDraft("image/jpeg", out.toByteArray())
    }.getOrNull()
}

@Composable fun ImageBytes(bytes: ByteArray, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, bytes) { value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } }
    image?.let { Image(it, contentDescription = "Attached image", modifier = modifier, contentScale = ContentScale.Crop) }
}

/** Reads assistant answers aloud with the system text-to-speech engine. */
class Speaker(context: Context) {
    var speakingId by mutableStateOf<String?>(null)
        private set
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId?.endsWith(":last") == true) speakingId = null }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { speakingId = null }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { speakingId = null }
        })
    }

    fun toggle(id: String, markdown: String) {
        if (speakingId == id) { tts.stop(); speakingId = null; return }
        if (!ready) return
        tts.stop()
        val chunks = speakableChunks(markdown, TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3500))
        if (chunks.isEmpty()) return
        speakingId = id
        chunks.forEachIndexed { i, chunk -> tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == chunks.lastIndex) "$id:last" else "$id:$i") }
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}

@Composable fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}

/** Turns Markdown into plain sentences for speech and splits it to fit the engine's input limit. */
fun speakableChunks(markdown: String, limit: Int): List<String> {
    val plain = markdown
        .replace(Regex("```[\\s\\S]*?(```|$)"), " Code block omitted. ")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_`#>]+"), "")
        .replace(Regex("\\s+"), " ").trim()
    if (plain.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>(); val current = StringBuilder()
    for (sentence in plain.split(Regex("(?<=[.!?])\\s+"))) {
        if (current.length + sentence.length + 1 > limit && current.isNotEmpty()) { chunks += current.toString(); current.clear() }
        if (sentence.length > limit) sentence.chunked(limit).forEach { chunks += it } else { if (current.isNotEmpty()) current.append(' '); current.append(sentence) }
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks
}
