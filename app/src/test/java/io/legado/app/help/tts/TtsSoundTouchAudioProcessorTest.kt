package io.legado.app.help.tts

import androidx.media3.common.PlaybackParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSoundTouchAudioProcessorTest {

    @Test
    fun `chain applies tempo without changing pitch`() {
        val chain = TtsSoundTouchAudioProcessorChain()
        val parameters = PlaybackParameters(1.5f, 1f)

        assertEquals(parameters, chain.applyPlaybackParameters(parameters))
        assertTrue(chain.audioProcessors.single().isActive)
        assertEquals(1_500_000L, chain.getMediaDuration(1_000_000L))
    }

    @Test
    fun `processor stays inactive at normal speed`() {
        val chain = TtsSoundTouchAudioProcessorChain()

        chain.applyPlaybackParameters(PlaybackParameters.DEFAULT)

        assertFalse(chain.audioProcessors.single().isActive)
        assertEquals(1_000_000L, chain.getMediaDuration(1_000_000L))
    }
}
