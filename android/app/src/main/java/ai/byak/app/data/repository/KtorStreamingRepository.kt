package ai.byak.app.data.repository

import ai.byak.app.data.localai.OnDeviceAvailability
import ai.byak.app.data.localai.OnDeviceModelManager
import ai.byak.app.data.localai.PortableLocalModelManager
import ai.byak.app.data.localai.PortableModelStatus
import ai.byak.app.data.security.SecureStore
import ai.byak.app.domain.model.AiProvider
import ai.byak.app.domain.model.MessageRole
import ai.byak.app.domain.repository.StreamChunk
import ai.byak.app.domain.repository.StreamingRepository
import ai.byak.app.domain.repository.StreamingRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class KtorStreamingRepository @Inject constructor(
    private val client: HttpClient,
    private val secureStore: SecureStore,
    private val json: Json,
    private val onDevice: OnDeviceModelManager,
    private val portableLocal: PortableLocalModelManager,
) : StreamingRepository {
    override fun stream(request: StreamingRequest): Flow<StreamChunk> = flow {
        val effective = resolveRequest(request)
        when (effective.provider) {
            AiProvider.ON_DEVICE -> {
                onDevice.stream(effective).collect { emit(it) }
                return@flow
            }
            AiProvider.PORTABLE_LOCAL -> {
                portableLocal.stream(effective).collect { emit(it) }
                return@flow
            }
            else -> Unit
        }

        val apiKey = secureStore.apiKey(effective.provider.name)
        require(apiKey.isNotBlank()) {
            "Add your ${effective.provider.displayName()} API key in You → AI connection."
        }

        var retry = 0
        var emittedAny = false
        while (true) {
            try {
                val fullText = StringBuilder()
                val endpoint = endpoint(effective, apiKey)
                client.preparePost(endpoint.url) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Text.EventStream)
                    endpoint.headers.forEach { (name, value) -> header(name, value) }
                    setBody(endpoint.body)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        val detail = response.bodyAsText().take(2_000)
                        if (response.status.value == 429 || response.status.value >= 500) {
                            val retryAfter = response.headers[HttpHeaders.RetryAfter]
                                ?.toLongOrNull()
                                ?.coerceIn(1, 60)
                                ?.times(1_000)
                            throw RetryableStreamException(
                                response.status.value,
                                providerFailure(effective.provider, response.status.value, detail),
                                retryAfter,
                            )
                        }
                        error(providerFailure(effective.provider, response.status.value, detail))
                    }

                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trimStart()
                        if (data.isBlank() || data == "[DONE]") continue
                        val eventError = providerMessage(data)
                            .takeIf { data.contains("\"error\"") && it.isNotBlank() }
                        if (eventError != null) {
                            error(effective.provider.displayName() + " stream failed: " + eventError)
                        }
                        val delta = parseDelta(effective.provider, data)
                        if (!delta.isNullOrEmpty()) {
                            emittedAny = true
                            fullText.append(delta)
                            emit(StreamChunk.Delta(delta))
                        }
                    }
                }
                emit(StreamChunk.Completed(fullText.toString()))
                return@flow
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (retryable: RetryableStreamException) {
                if (emittedAny || retry >= MAX_RETRIES) throw retryable
                val exponential = INITIAL_BACKOFF_MS * (1L shl retry)
                val jitter = Random.nextLong(0, 400)
                delay(retryable.retryAfterMillis ?: (min(MAX_BACKOFF_MS, exponential) + jitter))
                retry++
            }
        }
    }

    private suspend fun resolveRequest(request: StreamingRequest): StreamingRequest {
        if (request.provider != AiProvider.AUTO && request.provider != AiProvider.ON_DEVICE) return request
        val portableReady = portableLocal.state().status == PortableModelStatus.READY
        if (request.provider == AiProvider.AUTO && portableReady) {
            return request.copy(
                provider = AiProvider.PORTABLE_LOCAL,
                model = SecureStore.PORTABLE_LOCAL_MODEL,
            )
        }
        val phoneReady = runCatching { onDevice.status() }
            .getOrDefault(OnDeviceAvailability.UNSUPPORTED) == OnDeviceAvailability.AVAILABLE
        if (phoneReady) {
            return request.copy(provider = AiProvider.ON_DEVICE, model = SecureStore.ON_DEVICE_MODEL)
        }
        if (portableReady) {
            return request.copy(
                provider = AiProvider.PORTABLE_LOCAL,
                model = SecureStore.PORTABLE_LOCAL_MODEL,
            )
        }
        val cloud = secureStore.configuredCloudProvider()
            ?: error(
                "This phone cannot run Gemini Nano. Download Portable Local AI or connect OpenRouter, Gemini, NVIDIA, Groq, Mistral, DeepSeek, OpenAI, Claude, or a Universal API in You → AI connection.",
            )
        return request.copy(provider = cloud.provider, model = cloud.model)
    }

    private fun endpoint(request: StreamingRequest, apiKey: String): Endpoint = when (request.provider) {
        AiProvider.OPENAI -> openAiCompatibleEndpoint(
            url = "${secureStore.baseUrl(AiProvider.OPENAI)}/chat/completions",
            apiKey = apiKey,
            request = request,
            headers = emptyMap(),
            tokenField = "max_completion_tokens",
            includeUsage = true,
        )
        AiProvider.OPENROUTER -> openAiCompatibleEndpoint(
            url = "${secureStore.baseUrl(AiProvider.OPENROUTER)}/chat/completions",
            apiKey = apiKey,
            request = request,
            headers = mapOf(
                "HTTP-Referer" to "https://byak.site.je",
                "X-OpenRouter-Title" to "BYAK AI",
            ),
            tokenField = "max_tokens",
            includeUsage = true,
        )
        AiProvider.NVIDIA, AiProvider.GROQ, AiProvider.MISTRAL, AiProvider.DEEPSEEK, AiProvider.CUSTOM ->
            openAiCompatibleEndpoint(
                url = "${secureStore.baseUrl(request.provider)}/chat/completions",
                apiKey = apiKey,
                request = request,
                headers = emptyMap(),
                tokenField = "max_tokens",
                includeUsage = false,
            )
        AiProvider.ANTHROPIC -> Endpoint(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body = buildJsonObject {
                put("model", request.model)
                put("stream", true)
                put("max_tokens", request.maxTokens)
                request.systemPrompt?.takeIf(String::isNotBlank)?.let { put("system", it) }
                put("messages", buildJsonArray {
                    request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                        add(buildJsonObject {
                            put("role", if (message.role == MessageRole.ASSISTANT) "assistant" else "user")
                            put("content", message.content)
                        })
                    }
                })
            },
        )
        AiProvider.GEMINI -> Endpoint(
            url = "https://generativelanguage.googleapis.com/v1beta/models/${urlEncode(request.model)}:streamGenerateContent?alt=sse",
            headers = mapOf("x-goog-api-key" to apiKey),
            body = buildJsonObject {
                request.systemPrompt?.takeIf(String::isNotBlank)?.let { prompt ->
                    put("systemInstruction", buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                    })
                }
                put("contents", buildJsonArray {
                    request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                        add(buildJsonObject {
                            put("role", if (message.role == MessageRole.ASSISTANT) "model" else "user")
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", message.content) }) })
                        })
                    }
                })
                put("generationConfig", buildJsonObject { put("maxOutputTokens", request.maxTokens) })
            },
        )
        AiProvider.AUTO, AiProvider.PORTABLE_LOCAL, AiProvider.ON_DEVICE ->
            error("Local and automatic requests do not use a network endpoint")
    }

    private fun openAiCompatibleEndpoint(
        url: String,
        apiKey: String,
        request: StreamingRequest,
        headers: Map<String, String>,
        tokenField: String,
        includeUsage: Boolean,
    ) = Endpoint(
        url = url,
        headers = mapOf(HttpHeaders.Authorization to "Bearer $apiKey") + headers,
        body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            if (includeUsage) put("stream_options", buildJsonObject { put("include_usage", true) })
            put(tokenField, request.maxTokens)
            put("messages", buildJsonArray {
                request.systemPrompt?.takeIf(String::isNotBlank)?.let { prompt ->
                    add(buildJsonObject { put("role", "system"); put("content", prompt) })
                }
                request.messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role.wireName())
                        put("content", message.content)
                    })
                }
            })
        },
    )

    private fun parseDelta(provider: AiProvider, data: String): String? {
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        return when (provider) {
            AiProvider.OPENAI,
            AiProvider.OPENROUTER,
            AiProvider.NVIDIA,
            AiProvider.GROQ,
            AiProvider.MISTRAL,
            AiProvider.DEEPSEEK,
            AiProvider.CUSTOM -> root.array("choices")?.firstObject()
                ?.objectValue("delta")?.string("content")
            AiProvider.ANTHROPIC -> root.objectValue("delta")?.string("text")
            AiProvider.GEMINI -> root.array("candidates")?.firstObject()
                ?.objectValue("content")?.array("parts")?.firstObject()?.string("text")
            AiProvider.AUTO, AiProvider.PORTABLE_LOCAL, AiProvider.ON_DEVICE -> null
        }
    }

    private fun MessageRole.wireName(): String = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private data class Endpoint(
        val url: String,
        val headers: Map<String, String>,
        val body: JsonObject,
    )

    private class RetryableStreamException(
        code: Int,
        detail: String,
        val retryAfterMillis: Long?,
    ) : IllegalStateException(detail.ifBlank { "Temporary provider error $code" })

    private companion object {
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 8_000L
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonArray.firstObject(): JsonObject? = firstOrNull() as? JsonObject
