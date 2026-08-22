package ai.byak.app

import ai.byak.app.billing.BillingManager
import ai.byak.app.data.local.VectorCodec
import ai.byak.app.data.security.SecureState
import ai.byak.app.domain.model.AiProvider
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

    @Test fun encryptedStateMigrationDefaultsOpenRouterKeySafely() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<SecureState>("{}")
        assertEquals("", decoded.openRouterKey)
    }

    @Test fun playProductIdentifiersRemainStable() {
        assertEquals("byak_monthly_1", BillingManager.MONTHLY)
        assertEquals("byak_annual_10", BillingManager.ANNUAL)
    }
}
