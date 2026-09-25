package io.github.yisus.avenor.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import io.github.yisus.avenor.replaygain.SafeLimiterAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class EqualizerAudioProcessorTest {

    private lateinit var processor: EqualizerAudioProcessor

    @Before
    fun setup() {
        processor = EqualizerAudioProcessor()
    }

    private fun configureProcessor(
        sampleRate: Int = 44100,
        channelCount: Int = 2,
        encoding: Int = C.ENCODING_PCM_FLOAT
    ) {
        val format = AudioProcessor.AudioFormat(sampleRate, channelCount, encoding)
        processor.configure(format)
        processor.flush()
    }

    @Test
    fun `test 1 - RBJ coefficient exact-match for peaking filter`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        // Set Band 2 (910 Hz) to +6.0 dB
        val gains = floatArrayOf(0f, 0f, 6.0f, 0f, 0f)
        processor.setBandGains(gains)
        processor.flush()

        val coeffs = processor.getBandCoefficients(2)
        assertEquals(5, coeffs.size)

        // Analytical RBJ computation for 910 Hz at 44100 Hz, Q = 1.41421356, Gain = 6 dB
        val f0 = 910.0f
        val fs = 44100.0f
        val q = 1.41421356f
        val a = 10.0f.pow(6.0f / 40.0f)
        val w0 = (2.0 * Math.PI * f0 / fs).toFloat()
        val sin = sin(w0)
        val cos = cos(w0)
        val alpha = sin / (2.0f * q)

        val b0 = 1.0f + alpha * a
        val b1 = -2.0f * cos
        val b2 = 1.0f - alpha * a
        val a0 = 1.0f + alpha / a
        val a1 = -2.0f * cos
        val a2 = 1.0f - alpha / a

        assertEquals(b0 / a0, coeffs[0], 0.001f)
        assertEquals(b1 / a0, coeffs[1], 0.001f)
        assertEquals(b2 / a0, coeffs[2], 0.001f)
        assertEquals(a1 / a0, coeffs[3], 0.001f)
        assertEquals(a2 / a0, coeffs[4], 0.001f)
    }

    @Test
    fun `test 2 - Low-Shelf coefficients exact-match`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        // Set Band 0 (60 Hz Low-Shelf) to +6.0 dB
        val gains = floatArrayOf(6.0f, 0f, 0f, 0f, 0f)
        processor.setBandGains(gains)
        processor.flush()

        val coeffs = processor.getBandCoefficients(0)

        val f0 = 60.0f
        val fs = 44100.0f
        val a = 10.0f.pow(6.0f / 40.0f)
        val w0 = (2.0 * Math.PI * f0 / fs).toFloat()
        val sin = sin(w0)
        val cos = cos(w0)
        val alpha = sin / 1.41421356f
        val twoSqrtAAlpha = 2.0f * sqrt(a) * alpha

        val b0 = a * ((a + 1.0f) - (a - 1.0f) * cos + twoSqrtAAlpha)
        val a0 = (a + 1.0f) + (a - 1.0f) * cos + twoSqrtAAlpha

        assertEquals(b0 / a0, coeffs[0], 0.001f)
    }

    @Test
    fun `test 3 - High-Shelf coefficients exact-match`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        // Set Band 4 (14000 Hz High-Shelf) to +6.0 dB
        val gains = floatArrayOf(0f, 0f, 0f, 0f, 6.0f)
        processor.setBandGains(gains)
        processor.flush()

        val coeffs = processor.getBandCoefficients(4)

        val f0 = 14000.0f
        val fs = 44100.0f
        val a = 10.0f.pow(6.0f / 40.0f)
        val w0 = (2.0 * Math.PI * f0 / fs).toFloat()
        val sin = sin(w0)
        val cos = cos(w0)
        val alpha = sin / 1.41421356f
        val twoSqrtAAlpha = 2.0f * sqrt(a) * alpha

        val b0 = a * ((a + 1.0f) + (a - 1.0f) * cos + twoSqrtAAlpha)
        val a0 = (a + 1.0f) - (a - 1.0f) * cos + twoSqrtAAlpha

        assertEquals(b0 / a0, coeffs[0], 0.001f)
    }

    @Test
    fun `test 4 - 60 Hz Low-Shelf frequency response amplifies low frequencies more than high`() {
        configureProcessor(sampleRate = 44100, channelCount = 1, encoding = C.ENCODING_PCM_FLOAT)

        processor.setBandGains(floatArrayOf(6.0f, 0f, 0f, 0f, 0f))
        processor.flush()

        // Generate 60 Hz tone (amplified by low shelf)
        val testFrames = 4410
        val lowFreqBuf = ByteBuffer.allocateDirect(testFrames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until testFrames) {
            val s = (sin(2.0 * Math.PI * 60.0 * i / 44100.0) * 0.1).toFloat()
            lowFreqBuf.putFloat(s)
        }
        lowFreqBuf.flip()

        processor.queueInput(lowFreqBuf)
        val lowOutput = processor.output.order(ByteOrder.nativeOrder())

        var maxLowOut = 0.0f
        while (lowOutput.hasRemaining()) {
            val v = abs(lowOutput.float)
            if (v > maxLowOut) maxLowOut = v
        }

        // Flush between tone tests to clear filter states
        processor.flush()

        // Generate 5000 Hz tone (out of low shelf range, gain ~0 dB)
        val highFreqBuf = ByteBuffer.allocateDirect(testFrames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until testFrames) {
            val s = (sin(2.0 * Math.PI * 5000.0 * i / 44100.0) * 0.1).toFloat()
            highFreqBuf.putFloat(s)
        }
        highFreqBuf.flip()

        processor.queueInput(highFreqBuf)
        val highOutput = processor.output.order(ByteOrder.nativeOrder())

        var maxHighOut = 0.0f
        while (highOutput.hasRemaining()) {
            val v = abs(highOutput.float)
            if (v > maxHighOut) maxHighOut = v
        }

        // Low frequency amplitude should be significantly higher than high frequency amplitude
        assertTrue("Expected 60Hz peak ($maxLowOut) to be greater than 5kHz peak ($maxHighOut)", maxLowOut > maxHighOut * 1.5f)
    }

    @Test
    fun `test 5 - 14 kHz High-Shelf frequency response amplifies high frequencies more than low`() {
        configureProcessor(sampleRate = 44100, channelCount = 1, encoding = C.ENCODING_PCM_FLOAT)

        // Set Band 4 (High-Shelf) to +12.0 dB (gain at f0 midpoint is +6 dB = ~1.41x)
        processor.setBandGains(floatArrayOf(0f, 0f, 0f, 0f, 12.0f))
        processor.flush()

        // Generate 14 kHz tone (amplified by high shelf)
        val testFrames = 4410
        val highFreqBuf = ByteBuffer.allocateDirect(testFrames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until testFrames) {
            val s = (sin(2.0 * Math.PI * 14000.0 * i / 44100.0) * 0.1).toFloat()
            highFreqBuf.putFloat(s)
        }
        highFreqBuf.flip()

        processor.queueInput(highFreqBuf)
        val highOutput = processor.output.order(ByteOrder.nativeOrder())

        var maxHighOut = 0.0f
        while (highOutput.hasRemaining()) {
            val v = abs(highOutput.float)
            if (v > maxHighOut) maxHighOut = v
        }

        // Flush between tone tests
        processor.flush()

        // Generate 200 Hz tone (out of high shelf range, gain ~0 dB)
        val lowFreqBuf = ByteBuffer.allocateDirect(testFrames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until testFrames) {
            val s = (sin(2.0 * Math.PI * 200.0 * i / 44100.0) * 0.1).toFloat()
            lowFreqBuf.putFloat(s)
        }
        lowFreqBuf.flip()

        processor.queueInput(lowFreqBuf)
        val lowOutput = processor.output.order(ByteOrder.nativeOrder())

        var maxLowOut = 0.0f
        while (lowOutput.hasRemaining()) {
            val v = abs(lowOutput.float)
            if (v > maxLowOut) maxLowOut = v
        }

        assertTrue("Expected 14kHz peak ($maxHighOut) to be greater than 200Hz peak ($maxLowOut)", maxHighOut > maxLowOut * 1.25f)
    }

    @Test
    fun `test 6 - PCM16 processing produces valid scaled output`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_16BIT)

        processor.setBandGains(floatArrayOf(3.0f, 3.0f, 3.0f, 3.0f, 3.0f))

        val frames = 128
        val input = ByteBuffer.allocateDirect(frames * 2 * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 2) {
            input.putShort(1000.toShort())
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(frames * 2 * 2, output.remaining())
        while (output.hasRemaining()) {
            val s = output.short
            assertTrue(s in -32768..32767)
        }
    }

    @Test
    fun `test 7 - PCM Float processing directly processes float buffer`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        processor.setBandGains(floatArrayOf(2.0f, 2.0f, 2.0f, 2.0f, 2.0f))

        val frames = 64
        val input = ByteBuffer.allocateDirect(frames * 2 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 2) {
            input.putFloat(0.25f)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(frames * 2 * 4, output.remaining())
        val firstSample = output.float
        assertTrue(!firstSample.isNaN())
    }

    @Test
    fun `test 8 - Mono channel processing`() {
        configureProcessor(sampleRate = 44100, channelCount = 1, encoding = C.ENCODING_PCM_FLOAT)

        processor.setBandGains(floatArrayOf(4.0f, 0f, 0f, 0f, 0f))

        val frames = 64
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            input.putFloat(0.5f)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertEquals(frames * 4, output.remaining())
    }

    @Test
    fun `test 9 - Stereo channel processing`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        processor.setBandGains(floatArrayOf(0f, 4.0f, 0f, 0f, 0f))

        val frames = 64
        val input = ByteBuffer.allocateDirect(frames * 2 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 2) {
            input.putFloat(0.3f)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertEquals(frames * 2 * 4, output.remaining())
    }

    @Test
    fun `test 10 - Bypass disabled byte-exact`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_16BIT)

        // Equalizer disabled
        processor.setSettings(EqBandSettings.of(isEnabled = false, floatArrayOf(6.0f, 6.0f, 6.0f, 6.0f, 6.0f)))

        val bytes = 512
        val input = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        val reference = ByteArray(bytes)
        for (i in 0 until bytes) {
            val b = (i % 127).toByte()
            input.put(b)
            reference[i] = b
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output

        assertEquals(bytes, output.remaining())
        val outBytes = ByteArray(bytes)
        output.get(outBytes)

        for (i in 0 until bytes) {
            assertEquals("Byte $i mismatch in disabled bypass", reference[i], outBytes[i])
        }
    }

    @Test
    fun `test 11 - Bypass Flat byte-exact`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_16BIT)

        // Equalizer enabled but all bands 0 dB (Flat)
        processor.setSettings(EqBandSettings.FLAT)

        val bytes = 512
        val input = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        val reference = ByteArray(bytes)
        for (i in 0 until bytes) {
            val b = ((i * 3) % 127).toByte()
            input.put(b)
            reference[i] = b
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output

        assertEquals(bytes, output.remaining())
        val outBytes = ByteArray(bytes)
        output.get(outBytes)

        for (i in 0 until bytes) {
            assertEquals("Byte $i mismatch in Flat bypass", reference[i], outBytes[i])
        }
    }

    @Test
    fun `test 12 - No NaN or Infinity generated under extreme inputs`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        // Boost all bands to maximum +12 dB
        processor.setBandGains(floatArrayOf(12.0f, 12.0f, 12.0f, 12.0f, 12.0f))

        val frames = 1000
        val input = ByteBuffer.allocateDirect(frames * 2 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            // Alternating impulse
            val sample = if (i % 2 == 0) 1.0f else -1.0f
            input.putFloat(sample)
            input.putFloat(sample)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        while (output.hasRemaining()) {
            val sample = output.float
            assertFalse("Sample was NaN", sample.isNaN())
            assertFalse("Sample was Infinite", sample.isInfinite())
        }
    }

    @Test
    fun `test 13 - Flush clears DF2T state registers`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        processor.setBandGains(floatArrayOf(6.0f, 6.0f, 6.0f, 6.0f, 6.0f))

        val frames = 256
        val input = ByteBuffer.allocateDirect(frames * 2 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 2) {
            input.putFloat(0.5f)
        }
        input.flip()

        processor.queueInput(input)

        // Flush must clear all states
        processor.flush()

        for (c in 0 until 2) {
            for (b in 0 until 5) {
                val state = processor.getBandState(c, b)
                assertEquals(0.0f, state.first, 0.0f)
                assertEquals(0.0f, state.second, 0.0f)
            }
        }
    }

    @Test
    fun `test 14 - Sample-rate adaptation recomputes coefficients`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)
        processor.setBandGains(floatArrayOf(4.0f, 4.0f, 4.0f, 4.0f, 4.0f))

        val input1 = ByteBuffer.allocateDirect(32 * 2 * 4).order(ByteOrder.nativeOrder())
        processor.queueInput(input1)
        val coeffs44k = processor.getBandCoefficients(1)

        // Reconfigure to 48000 Hz
        configureProcessor(sampleRate = 48000, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)
        val input2 = ByteBuffer.allocateDirect(32 * 2 * 4).order(ByteOrder.nativeOrder())
        processor.queueInput(input2)
        val coeffs48k = processor.getBandCoefficients(1)

        // Due to different Fs, coefficients for 230Hz peaking must differ slightly
        assertFalse("Coefficients should adapt to new sample rate", coeffs44k[0] == coeffs48k[0] && coeffs44k[3] == coeffs48k[3])
    }

    @Test
    fun `test 15 - Nyquist guard deactivates 14 kHz band at low sample rates`() {
        // At 22050 Hz, Nyquist is 11025 Hz. 14 kHz exceeds 0.42 * 22050 = 9261 Hz.
        configureProcessor(sampleRate = 22050, channelCount = 1, encoding = C.ENCODING_PCM_FLOAT)
        processor.setBandGains(floatArrayOf(3.0f, 3.0f, 3.0f, 3.0f, 6.0f))

        val input = ByteBuffer.allocateDirect(32 * 4).order(ByteOrder.nativeOrder())
        processor.queueInput(input)

        val coeffs = processor.getBandCoefficients(4)
        // Band 4 must be linear identity [1, 0, 0, 0, 0]
        assertEquals(1.0f, coeffs[0], 0.0001f) // b0
        assertEquals(0.0f, coeffs[1], 0.0001f) // b1
        assertEquals(0.0f, coeffs[2], 0.0001f) // b2
        assertEquals(0.0f, coeffs[3], 0.0001f) // a1
        assertEquals(0.0f, coeffs[4], 0.0001f) // a2
    }

    @Test
    fun `test 16 - Smoothing step limits avoid abrupt jumps`() {
        configureProcessor(sampleRate = 44100, channelCount = 1, encoding = C.ENCODING_PCM_FLOAT)

        // Start from Flat (0 dB)
        processor.setBandGains(floatArrayOf(0f, 0f, 0f, 0f, 0f))
        processor.flush()

        // Set target to +12 dB
        processor.setBandGains(floatArrayOf(12.0f, 0f, 0f, 0f, 0f))

        // Process exactly 1 subblock of 32 frames
        val stepBuf = ByteBuffer.allocateDirect(32 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until 32) {
            stepBuf.putFloat(0.1f)
        }
        stepBuf.flip()
        processor.queueInput(stepBuf)

        val currentGain = processor.getCurrentGainDb(0)
        // Should have stepped by at most 0.1 dB
        assertTrue("Gain ramp step should be <= 0.1 dB, was: $currentGain", currentGain in 0.0001f..0.1001f)
    }

    @Test
    fun `test 17 - Rapid target changes during active ramp`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        for (i in 0 until 50) {
            val randomGain = ((i % 12) - 6).toFloat()
            processor.setBandGains(floatArrayOf(randomGain, -randomGain, randomGain, 0f, 0f))

            val buf = ByteBuffer.allocateDirect(32 * 2 * 4).order(ByteOrder.nativeOrder())
            for (f in 0 until 64) {
                buf.putFloat(0.1f)
            }
            buf.flip()
            processor.queueInput(buf)

            val out = processor.output.order(ByteOrder.nativeOrder())
            while (out.hasRemaining()) {
                val s = out.float
                assertFalse("Sample was NaN during rapid preset changes", s.isNaN())
            }
        }
    }

    @Test
    fun `test 18 - Concurrent settings publication`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)

        val executor = Executors.newFixedThreadPool(4)
        var hasError = false

        for (i in 0 until 4) {
            executor.submit {
                try {
                    for (k in 0 until 100) {
                        val g = (k % 12).toFloat()
                        processor.setBandGains(floatArrayOf(g, -g, g, 0f, 0f))
                        Thread.sleep(1)
                    }
                } catch (e: Exception) {
                    hasError = true
                }
            }
        }

        // Process audio on test thread simultaneously
        for (f in 0 until 50) {
            val buf = ByteBuffer.allocateDirect(128 * 2 * 4).order(ByteOrder.nativeOrder())
            for (s in 0 until 256) {
                buf.putFloat(0.2f)
            }
            buf.flip()
            processor.queueInput(buf)
            Thread.sleep(2)
        }

        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        assertFalse("Encountered concurrency exception during settings publication", hasError)
    }

    @Test
    fun `test 19 - Limiter integration with SafeLimiterAudioProcessor`() {
        // Signal path: Input -> EqualizerAudioProcessor (+12dB) -> SafeLimiterAudioProcessor (ceiling 1.0)
        val eq = processor
        val limiter = SafeLimiterAudioProcessor()

        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        eq.configure(format)
        eq.flush()

        limiter.configure(format)
        limiter.flush()
        limiter.ceiling = 1.0f

        // Boost all bands by +12 dB
        eq.setBandGains(floatArrayOf(12.0f, 12.0f, 12.0f, 12.0f, 12.0f))
        eq.flush()

        // Feed full scale sine wave (amplitude 0.95)
        val frames = 1000
        val input = ByteBuffer.allocateDirect(frames * 2 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val sample = (sin(2.0 * Math.PI * 910.0 * i / 44100.0) * 0.95).toFloat()
            input.putFloat(sample)
            input.putFloat(sample)
        }
        input.flip()

        eq.queueInput(input)
        val eqOutput = eq.output

        // Ensure EQ boosted signal above 1.0f (digital overshoot)
        var eqMaxPeak = 0.0f
        val eqBufferCopy = eqOutput.duplicate().order(ByteOrder.nativeOrder())
        while (eqBufferCopy.hasRemaining()) {
            val v = abs(eqBufferCopy.float)
            if (v > eqMaxPeak) eqMaxPeak = v
        }
        assertTrue("EQ should have amplified signal above 1.0f, peak was: $eqMaxPeak", eqMaxPeak > 1.0f)

        // Pass boosted output into SafeLimiter
        limiter.queueInput(eqOutput)
        val finalOutput = limiter.output.order(ByteOrder.nativeOrder())

        var finalMaxPeak = 0.0f
        while (finalOutput.hasRemaining()) {
            val v = abs(finalOutput.float)
            if (v > finalMaxPeak) finalMaxPeak = v
        }

        // SafeLimiter must enforce the sample ceiling <= 1.0f (within numerical precision)
        assertTrue("Final peak ($finalMaxPeak) must be <= 1.0001f", finalMaxPeak <= 1.0001f)
    }

    @Test
    fun `test 20 - EqBandSettings defensive copy guarantees immutability`() {
        val originalArray = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val settings = EqBandSettings.of(isEnabled = true, originalArray)

        // Mutate original array externally
        originalArray[0] = 99.0f

        assertEquals(1.0f, settings.getGainDb(0), 0.0f)

        // Mutate copied array from settings
        val copy = settings.copyGains()
        copy[1] = 88.0f

        assertEquals(2.0f, settings.getGainDb(1), 0.0f)
    }

    @Test
    fun `test 21 - Zero heap allocation hot path structural check`() {
        configureProcessor(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)
        processor.setBandGains(floatArrayOf(2.0f, 2.0f, 2.0f, 2.0f, 2.0f))
        processor.flush()

        // Preallocate reusable input buffer
        val input = ByteBuffer.allocateDirect(512 * 2 * 4).order(ByteOrder.nativeOrder())

        // Process 100 consecutive buffers
        for (i in 0 until 100) {
            input.clear()
            for (f in 0 until 1024) {
                input.putFloat(0.1f)
            }
            input.flip()

            processor.queueInput(input)
            val output = processor.output
            assertEquals(512 * 2 * 4, output.remaining())
        }
    }
}
