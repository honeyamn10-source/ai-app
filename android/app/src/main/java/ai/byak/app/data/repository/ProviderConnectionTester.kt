package ai.byak.app.data.repository

import ai.byak.app.data.localai.OnDeviceAvailability
import ai.byak.app.data.localai.OnDeviceModelManager
import ai.byak.app.data.security.SecureStore
import ai.byak.app.data.security.normalizeCompatibleBaseUrl
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
    val normalizedBaseUrl: String = "",
)

@Singleton
class ProviderConnectionTester @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
    private val onDevice: OnDeviceModelManager,
    private val secureStore: SecureStore,
) {
    suspend fun test(
        provider: AiProvider,
        apiKey: String,
        model: String,
        baseUrl: String = "",
    ): Result<ProviderConnectionReport> = runCatching {
        when (provider) {
            AiProvider.AUTO -> testAuto()
            AiProvider.ON_DEVICE -> {
                val prepared = onDevice.prepare()
                ProviderConnectionReport(
                    provider = provider,
                    resolvedModel = prepared.modelName,
                    availableModels = listOf(prepared.modelName),
                    message = prepared.message,
                )
            }
            else -> {
                val normalized = normalizeCredential(provider, apiKey)
                val key = normalized.value
                require(key.isNotBlank()) { "Enter an API key first." }
                val report = when (provider) {
                    AiProvider.OPENROUTER -> testCompatible(
                        provider, key, model, secureStore.baseUrl(provider),
                        listOf("openrouter/auto"),
                        aliases = setOf("openrouter/auto"),
                    )
                    AiProvider.GEMINI -> testGemini(key, model)
                    AiProvider.OPENAI -> testCompatible(
                        provider, key, model, secureStore.baseUrl(provider),
                        listOf("gpt-5-mini", "gpt-4.1-mini"),
                    )
                    AiProvider.ANTHROPIC -> testAnthropic(key, model)
                    AiProvider.NVIDIA -> testCompatible(
                        provider, key, model, secureStore.baseUrl(provider),
                        listOf("meta/llama-3.1-70b-instruct"),
                    )
                    AiProvider.GROQ -> testCompatible(
                        provider, key, model, secureStore.baseUrl(provider),
                        listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
                    )
                    AiProvider.MISTRAL -> testCompatible(
                        provider, key, model, secureStore.baseUrl(provider),
                        listOf("mistral-small-latest", "mistral-medium-latest"),
                    )
                    AiProvider.DEEPSEEK -> testCompatible(
                        provider, key, model, secureStore.baseUrl(provider),
                        listOf("deepseek-chat", "deepseek-reasoner"),
                    )
                    AiProvider.CUSTOM -> {
                        val cleanBase = normalizeCompatibleBaseUrl(baseUrl)
                        testCompatible(provider, key, model, cleanBase, emptyList())
                            .copy(normalizedBaseUrl = cleanBase)
                    }
                    AiProvider.AUTO, AiProvider.ON_DEVICE -> error("Handled above")
                }
                report.copy(
                    normalizedCredential = key,
                    message = report.message + if (normalized.repaired) {
                        " BYAK safely extracted the key value from the text you pasted."
                    } else "",
                )
            }
        }
    }

    private suspend fun testAuto(): ProviderConnectionReport {
        val localStatus = runCatching { onDevice.status() }.getOrDefault(OnDeviceAvailability.UNSUPPORTED)
        val cloud = secureStore.configuredCloudProvider()
        val message = when {
            localStatus == OnDeviceAvailability.AVAILABLE ->
                "Auto is ready. BYAK will use private Phone AI first and cloud only when you explicitly select it."
            cloud != null ->
                "Auto is ready. Phone AI is unavailable on this device, so BYAK will use ${cloud.provider.displayName()}."
            else ->
                "Auto is selected. This phone does not currently support Phone AI; connect any cloud provider below and BYAK will use it automatically."
        }
        return ProviderConnectionReport(
            provider = AiProvider.AUTO,
            resolvedModel = SecureStore.AUTO_MODEL,
            availableModels = emptyList(),
            message = message,
        )
    }

    private suspend fun testCompatible(
        provider: AiProvider,
        key: String,
        requestedModel: String,
        baseUrl: String,
        preferred: List<String>,
        aliases: Set<String> = emptySet(),
    ): ProviderConnectionReport {
        val cleanBase = baseUrl.trimEnd('/')
        val catalog = client.get("$cleanBase/models") {
            header(HttpHeaders.Authorization, "Bearer $key")
        }
        val models = when {
            catalog.status.isSuccess() -> parseDataModels(catalog.bodyAsText().take(MAX_BODY))
            catalog.status.value == 404 && requestedModel.isNotBlank() -> emptyList()
            else -> error(providerFailure(provider, catalog.status.value, catalog.bodyAsText().take(MAX_BODY)))
        }
        val resolved = resolveModel(requestedModel, models, aliases, preferred)
        val probe = client.post("$cleanBase/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $key")
            if (provider == AiProvider.OPENROUTER) {
                header("HTTP-Referer", "https://byak.site.je")
                header("X-OpenRouter-Title", "BYAK AI")
            }
            contentType(ContentType.Application.Json)
            setBody(openAiProbeBody(resolved))
        }
        val probeBody = probe.bodyAsText().take(MAX_BODY)
        if (!probe.status.isSuccess() && probe.status.value != 429) {
            error(providerFailure(provider, probe.status.value, probeBody))
        }
        val note = if (probe.status.value == 429) {
            " The key is valid; generation is temporarily rate-limited."
        } else ""
        return report(provider, requestedModel, resolved, models, note)
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
                put("generationConfig", buildJsonObject { put("maxOutputTokens", 32) })
            })
        }
        val probeBody = probe.bodyAsText().take(MAX_BODY)
        if (!probe.status.isSuccess() && probe.status.value != 429) {
            error(providerFailure(AiProvider.GEMINI, probe.status.value, probeBody))
        }
        val note = if (probe.status.value == 429) {
            " The key is valid; its Gemini quota is temporarily exhausted."
        } else ""
        return report(AiProvider.GEMINI, requestedModel, resolved, models, note)
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
        if (!response.status.isSuccess()) error(providerFailure(provider, response.status.value, body))
        return body
    }

    private fun report(
        provider: AiProvider,
        requested: String,
        resolved: String,
        models: List<String>,
        note: String = "",
    ): ProviderConnectionReport {
        val changed = requested.trim().removePrefix("models/").isNotBlank() &&
            requested.trim().removePrefix("models/") != resolved
        val selection = if (changed) {
            " The requested model is unavailable, so BYAK selected $resolved."
        } else {
            " $resolved is ready."
        }
        return ProviderConnectionReport(
            provider = provider,
            resolvedModel = resolved,
            availableModels = models.take(MAX_MODELS),
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
            ?: preferred.firstOrNull()
            ?: clean.takeIf(String::isNotBlank)
            ?: error("Enter a model ID or use a provider that exposes a model catalog.")
    }

    private fun openAiProbeBody(model: String): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", 16)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", "Reply OK")
            })
        })
    }

    private companion object {
        const val MAX_BODY = 1_000_000
        const val MAX_MODELS = 250
    }
}

private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
