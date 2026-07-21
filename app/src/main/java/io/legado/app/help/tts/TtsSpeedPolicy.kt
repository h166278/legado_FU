package io.legado.app.help.tts

/**
 * 区分脚本引擎的服务端合成速度与本地播放器速度。
 */
object TtsSpeedPolicy {

    fun playbackRate(speechRateProgress: Int): Float {
        return (speechRateProgress.coerceIn(0, 45) + 5) / 10f
    }

    fun synthesisSpeed(engine: TtsEngineSetting): Int {
        return engine.effectiveSpeed()
    }

    fun playbackLabel(speechRateProgress: Int): String {
        return "${playbackRate(speechRateProgress)}x"
    }
}
