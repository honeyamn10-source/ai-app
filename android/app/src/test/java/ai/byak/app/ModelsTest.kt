package ai.byak.app

import ai.byak.app.billing.BillingManager
import ai.byak.app.data.local.VectorCodec
import ai.byak.app.data.repository.providerFailure
import ai.byak.app.data.security.normalizeCompatibleBaseUrl
import ai.byak.app.data.security.normalizeCredential
import ai.byak.app.data.security.SecureState
import ai.byak.app.data.security.withSupportedProvider
import ai.byak.app.domain.model.AiProvider
import ai.byak.app.domain.model.ImageProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test fun vectorEncodingRoundTripsWithoutPrecisionLoss() {
        val input = floatArrayOf(0.25f, -0.75f, 1f)
        assertTrue(input.contentEquals(VectorCodec.decode(VectorCodec.encode(input))))
    }

    @Test fun cosineSimilarityRanksIdenticalVectorsFirst() {
        val input = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, VectorCodec.cosineSimilarity(input, input), 0.0001f)
    }

    @Test fun openRouterIsAFirstClassProvider() {
        assertTrue(AiProvider.entries.contains(AiProvider.OPENROUTER))
    }

    @Test fun localUniversalAndImageProvidersRemainFirstClassFeatures() {
        assertTrue(AiProvider.entries.contains(AiProvider.AUTO))
        assertTrue(AiProvider.entries.contains(AiProvider.PORTABLE_LOCAL))
        assertTrue(AiProvider.entries.contains(AiProvider.ON_DEVICE))
        assertTrue(AiProvider.entries.contains(AiProvider.NVIDIA))
        assertTrue(AiProvider.entries.contains(AiProvider.CUSTOM))
        assertEquals(AiProvider.AUTO, AiProvider.entries.first())
        assertEquals(listOf(ImageProvider.OPENROUTER, ImageProvider.GEMINI), ImageProvider.entries)
    }

    @Test fun providerErrorsDistinguishPermissionsCreditsAndInvalidCredentials() {
        assertTrue(providerFailure(AiProvider.OPENROUTER, 401, "{}").contains("rejected"))
        assertTrue(providerFailure(AiProvider.OPENROUTER, 402, "{}").contains("credits"))
        assertTrue(providerFailure(AiProvider.GEMINI, 403, "{}").contains("permissions"))
    }

    @Test fun credentialPasteFormatsAreNormalizedWithoutLeakingData() {
        val token = "sk-or-v1-1234567890abcdef"
        assertEquals(token, normalizeCredential(AiProvider.OPENROUTER, "OPENROUTER_API_KEY=\"$token\"").value)
        assertEquals(token, normalizeCredential(AiProvider.OPENROUTER, "Bearer $token").value)
        assertTrue(normalizeCredential(AiProvider.OPENROUTER, "{\"api_key\":\"$token\"}").repaired)
        assertEquals(
            "newGeminiAuthKey_1234567890",
            normalizeCredential(AiProvider.GEMINI, "{\"api_key\":\"newGeminiAuthKey_1234567890\"}").value,
        )
    }

    @Test fun providerErrorsNeverEchoSecrets() {
        val message = providerFailure(
            AiProvider.OPENROUTER,
            401,
            """{"error":{"message":"Bearer sk-or-v1-1234567890abcdef is invalid"}}""",
        )
        assertTrue(!message.contains("sk-or-v1-1234567890abcdef"))
    }

    @Test fun encryptedStateMigrationDefaultsOpenRouterKeySafely() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<SecureState>("{}")
        assertEquals("", decoded.openRouterKey)
        assertEquals("gemini-2.5-flash-lite", decoded.geminiModel)
        assertEquals("AUTO", decoded.selectedProvider)
        assertEquals("Best available", decoded.selectedModel)
    }

    @Test fun removedLocalServerSelectionMigratesToAuto() {
        val migrated = SecureState(
            selectedProvider = "OLLAMA",
            selectedModel = "deepseek-coder:6.7b",
        ).withSupportedProvider()
        assertEquals("AUTO", migrated.selectedProvider)
        assertEquals("Best available", migrated.selectedModel)
    }

    @Test fun universalEndpointNormalizationRequiresHttps() {
        assertEquals(
            "https://models.example.com/v1",
            normalizeCompatibleBaseUrl("https://models.example.com/v1/chat/completions"),
        )
        val failure = runCatching { normalizeCompatibleBaseUrl("http://models.example.com/v1") }
        assertTrue(failure.isFailure)
    }

    @Test fun playProductIdentifiersRemainStable() {
        assertEquals("byak_monthly_1", BillingManager.MONTHLY)
        assertEquals("byak_annual_10", BillingManager.ANNUAL)
    }
}
