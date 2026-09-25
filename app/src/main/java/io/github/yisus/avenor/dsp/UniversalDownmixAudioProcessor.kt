package io.github.yisus.avenor.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Universal Downmix AudioProcessor for Avenor.
 *
 * Converts multi-channel audio streams (1.0 Mono, 3.0, 4.0 Quad, 5.0, 5.1 Surround, 7.1)
 * into high-fidelity 2.0 Stereo prior to ReplayGain, Equalizer, and Limiter processing.
 *
 * Mathematical Standards & Conventions (ITU-R BS.775-3 & SMPTE / Android standard channel ordering):
 * - Mono (1.0): [M] -> L = M, R = M (replicated to both channels; unity coefficient 1.0f).
 * - Stereo (2.0): [L, R] -> Bypass (isActive = false).
 * - 3.0 (L, R, C): L = (L + 0.7071 * C) / (1 + 0.7071), R = (R + 0.7071 * C) / (1 + 0.7071).
 * - 4.0 / Quad (L, R, Ls, Rs): L = (L + 0.7071 * Ls) / (1 + 0.7071), R = (R + 0.7071 * Rs) / (1 + 0.7071).
 * - 5.0 (L, R, C, Ls, Rs): L = (L + 0.7071 * C + 0.7071 * Ls) / (1 + 2 * 0.7071), R = (R + 0.7071 * C + 0.7071 * Rs) / (1 + 2 * 0.7071).
 * - 5.1 (L, R, C, LFE, Ls, Rs):
 *     Standard ITU-R BS.775-3 discards LFE to prevent low-end muddying or over-driving small mobile/earphone drivers.
 *     L = (L + 0.7071 * C + 0.7071 * Ls) / (1 + 2 * 0.7071)
 *     R = (R + 0.7071 * C + 0.7071 * Rs) / (1 + 2 * 0.7071)
 * - 7.1 (L, R, C, LFE, Ls, Rs, Rls, Rrs):
 *     L = (L + 0.7071 * C + 0.7071 * Ls + 0.5 * Rls) / (1 + 2 * 0.7071 + 0.5)
 *     R = (R + 0.7071 * C + 0.7071 * Rs + 0.5 * Rrs) / (1 + 2 * 0.7071 + 0.5)
 *
 * Characteristics:
 * - Direct support for both [C.ENCODING_PCM_FLOAT] and [C.ENCODING_PCM_16BIT].
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
        // 3.0: 1 + 0.70710678 = 1.70710678
        private const val NORM_3_0 = 1.0f / (1.0f + INV_SQRT2)
        // 4.0: 1 + 0.70710678 = 1.70710678
        private const val NORM_4_0 = 1.0f / (1.0f + INV_SQRT2)
        // 5.0 and 5.1: 1 + 2 * 0.70710678 = 2.41421356
        private const val NORM_5_1 = 1.0f / (1.0f + 2.0f * INV_SQRT2)
        // 7.1: 1 + 2 * 0.70710678 + 0.5 = 2.91421356
        private const val NORM_7_1 = 1.0f / (1.0f + 2.0f * INV_SQRT2 + REAR_WEIGHT)

        // Pre-computed normalized weights:
        val W_C_3_0: Float = INV_SQRT2 * NORM_3_0
        val W_LR_3_0: Float = 1.0f * NORM_3_0

        val W_SUR_4_0: Float = INV_SQRT2 * NORM_4_0
        val W_LR_4_0: Float = 1.0f * NORM_4_0

        val W_C_5_1: Float = INV_SQRT2 * NORM_5_1
        val W_SUR_5_1: Float = INV_SQRT2 * NORM_5_1
        val W_LR_5_1: Float = 1.0f * NORM_5_1

        val W_C_7_1: Float = INV_SQRT2 * NORM_7_1
        val W_SUR_7_1: Float = INV_SQRT2 * NORM_7_1
        val W_REAR_7_1: Float = REAR_WEIGHT * NORM_7_1
        val W_LR_7_1: Float = 1.0f * NORM_7_1
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        val inChannels = inputAudioFormat.channelCount
        // If already 2 channels, downmixing is not active (bypass)
        if (inChannels == TARGET_CHANNEL_COUNT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        // Validate supported channel counts: 1 (Mono), 3 (3.0), 4 (4.0/Quad), 5 (5.0), 6 (5.1), 8 (7.1)
        if (inChannels !in setOf(1, 3, 4, 5, 6, 8)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            TARGET_CHANNEL_COUNT,
            encoding
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inChannels = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding

        inputBuffer.order(ByteOrder.nativeOrder())

        val outputBuffer: ByteBuffer
        if (encoding == C.ENCODING_PCM_FLOAT) {
            val bytesPerInputFrame = inChannels * 4
            val frameCount = remaining / bytesPerInputFrame
            val outputBytes = frameCount * TARGET_CHANNEL_COUNT * 4

            outputBuffer = replaceOutputBuffer(outputBytes)
            outputBuffer.order(ByteOrder.nativeOrder())

            when (inChannels) {
                1 -> downmixMonoFloat(inputBuffer, outputBuffer, frameCount)
                3 -> downmix30Float(inputBuffer, outputBuffer, frameCount)
                4 -> downmix40Float(inputBuffer, outputBuffer, frameCount)
                5 -> downmix50Float(inputBuffer, outputBuffer, frameCount)
                6 -> downmix51Float(inputBuffer, outputBuffer, frameCount)
                8 -> downmix71Float(inputBuffer, outputBuffer, frameCount)
            }
        } else {
            // ENCODING_PCM_16BIT
            val bytesPerInputFrame = inChannels * 2
            val frameCount = remaining / bytesPerInputFrame
            val outputBytes = frameCount * TARGET_CHANNEL_COUNT * 2

            outputBuffer = replaceOutputBuffer(outputBytes)
            outputBuffer.order(ByteOrder.nativeOrder())

            when (inChannels) {
                1 -> downmixMono16Bit(inputBuffer, outputBuffer, frameCount)
                3 -> downmix3016Bit(inputBuffer, outputBuffer, frameCount)
                4 -> downmix4016Bit(inputBuffer, outputBuffer, frameCount)
                5 -> downmix5016Bit(inputBuffer, outputBuffer, frameCount)
                6 -> downmix5116Bit(inputBuffer, outputBuffer, frameCount)
                8 -> downmix7116Bit(inputBuffer, outputBuffer, frameCount)
            }
        }
        outputBuffer.flip()
    }

    // =========================================================================
    // FLOAT PCM PROCESSING
    // =========================================================================

    private fun downmixMonoFloat(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val m = input.float
            output.putFloat(m)
            output.putFloat(m)
        }
    }

    private fun downmix30Float(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.float
            val r = input.float
            val c = input.float

            val outL = l * W_LR_3_0 + c * W_C_3_0
            val outR = r * W_LR_3_0 + c * W_C_3_0

            output.putFloat(outL)
            output.putFloat(outR)
        }
    }

    private fun downmix40Float(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.float
            val r = input.float
            val ls = input.float
            val rs = input.float

            val outL = l * W_LR_4_0 + ls * W_SUR_4_0
            val outR = r * W_LR_4_0 + rs * W_SUR_4_0

            output.putFloat(outL)
            output.putFloat(outR)
        }
    }

    private fun downmix50Float(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.float
            val r = input.float
            val c = input.float
            val ls = input.float
            val rs = input.float

            val outL = l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1
            val outR = r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1

            output.putFloat(outL)
            output.putFloat(outR)
        }
    }

    private fun downmix51Float(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.float
            val r = input.float
            val c = input.float
            val lfe = input.float // Discarded per ITU-R BS.775-3
            val ls = input.float
            val rs = input.float

            val outL = l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1
            val outR = r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1

            output.putFloat(outL)
            output.putFloat(outR)
        }
    }

    private fun downmix71Float(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.float
            val r = input.float
            val c = input.float
            val lfe = input.float // Discarded per ITU-R BS.775-3
            val ls = input.float
            val rs = input.float
            val rls = input.float
            val rrs = input.float

            val outL = l * W_LR_7_1 + c * W_C_7_1 + ls * W_SUR_7_1 + rls * W_REAR_7_1
            val outR = r * W_LR_7_1 + c * W_C_7_1 + rs * W_SUR_7_1 + rrs * W_REAR_7_1

            output.putFloat(outL)
            output.putFloat(outR)
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

    private fun downmix3016Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()

            val outL = (l * W_LR_3_0 + c * W_C_3_0).toInt().coerceIn(-32768, 32767).toShort()
            val outR = (r * W_LR_3_0 + c * W_C_3_0).toInt().coerceIn(-32768, 32767).toShort()

            output.putShort(outL)
            output.putShort(outR)
        }
    }

    private fun downmix4016Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()

            val outL = (l * W_LR_4_0 + ls * W_SUR_4_0).toInt().coerceIn(-32768, 32767).toShort()
            val outR = (r * W_LR_4_0 + rs * W_SUR_4_0).toInt().coerceIn(-32768, 32767).toShort()

            output.putShort(outL)
            output.putShort(outR)
        }
    }

    private fun downmix5016Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()

            val outL = (l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort()
            val outR = (r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort()

            output.putShort(outL)
            output.putShort(outR)
        }
    }

    private fun downmix5116Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()
            val lfe = input.short // Discarded
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()

            val outL = (l * W_LR_5_1 + c * W_C_5_1 + ls * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort()
            val outR = (r * W_LR_5_1 + c * W_C_5_1 + rs * W_SUR_5_1).toInt().coerceIn(-32768, 32767).toShort()

            output.putShort(outL)
            output.putShort(outR)
        }
    }

    private fun downmix7116Bit(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val l = input.short.toFloat()
            val r = input.short.toFloat()
            val c = input.short.toFloat()
            val lfe = input.short // Discarded
            val ls = input.short.toFloat()
            val rs = input.short.toFloat()
            val rls = input.short.toFloat()
            val rrs = input.short.toFloat()

            val outL = (l * W_LR_7_1 + c * W_C_7_1 + ls * W_SUR_7_1 + rls * W_REAR_7_1).toInt().coerceIn(-32768, 32767).toShort()
            val outR = (r * W_LR_7_1 + c * W_C_7_1 + rs * W_SUR_7_1 + rrs * W_REAR_7_1).toInt().coerceIn(-32768, 32767).toShort()

            output.putShort(outL)
            output.putShort(outR)
        }
    }
}
