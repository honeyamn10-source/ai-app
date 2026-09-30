package ai.byak.app

import ai.byak.app.data.local.Gateway
import ai.byak.app.data.local.pickModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderKeysTest {
    private val openRouter = listOf("anthropic/claude-sonnet-5", "cohere/north-mini-code:free", "google/gemma-3-27b-it:free", "meta-llama/llama-3.3-70b-instruct:free", "openai/gpt-4.1-mini", "openai/gpt-4o")
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
        assertEquals("google/gemma-3-27b-it:free", pickModel("openai/gpt-4.1-mini", suggested, Gateway.KeyCheck(openRouter, freeOnly = true)))
        assertEquals("google/gemma-3-27b-it:free", pickModel("google/gemma-3-27b-it:free", suggested, Gateway.KeyCheck(openRouter, freeOnly = true)))
    }

    @Test fun otherProvidersKeepTheirDefault() {
        assertEquals("gemini-2.5-flash", pickModel("gemini-2.5-flash", listOf("gemini-2.5-flash"), Gateway.KeyCheck(listOf("gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-pro"))))
    }

    /** The real OpenRouter free list, captured live on 2026-09-30. */
    private val liveFree = listOf("cohere/north-mini-code:free", "dots-studio/dots-3-note-preview:free", "google/gemma-4-26b-a4b-it:free", "google/gemma-4-31b-it:free", "inclusionai/ling-3.0-flash-sante:free", "liquid/lfm-2.5-2.6b:free", "nvidia/nemotron-3.5-content-safety:free", "nvidia/nemotron-3.5-lightning:free", "nvidia/nemotron-3-super-120b-a12b:free", "openai/gpt-4.1-mini", "poolside/laguna-s-2.1:free", "qwen/qwen3.8-27b:free")

    @Test fun freeKeyNeverGetsASafetyOrCodeModel() {
        assertEquals("google/gemma-4-31b-it:free", pickModel("openai/gpt-4.1-mini", suggested, Gateway.KeyCheck(liveFree, freeOnly = true)))
        assertEquals("qwen/qwen3.8-27b:free", pickModel("", suggested, Gateway.KeyCheck(liveFree.filterNot { it.startsWith("google/") }, freeOnly = true)))
    }

    @Test fun userChosenFreeModelIsKept() {
        assertEquals("liquid/lfm-2.5-2.6b:free", pickModel("liquid/lfm-2.5-2.6b:free", suggested, Gateway.KeyCheck(liveFree, freeOnly = true)))
        assertEquals("poolside/laguna-s-2.1:free", pickModel("poolside/laguna-s-2.1:free", suggested, Gateway.KeyCheck(liveFree, freeOnly = true)))
    }
}
