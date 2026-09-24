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

class ReplayGainAudioProcessorTest {

    private lateinit var processor: ReplayGainAudioProcessor

    @Before
    fun setup() {
        processor = ReplayGainAudioProcessor()
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
    fun `test 1 - gain 0 dB leaves float samples unchanged`() {
        configureProcessor(encoding = C.ENCODING_PCM_FLOAT)
        processor.setEffectiveGainDb(0.0f)
        processor.flush() // Apply gain instantaneously on flush

        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        input.putFloat(0.1f)
        input.putFloat(-0.2f)
        input.putFloat(0.5f)
        input.putFloat(-0.8f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        assertEquals(0.1f, output.float, 0.0001f)
        assertEquals(-0.2f, output.float, 0.0001f)
        assertEquals(0.5f, output.float, 0.0001f)
        assertEquals(-0.8f, output.float, 0.0001f)
    }

    @Test
    fun `test 2 - negative gain attenuates float samples`() {
        configureProcessor(encoding = C.ENCODING_PCM_FLOAT)
        // -6.0206 dB is approximately 0.5 linear gain
        processor.setEffectiveGainDb(-6.0206f)
        processor.flush()

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.8f)
        input.putFloat(-0.4f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        assertEquals(0.4f, output.float, 0.005f)
        assertEquals(-0.2f, output.float, 0.005f)
    }

    @Test
    fun `test 3 - positive gain amplifies float samples`() {
        configureProcessor(encoding = C.ENCODING_PCM_FLOAT)
        // +6.0206 dB is approximately 2.0 linear gain
        processor.setEffectiveGainDb(6.0206f)
        processor.flush()

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.25f)
        input.putFloat(-0.3f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        assertEquals(0.5f, output.float, 0.005f)
        assertEquals(-0.6f, output.float, 0.005f)
    }

    @Test
    fun `test 4 - 16-bit PCM gain attenuation and amplification`() {
        configureProcessor(encoding = C.ENCODING_PCM_16BIT)
        processor.setEffectiveGainDb(-6.0206f) // ~0.5x
        processor.flush()

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(10000.toShort())
        input.putShort((-20000).toShort())
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        val s1 = output.short
        val s2 = output.short
        assertTrue("Expected ~5000, got $s1", abs(s1 - 5000) <= 20)
        assertTrue("Expected ~-10000, got $s2", abs(s2 - (-10000)) <= 20)
    }

    @Test
    fun `test 5 - 16-bit PCM saturation clamps without integer overflow`() {
        configureProcessor(encoding = C.ENCODING_PCM_16BIT)
        // +12 dB is ~3.98x amplification
        processor.setEffectiveGainDb(12.0f)
        processor.flush()

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(20000.toShort())
        input.putShort((-25000).toShort())
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        // 20000 * 3.98 = ~79600 which overflows Short.MAX_VALUE (32767) if not saturated
        assertEquals(32767.toShort(), output.short)
        assertEquals((-32768).toShort(), output.short)
    }

    @Test
    fun `test 6 - empty buffer handling`() {
        configureProcessor()
        val emptyInput = ByteBuffer.allocateDirect(0)
        processor.queueInput(emptyInput)
        val output = processor.output
        assertFalse(output.hasRemaining())
    }

    @Test
    fun `test 7 - end of stream handling`() {
        configureProcessor()
        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
    }

    @Test
    fun `test 8 - reset restores default state`() {
        configureProcessor()
        processor.setEffectiveGainDb(-10.0f)
        processor.reset()

        assertEquals(1.0f, processor.targetGain, 0.0001f)
        assertEquals(1.0f, processor.currentGain, 0.0001f)
        assertEquals(ReplayGainMode.TRACK, processor.mode)
        assertEquals(0.0f, processor.preampDb, 0.0001f)
    }

    @Test
    fun `test 9 - dynamic gain update smoothly ramps across audio frames`() {
        configureProcessor(sampleRate = 10000, channelCount = 1, encoding = C.ENCODING_PCM_FLOAT)
        processor.setEffectiveGainDb(0.0f) // start at 1.0f
        processor.flush()

        // Request change to 2.0f (gain step scheduled over 100 frames)
        processor.setTargetLinearGain(2.0f)

        // Feed 10 frames of 1.0f samples
        val input = ByteBuffer.allocateDirect(40).order(ByteOrder.nativeOrder())
        for (i in 0 until 10) {
            input.putFloat(1.0f)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        // Ensure gain smoothly stepped rather than jumping directly to 2.0 on first sample
        val firstSample = output.float
        assertTrue("First sample ($firstSample) should be near 1.0f, ramping up", firstSample in 1.0f..1.15f)
        assertTrue(processor.currentGain in 1.0f..2.0f)
    }

    @Test
    fun `test 10 - updateGainFromSong integrates ReplayGainPolicy`() {
        configureProcessor()
        processor.updateGainFromSong(
            mode = ReplayGainMode.TRACK,
            trackGain = -4.5f,
            albumGain = -8.0f,
            preampDb = 1.0f
        )
        // Effective gain = -4.5 + 1.0 = -3.5 dB
        val expectedLinear = ReplayGainPolicy.dbToLinearGain(-3.5f)
        assertEquals(expectedLinear, processor.targetGain, 0.001f)
    }
}
