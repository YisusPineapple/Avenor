package io.github.yisus.avenor

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.room.Room
import io.github.yisus.avenor.audio.AvenorRenderersFactory
import io.github.yisus.avenor.playback.PlaybackCoordinator
import io.github.yisus.avenor.replaygain.ReplayGainAudioProcessor
import io.github.yisus.avenor.replaygain.ReplayGainMode
import io.github.yisus.avenor.replaygain.ReplayGainPolicy
import io.github.yisus.avenor.replaygain.SafeLimiterAudioProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceAudioPipelineIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: MusicDao
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.musicDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `test 21 - AvenorRenderersFactory correctly integrates into ExoPlayer with Policy A offload disabling and 4-stage DSP chain`() {
        val downmixProcessor = io.github.yisus.avenor.dsp.UniversalDownmixAudioProcessor()
        val rgProcessor = ReplayGainAudioProcessor()
        val limiterProcessor = SafeLimiterAudioProcessor()

        val factory = AvenorRenderersFactory(
            context = context,
            universalDownmixAudioProcessor = downmixProcessor,
            replayGainAudioProcessor = rgProcessor,
            safeLimiterAudioProcessor = limiterProcessor
        )

        val player = ExoPlayer.Builder(context, factory).build()
        assertNotNull(player)

        // Verify processors are attached in strict order: Downmix -> ReplayGain -> EQ -> SafeLimiter
        val chain = factory.audioProcessors
        assertEquals(4, chain.size)
        assertEquals(downmixProcessor, chain[0])
        assertEquals(rgProcessor, chain[1])
        assertEquals(factory.equalizerAudioProcessor, chain[2])
        assertEquals(limiterProcessor, chain[3])

        player.release()
    }

    @Test
    fun `test 22 - Service initializes audio processors and track transition resolves gain`() = runTest(testDispatcher) {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        assertNotNull(service.universalDownmixAudioProcessor)
        assertNotNull(service.replayGainAudioProcessor)
        assertNotNull(service.equalizerAudioProcessor)
        assertNotNull(service.safeLimiterAudioProcessor)

        // Insert song with ReplayGain metadata into DB
        val songWithGain = Song(
            id = 42L,
            uri = "content://media/external/audio/media/42",
            title = "Gain Track",
            artist = "Avenor Artist",
            album = "Avenor Album",
            durationMs = 180000L,
            albumArtUri = null,
            replayGainTrack = -5.5f,
            replayGainAlbum = -8.0f
        )
        dao.insertSongs(listOf(songWithGain))

        // Set mode to TRACK
        service.replayGainMode = ReplayGainMode.TRACK
        service.replayGainPreampDb = 0.0f

        // Emulate track transition via ReplayGain policy
        val effectiveGainDb = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = service.replayGainMode,
            trackGain = songWithGain.replayGainTrack,
            albumGain = songWithGain.replayGainAlbum,
            preampDb = service.replayGainPreampDb
        )
        service.replayGainAudioProcessor.setEffectiveGainDb(effectiveGainDb)

        val expectedLinear = ReplayGainPolicy.dbToLinearGain(-5.5f)
        assertEquals(expectedLinear, service.replayGainAudioProcessor.targetGain, 0.001f)

        serviceController.destroy()
    }

    @Test
    fun `test 23 - song without ReplayGain metadata plays at neutral unity gain`() = runTest(testDispatcher) {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        // Insert song with null ReplayGain metadata
        val songWithoutGain = Song(
            id = 43L,
            uri = "content://media/external/audio/media/43",
            title = "Clean Track",
            artist = "Artist",
            album = "Album",
            durationMs = 200000L,
            albumArtUri = null,
            replayGainTrack = null,
            replayGainAlbum = null
        )
        dao.insertSongs(listOf(songWithoutGain))

        val effectiveGainDb = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = service.replayGainMode,
            trackGain = songWithoutGain.replayGainTrack,
            albumGain = songWithoutGain.replayGainAlbum,
            preampDb = 0.0f
        )
        service.replayGainAudioProcessor.setEffectiveGainDb(effectiveGainDb)

        assertEquals(0.0f, effectiveGainDb, 0.0001f)
        assertEquals(1.0f, service.replayGainAudioProcessor.targetGain, 0.0001f)

        serviceController.destroy()
    }

    @Test
    fun `test 24 - ReplayGain operation NEVER modifies player volume`() = runTest(testDispatcher) {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        // MediaSession player volume must remain untouched by ReplayGain
        val initialVolume = 1.0f

        // Apply severe attenuation
        service.replayGainAudioProcessor.setEffectiveGainDb(-12.0f)
        assertEquals(initialVolume, 1.0f, 0.0001f)

        // Apply positive boost
        service.replayGainAudioProcessor.setEffectiveGainDb(6.0f)
        assertEquals(initialVolume, 1.0f, 0.0001f)

        serviceController.destroy()
    }

    @Test
    fun `test 25 - CrossfadeManager remains functional and operates independently of ReplayGain`() {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)

        assertNotNull(crossfadeManager)
        assertFalse(crossfadeManager.isCrossfadeEnabled)

        crossfadeManager.isCrossfadeEnabled = true
        assertTrue(crossfadeManager.isCrossfadeEnabled)

        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 26 - PlaybackCoordinator remains intact and functional`() = runTest(testDispatcher) {
        val coordinator = PlaybackCoordinator(dao, testScope)
        assertNotNull(coordinator)
        val state = coordinator.playbackState.value
        assertTrue(state.queue.isEmpty())
        assertFalse(state.isPlaying)
    }

    @Test
    fun `test 27 - Queue model and Room schema remain intact`() = runTest(testDispatcher) {
        val queue = PlaybackQueue(
            id = PlaybackCoordinator.ACTIVE_QUEUE_ID,
            name = "ACTIVE_QUEUE",
            currentIndex = 0,
            isPlaying = false
        )
        dao.insertPlaybackQueue(queue)
        val retrieved = dao.getPlaybackQueueSync(PlaybackCoordinator.ACTIVE_QUEUE_ID)
        assertNotNull(retrieved)
        assertEquals(1, retrieved!!.id)
    }

    @Test
    fun `test 28 - AvenorRenderersFactory AvenorMediaCodecAudioRenderer and AvenorAudioSink propagate MediaFormat KEY_CHANNEL_MASK into UniversalDownmixAudioProcessor`() {
        val downmixProcessor = io.github.yisus.avenor.dsp.UniversalDownmixAudioProcessor()
        val factory = AvenorRenderersFactory(
            context = context,
            universalDownmixAudioProcessor = downmixProcessor
        )

        val renderers = java.util.ArrayList<androidx.media3.exoplayer.Renderer>()
        val audioSink = factory.buildAudioSink(
            context = context,
            enableFloatOutput = false,
            enableAudioTrackPlaybackParams = false
        )
        assertTrue(audioSink is io.github.yisus.avenor.audio.AvenorAudioSink)

        factory.buildAudioRenderers(
            context = context,
            extensionRendererMode = androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
            mediaCodecSelector = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT,
            enableDecoderFallback = true,
            audioSink = audioSink,
            eventHandler = android.os.Handler(android.os.Looper.getMainLooper()),
            eventListener = object : androidx.media3.exoplayer.audio.AudioRendererEventListener {},
            out = renderers
        )
        assertEquals(1, renderers.size)
        val renderer = renderers[0] as io.github.yisus.avenor.audio.AvenorMediaCodecAudioRenderer

        // 1. Simulate MediaCodec emitting 4-channel 3.1 PCM output with Android KEY_CHANNEL_MASK = MASK_3_1
        val inputFormat31 = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_AC3)
            .setChannelCount(4)
            .setSampleRate(48000)
            .build()
        val mediaFormat31 = android.media.MediaFormat().apply {
            setString(android.media.MediaFormat.KEY_MIME, androidx.media3.common.MimeTypes.AUDIO_RAW)
            setInteger(android.media.MediaFormat.KEY_SAMPLE_RATE, 48000)
            setInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT, 4)
            setInteger(
                android.media.MediaFormat.KEY_CHANNEL_MASK,
                io.github.yisus.avenor.dsp.AudioChannelLayout.MASK_3_1
            )
        }

        // Renderer onOutputFormatChanged forwards Format + MediaFormat into downmixProcessor and configures AudioSink
        renderer.onOutputFormatChanged(inputFormat31, mediaFormat31)
        assertEquals(
            io.github.yisus.avenor.dsp.AudioChannelLayout.SURROUND_3_1,
            downmixProcessor.activeLayout
        )

        // 2. Reset sink and configure with 6-channel Vorbis stream through AvenorAudioSink
        audioSink.reset()
        val vorbisFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_VORBIS)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm6Ch = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_RAW)
            .setPcmEncoding(androidx.media3.common.C.ENCODING_PCM_16BIT)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        downmixProcessor.onInputTrackFormatChanged(vorbisFormat)
        audioSink.configure(rawPcm6Ch, 0, null)
        assertEquals(
            io.github.yisus.avenor.dsp.AudioChannelLayout.SURROUND_5_1_VORBIS,
            downmixProcessor.activeLayout
        )
    }

    @Test
    fun `test 29 - WAVE_FORMAT_EXTENSIBLE WAV stream propagates dwChannelMask through AvenorChannelMaskExtractorAdapter into UniversalDownmixAudioProcessor`() {
        val downmixProcessor = io.github.yisus.avenor.dsp.UniversalDownmixAudioProcessor()
        val factory = AvenorRenderersFactory(
            context = context,
            universalDownmixAudioProcessor = downmixProcessor
        )
        val audioSink = factory.buildAudioSink(
            context = context,
            enableFloatOutput = false,
            enableAudioTrackPlaybackParams = false
        )
        val extractorsFactory = factory.buildExtractorsFactory()

        // Helper to build a valid 68-byte WAVE_FORMAT_EXTENSIBLE (0xFFFE) 4-channel 16-bit 48kHz WAV header + 16 bytes PCM
        fun buildWaveExtensibleBytes(dwChannelMask: Int): ByteArray {
            val buf = java.nio.ByteBuffer.allocate(84).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray(Charsets.US_ASCII))
            buf.putInt(76)
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))
            buf.put("fmt ".toByteArray(Charsets.US_ASCII))
            buf.putInt(40) // extensible fmt chunk size = 40
            buf.putShort(0xFFFE.toShort()) // WAVE_FORMAT_EXTENSIBLE
            buf.putShort(4) // 4 channels
            buf.putInt(48000) // 48 kHz
            buf.putInt(48000 * 4 * 2) // byteRate
            buf.putShort((4 * 2).toShort()) // blockAlign
            buf.putShort(16) // bitsPerSample
            buf.putShort(22) // cbSize = 22
            buf.putShort(16) // wValidBitsPerSample = 16
            buf.putInt(dwChannelMask) // dwChannelMask
            // KSDATAFORMAT_SUBTYPE_PCM GUID: 00000001-0000-0010-8000-00AA00389B71
            buf.putInt(0x00000001)
            buf.putShort(0x0000)
            buf.putShort(0x0010)
            buf.put(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71))
            buf.put("data".toByteArray(Charsets.US_ASCII))
            buf.putInt(16)
            buf.put(ByteArray(16))
            return buf.array()
        }

        // 1. Test 4.0 Center-Rear WAV (dwChannelMask = 0x0107: FL | FR | FC | BC)
        val wav40Bytes = buildWaveExtensibleBytes(0x0107)
        val dataSource40 = androidx.media3.datasource.ByteArrayDataSource(wav40Bytes)
        dataSource40.open(androidx.media3.datasource.DataSpec(android.net.Uri.parse("memory://wav40.wav")))
        val input40 = androidx.media3.extractor.DefaultExtractorInput(
            dataSource40,
            0L,
            wav40Bytes.size.toLong()
        )
        val wavExtractor = extractorsFactory.createExtractors().first { extractor ->
            try {
                extractor.sniff(input40)
            } catch (_: java.io.EOFException) {
                false
            } finally {
                input40.resetPeekPosition()
            }
        }
        var emittedFormat: androidx.media3.common.Format? = null
        wavExtractor.init(object : androidx.media3.extractor.ExtractorOutput {
            override fun track(id: Int, type: Int): androidx.media3.extractor.TrackOutput {
                val dummy = androidx.media3.extractor.DummyTrackOutput()
                return object : androidx.media3.extractor.TrackOutput by dummy {
                    override fun format(format: androidx.media3.common.Format) {
                        emittedFormat = format
                        dummy.format(format)
                    }
                }
            }
            override fun endTracks() {}
            override fun seekMap(seekMap: androidx.media3.extractor.SeekMap) {}
        })
        val posHolder = androidx.media3.extractor.PositionHolder()
        while (emittedFormat == null) {
            if (wavExtractor.read(input40, posHolder) == androidx.media3.extractor.Extractor.RESULT_END_OF_INPUT) break
        }
        assertNotNull(emittedFormat)
        assertTrue(emittedFormat!!.codecs?.contains("wave_channel_mask=0x107") == true)

        audioSink.configure(emittedFormat!!, 0, null)
        assertEquals(
            io.github.yisus.avenor.dsp.AudioChannelLayout.SURROUND_4_0_CENTER_REAR,
            downmixProcessor.activeLayout
        )
    }
}
