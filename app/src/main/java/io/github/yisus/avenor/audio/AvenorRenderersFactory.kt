package io.github.yisus.avenor.audio

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import io.github.yisus.avenor.dsp.AudioChannelLayout
import io.github.yisus.avenor.dsp.EqualizerAudioProcessor
import io.github.yisus.avenor.dsp.UniversalDownmixAudioProcessor
import io.github.yisus.avenor.replaygain.ReplayGainAudioProcessor
import io.github.yisus.avenor.replaygain.SafeLimiterAudioProcessor
import java.util.ArrayList

/**
 * Lightweight [Extractor] decorator that peeks the first 128 bytes of a stream at position 0
 * to detect a `WAVE_FORMAT_EXTENSIBLE` (`0xFFFE`) `dwChannelMask` and attaches
 * `wave_channel_mask=0x...` to the emitted [Format.codecs] via [TrackOutput.format].
 *
 * This ensures raw PCM WAV tracks (which bypass `MediaCodec` and therefore have no `MediaFormat.KEY_CHANNEL_MASK`)
 * propagate their true speaker mask (e.g., 3.1 `0x0F` or 4.0 Center-Rear `0x107`) through the standard
 * Media3 [Format] pipeline into [AvenorAudioSink] and [UniversalDownmixAudioProcessor].
 */
@OptIn(UnstableApi::class)
class AvenorChannelMaskExtractorAdapter(
    private val delegate: Extractor
) : Extractor {

    @Volatile
    private var detectedWaveMask: Int = 0
    private var headerPeeked: Boolean = false

    private fun peekWaveMaskIfAtStart(input: ExtractorInput) {
        if (headerPeeked || input.position != 0L) return
        headerPeeked = true
        try {
            val buf = ByteArray(44)
            if (input.peekFully(buf, 0, buf.size, true)) {
                val mask = AudioChannelLayout.sniffWaveExtensibleChannelMask(buf, buf.size)
                if (mask != 0) {
                    detectedWaveMask = mask
                }
            }
        } catch (_: Exception) {
            // Ignore peek errors on non-WAV or truncated inputs
        } finally {
            try {
                input.resetPeekPosition()
            } catch (_: Exception) {
            }
        }
    }

    override fun sniff(input: ExtractorInput): Boolean {
        peekWaveMaskIfAtStart(input)
        return delegate.sniff(input)
    }

    override fun init(output: ExtractorOutput) {
        delegate.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput {
                val baseTrack = output.track(id, type)
                return object : TrackOutput {
                    override fun format(format: Format) {
                        val mask = detectedWaveMask
                        if (mask != 0) {
                            val maskToken = "wave_channel_mask=0x${mask.toString(16)}"
                            val existingCodecs = format.codecs
                            val mergedCodecs = if (existingCodecs.isNullOrBlank()) {
                                maskToken
                            } else if (!existingCodecs.contains("wave_channel_mask=", ignoreCase = true)) {
                                "$existingCodecs;$maskToken"
                            } else {
                                existingCodecs
                            }
                            baseTrack.format(format.buildUpon().setCodecs(mergedCodecs).build())
                        } else {
                            baseTrack.format(format)
                        }
                    }

                    override fun sampleData(
                        input: DataReader,
                        length: Int,
                        allowEndOfInput: Boolean,
                        sampleDataPart: Int
                    ): Int = baseTrack.sampleData(input, length, allowEndOfInput, sampleDataPart)

                    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
                        baseTrack.sampleData(data, length, sampleDataPart)
                    }

                    override fun sampleMetadata(
                        timeUs: Long,
                        flags: Int,
                        size: Int,
                        offset: Int,
                        cryptoData: TrackOutput.CryptoData?
                    ) {
                        baseTrack.sampleMetadata(timeUs, flags, size, offset, cryptoData)
                    }
                }
            }

            override fun endTracks() = output.endTracks()

            override fun seekMap(seekMap: SeekMap) = output.seekMap(seekMap)
        })
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        peekWaveMaskIfAtStart(input)
        return delegate.read(input, seekPosition)
    }

    override fun seek(position: Long, timeUs: Long) {
        delegate.seek(position, timeUs)
    }

    override fun release() {
        delegate.release()
    }
}

/**
 * Forwarding [AudioSink] decorator that intercepts [configure] to propagate Media3 [Format]
 * and channel mapping metadata to [UniversalDownmixAudioProcessor] immediately before
 * [DefaultAudioSink] configures the [AudioProcessor] chain.
 */
@OptIn(UnstableApi::class)
class AvenorAudioSink(
    private val delegate: AudioSink,
    private val downmixProcessor: UniversalDownmixAudioProcessor
) : AudioSink by delegate {

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        downmixProcessor.onSinkConfigureFormat(inputFormat, outputChannels)
        delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun reset() {
        delegate.reset()
        downmixProcessor.reset()
    }
}

/**
 * Custom [MediaCodecAudioRenderer] that intercepts upstream track [Format] changes and
 * decoder output [MediaFormat] (`KEY_CHANNEL_MASK`, MIME, channel ordering) and forwards
 * them to [UniversalDownmixAudioProcessor] prior to [AudioSink.configure].
 */
