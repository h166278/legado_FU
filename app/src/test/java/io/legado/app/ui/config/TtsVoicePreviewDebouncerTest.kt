package io.legado.app.ui.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoicePreviewDebouncerTest {

    @Test
    fun rejectsRapidRepeatedPreviewRequests() {
        val debouncer = TtsVoicePreviewDebouncer(intervalMillis = 600L)

        assertTrue(debouncer.tryAcquire("voice-a", 1_000L))
        assertFalse(debouncer.tryAcquire("voice-a", 1_599L))
        assertTrue(debouncer.tryAcquire("voice-a", 1_600L))
    }

    @Test
    fun rejectedRequestDoesNotExtendDebounceWindow() {
        val debouncer = TtsVoicePreviewDebouncer(intervalMillis = 600L)

        assertTrue(debouncer.tryAcquire("voice-a", 2_000L))
        assertFalse(debouncer.tryAcquire("voice-a", 2_400L))
        assertTrue(debouncer.tryAcquire("voice-a", 2_600L))
    }

    @Test
    fun switchingVoiceIsNotBlockedByDebounce() {
        val debouncer = TtsVoicePreviewDebouncer(intervalMillis = 600L)

        assertTrue(debouncer.tryAcquire("voice-a", 3_000L))
        assertTrue(debouncer.tryAcquire("voice-b", 3_100L))
    }

    @Test
    fun completionGuardRecognizesTimelineEnd() {
        assertFalse(TtsVoicePreviewCompletionGuard.isAtTimelineEnd(9_900L, 10_000L))
        assertTrue(TtsVoicePreviewCompletionGuard.isAtTimelineEnd(9_950L, 10_000L))
        assertTrue(TtsVoicePreviewCompletionGuard.isAtTimelineEnd(10_000L, 10_000L))
        assertFalse(TtsVoicePreviewCompletionGuard.isAtTimelineEnd(0L, 0L))
    }
}
