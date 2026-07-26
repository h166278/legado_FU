package io.legado.app.help.tts

import io.legado.app.data.entities.BookCharacterTtsBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTtsBindingPolicyTest {

    private val voices = setOf("voice_a", "voice_b")

    @Test
    fun pendingAndProvisional_retryOnlyWhenEvidenceChanges() {
        val pending = binding(voiceId = null, confidence = 0.2f, signature = "evidence-1")
        val provisional = binding(voiceId = "voice_a", confidence = 0.78f, signature = "evidence-1")

        assertFalse(BookTtsBindingPolicy.shouldEvaluate(pending, voices, "evidence-1", false))
        assertTrue(BookTtsBindingPolicy.shouldEvaluate(pending, voices, "evidence-2", false))
        assertFalse(BookTtsBindingPolicy.shouldEvaluate(provisional, voices, "evidence-1", false))
        assertTrue(BookTtsBindingPolicy.shouldEvaluate(provisional, voices, "evidence-2", false))
    }

    @Test
    fun stableAndProtectedBindings_doNotAutoRetry() {
        val stable = binding(voiceId = "voice_a", confidence = 0.9f, signature = "old")
        val manual = binding(
            voiceId = "voice_a",
            confidence = 1f,
            signature = "old",
            mode = BookCharacterTtsBinding.BindingMode.MANUAL
        )

        assertFalse(BookTtsBindingPolicy.shouldEvaluate(stable, voices, "new", false))
        assertFalse(BookTtsBindingPolicy.shouldEvaluate(manual, voices, "new", true))
    }

    @Test
    fun invalidAutomaticVoice_isPendingAndCanBeReevaluated() {
        val invalid = binding(voiceId = "removed_voice", confidence = 0.95f, signature = "old")

        assertEquals(BookTtsBindingPolicy.AutoState.PENDING, BookTtsBindingPolicy.autoState(invalid, voices))
        assertTrue(BookTtsBindingPolicy.shouldEvaluate(invalid, voices, "new", false))
    }

    @Test
    fun unassignedResult_recordsEvidenceWithoutCreatingAUsableBinding() {
        val result = BookTtsBindingPolicy.resolve(
            current = null,
            newBinding = ::newBinding,
            usableVoiceIds = voices,
            evidenceSignature = "evidence-1",
            acceptedVoiceId = null,
            confidence = 0.36f,
            replaceAuto = false,
            now = 20L
        )

        assertNull(result.binding.voiceId)
        assertEquals(BookCharacterTtsBinding.BindingMode.AUTO, result.binding.bindingMode)
        assertEquals(0.36f, result.binding.autoConfidence)
        assertEquals("evidence-1", result.binding.autoEvidenceSignature)
        assertEquals(BookTtsBindingPolicy.AutoState.PENDING, BookTtsBindingPolicy.autoState(result.binding, voices))
    }

    @Test
    fun provisionalVoice_isKeptUntilDifferentVoiceHasStableEvidence() {
        val current = binding("voice_a", 0.78f, "evidence-1")
        val weakReplacement = BookTtsBindingPolicy.resolve(
            current = current,
            newBinding = ::newBinding,
            usableVoiceIds = voices,
            evidenceSignature = "evidence-2",
            acceptedVoiceId = "voice_b",
            confidence = 0.8f,
            replaceAuto = false,
            now = 20L
        )
        val stableReplacement = BookTtsBindingPolicy.resolve(
            current = weakReplacement.binding,
            newBinding = ::newBinding,
            usableVoiceIds = voices,
            evidenceSignature = "evidence-3",
            acceptedVoiceId = "voice_b",
            confidence = 0.9f,
            replaceAuto = false,
            now = 30L
        )

        assertEquals("voice_a", weakReplacement.binding.voiceId)
        assertFalse(weakReplacement.voiceChanged)
        assertEquals("voice_b", stableReplacement.binding.voiceId)
        assertTrue(stableReplacement.voiceChanged)
        assertEquals(BookTtsBindingPolicy.AutoState.STABLE, BookTtsBindingPolicy.autoState(stableReplacement.binding, voices))
    }

    @Test
    fun laterUnassignedResult_doesNotEraseUsableProvisionalVoice() {
        val current = binding("voice_a", 0.78f, "evidence-1")
        val result = BookTtsBindingPolicy.resolve(
            current = current,
            newBinding = ::newBinding,
            usableVoiceIds = voices,
            evidenceSignature = "evidence-2",
            acceptedVoiceId = null,
            confidence = 0.4f,
            replaceAuto = false,
            now = 20L
        )

        assertEquals("voice_a", result.binding.voiceId)
        assertEquals(0.78f, result.binding.autoConfidence)
    }

    private fun binding(
        voiceId: String?,
        confidence: Float,
        signature: String,
        mode: String = BookCharacterTtsBinding.BindingMode.AUTO
    ) = newBinding().copy(
        voiceId = voiceId,
        bindingMode = mode,
        autoConfidence = confidence,
        autoEvidenceSignature = signature
    )

    private fun newBinding() = BookCharacterTtsBinding(
        workKey = "work",
        targetType = BookCharacterTtsBinding.TargetType.CAST_ROLE,
        targetId = 1L,
        engineId = "engine"
    )
}
