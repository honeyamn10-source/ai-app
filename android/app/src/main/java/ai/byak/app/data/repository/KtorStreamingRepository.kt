package ai.byak.app.data.repository

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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class KtorStreamingRepository @Inject constructor(
    private val client: HttpClient,
    private val secureStore: SecureStore,
    private val json: Json,
) : StreamingRepository {
    override fun stream(request: StreamingRequest): Flow<StreamChunk> = flow {
        val apiKey = secureStore.apiKey(request.provider.name)
        require(apiKey.isNotBlank()) { "Add your ${request.provider.displayName()} API key first" }

        var retry = 0
        var emittedAny = false
        while (true) {
            try {
                val fullText = StringBuilder()
                val endpoint = endpoint(request, apiKey)
                client.preparePost(endpoint.url) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Text.EventStream)
                    endpoint.headers.forEach { (name, value) -> header(name, value) }
                    setBody(endpoint.body)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        val detail = response.bodyAsText().take(2_000)
                        if (response.status.value == 429 || response.status.value >= 500) {
                            throw RetryableStreamException(response.status.value, detail)
                        }
                        error("${request.provider.displayName()} returned ${response.status.value}: ${detail.ifBlank { "Request failed" }}")
                    }

                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trimStart()
                        if (data.isBlank() || data == "[DONE]") continue
                        val delta = parseDelta(request.provider, data)
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
                delay(min(MAX_BACKOFF_MS, exponential) + jitter)
                retry++
            }
        }
    }

    private fun endpoint(request: StreamingRequest, apiKey: String): Endpoint = when (request.provider) {
        AiProvider.OPENAI -> Endpoint(
            url = "https://api.openai.com/v1/chat/completions",
            headers = mapOf(HttpHeaders.Authorization to "Bearer $apiKey"),
            body = buildJsonObject {
                put("model", request.model)
                put("stream", true)
                put("stream_options", buildJsonObject { put("include_usage", true) })
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
            url = "https://generativelanguage.googleapis.com/v1beta/models/${urlEncode(request.model)}:streamGenerateContent?alt=sse&key=${urlEncode(apiKey)}",
            headers = emptyMap(),
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
    }

    private fun parseDelta(provider: AiProvider, data: String): String? {
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        return when (provider) {
            AiProvider.OPENAI -> root.array("choices")?.firstObject()
                ?.objectValue("delta")?.string("content")
            AiProvider.ANTHROPIC -> root.objectValue("delta")?.string("text")
            AiProvider.GEMINI -> root.array("candidates")?.firstObject()
                ?.objectValue("content")?.array("parts")?.firstObject()?.string("text")
        }
    }

    private fun MessageRole.wireName(): String = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
    }

    private fun AiProvider.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)
    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private data class Endpoint(val url: String, val headers: Map<String, String>, val body: JsonObject)
    private class RetryableStreamException(code: Int, detail: String) :
        IllegalStateException("Temporary provider error $code: ${detail.take(400)}")

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
