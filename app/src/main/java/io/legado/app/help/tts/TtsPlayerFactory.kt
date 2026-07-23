package io.legado.app.help.tts

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Creates players for synthesized speech.
 *
 * SoundTouch replaces Media3 and Android's Sonic-based time stretching, which can introduce
 * audible artifacts in synthesized speech above 1x.
 */
@SuppressLint("UnsafeOptInUsageError")
object TtsPlayerFactory {

    fun create(context: Context): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(TtsSoundTouchAudioProcessorChain())
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(false)
                    .build()
            }
        }
        return ExoPlayer.Builder(context, renderersFactory).build()
    }
}
