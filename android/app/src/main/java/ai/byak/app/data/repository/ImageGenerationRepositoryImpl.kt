package ai.byak.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import ai.byak.app.data.security.SecureStore
import ai.byak.app.domain.model.AiProvider
import ai.byak.app.domain.model.GeneratedImage
import ai.byak.app.domain.model.ImageGenerationRequest
import ai.byak.app.domain.model.ImageProvider
import ai.byak.app.domain.model.ImageQuality
import ai.byak.app.domain.model.ImageResolution
import ai.byak.app.domain.repository.ImageGenerationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class ImageGenerationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HttpClient,
    private val secureStore: SecureStore,
    private val json: Json,
) : ImageGenerationRepository {
    override suspend fun discoverModels(provider: ImageProvider): Result<List<String>> = runCatching {
        when (provider) {
            ImageProvider.OPENROUTER -> {
                val key = secureStore.apiKey("OPENROUTER")
                require(key.isNotBlank()) { "Connect OpenRouter in You → AI connection first." }
                val response = client.get("https://openrouter.ai/api/v1/images/models") {
                    header(HttpHeaders.Authorization, "Bearer $key")
                }
                val body = response.bodyAsText().take(MAX_CATALOG_BODY)
                if (!response.status.isSuccess()) {
                    error(providerFailure(AiProvider.OPENROUTER, response.status.value, body))
                }
                parseDataIds(body)
            }
            ImageProvider.GEMINI -> {
                val key = secureStore.apiKey("GEMINI")
                require(key.isNotBlank()) { "Connect Gemini in You → AI connection first." }
                val response = client.get("https://generativelanguage.googleapis.com/v1beta/models") {
                    header("x-goog-api-key", key)
                }
                val body = response.bodyAsText().take(MAX_CATALOG_BODY)
                if (!response.status.isSuccess()) {
                    error(providerFailure(AiProvider.GEMINI, response.status.value, body))
                }
                parseGeminiImageModels(body)
            }
        }.ifEmpty { provider.fallbackModels() }
    }

    override suspend fun generate(request: ImageGenerationRequest): Result<GeneratedImage> = runCatching {
        require(request.prompt.trim().length >= 3) { "Describe the image you want to create." }
        require(request.prompt.length <= 8_000) { "Image prompt is too long." }
        val encoded = when (request.provider) {
            ImageProvider.OPENROUTER -> generateOpenRouter(request)
            ImageProvider.GEMINI -> generateGemini(request)
        }
        require(encoded.base64.length <= MAX_BASE64_CHARS) { "The generated image is too large for this device." }
        val bytes = runCatching { Base64.decode(encoded.base64, Base64.DEFAULT) }
            .getOrElse { error("The provider returned unreadable image data.") }
        require(bytes.size in 1..MAX_IMAGE_BYTES) { "The provider returned an invalid image size." }
        writeCache(request, bytes, encoded.mimeType)
    }

    override suspend fun saveToGallery(image: GeneratedImage): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val source = File(image.filePath)
            require(source.isFile) { "This generated image is no longer available. Generate it again." }
            val extension = image.mimeType.extension()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "BYAK-${image.id}.$extension")
                put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BYAK AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Android could not create a gallery item.")
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { it.copyTo(output) }
                } ?: error("Android could not open the gallery destination.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
                uri.toString()
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        }
    }

    private suspend fun generateOpenRouter(request: ImageGenerationRequest): EncodedImage {
        val key = secureStore.apiKey("OPENROUTER")
        require(key.isNotBlank()) { "Connect OpenRouter in You → AI connection first." }
        var response = openRouterImageRequest(request, key, advanced = true)
        var body = response.bodyAsText()
        // Not every routed image model implements every normalized control. Retry a
        // 400 once with the portable core request rather than making the customer
        // guess which optional parameter a model rejected.
        if (response.status.value == 400 && providerMessage(body).contains("parameter", ignoreCase = true)) {
            response = openRouterImageRequest(request, key, advanced = false)
            body = response.bodyAsText()
        }
        if (!response.status.isSuccess()) {
            error(providerFailure(AiProvider.OPENROUTER, response.status.value, body.take(MAX_ERROR_BODY)))
        }
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("OpenRouter returned an invalid image response.")
        val item = (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: error("OpenRouter returned no image. Check that the selected model supports image output.")
        val data = item.string("b64_json") ?: error("OpenRouter returned no image bytes.")
        return EncodedImage(data, item.string("media_type") ?: "image/png")
    }

    private suspend fun openRouterImageRequest(
        request: ImageGenerationRequest,
        key: String,
        advanced: Boolean,
    ) = client.post("https://openrouter.ai/api/v1/images") {
            header(HttpHeaders.Authorization, "Bearer $key")
            header("HTTP-Referer", "https://byak.ai")
            header("X-OpenRouter-Title", "BYAK AI")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", request.model)
                put("prompt", request.prompt.trim())
                put("n", 1)
                put("aspect_ratio", request.aspectRatio)
                if (advanced) {
                    put("resolution", request.resolution.wireValue())
                    put("quality", request.quality.wireValue())
                    put("output_format", "png")
                }
            })
        }

    private suspend fun generateGemini(request: ImageGenerationRequest): EncodedImage {
        val key = secureStore.apiKey("GEMINI")
        require(key.isNotBlank()) { "Connect Gemini in You → AI connection first." }
        val response = client.post("https://generativelanguage.googleapis.com/v1beta/interactions") {
            header("x-goog-api-key", key)
            header("Api-Revision", "2026-05-20")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", request.model)
                put("input", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", request.prompt.trim())
                    })
                })
                put("response_format", buildJsonObject {
                    put("type", "image")
                    put("mime_type", "image/png")
                    put("aspect_ratio", request.aspectRatio)
                    put("image_size", request.resolution.wireValue())
                })
            })
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(providerFailure(AiProvider.GEMINI, response.status.value, body.take(MAX_ERROR_BODY)))
        }
        return findEncodedImage(json.parseToJsonElement(body))
            ?: error("Gemini returned no image. Choose a Gemini image model and try again.")
    }

    private suspend fun writeCache(
        request: ImageGenerationRequest,
        bytes: ByteArray,
        rawMimeType: String,
    ): GeneratedImage = withContext(Dispatchers.IO) {
        val mimeType = rawMimeType.takeIf { it in ALLOWED_MIME_TYPES } ?: "image/png"
        val id = UUID.randomUUID().toString()
        val directory = File(context.cacheDir, "generated-images").apply { mkdirs() }
        val file = File(directory, "$id.${mimeType.extension()}")
        file.outputStream().use { it.write(bytes) }
        GeneratedImage(
            id = id,
            filePath = file.absolutePath,
            mimeType = mimeType,
            prompt = request.prompt.trim(),
            provider = request.provider,
            model = request.model,
        )
    }

    private fun parseDataIds(body: String): List<String> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return (root["data"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("id") }
            .distinct()
    }

    private fun parseGeminiImageModels(body: String): List<String> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return (root["models"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("name")?.removePrefix("models/") }
            .filter { it.contains("image", ignoreCase = true) || it.contains("banana", ignoreCase = true) }
            .distinct()
    }

    private fun findEncodedImage(element: JsonElement): EncodedImage? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull(::findEncodedImage)
        is JsonObject -> {
            val data = element.string("data") ?: element.string("b64_json")
            val type = element.string("mime_type") ?: element.string("media_type") ?: "image/png"
            if (data != null && (element.string("type") == "image" || type.startsWith("image/"))) {
                EncodedImage(data, type)
            } else {
                element.values.firstNotNullOfOrNull(::findEncodedImage)
            }
        }
        else -> null
    }

    private data class EncodedImage(val base64: String, val mimeType: String)

    private companion object {
        const val MAX_CATALOG_BODY = 2_000_000
        const val MAX_ERROR_BODY = 8_000
        const val MAX_BASE64_CHARS = 48_000_000
        const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
        val ALLOWED_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")
    }
}

private fun ImageProvider.fallbackModels(): List<String> = when (this) {
    ImageProvider.OPENROUTER -> listOf("openai/gpt-image-2")
    ImageProvider.GEMINI -> listOf("gemini-3.1-flash-image", "gemini-3.1-flash-lite-image")
}

private fun ImageResolution.wireValue(): String = when (this) {
    ImageResolution.ONE_K -> "1K"
    ImageResolution.TWO_K -> "2K"
    ImageResolution.FOUR_K -> "4K"
}

private fun ImageQuality.wireValue(): String = when (this) {
    ImageQuality.AUTO -> "auto"
    ImageQuality.MEDIUM -> "medium"
    ImageQuality.HIGH -> "high"
}

private fun String.extension(): String = when (this) {
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    else -> "png"
}

private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
