package ai.byak.app

import ai.byak.app.data.local.Gateway
import ai.byak.app.data.local.pickModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderKeysTest {
    private val openRouter = listOf("anthropic/claude-sonnet-5", "deepseek/deepseek-chat-v3-0324:free", "google/gemma-3-27b-it:free", "meta-llama/llama-3.3-70b-instruct:free", "openai/gpt-4.1-mini", "openai/gpt-4o")
    private val suggested = listOf("openai/gpt-4.1-mini", "google/gemini-2.5-flash")

    @Test fun keyWithCreditsKeepsTheTypedModel() {
        assertEquals("openai/gpt-4o", pickModel("openai/gpt-4o", suggested, Gateway.KeyCheck(openRouter)))
    }

    @Test fun shortModelNamesMatchTheProviderPrefix() {
        assertEquals("openai/gpt-4o", pickModel("gpt-4o", suggested, Gateway.KeyCheck(openRouter)))
    }

    @Test fun unknownModelFallsBackToASuggestedOne() {
        assertEquals("openai/gpt-4.1-mini", pickModel("not-a-model", suggested, Gateway.KeyCheck(openRouter)))
    }

    @Test fun keyWithoutCreditsGetsAFreeModel() {
        assertEquals("deepseek/deepseek-chat-v3-0324:free", pickModel("openai/gpt-4.1-mini", suggested, Gateway.KeyCheck(openRouter, freeOnly = true)))
        assertEquals("google/gemma-3-27b-it:free", pickModel("google/gemma-3-27b-it:free", suggested, Gateway.KeyCheck(openRouter, freeOnly = true)))
    }

    @Test fun otherProvidersKeepTheirDefault() {
        assertEquals("gemini-2.5-flash", pickModel("gemini-2.5-flash", listOf("gemini-2.5-flash"), Gateway.KeyCheck(listOf("gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-pro"))))
    }
}
