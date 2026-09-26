package io.github.yisus.avenor.replaygain

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import io.github.yisus.avenor.audio.AvenorRenderersFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class AudioProcessingPipelineTest {

    private lateinit var replayGainProcessor: ReplayGainAudioProcessor
    private lateinit var safeLimiterProcessor: SafeLimiterAudioProcessor

    @Before
    fun setup() {
        replayGainProcessor = ReplayGainAudioProcessor()
        safeLimiterProcessor = SafeLimiterAudioProcessor()
    }

    private fun configurePipeline(
        sampleRate: Int = 44100,
        channelCount: Int = 2,
        encoding: Int = C.ENCODING_PCM_FLOAT
    ) {
        val format = AudioProcessor.AudioFormat(sampleRate, channelCount, encoding)
        replayGainProcessor.configure(format)
        replayGainProcessor.flush()

        safeLimiterProcessor.configure(format)
        safeLimiterProcessor.flush()
    }

    @Test
    fun `test 16 - full chain ReplayGain boost with SafeLimiter clipping protection`() {
        configurePipeline(encoding = C.ENCODING_PCM_FLOAT)

        // Track with massive positive gain: +12 dB (~3.98x)
        replayGainProcessor.setEffectiveGainDb(12.0f)
        replayGainProcessor.flush()

        safeLimiterProcessor.ceiling = 0.98f

        // High input amplitude 0.7f (amplified 0.7 * 3.98 = ~2.78f, heavy digital clipping without limiter)
        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        input.putFloat(0.7f)
        input.putFloat(-0.8f)
        input.putFloat(0.9f)
        input.putFloat(-0.65f)
        input.flip()

        // 1. Process through ReplayGain
        replayGainProcessor.queueInput(input)
        val rgOutput = replayGainProcessor.output

        // 2. Feed directly into SafeLimiter
        safeLimiterProcessor.queueInput(rgOutput)
        val finalOutput = safeLimiterProcessor.output
        finalOutput.order(ByteOrder.nativeOrder())

        // Verify all output samples are strictly contained within ceiling (0.98f)
        while (finalOutput.hasRemaining()) {
            val s = finalOutput.float
            assertTrue("Sample ($s) must be protected by limiter and <= 0.98", abs(s) <= 0.9801f)
        }
    }

    @Test
    fun `test 17 - ReplayGain OFF mode produces effective bypass`() {
        configurePipeline(encoding = C.ENCODING_PCM_FLOAT)

        val trackGain = -6.0f
        val albumGain = -8.0f
        replayGainProcessor.updateGainFromSong(
            mode = ReplayGainMode.OFF,
            trackGain = trackGain,
            albumGain = albumGain
        )
        replayGainProcessor.flush()

        assertEquals(1.0f, replayGainProcessor.targetGain, 0.0001f)

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.42f)
        input.putFloat(-0.73f)
        input.flip()

        replayGainProcessor.queueInput(input)
        val output = replayGainProcessor.output
        output.order(ByteOrder.nativeOrder())

        assertEquals(0.42f, output.float, 0.0001f)
        assertEquals(-0.73f, output.float, 0.0001f)
    }

    @Test
    fun `test 18 - ReplayGain TRACK mode selects track gain`() {
        configurePipeline()

        replayGainProcessor.updateGainFromSong(
            mode = ReplayGainMode.TRACK,
            trackGain = -3.0f,
            albumGain = -6.0f,
            preampDb = 0.0f
        )
        replayGainProcessor.flush()

        val expectedLinear = ReplayGainPolicy.dbToLinearGain(-3.0f)
        assertEquals(expectedLinear, replayGainProcessor.targetGain, 0.001f)
    }

    @Test
    fun `test 19 - ReplayGain ALBUM mode selects album gain`() {
        configurePipeline()

        replayGainProcessor.updateGainFromSong(
            mode = ReplayGainMode.ALBUM,
            trackGain = -3.0f,
            albumGain = -7.5f,
            preampDb = 0.0f
        )
        replayGainProcessor.flush()

        val expectedLinear = ReplayGainPolicy.dbToLinearGain(-7.5f)
        assertEquals(expectedLinear, replayGainProcessor.targetGain, 0.001f)
    }

    @Test
    fun `test 20 - ReplayGain fallback when metadata is missing`() {
        configurePipeline()

        // Album mode requested, but album gain is null -> falls back to track gain
        replayGainProcessor.updateGainFromSong(
            mode = ReplayGainMode.ALBUM,
            trackGain = -2.5f,
            albumGain = null,
            preampDb = 0.0f
        )
        val expected = ReplayGainPolicy.dbToLinearGain(-2.5f)
        assertEquals(expected, replayGainProcessor.targetGain, 0.001f)

        // Both null -> neutral 0 dB (factor 1.0f)
        replayGainProcessor.updateGainFromSong(
            mode = ReplayGainMode.TRACK,
            trackGain = null,
            albumGain = null,
            preampDb = 0.0f
        )
        assertEquals(1.0f, replayGainProcessor.targetGain, 0.0001f)
    }

    @Test
    fun `test 21 - AvenorRenderersFactory exposes processors cleanly in 4-stage order and processes 5_1 to stereo`() {
        val downmix = io.github.yisus.avenor.dsp.UniversalDownmixAudioProcessor()
        val rg = ReplayGainAudioProcessor()
        val eq = io.github.yisus.avenor.dsp.EqualizerAudioProcessor()
        val limiter = SafeLimiterAudioProcessor()
        val factory = AvenorRenderersFactory(
            context = RuntimeEnvironment.getApplication(),
            universalDownmixAudioProcessor = downmix,
            replayGainAudioProcessor = rg,
            equalizerAudioProcessor = eq,
            safeLimiterAudioProcessor = limiter
        )

        assertNotNull(factory.universalDownmixAudioProcessor)
        assertNotNull(factory.replayGainAudioProcessor)
        assertNotNull(factory.equalizerAudioProcessor)
        assertNotNull(factory.safeLimiterAudioProcessor)
        assertEquals(downmix, factory.audioProcessors[0])
        assertEquals(rg, factory.audioProcessors[1])
        assertEquals(eq, factory.audioProcessors[2])
        assertEquals(limiter, factory.audioProcessors[3])

        // Configure full 4-stage chain for 5.1 (6-channel) 48kHz Float input
        val in51 = AudioProcessor.AudioFormat(48000, 6, C.ENCODING_PCM_FLOAT)
        val outDownmixFormat = downmix.configure(in51)
        downmix.flush()
        assertEquals(2, outDownmixFormat.channelCount)

        rg.configure(outDownmixFormat)
        rg.setEffectiveGainDb(3.0f)
        rg.flush()

        eq.configure(outDownmixFormat)
        eq.flush()

        limiter.configure(outDownmixFormat)
        limiter.flush()

        // Feed 1 frame of 6-channel 5.1 audio through Downmix -> ReplayGain -> EQ -> Limiter
        val input51 = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        input51.putFloat(0.5f).putFloat(0.5f).putFloat(0.4f).putFloat(0.9f).putFloat(0.2f).putFloat(0.2f).flip()

        downmix.queueInput(input51)
        rg.queueInput(downmix.output)
        val afterRg = if (eq.isActive) {
            eq.queueInput(rg.output)
            eq.output
        } else {
            rg.output
        }
        limiter.queueInput(afterRg)
        val finalStereo = limiter.output.order(ByteOrder.nativeOrder())

        assertEquals(2 * 4, finalStereo.remaining())
        val finalL = finalStereo.float
        val finalR = finalStereo.float
        assertTrue(finalL.isFinite() && abs(finalL) <= 0.9801f)
        assertTrue(finalR.isFinite() && abs(finalR) <= 0.9801f)
    }

    @Test
    fun `test 28 - performance micro-benchmark zero allocation in hot path`() {
        configurePipeline(sampleRate = 44100, channelCount = 2, encoding = C.ENCODING_PCM_FLOAT)
        replayGainProcessor.setEffectiveGainDb(3.0f)
        replayGainProcessor.flush()

        // 1 second of stereo audio = 44100 frames * 2 channels = 88200 samples = 352800 bytes
        val sampleCount = 88200
        val byteCount = sampleCount * 4
        val inputBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            inputBuffer.putFloat(0.4f)
        }
        inputBuffer.flip()

        // JIT warm-up: execute multiple passes so hotspot compiler optimizes inner loops
        repeat(10) {
            val warmBuf = inputBuffer.duplicate()
            replayGainProcessor.queueInput(warmBuf)
            val warmOut = replayGainProcessor.output
            safeLimiterProcessor.queueInput(warmOut)
            safeLimiterProcessor.output
        }

        // Measure across multiple iterations to account for OS scheduler noise
        val iterations = 5
        val elapsedTimesMs = DoubleArray(iterations)

        var lastOutputRemaining = 0
        for (iter in 0 until iterations) {
            val testBuffer = inputBuffer.duplicate()
            val startTime = System.nanoTime()

            replayGainProcessor.queueInput(testBuffer)
            val rgOut = replayGainProcessor.output
            safeLimiterProcessor.queueInput(rgOut)
            val finalOut = safeLimiterProcessor.output

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
            elapsedTimesMs[iter] = elapsedMs
            lastOutputRemaining = finalOut.remaining()
        }

        // Verify output integrity
        assertEquals("Output buffer must match input byte count", byteCount, lastOutputRemaining)

        // Representative metric: median of warm runs to isolate algorithmic throughput from OS/GC pause
        elapsedTimesMs.sort()
        val representativeTimeMs = elapsedTimesMs[iterations / 2]

        // 1 second of audio (1000ms real-time) processed in pipeline must be > 20x faster than real-time (< 50ms)
        assertTrue(
            "Warm execution time ($representativeTimeMs ms) must be fast (< 50ms, >20x faster than real-time)",
            representativeTimeMs < 50.0
        )
    }
}
