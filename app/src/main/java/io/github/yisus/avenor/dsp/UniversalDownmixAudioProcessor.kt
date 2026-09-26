package io.github.yisus.avenor.dsp

import android.media.AudioFormat
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Explicit multi-channel speaker layout and channel ordering contract.
 *
 * Addresses the architectural requirement that `channelCount` alone is ambiguous
 * across containers/decoders (e.g., 4 channels can be Quad `[L, R, Ls, Rs]`, 3.1 `[L, R, C, LFE]`,
 * or 4.0 Surround `[L, R, C, Cs]`; 6 channels can be SMPTE/Android PCM `[L, R, C, LFE, Ls, Rs]`
 * or Vorbis/Film `[L, C, R, Ls, Rs, LFE]`).
 */
enum class AudioChannelLayout(
    val channelCount: Int,
    val description: String
) {
    UNKNOWN(0, "Unknown"),
    MONO_1_0(1, "1.0 Mono [M]"),
    STEREO_2_0(2, "2.0 Stereo [L, R]"),
    SURROUND_3_0_SMPTE(3, "3.0 SMPTE/Android [L, R, C]"),
    SURROUND_3_0_VORBIS(3, "3.0 Vorbis/Film [L, C, R]"),
    QUAD_4_0(4, "4.0 Quadraphonic [L, R, Ls, Rs]"),
    SURROUND_3_1(4, "3.1 Surround [L, R, C, LFE]"),
    SURROUND_4_0_CENTER_REAR(4, "4.0 Surround [L, R, C, Cs]"),
    SURROUND_5_0_SMPTE(5, "5.0 SMPTE/Android [L, R, C, Ls, Rs]"),
    SURROUND_5_0_VORBIS(5, "5.0 Vorbis/Film [L, C, R, Ls, Rs]"),
    SURROUND_5_1_SMPTE(6, "5.1 SMPTE/Android [L, R, C, LFE, Ls, Rs]"),
    SURROUND_5_1_VORBIS(6, "5.1 Vorbis/Film [L, C, R, Ls, Rs, LFE]"),
    SURROUND_7_1_SMPTE(8, "7.1 SMPTE/Android [L, R, C, LFE, Ls, Rs, Rls, Rrs]"),
    SURROUND_7_1_VORBIS(8, "7.1 Vorbis/Film [L, C, R, Ls, Rs, Rls, Rrs, LFE]");

    companion object {
        // Android AudioFormat channel mask constants (values from android.media.AudioFormat)
        const val MASK_MONO = AudioFormat.CHANNEL_OUT_MONO
        const val MASK_STEREO = AudioFormat.CHANNEL_OUT_STEREO
        const val MASK_3_0 = AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER
        const val MASK_3_1 = MASK_3_0 or AudioFormat.CHANNEL_OUT_LOW_FREQUENCY
        const val MASK_QUAD = AudioFormat.CHANNEL_OUT_QUAD
        const val MASK_SURROUND_4_0 = AudioFormat.CHANNEL_OUT_SURROUND
        const val MASK_5_0 = MASK_3_0 or AudioFormat.CHANNEL_OUT_BACK_LEFT or AudioFormat.CHANNEL_OUT_BACK_RIGHT
        const val MASK_5_1 = AudioFormat.CHANNEL_OUT_5POINT1
        const val MASK_7_1 = AudioFormat.CHANNEL_OUT_7POINT1_SURROUND

        /**
         * Resolves an [AudioChannelLayout] from an Android [AudioFormat] channel mask and channel count,
         * falling back to the standard SMPTE/WAV/Android MediaCodec PCM output layout for that count.
         */
        fun resolve(
            channelCount: Int,
            androidChannelMask: Int = 0,
            isVorbisFilmOrder: Boolean = false
        ): AudioChannelLayout {
            if (androidChannelMask != 0) {
                when (androidChannelMask) {
                    MASK_MONO -> if (channelCount == 1) return MONO_1_0
                    MASK_STEREO -> if (channelCount == 2) return STEREO_2_0
                    MASK_3_0 -> if (channelCount == 3) return if (isVorbisFilmOrder) SURROUND_3_0_VORBIS else SURROUND_3_0_SMPTE
                    MASK_3_1 -> if (channelCount == 4) return SURROUND_3_1
                    MASK_SURROUND_4_0 -> if (channelCount == 4) return SURROUND_4_0_CENTER_REAR
                    MASK_QUAD -> if (channelCount == 4) return QUAD_4_0
                    MASK_5_0 -> if (channelCount == 5) return if (isVorbisFilmOrder) SURROUND_5_0_VORBIS else SURROUND_5_0_SMPTE
                    MASK_5_1 -> if (channelCount == 6) return if (isVorbisFilmOrder) SURROUND_5_1_VORBIS else SURROUND_5_1_SMPTE
                    MASK_7_1 -> if (channelCount == 8) return if (isVorbisFilmOrder) SURROUND_7_1_VORBIS else SURROUND_7_1_SMPTE
                }
            }

            return when (channelCount) {
                1 -> MONO_1_0
                2 -> STEREO_2_0
                3 -> if (isVorbisFilmOrder) SURROUND_3_0_VORBIS else SURROUND_3_0_SMPTE
                4 -> QUAD_4_0
                5 -> if (isVorbisFilmOrder) SURROUND_5_0_VORBIS else SURROUND_5_0_SMPTE
                6 -> if (isVorbisFilmOrder) SURROUND_5_1_VORBIS else SURROUND_5_1_SMPTE
                8 -> if (isVorbisFilmOrder) SURROUND_7_1_VORBIS else SURROUND_7_1_SMPTE
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Universal Downmix AudioProcessor for Avenor.
 *
 * Converts multi-channel audio streams (1.0 Mono, 3.0, 3.1, 4.0 Quad/Surround, 5.0, 5.1 Surround, 7.1)
 * into high-fidelity 2.0 Stereo prior to ReplayGain, Equalizer, and Limiter processing.
 *
 * Mathematical Fold-Down Conventions (ITU-R BS.775-3 coefficients with unity-sum peak normalization):
 * - Mono (1.0): [M] -> L = M, R = M (replicated to both channels; unity coefficient 1.0f).
 * - Stereo (2.0): [L, R] -> Bypass (isActive = false).
 * - 3.0 ([L, R, C] or [L, C, R]) & 3.1 ([L, R, C, LFE] with LFE discarded):
 *     L = (L + 0.7071 * C) / (1 + 0.7071), R = (R + 0.7071 * C) / (1 + 0.7071).
 * - 4.0 Quad ([L, R, Ls, Rs]):
 *     L = (L + 0.7071 * Ls) / (1 + 0.7071), R = (R + 0.7071 * Rs) / (1 + 0.7071).
 * - 4.0 Center-Rear ([L, R, C, Cs]):
 *     L = (L + 0.7071 * C + 0.5 * Cs) / (1 + 0.7071 + 0.5),
 *     R = (R + 0.7071 * C + 0.5 * Cs) / (1 + 0.7071 + 0.5).
 * - 5.0 ([L, R, C, Ls, Rs] or [L, C, R, Ls, Rs]):
 *     L = (L + 0.7071 * C + 0.7071 * Ls) / (1 + 2 * 0.7071),
 *     R = (R + 0.7071 * C + 0.7071 * Rs) / (1 + 2 * 0.7071).
 * - 5.1 (SMPTE [L, R, C, LFE, Ls, Rs] or Vorbis/Film [L, C, R, Ls, Rs, LFE]):
 *     Discards LFE per ITU-R BS.775-3 to prevent low-end muddying or over-driving mobile/earphone drivers.
 *     L = (L + 0.7071 * C + 0.7071 * Ls) / (1 + 2 * 0.7071)
 *     R = (R + 0.7071 * C + 0.7071 * Rs) / (1 + 2 * 0.7071)
 * - 7.1 (SMPTE [L, R, C, LFE, Ls, Rs, Rls, Rrs] or Vorbis/Film [L, C, R, Ls, Rs, Rls, Rrs, LFE]):
 *     L = (L + 0.7071 * C + 0.7071 * Ls + 0.5 * Rls) / (1 + 2 * 0.7071 + 0.5)
 *     R = (R + 0.7071 * C + 0.7071 * Rs + 0.5 * Rrs) / (1 + 2 * 0.7071 + 0.5)
 *
 * Characteristics:
 * - Explicit [AudioChannelLayout] resolution from channel mask, layout override, or Android PCM default.
 * - Direct support for both [C.ENCODING_PCM_FLOAT] and [C.ENCODING_PCM_16BIT].
 * - Active sanitization of non-finite (`NaN` / `Infinity`) float samples before mixing.
 * - Full normalization prevents digital clipping during downmixing.
 * - Zero heap allocations inside [queueInput] hot path (reuses output buffer via [replaceOutputBuffer]).
 * - Responds to flush, reset and channel reconfiguration deterministically.
 */
@OptIn(UnstableApi::class)
class UniversalDownmixAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val TARGET_CHANNEL_COUNT = 2

        // Standard coefficients: 1 / sqrt(2) ≈ 0.70710678f
        private const val INV_SQRT2 = 0.70710678f
        private const val REAR_WEIGHT = 0.50f

        // Normalization denominators to guarantee absolute peak sum <= 1.0f:
        // 3.0 / 3.1: 1 + 0.70710678 = 1.70710678
        private const val NORM_3_0 = 1.0f / (1.0f + INV_SQRT2)
        // 4.0 Quad: 1 + 0.70710678 = 1.70710678
        private const val NORM_4_0 = 1.0f / (1.0f + INV_SQRT2)
        // 4.0 Center-Rear: 1 + 0.70710678 + 0.5 = 2.20710678
        private const val NORM_4_0_CR = 1.0f / (1.0f + INV_SQRT2 + REAR_WEIGHT)
        // 5.0 and 5.1: 1 + 2 * 0.70710678 = 2.41421356
        private const val NORM_5_1 = 1.0f / (1.0f + 2.0f * INV_SQRT2)
        // 7.1: 1 + 2 * 0.70710678 + 0.5 = 2.91421356
        private const val NORM_7_1 = 1.0f / (1.0f + 2.0f * INV_SQRT2 + REAR_WEIGHT)

        // Pre-computed normalized weights:
        val W_C_3_0: Float = INV_SQRT2 * NORM_3_0
        val W_LR_3_0: Float = 1.0f * NORM_3_0

        val W_SUR_4_0: Float = INV_SQRT2 * NORM_4_0
        val W_LR_4_0: Float = 1.0f * NORM_4_0

        val W_LR_4_0_CR: Float = 1.0f * NORM_4_0_CR
        val W_C_4_0_CR: Float = INV_SQRT2 * NORM_4_0_CR
        val W_CS_4_0_CR: Float = REAR_WEIGHT * NORM_4_0_CR

        val W_C_5_1: Float = INV_SQRT2 * NORM_5_1
        val W_SUR_5_1: Float = INV_SQRT2 * NORM_5_1
        val W_LR_5_1: Float = 1.0f * NORM_5_1

        val W_C_7_1: Float = INV_SQRT2 * NORM_7_1
        val W_SUR_7_1: Float = INV_SQRT2 * NORM_7_1
        val W_REAR_7_1: Float = REAR_WEIGHT * NORM_7_1
        val W_LR_7_1: Float = 1.0f * NORM_7_1

        @Suppress("NOTHING_TO_INLINE")
        private inline fun sanitizeFloat(sample: Float): Float {
            return if (sample.isFinite()) sample else 0.0f
        }
    }

    /**
     * Optional explicit layout override. When non-null and matching `inputAudioFormat.channelCount`,
     * takes precedence over default channel count inference.
     */
    @Volatile
    var channelLayoutOverride: AudioChannelLayout? = null

    /**
     * Optional Android `AudioFormat.CHANNEL_OUT_*` mask hint supplied by decoder/track metadata.
     */
    @Volatile
    var channelMaskHint: Int = 0

    /**
     * Optional hint indicating whether the upstream decoder emits Vorbis/Film channel ordering
     * (`[L, C, R, ...]`) instead of SMPTE/WAV/Android ordering (`[L, R, C, LFE, ...]`).
     */
    @Volatile
    var isVorbisFilmOrderHint: Boolean = false

    /**
     * The currently resolved [AudioChannelLayout] for the configured stream.
     */
    @Volatile
    var activeLayout: AudioChannelLayout = AudioChannelLayout.UNKNOWN
        private set

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        val inChannels = inputAudioFormat.channelCount
        val override = channelLayoutOverride
        val resolvedLayout = if (override != null && override.channelCount == inChannels) {
            override
        } else {
            AudioChannelLayout.resolve(
                channelCount = inChannels,
                androidChannelMask = channelMaskHint,
                isVorbisFilmOrder = isVorbisFilmOrderHint
            )
        }

        if (resolvedLayout == AudioChannelLayout.UNKNOWN) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        activeLayout = resolvedLayout

        // If already 2 channels, downmixing is not active (bypass)
        if (resolvedLayout == AudioChannelLayout.STEREO_2_0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            TARGET_CHANNEL_COUNT,
            encoding
        )
    }

    override fun onReset() {
        activeLayout = AudioChannelLayout.UNKNOWN
        channelLayoutOverride = null
        channelMaskHint = 0
        isVorbisFilmOrderHint = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inChannels = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding
        val layout = activeLayout

        inputBuffer.order(ByteOrder.nativeOrder())

        val outputBuffer: ByteBuffer
        if (encoding == C.ENCODING_PCM_FLOAT) {
            val bytesPerInputFrame = inChannels * 4
            val frameCount = remaining / bytesPerInputFrame
            val outputBytes = frameCount * TARGET_CHANNEL_COUNT * 4

            outputBuffer = replaceOutputBuffer(outputBytes)
            outputBuffer.order(ByteOrder.nativeOrder())

            when (layout) {
                AudioChannelLayout.MONO_1_0 -> downmixMonoFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_3_0_SMPTE -> downmix30SmpteFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_3_0_VORBIS -> downmix30VorbisFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.QUAD_4_0 -> downmix40QuadFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_3_1 -> downmix31Float(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_4_0_CENTER_REAR -> downmix40CenterRearFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_5_0_SMPTE -> downmix50SmpteFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_5_0_VORBIS -> downmix50VorbisFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_5_1_SMPTE -> downmix51SmpteFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_5_1_VORBIS -> downmix51VorbisFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_7_1_SMPTE -> downmix71SmpteFloat(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_7_1_VORBIS -> downmix71VorbisFloat(inputBuffer, outputBuffer, frameCount)
                else -> Unit
            }
        } else {
            // ENCODING_PCM_16BIT
            val bytesPerInputFrame = inChannels * 2
            val frameCount = remaining / bytesPerInputFrame
            val outputBytes = frameCount * TARGET_CHANNEL_COUNT * 2

            outputBuffer = replaceOutputBuffer(outputBytes)
            outputBuffer.order(ByteOrder.nativeOrder())

            when (layout) {
                AudioChannelLayout.MONO_1_0 -> downmixMono16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_3_0_SMPTE -> downmix30Smpte16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_3_0_VORBIS -> downmix30Vorbis16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.QUAD_4_0 -> downmix40Quad16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_3_1 -> downmix3116Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_4_0_CENTER_REAR -> downmix40CenterRear16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_5_0_SMPTE -> downmix50Smpte16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_5_0_VORBIS -> downmix50Vorbis16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_5_1_SMPTE -> downmix51Smpte16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_5_1_VORBIS -> downmix51Vorbis16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_7_1_SMPTE -> downmix71Smpte16Bit(inputBuffer, outputBuffer, frameCount)
                AudioChannelLayout.SURROUND_7_1_VORBIS -> downmix71Vorbis16Bit(inputBuffer, outputBuffer, frameCount)
                else -> Unit
            }
        }
        outputBuffer.flip()
    }

    // =========================================================================
    // FLOAT PCM PROCESSING (with NaN / Infinity sanitization)
    // =========================================================================

    private fun downmixMonoFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val m = sanitizeFloat(input.float)
            output.putFloat(m)
            output.putFloat(m)
        }
    }

    private fun downmix30SmpteFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)

            output.putFloat(l * W_LR_3_0 + c * W_C_3_0)
            output.putFloat(r * W_LR_3_0 + c * W_C_3_0)
        }
    }

    private fun downmix30VorbisFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)

            output.putFloat(l * W_LR_3_0 + c * W_C_3_0)
            output.putFloat(r * W_LR_3_0 + c * W_C_3_0)
        }
    }

    private fun downmix40QuadFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val ls = sanitizeFloat(input.float)
            val rs = sanitizeFloat(input.float)

            output.putFloat(l * W_LR_4_0 + ls * W_SUR_4_0)
            output.putFloat(r * W_LR_4_0 + rs * W_SUR_4_0)
        }
    }

    private fun downmix31Float(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            input.float // LFE discarded per ITU-R BS.775-3

            output.putFloat(l * W_LR_3_0 + c * W_C_3_0)
            output.putFloat(r * W_LR_3_0 + c * W_C_3_0)
        }
    }

    private fun downmix40CenterRearFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            val cs = sanitizeFloat(input.float)

            val centerRear = c * W_C_4_0_CR + cs * W_CS_4_0_CR
            output.putFloat(l * W_LR_4_0_CR + centerRear)
            output.putFloat(r * W_LR_4_0_CR + centerRear)
        }
    }

    private fun downmix50SmpteFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            val ls = sanitizeFloat(input.float)
            val rs = sanitizeFloat(input.float)

            output.putFloat(l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1)
            output.putFloat(r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1)
        }
    }

    private fun downmix50VorbisFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val ls = sanitizeFloat(input.float)
            val rs = sanitizeFloat(input.float)

            output.putFloat(l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1)
            output.putFloat(r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1)
        }
    }

    private fun downmix51SmpteFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            input.float // LFE discarded per ITU-R BS.775-3
            val ls = sanitizeFloat(input.float)
            val rs = sanitizeFloat(input.float)

            output.putFloat(l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1)
            output.putFloat(r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1)
        }
    }

    private fun downmix51VorbisFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val ls = sanitizeFloat(input.float)
            val rs = sanitizeFloat(input.float)
            input.float // LFE discarded per ITU-R BS.775-3

            output.putFloat(l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1)
            output.putFloat(r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1)
        }
    }

    private fun downmix71SmpteFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            input.float // LFE discarded per ITU-R BS.775-3
            val ls = sanitizeFloat(input.float)
            val rs = sanitizeFloat(input.float)
            val rls = sanitizeFloat(input.float)
            val rrs = sanitizeFloat(input.float)

            output.putFloat(l * W_LR_7_1 + c * W_C_7_1 + ls * W_SUR_7_1 + rls * W_REAR_7_1)
            output.putFloat(r * W_LR_7_1 + c * W_C_7_1 + rs * W_SUR_7_1 + rrs * W_REAR_7_1)
        }
    }

    private fun downmix71VorbisFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = sanitizeFloat(input.float)
            val c = sanitizeFloat(input.float)
            val r = sanitizeFloat(input.float)
            val ls = sanitizeFloat(input.float)
            val rs = sanitizeFloat(input.float)
            val rls = sanitizeFloat(input.float)
            val rrs = sanitizeFloat(input.float)
            input.float // LFE discarded per ITU-R BS.775-3

            output.putFloat(l * W_LR_7_1 + c * W_C_7_1 + ls * W_SUR_7_1 + rls * W_REAR_7_1)
            output.putFloat(r * W_LR_7_1 + c * W_C_7_1 + rs * W_SUR_7_1 + rrs * W_REAR_7_1)
        }
    }

    // =========================================================================
    // 16-BIT INTEGER PCM PROCESSING
    // =========================================================================

    private fun downmixMono16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val m = input.short
            output.putShort(m)
            output.putShort(m)
        }
    }

    private fun downmix30Smpte16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()

            output.putShort((l * W_LR_3_0 + c * W_C_3_0).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_3_0 + c * W_C_3_0).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix30Vorbis16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val c = input.short.toFloat()
            val r = input.short.toFloat()

            output.putShort((l * W_LR_3_0 + c * W_C_3_0).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_3_0 + c * W_C_3_0).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix40Quad16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()

            output.putShort((l * W_LR_4_0 + ls * W_SUR_4_0).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_4_0 + rs * W_SUR_4_0).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix3116Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()
            input.short // LFE discarded

            output.putShort((l * W_LR_3_0 + c * W_C_3_0).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_3_0 + c * W_C_3_0).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix40CenterRear16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()
            val cs = input.short.toFloat()

            val centerRear = c * W_C_4_0_CR + cs * W_CS_4_0_CR
            output.putShort((l * W_LR_4_0_CR + centerRear).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_4_0_CR + centerRear).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix50Smpte16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()

            output.putShort((l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix50Vorbis16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val c = input.short.toFloat()
            val r = input.short.toFloat()
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()

            output.putShort((l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix51Smpte16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()
            input.short // Discarded
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()

            output.putShort((l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix51Vorbis16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val c = input.short.toFloat()
            val r = input.short.toFloat()
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()
            input.short // Discarded

            output.putShort((l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix71Smpte16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()
            input.short // Discarded
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()
            val rls = input.short.toFloat()
            val rrs = input.short.toFloat()

            output.putShort((l * W_LR_7_1 + c * W_C_7_1 + ls * W_SUR_7_1 + rls * W_REAR_7_1).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_7_1 + c * W_C_7_1 + rs * W_SUR_7_1 + rrs * W_REAR_7_1).toInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun downmix71Vorbis16Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val c = input.short.toFloat()
            val r = input.short.toFloat()
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()
            val rls = input.short.toFloat()
            val rrs = input.short.toFloat()
            input.short // Discarded

            output.putShort((l * W_LR_7_1 + c * W_C_7_1 + ls * W_SUR_7_1 + rls * W_REAR_7_1).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((r * W_LR_7_1 + c * W_C_7_1 + rs * W_SUR_7_1 + rrs * W_REAR_7_1).toInt().coerceIn(-32768, 32767).toShort())
        }
    }
}
