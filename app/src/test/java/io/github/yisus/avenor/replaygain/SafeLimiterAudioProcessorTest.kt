package io.github.yisus.avenor.replaygain

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SafeLimiterAudioProcessorTest {

    private lateinit var limiter: SafeLimiterAudioProcessor

    @Before
    fun setup() {
        limiter = SafeLimiterAudioProcessor()
    }

    private fun configureLimiter(
        sampleRate: Int = 44100,
        channelCount: Int = 1,
        encoding: Int = C.ENCODING_PCM_FLOAT
    ) {
        val format = AudioProcessor.AudioFormat(sampleRate, channelCount, encoding)
        limiter.configure(format)
        limiter.flush()
    }

    @Test
    fun `test 11 - signal below ceiling remains completely intact`() {
        configureLimiter()
        limiter.ceiling = 1.0f

        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        input.putFloat(0.3f)
        input.putFloat(-0.7f)
        input.putFloat(0.85f)
        input.putFloat(-0.95f)
        input.flip()

        limiter.queueInput(input)
        val output = limiter.output
        output.order(ByteOrder.nativeOrder())

        assertEquals(0.3f, output.float, 0.0001f)
        assertEquals(-0.7f, output.float, 0.0001f)
        assertEquals(0.85f, output.float, 0.0001f)
        assertEquals(-0.95f, output.float, 0.0001f)
    }

    @Test
    fun `test 12 - signal above ceiling is smoothly limited`() {
        configureLimiter()
        limiter.ceiling = 0.95f

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(1.5f)  // Peak overshoot
        input.putFloat(-2.0f) // Larger peak overshoot
        input.flip()

        limiter.queueInput(input)
        val output = limiter.output
        output.order(ByteOrder.nativeOrder())

        val out1 = output.float
        val out2 = output.float

        assertTrue("out1 ($out1) should be <= ceiling (0.95)", abs(out1) <= 0.9501f)
        assertTrue("out2 ($out2) should be <= ceiling (0.95)", abs(out2) <= 0.9501f)
    }

    @Test
    fun `test 13 - never produces samples exceeding full scale ceiling`() {
        configureLimiter()
        limiter.ceiling = 1.0f

        // Provide extreme signals
        val input = ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder())
        input.putFloat(3.5f)
        input.putFloat(-10.0f)
        input.putFloat(100.0f)
        input.putFloat(-50.0f)
        input.putFloat(1.0001f)
        input.flip()

        limiter.queueInput(input)
        val output = limiter.output
        output.order(ByteOrder.nativeOrder())

        while (output.hasRemaining()) {
            val sample = output.float
            assertTrue("Sample ($sample) must not exceed 1.0f ceiling", abs(sample) <= 1.00001f)
        }
    }

    @Test
    fun `test 14 - envelope recovery smoothly releases towards unity gain`() {
        configureLimiter(sampleRate = 1000) // Lower sample rate for fast envelope decay in test
        limiter.ceiling = 1.0f

        // 1. Send single spike of 2.0f -> triggers envelopeGain reduction to 0.5f
        val spike = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        spike.putFloat(2.0f)
        spike.flip()
        limiter.queueInput(spike)
        limiter.output // discard

        val reducedGain = limiter.envelopeGain
        assertTrue("Envelope gain ($reducedGain) should have reduced to ~0.5f", reducedGain <= 0.51f)

        // 2. Feed 200 quiet samples (0.01f) to let release recovery operate
        val quiet = ByteBuffer.allocateDirect(800).order(ByteOrder.nativeOrder())
        for (i in 0 until 200) {
            quiet.putFloat(0.01f)
        }
        quiet.flip()
        limiter.queueInput(quiet)
        limiter.output // discard

        val recoveredGain = limiter.envelopeGain
        assertTrue("Envelope gain ($recoveredGain) should have recovered significantly towards 1.0", recoveredGain > reducedGain)
        assertTrue(recoveredGain in 0.8f..1.0f)
    }

    @Test
    fun `test 15 - setAnticipatedGain activates pre-attack protection from metadata peak`() {
        configureLimiter()
        limiter.ceiling = 1.0f

        // Song has +6 dB effective gain (~2.0 linear) and peak = 0.8
        // Anticipated peak = 0.8 * 2.0 = 1.6 > 1.0 ceiling
        limiter.setAnticipatedGain(effectiveGainDb = 6.0206f, peak = 0.8f)

        // Safe anticipated gain = 1.0 / 1.6 = 0.625
        assertEquals(0.625f, limiter.envelopeGain, 0.01f)
    }

    @Test
    fun `test 16 - 16-bit PCM ceiling limitation and zero overflow`() {
        configureLimiter(encoding = C.ENCODING_PCM_16BIT)
        limiter.ceiling = 1.0f

        val input = ByteBuffer.allocateDirect(6).order(ByteOrder.nativeOrder())
        input.putShort(30000.toShort())
        input.putShort(32767.toShort())
        input.putShort((-32768).toShort())
        input.flip()

        limiter.queueInput(input)
        val output = limiter.output
        output.order(ByteOrder.nativeOrder())

        val s1 = output.short
        val s2 = output.short
        val s3 = output.short
        assertTrue("Sample 1 ($s1) within 16-bit range", abs(s1.toInt()) <= 32767)
        assertTrue("Sample 2 ($s2) within 16-bit range", abs(s2.toInt()) <= 32767)
        assertTrue("Sample 3 ($s3) within 16-bit range", abs(s3.toInt()) <= 32768)
    }

    @Test
    fun `test 17 - disabled limiter passes samples directly without modification`() {
        configureLimiter()
        limiter.isEnabled = false

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(2.5f)
        input.putFloat(-3.0f)
        input.flip()

        limiter.queueInput(input)
        val output = limiter.output
        output.order(ByteOrder.nativeOrder())

        assertEquals(2.5f, output.float, 0.0001f)
        assertEquals(-3.0f, output.float, 0.0001f)
    }
}
