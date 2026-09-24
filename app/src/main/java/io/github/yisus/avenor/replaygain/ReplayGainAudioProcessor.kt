package io.github.yisus.avenor.replaygain

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * ReplayGain AudioProcessor for Avenor.
 *
 * Applies perceived loudness gain adjustments to decoded PCM audio samples
 * based on [ReplayGainPolicy].
 *
 * Characteristics:
 * - Operates strictly at the PCM level (16-bit integer PCM and 32-bit Float PCM).
 * - Thread-safe target gain updates outside the audio thread without locking or I/O.
 * - Smooth sample-level gain ramping (~10 ms) across track transitions to eliminate clicks/pops.
 * - Direct zero-allocation buffer pass-through when unity gain (bypass) is active.
 * - NEVER manipulates [androidx.media3.common.Player.getVolume] or [androidx.media3.common.Player.setVolume].
 */
@OptIn(UnstableApi::class)
class ReplayGainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var targetGain: Float = 1.0f
        private set

    @Volatile
    var currentGain: Float = 1.0f
        private set

    @Volatile
    var mode: ReplayGainMode = ReplayGainMode.TRACK

    @Volatile
    var preampDb: Float = 0.0f

    private var gainStep: Float = 0.0f
    private var rampFramesRemaining: Int = 0

    /**
     * Updates target linear gain directly.
     */
    fun setTargetLinearGain(gain: Float) {
        val safeGain = gain.coerceAtLeast(0.0f)
        if (abs(safeGain - targetGain) < 0.0001f) {
            return
        }
        targetGain = safeGain
        scheduleGainRamp()
    }

    /**
     * Updates target gain in decibels. Converted via [ReplayGainPolicy.dbToLinearGain].
     */
    fun setEffectiveGainDb(gainDb: Float) {
        val linear = ReplayGainPolicy.dbToLinearGain(gainDb)
        setTargetLinearGain(linear)
    }

    /**
     * High-level resolver applying [ReplayGainPolicy] without duplicating policy logic.
     */
    fun updateGainFromSong(
        mode: ReplayGainMode,
        trackGain: Float?,
        albumGain: Float?,
        preampDb: Float = this.preampDb
    ) {
        this.mode = mode
        this.preampDb = preampDb
        val effectiveGainDb = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = mode,
            trackGain = trackGain,
            albumGain = albumGain,
            preampDb = preampDb
        )
        setEffectiveGainDb(effectiveGainDb)
    }

    private fun scheduleGainRamp() {
        val sampleRate = if (inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET && inputAudioFormat.sampleRate > 0) {
            inputAudioFormat.sampleRate
        } else 44100
        // Smooth transition over ~10ms (e.g. 441 frames at 44.1kHz)
        val totalRampFrames = (sampleRate * 0.010f).toInt().coerceAtLeast(64)
        gainStep = (targetGain - currentGain) / totalRampFrames
        rampFramesRemaining = totalRampFrames
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val encoding = inputAudioFormat.encoding
        val channelCount = inputAudioFormat.channelCount
        if (channelCount <= 0) return

        // Bypass optimization: if current and target gain are 1.0f (unity/bypass) and no ramp is active
        if (currentGain == 1.0f && targetGain == 1.0f && rampFramesRemaining == 0) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val outputBuffer = replaceOutputBuffer(remaining)

        if (encoding == C.ENCODING_PCM_16BIT) {
            val bytesPerFrame = channelCount * 2
            val frameCount = remaining / bytesPerFrame
            for (f in 0 until frameCount) {
                val g = currentGain
                for (c in 0 until channelCount) {
                    val sample = inputBuffer.short
                    val scaled = (sample * g).toInt()
                    val clamped = scaled.coerceIn(-32768, 32767).toShort()
                    outputBuffer.putShort(clamped)
                }
                if (rampFramesRemaining > 0) {
                    currentGain += gainStep
                    rampFramesRemaining--
                    if (rampFramesRemaining == 0) currentGain = targetGain
                }
            }
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            val bytesPerFrame = channelCount * 4
            val frameCount = remaining / bytesPerFrame
            for (f in 0 until frameCount) {
                val g = currentGain
                for (c in 0 until channelCount) {
                    val sample = inputBuffer.float
                    val scaled = sample * g
                    outputBuffer.putFloat(scaled)
                }
                if (rampFramesRemaining > 0) {
                    currentGain += gainStep
                    rampFramesRemaining--
                    if (rampFramesRemaining == 0) currentGain = targetGain
                }
            }
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        currentGain = targetGain
        rampFramesRemaining = 0
        gainStep = 0.0f
    }

    override fun onReset() {
        targetGain = 1.0f
        currentGain = 1.0f
        mode = ReplayGainMode.TRACK
        preampDb = 0.0f
        rampFramesRemaining = 0
        gainStep = 0.0f
    }
}
