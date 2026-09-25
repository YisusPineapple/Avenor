package io.github.yisus.avenor.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import io.github.yisus.avenor.dsp.EqualizerAudioProcessor
import io.github.yisus.avenor.replaygain.ReplayGainAudioProcessor
import io.github.yisus.avenor.replaygain.SafeLimiterAudioProcessor

/**
 * Custom [DefaultRenderersFactory] for Avenor.
 *
 * Configures the audio rendering pipeline with:
 * 1. [ReplayGainAudioProcessor] for accurate perceived loudness normalization.
 * 2. [EqualizerAudioProcessor] for 5-band biquad DF2T frequency shaping.
 * 3. [SafeLimiterAudioProcessor] for digital sample ceiling and clipping prevention.
 *
 * Signal Chain Placement:
 * Decoder -> ReplayGainAudioProcessor -> EqualizerAudioProcessor -> SafeLimiterAudioProcessor -> AudioTrack
 *
 * Offload & Passthrough Policy (Policy A):
 * Hardware offload bypasses user-space software AudioProcessors.
 * To ensure ReplayGain, Equalization, and Safe Limiting are guaranteed on every sample and track,
 * the [AudioOffloadSupport] provider is explicitly set to [AudioOffloadSupport.DEFAULT_UNSUPPORTED],
 * ensuring audio is decoded to PCM and processed by the DSP chain.
 */
@OptIn(UnstableApi::class)
class AvenorRenderersFactory(
    context: Context,
    val replayGainAudioProcessor: ReplayGainAudioProcessor = ReplayGainAudioProcessor(),
    val equalizerAudioProcessor: EqualizerAudioProcessor = EqualizerAudioProcessor(),
    val safeLimiterAudioProcessor: SafeLimiterAudioProcessor = SafeLimiterAudioProcessor()
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val processors: Array<AudioProcessor> = arrayOf(
            replayGainAudioProcessor,
            equalizerAudioProcessor,
            safeLimiterAudioProcessor
        )

        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(processors)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
            .build()
    }
}
