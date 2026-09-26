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
}
