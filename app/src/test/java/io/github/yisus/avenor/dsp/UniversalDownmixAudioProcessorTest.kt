package io.github.yisus.avenor.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class UniversalDownmixAudioProcessorTest {

    private lateinit var processor: UniversalDownmixAudioProcessor

    private val TOLERANCE_FLOAT = 1.0e-5f
    private val TOLERANCE_SHORT = 2 // max 2 short values difference due to rounding/truncation

    // Independent double-precision ITU-R BS.775-3 reference constants (NOT referencing processor constants)
    private val refInvSqrt2 = 1.0 / sqrt(2.0) // ~0.7071067811865475

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
        assertEquals(AudioChannelLayout.MONO_1_0, processor.activeLayout)

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
        assertEquals(AudioChannelLayout.STEREO_2_0, processor.activeLayout)
    }

    // =========================================================================
    // C. 3.0 -> STEREO (Independent Reference Vector)
    // =========================================================================

    @Test
    fun `test C1 - 3_0 to Stereo Float analytical match against independent ITU reference`() {
        configureAndFlush(channelCount = 3, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_3_0_SMPTE, processor.activeLayout)

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

        // Independent ITU-R BS.775-3 normalized formula: (L + (1/sqrt(2))*C) / (1 + 1/sqrt(2))
        val norm30 = 1.0 + refInvSqrt2
        val expectedL = ((l + refInvSqrt2 * c) / norm30).toFloat()
        val expectedR = ((r + refInvSqrt2 * c) / norm30).toFloat()

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
        // Also verify literal decimal expectations (~0.9171573f and ~0.0384776f)
        assertEquals(0.9171573f, actualL, 1e-4f)
        assertEquals(0.0384776f, actualR, 1e-4f)
    }

    // =========================================================================
    // D. 4.0 / QUAD -> STEREO (Independent Reference Vector)
    // =========================================================================

    @Test
    fun `test D1 - 4_0 Quad to Stereo Float analytical match against independent reference`() {
        configureAndFlush(channelCount = 4, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.QUAD_4_0, processor.activeLayout)

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

        val norm40 = 1.0 + refInvSqrt2
        val expectedL = ((l + refInvSqrt2 * ls) / norm40).toFloat()
        val expectedR = ((r + refInvSqrt2 * rs) / norm40).toFloat()

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    // =========================================================================
    // E. 5.0 -> STEREO (Independent Reference Vector)
    // =========================================================================

    @Test
    fun `test E1 - 5_0 to Stereo Float analytical match against independent reference`() {
        configureAndFlush(channelCount = 5, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_5_0_SMPTE, processor.activeLayout)

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

        val norm50 = 1.0 + 2.0 * refInvSqrt2
        val expectedL = ((l + refInvSqrt2 * c + refInvSqrt2 * ls) / norm50).toFloat()
        val expectedR = ((r + refInvSqrt2 * c + refInvSqrt2 * rs) / norm50).toFloat()

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    // =========================================================================
    // F. 5.1 -> STEREO (LFE Discard Verification & Independent Reference)
    // =========================================================================

    @Test
    fun `test F1 - 5_1 to Stereo Float discards LFE and matches independent ITU-R BS_775 reference`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_5_1_SMPTE, processor.activeLayout)

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

        val norm51 = 1.0 + 2.0 * refInvSqrt2
        val expectedL = ((l + refInvSqrt2 * c + refInvSqrt2 * ls) / norm51).toFloat()
        val expectedR = ((r + refInvSqrt2 * c + refInvSqrt2 * rs) / norm51).toFloat()

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    @Test
    fun `test F2 - 5_1 to Stereo 16-bit matches expected independent quantization`() {
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

        val norm51 = 1.0 + 2.0 * refInvSqrt2
        val expectedL = ((l.toDouble() + refInvSqrt2 * c.toDouble() + refInvSqrt2 * ls.toDouble()) / norm51).toInt()
        val expectedR = ((r.toDouble() + refInvSqrt2 * c.toDouble() + refInvSqrt2 * rs.toDouble()) / norm51).toInt()

        val actualL = output.short.toInt()
        val actualR = output.short.toInt()

        assertTrue(abs(expectedL - actualL) <= TOLERANCE_SHORT)
        assertTrue(abs(expectedR - actualR) <= TOLERANCE_SHORT)
    }

    // =========================================================================
    // G. 7.1 -> STEREO (Independent Reference Vector)
    // =========================================================================

    @Test
    fun `test G1 - 7_1 to Stereo Float analytical match against independent reference`() {
        configureAndFlush(channelCount = 8, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_7_1_SMPTE, processor.activeLayout)

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

        val norm71 = 1.0 + 2.0 * refInvSqrt2 + 0.5
        val expectedL = ((l + refInvSqrt2 * c + refInvSqrt2 * ls + 0.5 * rls) / norm71).toFloat()
        val expectedR = ((r + refInvSqrt2 * c + refInvSqrt2 * rs + 0.5 * rrs) / norm71).toFloat()

        val actualL = output.float
        val actualR = output.float

        assertEquals(expectedL, actualL, TOLERANCE_FLOAT)
        assertEquals(expectedR, actualR, TOLERANCE_FLOAT)
    }

    // =========================================================================
    // H. EXPLICIT CHANNEL LAYOUTS (Vorbis/Film vs SMPTE, 3.1, 4.0 Surround)
    // =========================================================================

    @Test
    fun `test H1 - 5_1 Vorbis Film ordering (L C R Ls Rs LFE) produces identical stereo fold-down to SMPTE`() {
        val l = 0.6f
        val r = -0.2f
        val c = 0.4f
        val lfe = 0.99f // LFE at index 3 in SMPTE, index 5 in Vorbis
        val ls = -0.5f
        val rs = 0.3f

        // 1. SMPTE [L, R, C, LFE, Ls, Rs]
        processor.channelLayoutOverride = AudioChannelLayout.SURROUND_5_1_SMPTE
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)
        val smpteIn = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        smpteIn.putFloat(l).putFloat(r).putFloat(c).putFloat(lfe).putFloat(ls).putFloat(rs).flip()
        processor.queueInput(smpteIn)
        val smpteOut = processor.output.order(ByteOrder.nativeOrder())
        val smpteL = smpteOut.float
        val smpteR = smpteOut.float

        // 2. Vorbis/Film [L, C, R, Ls, Rs, LFE]
        processor.reset()
        processor.isVorbisFilmOrderHint = true
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_5_1_VORBIS, processor.activeLayout)
        val vorbisIn = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        vorbisIn.putFloat(l).putFloat(c).putFloat(r).putFloat(ls).putFloat(rs).putFloat(lfe).flip()
        processor.queueInput(vorbisIn)
        val vorbisOut = processor.output.order(ByteOrder.nativeOrder())
        val vorbisL = vorbisOut.float
        val vorbisR = vorbisOut.float

        assertEquals(smpteL, vorbisL, TOLERANCE_FLOAT)
        assertEquals(smpteR, vorbisR, TOLERANCE_FLOAT)
    }

    @Test
    fun `test H2 - 4-channel 3_1 layout via Android channel mask discards LFE and mixes Center`() {
        processor.channelMaskHint = AudioChannelLayout.MASK_3_1
        configureAndFlush(channelCount = 4, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_3_1, processor.activeLayout)

        val l = 0.8f
        val r = 0.2f
        val c = 0.5f
        val lfe = 1.0f // Discarded in 3.1

        val input = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())
        input.putFloat(l).putFloat(r).putFloat(c).putFloat(lfe).flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        val norm31 = 1.0 + refInvSqrt2
        val expectedL = ((l + refInvSqrt2 * c) / norm31).toFloat()
        val expectedR = ((r + refInvSqrt2 * c) / norm31).toFloat()

        assertEquals(expectedL, output.float, TOLERANCE_FLOAT)
        assertEquals(expectedR, output.float, TOLERANCE_FLOAT)
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
    fun `test N - NaN and Infinity samples are sanitized to finite bounds without poisoning output`() {
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)

        // Frame 1: NaN in L and LFE, +Infinity in C, -Infinity in Rs, valid finite in R (0.5f) and Ls (0.4f)
        // Frame 2: All channels NaN or Infinity
        val input = ByteBuffer.allocateDirect(2 * 6 * 4).order(ByteOrder.nativeOrder())
        // Frame 1:
        input.putFloat(Float.NaN)               // L -> sanitized to 0.0f
        input.putFloat(0.5f)                    // R -> valid 0.5f
        input.putFloat(Float.POSITIVE_INFINITY) // C -> sanitized to 0.0f
        input.putFloat(Float.NaN)               // LFE -> discarded
        input.putFloat(0.4f)                    // Ls -> valid 0.4f
        input.putFloat(Float.NEGATIVE_INFINITY) // Rs -> sanitized to 0.0f
        // Frame 2:
        input.putFloat(Float.NaN)
        input.putFloat(Float.POSITIVE_INFINITY)
        input.putFloat(Float.NEGATIVE_INFINITY)
        input.putFloat(Float.NaN)
        input.putFloat(Float.POSITIVE_INFINITY)
        input.putFloat(Float.NEGATIVE_INFINITY)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        output.order(ByteOrder.nativeOrder())

        // Frame 1 verification: L has only Ls=0.4f, R has only R=0.5f
        val norm51 = 1.0 + 2.0 * refInvSqrt2
        val expectedFrame1L = ((0.0 + refInvSqrt2 * 0.0 + refInvSqrt2 * 0.4) / norm51).toFloat()
        val expectedFrame1R = ((0.5 + refInvSqrt2 * 0.0 + refInvSqrt2 * 0.0) / norm51).toFloat()

        val l1 = output.float
        val r1 = output.float
        assertFalse("l1 must not be NaN", l1.isNaN())
        assertFalse("l1 must not be Infinite", l1.isInfinite())
        assertFalse("r1 must not be NaN", r1.isNaN())
        assertFalse("r1 must not be Infinite", r1.isInfinite())
        assertEquals(expectedFrame1L, l1, TOLERANCE_FLOAT)
        assertEquals(expectedFrame1R, r1, TOLERANCE_FLOAT)

        // Frame 2 verification: all non-finite inputs sanitized to 0.0f
        val l2 = output.float
        val r2 = output.float
        assertFalse("l2 must not be NaN", l2.isNaN())
        assertFalse("l2 must not be Infinite", l2.isInfinite())
        assertFalse("r2 must not be NaN", r2.isNaN())
        assertFalse("r2 must not be Infinite", r2.isInfinite())
        assertEquals(0.0f, l2, TOLERANCE_FLOAT)
        assertEquals(0.0f, r2, TOLERANCE_FLOAT)
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
        assertEquals(AudioChannelLayout.UNKNOWN, processor.activeLayout)
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

    @Test
    fun `test H3 - Stream Format and WAVEFORMATEXTENSIBLE mask propagation resolves 3_1, 4_0 Center-Rear, and Vorbis 5_1 without manual hints`() {
        // 1. 4-channel stream with WAVEFORMATEXTENSIBLE mask 0x000F (3.1: FL|FR|FC|LFE)
        val format31 = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_FLAC)
            .setCodecs("wave_channel_mask=0x000F")
            .setChannelCount(4)
            .setSampleRate(48000)
            .build()
        processor.onSinkConfigureFormat(format31, null)
        configureAndFlush(channelCount = 4, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_3_1, processor.activeLayout)

        // 2. 4-channel stream with WAVEFORMATEXTENSIBLE mask 0x0107 (4.0 Center-Rear: FL|FR|FC|BC)
        processor.reset()
        val format40Cr = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_FLAC)
            .setCodecs("wave_channel_mask=0x0107")
            .setChannelCount(4)
            .setSampleRate(48000)
            .build()
        processor.onSinkConfigureFormat(format40Cr, null)
        configureAndFlush(channelCount = 4, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_4_0_CENTER_REAR, processor.activeLayout)

        // 3. 6-channel Vorbis stream propagated via onInputTrackFormatChanged -> onSinkConfigureFormat
        processor.reset()
        val vorbisTrackFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_VORBIS)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcmSinkFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        processor.onInputTrackFormatChanged(vorbisTrackFormat)
        processor.onSinkConfigureFormat(rawPcmSinkFormat, null)
        configureAndFlush(channelCount = 6, encoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioChannelLayout.SURROUND_5_1_VORBIS, processor.activeLayout)
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
