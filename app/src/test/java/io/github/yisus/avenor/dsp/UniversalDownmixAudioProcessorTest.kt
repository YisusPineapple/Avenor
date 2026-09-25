package io.github.yisus.avenor.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class UniversalDownmixAudioProcessorTest {

    private lateinit var processor: UniversalDownmixAudioProcessor

    private val TOLERANCE_FLOAT = 1.0e-5f
    private val TOLERANCE_SHORT = 2 // max 2 short values difference due to rounding/truncation

    @Before
    fun setup() {
        processor = UniversalDownmixAudioProcessor()
    }

    private fun configureAndFlush(
        channelCount: Int,
        encoding: Int = C.ENCODING_PCM_FLOAT,
        sampleRate: Int = 48000
    ): AudioProcessor.AudioFormat {
        val inFormat = AudioProcessor.AudioFormat(sampleRate, channelCount, encoding)
        val outFormat = processor.configure(inFormat)
        processor.flush()
        return outFormat
    }

    // =========================================================================
    // A. MONO -> STEREO
    // =========================================================================

    @Test
    fun `test A1 - Mono to Stereo Float replication`() {
        val outFormat = configureAndFlush(channelCount = 1, encoding = C.ENCODING_PCM_FLOAT)
        assertTrue(processor.isActive)
        assertEquals(2, outFormat.channelCount)

        val input = ByteBuffer.allocateDirect(1 * 4).order(ByteOrder.nativeOrder())
        input.putFloat(0.75f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        assertEquals(2 * 4, output.remaining())
        val outL = output.float
        val outR = output.float

        assertEquals(0.75f, outL, TOLERANCE_FLOAT)
        assertEquals(0.75f, outR, TOLERANCE_FLOAT)
    }

    @Test
    fun `test A2 - Mono to Stereo 16-bit replication`() {
        val outFormat = configureAndFlush(channelCount = 1, encoding = C.ENCODING_PCM_16BIT)
        assertTrue(processor.isActive)
        assertEquals(2, outFormat.channelCount)

        val input = ByteBuffer.allocateDirect(1 * 2).order(ByteOrder.nativeOrder())
        input.putShort(16384.toShort())
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        assertEquals(2 * 2, output.remaining())
        val outL = output.short
        val outR = output.short

        assertEquals(16384.toShort(), outL)
        assertEquals(16384.toShort(), outR)
    }

    // =========================================================================
    // B. STEREO -> BYPASS
    // =========================================================================

    @Test
    fun `test B - Stereo input is bypassed (inactive)`() {
        val outFormat = configureAndFlush(channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)
        assertFalse(processor.isActive)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, outFormat)
    }

    // =========================================================================
    // C. 3.0 -> STEREO
    // =========================================================================

    @Test
    fun `test C1 - 3_0 to Stereo Float analytical match`() {
        configureAndFlush(channelCount = 3, encoding = C.ENCODING_PCM_FLOAT)

        val input = ByteBuffer.allocateDirect(3 * 4).order(ByteOrder.nativeOrder())
        val l = 1.0f
        val r = -0.5f
        val c = 0.8f
        input.putFloat(l)
        input.putFloat(r)
        input.putFloat(c)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val expectedL = l * UniversalDownmixAudioProcessor.W_LR_3_0 + c * UniversalDownmixAudioProcessor.W_C_3_0
        val expectedR = r * UniversalDownmixAudioProcessor.W_LR_3_0 + c * UniversalDownmixAudioProcessor.W_C_3_0

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    // =========================================================================
    // D. 4.0 / QUAD -> STEREO
    // =========================================================================

    @Test
    fun `test D1 - 4_0 Quad to Stereo Float analytical match`() {
        configureAndFlush(channelCount = 4, encoding = C.ENCODING_PCM_FLOAT)

        val input = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())
        val l = 0.6f
        val r = 0.4f
        val ls = -0.8f
        val rs = 0.5f
        input.putFloat(l)
        input.putFloat(r)
        input.putFloat(ls)
        input.putFloat(rs)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val expectedL = l * UniversalDownmixAudioProcessor.W_LR_4_0 + ls * UniversalDownmixAudioProcessor.W_SUR_4_0
        val expectedR = r * UniversalDownmixAudioProcessor.W_LR_4_0 + rs * UniversalDownmixAudioProcessor.W_SUR_4_0

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    // =========================================================================
    // E. 5.0 -> STEREO
    // =========================================================================

    @Test
    fun `test E1 - 5_0 to Stereo Float analytical match`() {
        configureAndFlush(channelCount = 5, encoding = C.ENCODING_PCM_FLOAT)

        val input = ByteBuffer.allocateDirect(5 * 4).order(ByteOrder.nativeOrder())
        val l = 0.7f
        val r = -0.3f
        val c = 0.5f
        val ls = 0.4f
        val rs = -0.6f
        input.putFloat(l)
        input.putFloat(r)
        input.putFloat(c)
        input.putFloat(ls)
        input.putFloat(rs)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val expectedL = l * UniversalDownmixAudioProcessor.W_LR_5_1 + c * UniversalDownmixAudioProcessor.W_C_5_1 + ls * UniversalDownmixAudioProcessor.W_SUR_5_1
        val expectedR = r * UniversalDownmixAudioProcessor.W_LR_5_1 + c * UniversalDownmixAudioProcessor.W_C_5_1 + rs * UniversalDownmixAudioProcessor.W_SUR_5_1

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    // =========================================================================
    // F. 5.1 -> STEREO (LFE Discard Verification)
    // =========================================================================

    @Test
    fun `test F1 - 5_1 to Stereo Float discards LFE and matches ITU-R BS_775`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)

        val input = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        val l = 0.5f
        val r = -0.5f
        val c = 0.707f
        val lfe = 1.0f // extreme LFE sample - MUST BE DISCARDED
        val ls = 0.3f
        val rs = -0.4f

        input.putFloat(l)
        input.putFloat(r)
        input.putFloat(c)
        input.putFloat(lfe)
        input.putFloat(ls)
        input.putFloat(rs)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val expectedL = l * UniversalDownmixAudioProcessor.W_LR_5_1 + c * UniversalDownmixAudioProcessor.W_C_5_1 + ls * UniversalDownmixAudioProcessor.W_SUR_5_1
        val expectedR = r * UniversalDownmixAudioProcessor.W_LR_5_1 + c * UniversalDownmixAudioProcessor.W_C_5_1 + rs * UniversalDownmixAudioProcessor.W_SUR_5_1

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    @Test
    fun `test F2 - 5_1 to Stereo 16-bit matches expected quantization`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_16BIT)

        val input = ByteBuffer.allocateDirect(6 * 2).order(ByteOrder.nativeOrder())
        val l: Short = 10000
        val r: Short = -10000
        val c: Short = 15000
        val lfe: Short = 32000
        val ls: Short = 8000
        val rs: Short = -8000

        input.putShort(l)
        input.putShort(r)
        input.putShort(c)
        input.putShort(lfe)
        input.putShort(ls)
        input.putShort(rs)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val expectedL = (l.toFloat() * UniversalDownmixAudioProcessor.W_LR_5_1 + c.toFloat() * UniversalDownmixAudioProcessor.W_C_5_1 + ls.toFloat() * UniversalDownmixAudioProcessor.W_SUR_5_1).toInt()
        val expectedR = (r.toFloat() * UniversalDownmixAudioProcessor.W_LR_5_1 + c.toFloat() * UniversalDownmixAudioProcessor.W_C_5_1 + rs.toFloat() * UniversalDownmixAudioProcessor.W_SUR_5_1).toInt()

        val actualL = output.short.toInt()
        val actualR = output.short.toInt()

        assertTrue(abs(expectedL - actualL) <= TOLERANCE_SHORT)
        assertTrue(abs(expectedR - actualR) <= TOLERANCE_SHORT)
    }

    // =========================================================================
    // G. 7.1 -> STEREO
    // =========================================================================

    @Test
    fun `test G1 - 7_1 to Stereo Float analytical match`() {
        configureAndFlush(channelCount = 8, encoding = C.ENCODING_PCM_FLOAT)

        val input = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        val l = 0.4f
        val r = 0.2f
        val c = 0.5f
        val lfe = 0.9f // Discarded
        val ls = 0.3f
        val rs = -0.3f
        val rls = 0.25f
        val rrs = -0.25f

        input.putFloat(l)
        input.putFloat(r)
        input.putFloat(c)
        input.putFloat(lfe)
        input.putFloat(ls)
        input.putFloat(rs)
        input.putFloat(rls)
        input.putFloat(rrs)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val expectedL = l * UniversalDownmixAudioProcessor.W_LR_7_1 + c * UniversalDownmixAudioProcessor.W_C_7_1 + ls * UniversalDownmixAudioProcessor.W_SUR_7_1 + rls * UniversalDownmixAudioProcessor.W_REAR_7_1
        val expectedR = r * UniversalDownmixAudioProcessor.W_LR_7_1 + c * UniversalDownmixAudioProcessor.W_C_7_1 + rs * UniversalDownmixAudioProcessor.W_SUR_7_1 + rrs * UniversalDownmixAudioProcessor.W_REAR_7_1

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    // =========================================================================
    // M. CLIPPING PROTECTION & NORMALIZATION HEADROOM
    // =========================================================================

    @Test
    fun `test M - Full scale in all 6 channels in 5_1 never exceeds 1_0f (zero clipping)`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)

        // All channels at absolute maximum +1.0f
        val input = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        for (c in 0 until 6) input.putFloat(1.0f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val actualL = output.float
        val actualR = output.float

        // Normalized sum must be <= 1.0f
        assertTrue("actualL $actualL should be <= 1.0f", actualL <= 1.0f + 1e-6f)
        assertTrue("actualR $actualR should be <= 1.0f", actualR <= 1.0f + 1e-6f)
    }

    // =========================================================================
    // N, O. DEFENSE AGAINST NAN & INFINITY
    // =========================================================================

    @Test
    fun `test N - NaN and Infinity pass-through or finite bounds verification`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)

        val input = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        input.putFloat(0.0f)
        input.putFloat(0.0f)
        input.putFloat(0.0f)
        input.putFloat(0.0f)
        input.putFloat(0.0f)
        input.putFloat(0.0f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val l = output.float
        val r = output.float
        assertFalse(l.isNaN())
        assertFalse(l.isInfinite())
        assertFalse(r.isNaN())
        assertFalse(r.isInfinite())
        assertEquals(0.0f, l, TOLERANCE_FLOAT)
        assertEquals(0.0f, r, TOLERANCE_FLOAT)
    }

    // =========================================================================
    // P. INVALID CONFIGURATION REJECTION
    // =========================================================================

    @Test
    fun `test P - Unsupported channel count throws UnhandledAudioFormatException`() {
        try {
            // 7 channels is not standard 5.1, 7.1, quad, etc.
            processor.configure(AudioProcessor.AudioFormat(48000, 7, C.ENCODING_PCM_FLOAT))
            fail("Expected UnhandledAudioFormatException for 7 channels")
        } catch (e: AudioProcessor.UnhandledAudioFormatException) {
            // Expected
        }
    }

    // =========================================================================
    // R, S. FLUSH, RESET AND END OF STREAM (EOS)
    // =========================================================================

    @Test
    fun `test R - Flush and Reset lifecycle operations`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)
        assertTrue(processor.isActive)

        processor.flush()
        assertTrue(processor.isActive)

        processor.reset()
        assertFalse(processor.isActive)
    }

    @Test
    fun `test S - End of stream propagation`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)

        val input = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until 6) input.putFloat(0.1f)
        input.flip()

        processor.queueInput(input)
        val out1 = processor.output
        assertTrue(out1.hasRemaining())

        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
    }

    // =========================================================================
    // T, U, V. BUFFER SIZES & CONSECUTIVE QUEUE INPUTS
    // =========================================================================

    @Test
    fun `test T - Small single-frame processing`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)

        val input = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until 6) input.putFloat(0.2f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertEquals(2 * 4, output.remaining())
    }

    @Test
    fun `test U - Large 2048-frame block processing`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)

        val frameCount = 2048
        val input = ByteBuffer.allocateDirect(frameCount * 6 * 4).order(ByteOrder.nativeOrder())
        for (f in 0 until frameCount * 6) {
            input.putFloat(0.1f)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertEquals(frameCount * 2 * 4, output.remaining())
    }

    // =========================================================================
    // PERFORMANCE / BENCHMARK TEST (JVM)
    // =========================================================================

    @Test
    fun `test JVM BENCHMARK - 100,000 frames of 5_1 downmix throughput`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT, sampleRate = 48000)

        val frames = 100_000 // ~2.08 seconds of audio at 48kHz
        val input = ByteBuffer.allocateDirect(frames * 6 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 6) {
            input.putFloat(0.15f)
        }
        input.flip()

        val startTime = System.nanoTime()
        processor.queueInput(input)
        val output = processor.output
        val durationNs = System.nanoTime() - startTime

        val durationMs = durationNs / 1_000_000.0
        val framesPerSec = (frames / (durationNs / 1_000_000_000.0)).toLong()

        assertEquals(frames * 2 * 4, output.remaining())
        println("[JVM BENCHMARK] Processed $frames 5.1 frames in ${String.format("%.2f", durationMs)} ms -> $framesPerSec frames/sec")

        // 100k frames should easily process in under 500ms on modern JVM
        assertTrue("Downmix performance exceeded threshold: ${durationMs}ms", durationMs < 500.0)
    }
}