@OptIn(UnstableApi::class)
class AvenorMediaCodecAudioRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink,
    private val downmixProcessor: UniversalDownmixAudioProcessor
) : MediaCodecAudioRenderer(
    context,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink
) {
    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        downmixProcessor.onInputTrackFormatChanged(formatHolder.format)
        return super.onInputFormatChanged(formatHolder)
    }

    public override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        downmixProcessor.applyStreamFormat(format, mediaFormat)
        val effectiveFormat = if (
            codec == null &&
            mediaFormat != null &&
            format.sampleMimeType != androidx.media3.common.MimeTypes.AUDIO_RAW &&
            mediaFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) &&
            mediaFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
        ) {
            val pcmEncoding = if (mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                androidx.media3.common.C.ENCODING_PCM_16BIT
            }
            format.buildUpon()
                .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_RAW)
                .setPcmEncoding(pcmEncoding)
                .setChannelCount(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                .setSampleRate(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                .build()
        } else {
            format
        }
        super.onOutputFormatChanged(effectiveFormat, mediaFormat)
    }
}

/**
 * Custom [DefaultRenderersFactory] for Avenor.
 *
 * Configures the audio rendering pipeline with:
 * 1. [UniversalDownmixAudioProcessor] to fold down multi-channel (1.0, 3.0, 3.1, 4.0, 5.0, 5.1, 7.1) streams into 2.0 Stereo.
 * 2. [ReplayGainAudioProcessor] for accurate perceived loudness normalization.
 * 3. [EqualizerAudioProcessor] for 5-band biquad DF2T frequency shaping.
 * 4. [SafeLimiterAudioProcessor] for digital sample ceiling and clipping prevention.
 *
 * Signal Chain Placement:
 * Decoder (AvenorMediaCodecAudioRenderer)
 *   -> AvenorAudioSink (propagates Format + MediaFormat KEY_CHANNEL_MASK)
 *   -> UniversalDownmixAudioProcessor
 *   -> ReplayGainAudioProcessor
 *   -> EqualizerAudioProcessor
 *   -> SafeLimiterAudioProcessor
 *   -> AudioTrack
 *
 * Offload & Passthrough Policy (Policy A):
 * Hardware offload bypasses user-space software AudioProcessors.
 * To ensure Downmixing, ReplayGain, Equalization, and Safe Limiting are guaranteed on every sample and track,
 * the [AudioOffloadSupport] provider is explicitly set to [AudioOffloadSupport.DEFAULT_UNSUPPORTED],
 * ensuring audio is decoded to PCM and processed by the DSP chain.
 */
@OptIn(UnstableApi::class)
class AvenorRenderersFactory(
    context: Context,
    val universalDownmixAudioProcessor: UniversalDownmixAudioProcessor = UniversalDownmixAudioProcessor(),
    val replayGainAudioProcessor: ReplayGainAudioProcessor = ReplayGainAudioProcessor(),
    val equalizerAudioProcessor: EqualizerAudioProcessor = EqualizerAudioProcessor(),
    val safeLimiterAudioProcessor: SafeLimiterAudioProcessor = SafeLimiterAudioProcessor()
) : DefaultRenderersFactory(context) {

    /**
     * Ordered DSP processor array exposed for pipeline inspection and verification.
     */
    val audioProcessors: Array<AudioProcessor>
        get() = arrayOf(
            universalDownmixAudioProcessor,
            replayGainAudioProcessor,
            equalizerAudioProcessor,
            safeLimiterAudioProcessor
        )

    public override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        out.add(
            AvenorMediaCodecAudioRenderer(
                context = context,
                mediaCodecSelector = mediaCodecSelector,
                enableDecoderFallback = enableDecoderFallback,
                eventHandler = eventHandler,
                eventListener = eventListener,
                audioSink = audioSink,
                downmixProcessor = universalDownmixAudioProcessor
            )
        )
    }

    public override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val defaultSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(audioProcessors)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
            .build()
        return AvenorAudioSink(defaultSink, universalDownmixAudioProcessor)
    }

    /**
     * Builds an [ExtractorsFactory] wrapping [DefaultExtractorsFactory] with
     * [AvenorChannelMaskExtractorAdapter] so `WAVE_FORMAT_EXTENSIBLE` WAV channel masks
     * (`dwChannelMask`) are propagated into [Format.codecs] (`wave_channel_mask=0x...`).
     */
    fun buildExtractorsFactory(): ExtractorsFactory {
        val baseFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        return object : ExtractorsFactory {
            override fun createExtractors(): Array<Extractor> =
                baseFactory.createExtractors().map { AvenorChannelMaskExtractorAdapter(it) }.toTypedArray()

            override fun createExtractors(
                uri: Uri,
                responseHeaders: Map<String, List<String>>
            ): Array<Extractor> =
                baseFactory.createExtractors(uri, responseHeaders)
                    .map { AvenorChannelMaskExtractorAdapter(it) }
                    .toTypedArray()
        }
    }
}
