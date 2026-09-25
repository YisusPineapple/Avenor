package io.github.yisus.avenor.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable snapshot of Equalizer settings.
 *
 * Guarantees thread-safe parameter publication outside of audio lock contention.
 */
class EqBandSettings private constructor(
    val isEnabled: Boolean,
    private val gains: FloatArray
) {
    fun getGainDb(bandIndex: Int): Float = gains[bandIndex]

    fun copyGains(): FloatArray = gains.clone()

    companion object {
        const val BAND_COUNT = 5
        private const val MIN_GAIN_DB = -12.0f
        private const val MAX_GAIN_DB = 12.0f

        fun of(isEnabled: Boolean, bandGainsDb: FloatArray): EqBandSettings {
            require(bandGainsDb.size == BAND_COUNT) { "Band count must be exactly $BAND_COUNT" }
            val clamped = FloatArray(BAND_COUNT) { i ->
                bandGainsDb[i].coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            }
            return EqBandSettings(isEnabled, clamped)
        }

        val FLAT: EqBandSettings = EqBandSettings(
            isEnabled = true,
            gains = FloatArray(BAND_COUNT) { 0.0f }
        )
    }
}

/**
 * 5-Band Graphic Equalizer AudioProcessor for Avenor.
 *
 * Signal Chain Placement:
 * ExoPlayer Decoder -> ReplayGainAudioProcessor -> EqualizerAudioProcessor -> SafeLimiterAudioProcessor -> AudioTrack
 *
 * Characteristics:
 * - 5 standardized frequency bands: 60Hz (Low-Shelf), 230Hz (Peaking), 910Hz (Peaking), 3600Hz (Peaking), 14000Hz (High-Shelf).
 * - Robert Bristow-Johnson (RBJ) Audio EQ Cookbook biquad equations in Direct Form II Transposed (DF2T).
 * - Sample-rate dependent filter calculations with dynamic Nyquist guard (f0 <= 0.42 * Fs).
 * - Block-based parameter gain smoothing (32 audio frames sub-blocks, max 0.1 dB/step) to minimize zipper noise and phase clicks.
 * - Lock-free thread-safe updates via @Volatile immutable snapshot references.
 * - Zero heap allocations inside [queueInput] hot path.
 * - Full support for C.ENCODING_PCM_16BIT and C.ENCODING_PCM_FLOAT across mono, stereo, and multi-channel streams.
 */
