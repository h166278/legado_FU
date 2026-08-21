package io.legado.app.help.tts

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import com.tianscar.soundtouch.SoundTouch
import java.nio.ByteBuffer
import kotlin.math.abs

internal class TtsSoundTouchAudioProcessor : BaseAudioProcessor() {

    private var playbackParameters = PlaybackParameters.DEFAULT
    private var soundTouch: SoundTouch? = null

    fun setPlaybackParameters(parameters: PlaybackParameters): PlaybackParameters {
        playbackParameters = parameters
        return parameters
    }

    fun getMediaDuration(playoutDurationUs: Long): Long {
        return (playoutDurationUs * playbackParameters.speed).toLong()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return abs(playbackParameters.speed - 1f) >= CLOSE_THRESHOLD ||
            abs(playbackParameters.pitch - 1f) >= CLOSE_THRESHOLD
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val processor = checkNotNull(soundTouch)
        val channelCount = inputAudioFormat.channelCount
        val sampleCount = inputBuffer.remaining() / BYTES_PER_SAMPLE
        if (sampleCount == 0) return

        val inputSamples = ShortArray(sampleCount)
        inputBuffer.asShortBuffer().get(inputSamples)
        inputBuffer.position(inputBuffer.position() + sampleCount * BYTES_PER_SAMPLE)
        processor.putSamples(inputSamples, 0, sampleCount / channelCount)
        drainOutput(processor, channelCount)
    }

    override fun onQueueEndOfStream() {
        val processor = soundTouch ?: return
        processor.flush()
        drainOutput(processor, inputAudioFormat.channelCount)
    }

    override fun onFlush() {
        releaseProcessor()
        if (!isActive()) return
        soundTouch = SoundTouch().apply {
            setSampleRate(inputAudioFormat.sampleRate.toLong())
            setChannels(inputAudioFormat.channelCount.toLong())
            setTempo(playbackParameters.speed)
            setPitch(playbackParameters.pitch)
            setSetting(SoundTouch.SETTING_USE_QUICKSEEK, 0)
            setSetting(SoundTouch.SETTING_USE_AA_FILTER, 1)
            setSetting(SoundTouch.SETTING_SEQUENCE_MS, SPEECH_SEQUENCE_MS)
            setSetting(SoundTouch.SETTING_SEEKWINDOW_MS, SPEECH_SEEK_WINDOW_MS)
            setSetting(SoundTouch.SETTING_OVERLAP_MS, SPEECH_OVERLAP_MS)
        }
    }

    override fun onReset() {
        releaseProcessor()
        playbackParameters = PlaybackParameters.DEFAULT
    }

    private fun drainOutput(processor: SoundTouch, channelCount: Int) {
        val availableFrames = processor.numSamples().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (availableFrames == 0) return

        val outputSamples = ShortArray(availableFrames * channelCount)
        val receivedFrames = processor.receiveSamplesI16(outputSamples, 0, availableFrames)
        if (receivedFrames == 0) return

        val outputSize = receivedFrames * channelCount * BYTES_PER_SAMPLE
        replaceOutputBuffer(outputSize).apply {
            asShortBuffer().put(outputSamples, 0, receivedFrames * channelCount)
            position(outputSize)
            flip()
        }
    }

    private fun releaseProcessor() {
        soundTouch?.dispose()
        soundTouch = null
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val CLOSE_THRESHOLD = 0.0001f
        const val SPEECH_SEQUENCE_MS = 40
        const val SPEECH_SEEK_WINDOW_MS = 15
        const val SPEECH_OVERLAP_MS = 8
    }
}

internal class TtsSoundTouchAudioProcessorChain : AudioProcessorChain {

    private val processor = TtsSoundTouchAudioProcessor()
    private val processors = arrayOf<AudioProcessor>(processor)

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        return processor.setPlaybackParameters(playbackParameters)
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long {
        return processor.getMediaDuration(playoutDuration)
    }

    override fun getSkippedOutputFrameCount(): Long = 0L
}
