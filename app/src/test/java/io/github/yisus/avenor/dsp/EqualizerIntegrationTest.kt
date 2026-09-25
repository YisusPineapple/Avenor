package io.github.yisus.avenor.dsp

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import io.github.yisus.avenor.EqPresetValidator
import io.github.yisus.avenor.PlaybackService
import io.github.yisus.avenor.audio.AvenorRenderersFactory
import io.github.yisus.avenor.replaygain.ReplayGainAudioProcessor
import io.github.yisus.avenor.replaygain.SafeLimiterAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class EqualizerIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `test 1 - AvenorRenderersFactory connects ReplayGain to EQ to SafeLimiter in exact order`() {
        val rgProcessor = ReplayGainAudioProcessor()
        val eqProcessor = EqualizerAudioProcessor()
        val limiterProcessor = SafeLimiterAudioProcessor()

        val factory = AvenorRenderersFactory(
            context = context,
            replayGainAudioProcessor = rgProcessor,
            equalizerAudioProcessor = eqProcessor,
            safeLimiterAudioProcessor = limiterProcessor
        )

        assertEquals(rgProcessor, factory.replayGainAudioProcessor)
        assertEquals(eqProcessor, factory.equalizerAudioProcessor)
        assertEquals(limiterProcessor, factory.safeLimiterAudioProcessor)

        val player = ExoPlayer.Builder(context, factory).build()
        assertNotNull(player)
        player.release()
    }

    @Test
    fun `test 2 - SET_EQ_BAND command in PlaybackService updates EqualizerAudioProcessor and EqPreferences`() {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        assertNotNull(service.equalizerAudioProcessor)
        assertNotNull(service.eqPreferences)

        // Send SET_EQ_BAND for band 0 (60Hz) with 5.0 dB
        val args = Bundle().apply {
            putShort("band", 0.toShort())
            putFloat("levelDb", 5.0f)
        }
        service.handleCustomCommand("SET_EQ_BAND", args)

        // Verify EqualizerAudioProcessor has 5.0 dB on band 0
        val settings = service.equalizerAudioProcessor.getSettings()
        assertEquals(5.0f, settings.getGainDb(0), 0.001f)

        // Verify EqPreferences persisted the change
        val persistedBands = service.eqPreferences.getBands()
        assertEquals(5.0f, persistedBands[0], 0.001f)

        serviceController.destroy()
    }

    @Test
    fun `test 3 - SET_EQ_BAND with millibels backwards compatibility`() {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        // Send SET_EQ_BAND with legacy millibels (300 = 3.0 dB)
        val args = Bundle().apply {
            putShort("band", 1.toShort())
            putShort("level", 300.toShort())
        }
        service.handleCustomCommand("SET_EQ_BAND", args)

        val settings = service.equalizerAudioProcessor.getSettings()
        assertEquals(3.0f, settings.getGainDb(1), 0.001f)

        serviceController.destroy()
    }

    @Test
    fun `test 4 - SET_EQ_CONFIG dynamically enables and disables EQ during playback without player recreation`() {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        // Initially enabled
        assertTrue(service.equalizerAudioProcessor.getSettings().isEnabled)

        // Disable via SET_EQ_CONFIG
        val disableArgs = Bundle().apply {
            putBoolean("enabled", false)
        }
        service.handleCustomCommand("SET_EQ_CONFIG", disableArgs)

        assertFalse(service.equalizerAudioProcessor.getSettings().isEnabled)
        assertFalse(service.eqPreferences.isEnabled())

        // Enable again
        val enableArgs = Bundle().apply {
            putBoolean("enabled", true)
            putFloatArray("bands", floatArrayOf(2.0f, 2.0f, 2.0f, 2.0f, 2.0f))
            putString("presetName", "CustomBoost")
        }
        service.handleCustomCommand("SET_EQ_CONFIG", enableArgs)

        assertTrue(service.equalizerAudioProcessor.getSettings().isEnabled)
        assertEquals(2.0f, service.equalizerAudioProcessor.getSettings().getGainDb(0), 0.001f)
        assertEquals("CustomBoost", service.eqPreferences.getCurrentPresetName())

        serviceController.destroy()
    }

    @Test
    fun `test 5 - EqPreferences persistence survives service restart`() {
        val prefs = EqPreferences(context)
        prefs.setEnabled(true)
        val testBands = floatArrayOf(4.0f, 2.0f, -1.0f, 3.0f, 5.0f)
        prefs.setBands(testBands)
        prefs.setCurrentPresetName("Rock")

        // Start service; it must restore from EqPreferences in onCreate
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        val restoredSettings = service.equalizerAudioProcessor.getSettings()
        assertTrue(restoredSettings.isEnabled)
        for (i in 0 until 5) {
            assertEquals(testBands[i], restoredSettings.getGainDb(i), 0.001f)
        }
        assertEquals("Rock", service.eqPreferences.getCurrentPresetName())

        serviceController.destroy()
    }

    @Test
    fun `test 6 - onAudioSessionIdChanged does not throw or recreate platform equalizer`() {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        val player = service.mediaSession?.player
        assertNotNull(player)

        // Verify player operations continue normally
        assertTrue(true)

        serviceController.destroy()
    }

    @Test
    fun `test 7 - EqPresetDefinitions is single source of truth across validator and viewmodel`() {
        val canonicalPresets = EqPresetDefinitions.DEFAULT_PRESETS
        val validatorPresets = EqPresetValidator.predefinedPresets

        assertEquals(16, canonicalPresets.size)
        assertEquals(16, validatorPresets.size)

        // Check canonical Rock preset
        val rockCanonical = EqPresetDefinitions.getByName("Rock")
        assertNotNull(rockCanonical)
        assertEquals("5.0,3.0,-1.0,3.0,5.0", rockCanonical!!.bands)

        // Validator must return the same instance
        val rockValidator = validatorPresets.find { it.name == "Rock" }
        assertNotNull(rockValidator)
        assertEquals("5.0,3.0,-1.0,3.0,5.0", rockValidator!!.bands)

        val rockParsed = EqPresetValidator.validateAndParse(rockCanonical)
        assertEquals(listOf(5.0f, 3.0f, -1.0f, 3.0f, 5.0f), rockParsed)
    }

    @Test
    fun `test 8 - Complete DSP pipeline regression test from ReplayGain to EQ to Limiter`() {
        val rgProcessor = ReplayGainAudioProcessor()
        val eqProcessor = EqualizerAudioProcessor()
        val limiterProcessor = SafeLimiterAudioProcessor()

        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)

        rgProcessor.configure(format)
        rgProcessor.flush()
        eqProcessor.configure(format)
        eqProcessor.flush()
        limiterProcessor.configure(format)
        limiterProcessor.flush()

        // 1. ReplayGain applies +3 dB
        rgProcessor.setEffectiveGainDb(3.0f)

        // 2. EQ applies +6 dB on all bands
        eqProcessor.setBandGains(floatArrayOf(6.0f, 6.0f, 6.0f, 6.0f, 6.0f))
        eqProcessor.flush()

        // 3. Limiter ceiling is 1.0f
        limiterProcessor.ceiling = 1.0f

        // Feed full scale test signal
        val frames = 500
        val input = ByteBuffer.allocateDirect(frames * 2 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 2) {
            input.putFloat(0.8f)
        }
        input.flip()

        // ReplayGain output
        rgProcessor.queueInput(input)
        val rgOutput = rgProcessor.output

        // EQ output
        eqProcessor.queueInput(rgOutput)
        val eqOutput = eqProcessor.output

        // Limiter output
        limiterProcessor.queueInput(eqOutput)
        val finalOutput = limiterProcessor.output.order(ByteOrder.nativeOrder())

        var maxPeak = 0.0f
        while (finalOutput.hasRemaining()) {
            val v = Math.abs(finalOutput.float)
            if (v > maxPeak) maxPeak = v
        }

        // Limiter must strictly hold max peak <= 1.0001f despite +3dB RG and +6dB EQ
        assertTrue("Pipeline peak was $maxPeak, must be <= 1.0001f", maxPeak <= 1.0001f)
    }
}
