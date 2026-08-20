package ai.byak.app

import ai.byak.app.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test fun providerRetainsOnlyMaskedDisplayValue() {
        val provider = Provider("id", "openai", "OpenAI", "••••••••1234", "gpt-4.1-mini")
        assertEquals("••••••••1234", provider.maskedKey)
    }
}

