package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSpeedPolicyTest {

    @Test
    fun `playback rate follows listening preference`() {
        assertEquals(0.5f, TtsSpeedPolicy.playbackRate(0), 0.001f)
        assertEquals(1.0f, TtsSpeedPolicy.playbackRate(5), 0.001f)
        assertEquals(1.5f, TtsSpeedPolicy.playbackRate(10), 0.001f)
        assertEquals(5.0f, TtsSpeedPolicy.playbackRate(45), 0.001f)
        assertEquals("1.5x", TtsSpeedPolicy.playbackLabel(10))
    }

    @Test
    fun `script synthesis speed is independent from playback preference`() {
        val engine = TtsEngineSetting(
            id = "test",
            name = "Test",
            type = TtsEngineType.SCRIPT,
            defaultSpeed = 50,
            runtimeSpeed = 42
        )

        assertEquals(42, TtsSpeedPolicy.synthesisSpeed(engine))
        TtsSpeedPolicy.playbackRate(10)
        assertEquals(42, TtsSpeedPolicy.synthesisSpeed(engine))
    }
}