@OptIn(UnstableApi::class)
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val BAND_COUNT = 5
        val BAND_FREQUENCIES_HZ = floatArrayOf(60.0f, 230.0f, 910.0f, 3600.0f, 14000.0f)
        private const val SQRT2 = 1.41421356f
        private const val Q_PEAKING = 1.41421356f // ~1 octave bandwidth
        private const val SUB_BLOCK_FRAMES = 32
        private const val MAX_GAIN_STEP_DB = 0.1f
        private const val DENORMAL_THRESHOLD = 1.0e-15f
        private const val NYQUIST_SAFETY_FACTOR = 0.42f
    }

    @Volatile
    private var currentSettings: EqBandSettings = EqBandSettings.FLAT

    // Internal gain states (one per band)
    private val targetGainsDb = FloatArray(BAND_COUNT)
    private val currentGainsDb = FloatArray(BAND_COUNT)

    // Precalculated trigonometric constants for current sample rate: [sin(w0), cos(w0)] per band
    private val sinW0 = FloatArray(BAND_COUNT)
    private val cosW0 = FloatArray(BAND_COUNT)
    private val isBandBypassedByNyquist = BooleanArray(BAND_COUNT)

    // Normalized biquad coefficients: 5 coefficients per band [b0, b1, b2, a1, a2]
    // Total = 5 bands * 5 = 25 floats
    private val coeffs = FloatArray(BAND_COUNT * 5)

    // DF2T delay states: 2 states (s1, s2) per band per channel
    // Size = channelCount * BAND_COUNT * 2
    private var states = FloatArray(0)

    // Tracks if any band is currently interpolating towards target gain
    private var isRamping = false

    /**
     * Updates active equalizer settings atomically.
     */
    fun setSettings(settings: EqBandSettings) {
        currentSettings = settings
    }

    /**
     * Convenience method to update gains directly.
     */
    fun setBandGains(gainsDb: FloatArray, enabled: Boolean = currentSettings.isEnabled) {
        setSettings(EqBandSettings.of(enabled, gainsDb))
    }

    fun getSettings(): EqBandSettings = currentSettings

    /**
     * Inspects current internal gain for a band (including in-flight smoothing progress).
     */
    internal fun getCurrentGainDb(bandIndex: Int): Float = currentGainsDb[bandIndex]

    /**
     * Inspects normalized coefficients for a band [b0, b1, b2, a1, a2].
     */
    internal fun getBandCoefficients(bandIndex: Int): FloatArray {
        val offset = bandIndex * 5
        return FloatArray(5) { coeffs[offset + it] }
    }

    /**
     * Inspects state registers for a channel and band [s1, s2].
     */
    internal fun getBandState(channelIndex: Int, bandIndex: Int): Pair<Float, Float> {
        val idx = (channelIndex * BAND_COUNT + bandIndex) * 2
        return if (idx + 1 < states.size) {
            Pair(states[idx], states[idx + 1])
        } else {
            Pair(0.0f, 0.0f)
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val sampleRate = inputAudioFormat.sampleRate
        val channelCount = inputAudioFormat.channelCount

        // Preallocate states for all channels: 2 states per band per channel
        val totalStates = channelCount * BAND_COUNT * 2
        if (states.size != totalStates) {
            states = FloatArray(totalStates)
        } else {
            states.fill(0.0f)
        }

        // Precalculate trigonometry and Nyquist guard per band
        for (b in 0 until BAND_COUNT) {
            val f0 = BAND_FREQUENCIES_HZ[b]
            if (sampleRate <= 0 || f0 > NYQUIST_SAFETY_FACTOR * sampleRate) {
                isBandBypassedByNyquist[b] = true
                sinW0[b] = 0.0f
                cosW0[b] = 1.0f
            } else {
                isBandBypassedByNyquist[b] = false
                val w0 = (2.0 * Math.PI * f0 / sampleRate).toFloat()
                sinW0[b] = sin(w0)
                cosW0[b] = cos(w0)
            }
        }

        // Recalculate coefficients for all bands with current gains
        val settings = currentSettings
        val isEnabled = settings.isEnabled
        for (b in 0 until BAND_COUNT) {
            targetGainsDb[b] = if (isEnabled) settings.getGainDb(b) else 0.0f
            currentGainsDb[b] = targetGainsDb[b]
            computeCoefficients(b, currentGainsDb[b])
        }
        isRamping = false

        return inputAudioFormat
    }

    override fun onFlush() {
        // Reset delay states to avoid clicks or thumps across timeline seeks
        states.fill(0.0f)
        val settings = currentSettings
        val isEnabled = settings.isEnabled
        for (b in 0 until BAND_COUNT) {
            targetGainsDb[b] = if (isEnabled) settings.getGainDb(b) else 0.0f
            currentGainsDb[b] = targetGainsDb[b]
            computeCoefficients(b, currentGainsDb[b])
        }
        isRamping = false
    }

    override fun onReset() {
        states = FloatArray(0)
        currentGainsDb.fill(0.0f)
        targetGainsDb.fill(0.0f)
        isRamping = false
        currentSettings = EqBandSettings.FLAT
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val encoding = inputAudioFormat.encoding
        val channelCount = inputAudioFormat.channelCount
        if (channelCount <= 0) return

        // Read snapshot reference atomically once
        val settings = currentSettings
        val isEnabled = settings.isEnabled

        // Update target gains from settings
        for (b in 0 until BAND_COUNT) {
            targetGainsDb[b] = if (isEnabled) settings.getGainDb(b) else 0.0f
        }

        // Check if currently flat and no ramping
        val isTargetFlat = isAllTargetGainsFlat()
        val isCurrentFlat = isAllCurrentGainsFlat()

        if (!isEnabled || (isTargetFlat && isCurrentFlat && !isRamping)) {
            // Bypass mode: direct bulk block copy into outputBuffer without sample-by-sample DSP
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val outputBuffer = replaceOutputBuffer(remaining)

        val bytesPerSample = if (encoding == C.ENCODING_PCM_16BIT) 2 else 4
        val bytesPerFrame = channelCount * bytesPerSample
        val totalFrames = remaining / bytesPerFrame

        var framesProcessed = 0
        while (framesProcessed < totalFrames) {
            val framesInSubBlock = (totalFrames - framesProcessed).coerceAtMost(SUB_BLOCK_FRAMES)

            // 1. Advance gain ramp and recompute coefficients if any band is changing
            updateGainRampAndCoefficients()

            // 2. Process subblock of audio frames through cascaded DF2T biquads
            if (encoding == C.ENCODING_PCM_16BIT) {
                processSubBlockPcm16(inputBuffer, outputBuffer, channelCount, framesInSubBlock)
            } else {
                processSubBlockFloat(inputBuffer, outputBuffer, channelCount, framesInSubBlock)
            }

            framesProcessed += framesInSubBlock
        }

        outputBuffer.flip()
    }

    private fun isAllTargetGainsFlat(): Boolean {
        for (b in 0 until BAND_COUNT) {
            if (abs(targetGainsDb[b]) > 0.0001f) return false
        }
        return true
    }

    private fun isAllCurrentGainsFlat(): Boolean {
        for (b in 0 until BAND_COUNT) {
            if (abs(currentGainsDb[b]) > 0.0001f) return false
        }
        return true
    }

    /**
     * Interpolates gain in dB and recalculates RBJ coefficients for bands that changed.
     */
    private fun updateGainRampAndCoefficients() {
        var stillRamping = false
        for (b in 0 until BAND_COUNT) {
            val diff = targetGainsDb[b] - currentGainsDb[b]
            if (abs(diff) > 0.0001f) {
                stillRamping = true
                val step = diff.coerceIn(-MAX_GAIN_STEP_DB, MAX_GAIN_STEP_DB)
                currentGainsDb[b] += step
                if (abs(targetGainsDb[b] - currentGainsDb[b]) <= 0.0001f) {
                    currentGainsDb[b] = targetGainsDb[b]
                }
                computeCoefficients(b, currentGainsDb[b])
            }
        }
        isRamping = stillRamping
    }

    /**
     * Computes RBJ normalized coefficients for band [bandIndex] given [gainDb].
     */
    private fun computeCoefficients(bandIndex: Int, gainDb: Float) {
        val offset = bandIndex * 5

        // If band is above Nyquist safety threshold, set to linear identity
        if (isBandBypassedByNyquist[bandIndex]) {
            coeffs[offset] = 1.0f     // b0
            coeffs[offset + 1] = 0.0f // b1
            coeffs[offset + 2] = 0.0f // b2
            coeffs[offset + 3] = 0.0f // a1
            coeffs[offset + 4] = 0.0f // a2
            return
        }

        // If gain is virtually 0 dB, filter acts as pure identity pass-through
        if (abs(gainDb) < 0.0001f) {
            coeffs[offset] = 1.0f
            coeffs[offset + 1] = 0.0f
            coeffs[offset + 2] = 0.0f
            coeffs[offset + 3] = 0.0f
            coeffs[offset + 4] = 0.0f
            return
        }

        val a = 10.0f.pow(gainDb / 40.0f)
        val sin = sinW0[bandIndex]
        val cos = cosW0[bandIndex]

        var b0: Float
        var b1: Float
        var b2: Float
        var a0: Float
        var a1: Float
        var a2: Float

        when (bandIndex) {
            0 -> {
                // Low-Shelf (Band 0: 60Hz, S=1.0)
                val alpha = sin / SQRT2
                val twoSqrtAAlpha = 2.0f * sqrt(a) * alpha
                b0 = a * ((a + 1.0f) - (a - 1.0f) * cos + twoSqrtAAlpha)
                b1 = 2.0f * a * ((a - 1.0f) - (a + 1.0f) * cos)
                b2 = a * ((a + 1.0f) - (a - 1.0f) * cos - twoSqrtAAlpha)
                a0 = (a + 1.0f) + (a - 1.0f) * cos + twoSqrtAAlpha
                a1 = -2.0f * ((a - 1.0f) + (a + 1.0f) * cos)
                a2 = (a + 1.0f) + (a - 1.0f) * cos - twoSqrtAAlpha
            }
            4 -> {
                // High-Shelf (Band 4: 14000Hz, S=1.0)
                val alpha = sin / SQRT2
                val twoSqrtAAlpha = 2.0f * sqrt(a) * alpha
                b0 = a * ((a + 1.0f) + (a - 1.0f) * cos + twoSqrtAAlpha)
                b1 = -2.0f * a * ((a - 1.0f) + (a + 1.0f) * cos)
                b2 = a * ((a + 1.0f) + (a - 1.0f) * cos - twoSqrtAAlpha)
                a0 = (a + 1.0f) - (a - 1.0f) * cos + twoSqrtAAlpha
                a1 = 2.0f * ((a - 1.0f) - (a + 1.0f) * cos)
                a2 = (a + 1.0f) - (a - 1.0f) * cos - twoSqrtAAlpha
            }
            else -> {
                // Peaking (Bands 1, 2, 3: 230Hz, 910Hz, 3600Hz, Q=1.4142)
                val alpha = sin / (2.0f * Q_PEAKING)
                b0 = 1.0f + alpha * a
                b1 = -2.0f * cos
                b2 = 1.0f - alpha * a
                a0 = 1.0f + alpha / a
                a1 = -2.0f * cos
                a2 = 1.0f - alpha / a
            }
        }

        // Store normalized coefficients
        val invA0 = 1.0f / a0
        coeffs[offset] = b0 * invA0
        coeffs[offset + 1] = b1 * invA0
        coeffs[offset + 2] = b2 * invA0
        coeffs[offset + 3] = a1 * invA0
        coeffs[offset + 4] = a2 * invA0
    }

    /**
     * Hot path for 16-bit PCM frames.
     */
    private fun processSubBlockPcm16(
        input: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        frameCount: Int
    ) {
        val statesArr = states
        val coeffsArr = coeffs

        for (f in 0 until frameCount) {
            for (c in 0 until channelCount) {
                var sample = input.short / 32768.0f
                val channelStateOffset = c * BAND_COUNT * 2

                for (b in 0 until BAND_COUNT) {
                    val coeffOffset = b * 5
                    val sIdx = channelStateOffset + (b * 2)

                    val b0 = coeffsArr[coeffOffset]
                    val b1 = coeffsArr[coeffOffset + 1]
                    val b2 = coeffsArr[coeffOffset + 2]
                    val a1 = coeffsArr[coeffOffset + 3]
                    val a2 = coeffsArr[coeffOffset + 4]

                    var s1 = statesArr[sIdx]
                    var s2 = statesArr[sIdx + 1]

                    // Direct Form II Transposed difference equations
                    val y = (b0 * sample) + s1
                    val newS1 = (b1 * sample) - (a1 * y) + s2
                    val newS2 = (b2 * sample) - (a2 * y)

                    // Denormal prevention
                    s1 = if (abs(newS1) < DENORMAL_THRESHOLD) 0.0f else newS1
                    s2 = if (abs(newS2) < DENORMAL_THRESHOLD) 0.0f else newS2

                    statesArr[sIdx] = s1
                    statesArr[sIdx + 1] = s2
                    sample = y
                }

                val scaled = (sample * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
                output.putShort(scaled)
            }
        }
    }

    /**
     * Hot path for 32-bit Float PCM frames.
     */
    private fun processSubBlockFloat(
        input: ByteBuffer,
        output: ByteBuffer,
        channelCount: Int,
        frameCount: Int
    ) {
        val statesArr = states
        val coeffsArr = coeffs

        for (f in 0 until frameCount) {
            for (c in 0 until channelCount) {
                var sample = input.float
                val channelStateOffset = c * BAND_COUNT * 2

                for (b in 0 until BAND_COUNT) {
                    val coeffOffset = b * 5
                    val sIdx = channelStateOffset + (b * 2)

                    val b0 = coeffsArr[coeffOffset]
                    val b1 = coeffsArr[coeffOffset + 1]
                    val b2 = coeffsArr[coeffOffset + 2]
                    val a1 = coeffsArr[coeffOffset + 3]
                    val a2 = coeffsArr[coeffOffset + 4]

                    var s1 = statesArr[sIdx]
                    var s2 = statesArr[sIdx + 1]

                    // Direct Form II Transposed difference equations
                    val y = (b0 * sample) + s1
                    val newS1 = (b1 * sample) - (a1 * y) + s2
                    val newS2 = (b2 * sample) - (a2 * y)

                    // Denormal prevention
                    s1 = if (abs(newS1) < DENORMAL_THRESHOLD) 0.0f else newS1
                    s2 = if (abs(newS2) < DENORMAL_THRESHOLD) 0.0f else newS2

                    statesArr[sIdx] = s1
                    statesArr[sIdx + 1] = s2
                    sample = y
                }

                output.putFloat(sample)
            }
        }
    }
}
