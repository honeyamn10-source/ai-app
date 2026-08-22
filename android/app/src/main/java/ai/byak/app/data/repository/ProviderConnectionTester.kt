package ai.byak.app.data.repository

import ai.byak.app.BuildConfig
import ai.byak.app.data.localai.validateOllamaEndpoint
import ai.byak.app.data.localai.OnDeviceModelManager
import ai.byak.app.data.security.normalizeCredential
import ai.byak.app.domain.model.AiProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ProviderConnectionReport(
    val provider: AiProvider,
    val resolvedModel: String,
    val availableModels: List<String>,
    val message: String,
    val normalizedCredential: String = "",
)

@Singleton
class ProviderConnectionTester @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
    private val onDevice: OnDeviceModelManager,
) {
    suspend fun test(
        provider: AiProvider,
        apiKey: String,
        model: String,
        endpoint: String = "",
    ): Result<ProviderConnectionReport> =
        runCatching {
            if (provider == AiProvider.ON_DEVICE) {
                val prepared = onDevice.prepare()
                return@runCatching ProviderConnectionReport(
                    provider = provider,
                    resolvedModel = prepared.modelName,
                    availableModels = listOf(prepared.modelName),
                    message = prepared.message,
                )
            }

            if (provider == AiProvider.OLLAMA) {
                return@runCatching testOllama(endpoint, model)
            }

            val normalized = normalizeCredential(provider, apiKey)
            val key = normalized.value
            require(key.isNotBlank()) { "Enter an API key first." }
            val report = when (provider) {
                AiProvider.OPENROUTER -> testOpenRouter(key, model)
                AiProvider.GEMINI -> testGemini(key, model)
                AiProvider.OPENAI -> testOpenAi(key, model)
                AiProvider.ANTHROPIC -> testAnthropic(key, model)
                AiProvider.OLLAMA, AiProvider.ON_DEVICE -> error("Handled above")
            }
            report.copy(
                normalizedCredential = key,
                message = report.message + if (normalized.repaired) {
                    " BYAK safely extracted the key value from the text you pasted."
                } else "",
            )
        }

    private suspend fun testOpenRouter(key: String, requestedModel: String): ProviderConnectionReport {
        val catalogBody = requireSuccess(
            AiProvider.OPENROUTER,
            client.get("https://openrouter.ai/api/v1/models?output_modalities=text&limit=1000") {
                header(HttpHeaders.Authorization, "Bearer $key")
            },
        )
        val models = parseDataModels(catalogBody)
        val resolved = resolveModel(
            requested = requestedModel,
            available = models,
            aliases = setOf("openrouter/auto"),
            preferred = listOf("openrouter/auto"),
        )
        val probe = client.post("https://openrouter.ai/api/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $key")
            header("HTTP-Referer", "https://byak.ai")
            header("X-OpenRouter-Title", "BYAK AI")
            contentType(ContentType.Application.Json)
            setBody(openAiProbeBody(resolved, "max_tokens"))
        }
        val probeBody = probe.bodyAsText()
        if (!probe.status.isSuccess() && probe.status.value != 429) {
            error(providerFailure(AiProvider.OPENROUTER, probe.status.value, probeBody))
        }
        val note = if (probe.status.value == 429) {
            " The key is valid; generation is temporarily rate-limited."
        } else {
            ""
        }
        return report(AiProvider.OPENROUTER, requestedModel, resolved, models, note)
    }

    private suspend fun testOllama(rawEndpoint: String, requestedModel: String): ProviderConnectionReport {
        val base = validateOllamaEndpoint(rawEndpoint, allowPrivateHttp = BuildConfig.DEBUG).baseUrl
        return try {
            val catalog = requireSuccess(
                AiProvider.OLLAMA,
                client.get("$base/api/tags"),
            )
            val models = parseOllamaModels(catalog)
            val cleanRequested = requestedModel.trim()
            val resolved = when {
                cleanRequested in models -> cleanRequested
                models.isNotEmpty() -> models.first()
                else -> error("Ollama is reachable but has no installed model. Run ollama pull deepseek-coder:6.7b on the computer first.")
            }
            val probe = client.post("$base/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("model", resolved)
                    put("stream", false)
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "user"); put("content", "Reply OK") })
                    })
                    put("options", buildJsonObject { put("num_predict", 8) })
                })
            }
            val probeBody = probe.bodyAsText()
            if (!probe.status.isSuccess()) {
                error(providerFailure(AiProvider.OLLAMA, probe.status.value, probeBody))
            }
            report(AiProvider.OLLAMA, requestedModel, resolved, models).copy(
                message = "Local Ollama is connected. $resolved is ready; prompts stay on your Wi-Fi network.",
            )
        } catch (error: Throwable) {
            val known = error.message.orEmpty()
            if (known.contains("Ollama", ignoreCase = true) && !known.contains("connect", ignoreCase = true)) throw error
            throw IllegalStateException(
                "Could not reach Ollama at $base. On the computer, start Ollama for your Wi-Fi network, allow port 11434 in the private firewall zone, and use the computer's Wi-Fi IP—not 10.0.2.2.",
                error,
            )
        }
    }

    private suspend fun testGemini(key: String, requestedModel: String): ProviderConnectionReport {
        val catalogBody = requireSuccess(
            AiProvider.GEMINI,
            client.get("https://generativelanguage.googleapis.com/v1beta/models") {
                header("x-goog-api-key", key)
            },
        )
        val models = parseGeminiModels(catalogBody)
        val resolved = resolveModel(
            requested = requestedModel,
            available = models,
            preferred = listOf(
                "gemini-3.1-flash-lite",
                "gemini-2.5-flash-lite",
                "gemini-2.5-flash",
            ),
        )
        val probe = client.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$resolved:generateContent",
        ) {
            header("x-goog-api-key", key)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", "Reply OK") }) })
                    })
                })
                put("generationConfig", buildJsonObject { put("maxOutputTokens", 8) })
            })
        }
        val probeBody = probe.bodyAsText()
        if (!probe.status.isSuccess() && probe.status.value != 429) {
            error(providerFailure(AiProvider.GEMINI, probe.status.value, probeBody))
        }
        val note = if (probe.status.value == 429) {
            " The key is valid; its Gemini quota is temporarily exhausted."
        } else {
            ""
        }
        return report(AiProvider.GEMINI, requestedModel, resolved, models, note)
    }

    private suspend fun testOpenAi(key: String, requestedModel: String): ProviderConnectionReport {
        val catalogBody = requireSuccess(
            AiProvider.OPENAI,
            client.get("https://api.openai.com/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $key")
            },
        )
        val models = parseDataModels(catalogBody)
        val resolved = resolveModel(
            requested = requestedModel,
            available = models,
            preferred = listOf("gpt-5-mini", "gpt-4.1-mini"),
        )
        return report(AiProvider.OPENAI, requestedModel, resolved, models)
    }

    private suspend fun testAnthropic(key: String, requestedModel: String): ProviderConnectionReport {
        val catalogBody = requireSuccess(
            AiProvider.ANTHROPIC,
            client.get("https://api.anthropic.com/v1/models") {
                header("x-api-key", key)
                header("anthropic-version", "2023-06-01")
            },
        )
        val models = parseDataModels(catalogBody)
        val resolved = resolveModel(
            requested = requestedModel,
            available = models,
            preferred = listOf("claude-sonnet-4-5", "claude-3-5-haiku-latest"),
        )
        return report(AiProvider.ANTHROPIC, requestedModel, resolved, models)
    }

    private suspend fun requireSuccess(provider: AiProvider, response: HttpResponse): String {
        val body = response.bodyAsText().take(MAX_BODY)
        if (!response.status.isSuccess()) {
            error(providerFailure(provider, response.status.value, body))
        }
        return body
    }

    private fun report(
        provider: AiProvider,
        requested: String,
        resolved: String,
        models: List<String>,
        note: String = "",
    ): ProviderConnectionReport {
        val changed = requested.trim().isNotBlank() && requested.trim() != resolved
        val selection = if (changed) {
            " The requested model is unavailable, so BYAK selected $resolved."
        } else {
            " $resolved is ready."
        }
        return ProviderConnectionReport(
            provider = provider,
            resolvedModel = resolved,
            availableModels = models.take(100),
            message = provider.displayName() + " is connected." + selection + note,
        )
    }

    private fun parseDataModels(body: String): List<String> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return (root["data"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("id") }
            .distinct()
            .sorted()
    }

    private fun parseGeminiModels(body: String): List<String> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return (root["models"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val methods = (item["supportedGenerationMethods"] as? JsonArray).orEmpty()
                .mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            if ("generateContent" !in methods) null else item.string("name")?.removePrefix("models/")
        }.distinct().sorted()
    }

    private fun parseOllamaModels(body: String): List<String> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return (root["models"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("name") }
            .distinct()
            .sorted()
    }

    private fun resolveModel(
        requested: String,
        available: List<String>,
        aliases: Set<String> = emptySet(),
        preferred: List<String>,
    ): String {
        val clean = requested.trim().removePrefix("models/")
        if (clean in aliases || clean in available) return clean
        return preferred.firstOrNull { it in aliases || it in available }
            ?: available.firstOrNull()
            ?: clean.takeIf(String::isNotBlank)
            ?: error("This provider returned no compatible text-generation models.")
    }

    private fun openAiProbeBody(model: String, tokenField: String): JsonObject = buildJsonObject {
        put("model", model)
        put(tokenField, 8)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", "Reply OK")
            })
        })
    }

    private companion object {
        const val MAX_BODY = 1_000_000
    }
}

private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
