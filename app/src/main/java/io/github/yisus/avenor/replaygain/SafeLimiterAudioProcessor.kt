package io.github.yisus.avenor.replaygain

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Safe Sample-Peak Limiter AudioProcessor for Avenor.
 *
 * Prevents digital PCM clipping by enforcing a hard sample ceiling
 * with dynamic envelope gain reduction and controlled exponential release recovery.
 *
 * Signal Chain Placement:
 * Input PCM -> ReplayGainAudioProcessor -> SafeLimiterAudioProcessor -> AudioSink / AudioTrack
 *
 * Characteristics:
 * - Operates at sample-level across Float PCM (-1.0f .. +1.0f) and 16-bit PCM (-32768 .. +32767).
 * - Instantaneous attack whenever a sample exceeds ceiling.
 * - Smooth exponential release recovery (~50 ms) back towards unity gain.
 * - Anticipatory protection hook when ReplayGain peak metadata is available.
 * - Absolute guarantee of zero digital sample overflow.
 */
@OptIn(UnstableApi::class)
class SafeLimiterAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var isEnabled: Boolean = true

    /**
     * Maximum permissible peak amplitude in normalized full-scale [0.5 .. 1.0].
     * Defaults to 1.0f (0 dBFS).
     */
    @Volatile
    var ceiling: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 1.0f)
        }

    @Volatile
    var envelopeGain: Float = 1.0f
        internal set

    private var releaseCoeff: Float = 0.001f

    /**
     * Hook to notify the limiter of upcoming track gain and peak metadata.
     * If anticipated peak exceeds ceiling, pre-reduces envelope gain to soften the initial transient.
     */
    fun setAnticipatedGain(effectiveGainDb: Float, peak: Float?) {
        if (!isEnabled || peak == null || peak <= 0.0f) return
        val linearGain = ReplayGainPolicy.dbToLinearGain(effectiveGainDb)
        val anticipatedPeak = peak * linearGain
        if (anticipatedPeak > ceiling) {
            val safeGain = (ceiling / anticipatedPeak).coerceIn(0.1f, 1.0f)
            if (safeGain < envelopeGain) {
                envelopeGain = safeGain
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100
        // Exponential release time of ~50 ms
        releaseCoeff = (1.0f - exp(-1.0f / (sampleRate * 0.050f))).coerceIn(0.0001f, 0.05f)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!isEnabled) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val encoding = inputAudioFormat.encoding
        val channelCount = inputAudioFormat.channelCount
        if (channelCount <= 0) return

        inputBuffer.order(ByteOrder.nativeOrder())
        val outputBuffer = replaceOutputBuffer(remaining)

        if (encoding == C.ENCODING_PCM_FLOAT) {
            val bytesPerFrame = channelCount * 4
            val frameCount = remaining / bytesPerFrame
            val currentCeiling = ceiling

            for (f in 0 until frameCount) {
                val framePos = inputBuffer.position()
                var maxSampleAbs = 0.0f
                for (c in 0 until channelCount) {
                    val s = inputBuffer.getFloat(framePos + c * 4)
                    val a = abs(s)
                    if (a > maxSampleAbs) maxSampleAbs = a
                }

                // If sample with current envelope exceeds ceiling, attack immediately
                val potentialPeak = maxSampleAbs * envelopeGain
                if (potentialPeak > currentCeiling && maxSampleAbs > 0.0f) {
                    envelopeGain = currentCeiling / maxSampleAbs
                }

                for (c in 0 until channelCount) {
                    val s = inputBuffer.float
                    val limited = (s * envelopeGain).coerceIn(-currentCeiling, currentCeiling)
                    outputBuffer.putFloat(limited)
                }

                // Controlled exponential release towards 1.0f
                if (envelopeGain < 1.0f) {
                    envelopeGain = min(1.0f, envelopeGain + (1.0f - envelopeGain) * releaseCoeff)
                }
            }
        } else if (encoding == C.ENCODING_PCM_16BIT) {
            val bytesPerFrame = channelCount * 2
            val frameCount = remaining / bytesPerFrame
            val maxShortCeiling = (32767f * ceiling).toInt().coerceIn(1, 32767)

            for (f in 0 until frameCount) {
                val framePos = inputBuffer.position()
                var maxSampleAbs = 0
                for (c in 0 until channelCount) {
                    val s = inputBuffer.getShort(framePos + c * 2).toInt()
                    val a = abs(s)
                    if (a > maxSampleAbs) maxSampleAbs = a
                }

                val potentialPeak = (maxSampleAbs * envelopeGain).toInt()
                if (potentialPeak > maxShortCeiling && maxSampleAbs > 0) {
                    envelopeGain = maxShortCeiling.toFloat() / maxSampleAbs
                }

                for (c in 0 until channelCount) {
                    val s = inputBuffer.short
                    val scaled = (s * envelopeGain).toInt()
                    val limited = scaled.coerceIn(-maxShortCeiling, maxShortCeiling).toShort()
                    outputBuffer.putShort(limited)
                }

                if (envelopeGain < 1.0f) {
                    envelopeGain = min(1.0f, envelopeGain + (1.0f - envelopeGain) * releaseCoeff)
                }
            }
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        envelopeGain = 1.0f
    }

    override fun onReset() {
        isEnabled = true
        ceiling = 1.0f
        envelopeGain = 1.0f
    }
}
