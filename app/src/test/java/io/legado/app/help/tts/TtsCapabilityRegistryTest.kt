package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsCapabilityRegistryTest {

    @Test
    fun normalize_acceptsVersionedAndLegacyDeclarations() {
        assertEquals(
            setOf(TtsEngineCapability.SCENE_CONTEXT, TtsEngineCapability.PERFORMANCE_INSTRUCTION),
            TtsCapabilityRegistry.normalize(
                setOf("scene_context@1", "performance_instruction", "unknown_provider_flag")
            )
        )
    }

    @Test
    fun normalize_expandsCapabilityDependencies() {
        assertTrue(
            TtsCapabilityRegistry.supports(
                setOf(TtsEngineCapability.PERFORMANCE_INSTRUCTION),
                TtsEngineCapability.PERFORMANCE_INSTRUCTION
            )
        )
        assertTrue(
            TtsCapabilityRegistry.supports(
                setOf(TtsEngineCapability.PERFORMANCE_INSTRUCTION),
                TtsEngineCapability.SCENE_CONTEXT
            )
        )
        assertTrue(
            TtsCapabilityRegistry.supports(
                setOf(TtsEngineCapability.EMOTION_INTENSITY),
                TtsEngineCapability.EMOTION_INTENSITY
            )
        )
        assertTrue(
            TtsCapabilityRegistry.supports(
                setOf(TtsEngineCapability.EMOTION_INTENSITY),
                TtsEngineCapability.EMOTION
            )
        )
    }

    @Test
    fun versionedIdentity_isStableAndSorted() {
        assertEquals(
            listOf("emotion@1", "emotion_intensity@1", "style_tags@1"),
            TtsCapabilityRegistry.versioned(
                setOf("style_tags", "emotion_intensity@1", "emotion")
            )
        )
        assertTrue(TtsCapabilityRegistry.supports(setOf("style_tags@1"), "style_tags"))
    }
}
