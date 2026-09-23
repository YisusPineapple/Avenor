package io.github.yisus.avenor

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.yisus.avenor.playback.PlaybackCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransitionEngineAndLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var player: ExoPlayer
    private lateinit var crossfadeManager: CrossfadeManager
    private lateinit var db: AppDatabase
    private lateinit var coordinator: PlaybackCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        player = ExoPlayer.Builder(context).build()
        crossfadeManager = CrossfadeManager(player, context)
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coordinator = PlaybackCoordinator(db.musicDao())
    }

    @After
    fun tearDown() {
        crossfadeManager.release()
        player.release()
        db.close()
        Dispatchers.resetMain()
    }

    private fun createDummyMediaItem(id: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri("file:///android_asset/dummy_$id.mp3")
            .build()
    }

    private fun createDummyPositionInfo(): Player.PositionInfo {
        return Player.PositionInfo(
            null,
            0,
            createDummyMediaItem("1"),
            null,
            0,
            0L,
            0L,
            0,
            0
        )
    }

    // -------------------------------------------------------------
    // PARTE C: GAPLESS REGRESSION & TRANSITION ENGINE TESTS
    // -------------------------------------------------------------

    @Test
    fun testAutoTransition_whenCrossfadeOff_doesNotAlterVolume() {
        crossfadeManager.isCrossfadeEnabled = false
        player.volume = 1.0f

        val posInfo = createDummyPositionInfo()
        crossfadeManager.playerListener.onPositionDiscontinuity(posInfo, posInfo, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST remain 1.0f when crossfade is OFF", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST remain IDLE when crossfade is OFF", TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse("isCrossfading MUST be false", crossfadeManager.isCrossfading.value)
    }

    @Test
    fun testAutoTransition_whenCrossfadeOn_appliesFadeInAndRestoresVolume() = runTest(testDispatcher) {
        crossfadeManager.isCrossfadeEnabled = true
        player.volume = 1.0f

        val posInfo = createDummyPositionInfo()
        crossfadeManager.playerListener.onPositionDiscontinuity(posInfo, posInfo, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        shadowOf(Looper.getMainLooper()).idle()

        // Advance through the fade in animation
        testDispatcher.scheduler.advanceTimeBy(5000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST restore to 1.0f after fade completes", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST return to IDLE after fade completes", TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse("isCrossfading MUST be false after completion", crossfadeManager.isCrossfading.value)
    }

    @Test
    fun testManualSkip_doesNotBreakPlayerState() = runTest(testDispatcher) {
        crossfadeManager.manualSkip(forward = true)
        shadowOf(Looper.getMainLooper()).idle()

        testDispatcher.scheduler.advanceTimeBy(5000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST reset to 1.0f after manual skip", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST be IDLE", TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse("isCrossfading MUST be false", crossfadeManager.isCrossfading.value)
    }

    @Test
    fun testPauseDuringFade_cancelsTransitionAndRestoresVolume() = runTest(testDispatcher) {
        crossfadeManager.manualSkip(forward = true)

        // Simulate pause event
        crossfadeManager.playerListener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST restore to 1.0f immediately upon pause", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST return to IDLE on pause", TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse("isCrossfading MUST be false after pause cancellation", crossfadeManager.isCrossfading.value)
    }

    @Test
    fun testStopDuringFade_cancelsTransitionAndRestoresVolume() = runTest(testDispatcher) {
        crossfadeManager.manualSkip(forward = true)
        shadowOf(Looper.getMainLooper()).idle()

        // Simulate stop event
        crossfadeManager.playerListener.onPlaybackStateChanged(Player.STATE_IDLE)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST restore to 1.0f on stop", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST be IDLE on stop", TransitionState.IDLE, crossfadeManager.transitionState.value)
    }

    @Test
    fun testErrorDuringFade_restoresSafeState() = runTest(testDispatcher) {
        crossfadeManager.manualSkip(forward = true)
        shadowOf(Looper.getMainLooper()).idle()

        // Simulate playback error
        crossfadeManager.playerListener.onPlayerError(PlaybackException("Simulated AudioSink Error", null, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST restore to 1.0f on error", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST be IDLE on error", TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse("isCrossfading MUST be false on error", crossfadeManager.isCrossfading.value)
    }

    @Test
    fun testSeekDuringFade_cancelsTransitionAndRestoresVolume() = runTest(testDispatcher) {
        crossfadeManager.manualSkip(forward = true)
        shadowOf(Looper.getMainLooper()).idle()

        // Simulate manual seek discontinuity
        val posInfo = createDummyPositionInfo()
        crossfadeManager.playerListener.onPositionDiscontinuity(posInfo, posInfo, Player.DISCONTINUITY_REASON_SEEK)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST restore to 1.0f on manual seek", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST be IDLE on manual seek", TransitionState.IDLE, crossfadeManager.transitionState.value)
    }

    @Test
    fun testNewTransition_cancelsPreviousTransitionSafely() = runTest(testDispatcher) {
        crossfadeManager.manualSkip(forward = true)
        shadowOf(Looper.getMainLooper()).idle()

        // Trigger immediate second skip without waiting
        crossfadeManager.manualSkip(forward = true)
        shadowOf(Looper.getMainLooper()).idle()

        testDispatcher.scheduler.advanceTimeBy(5000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST restore to 1.0f after second transition completes", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST be IDLE", TransitionState.IDLE, crossfadeManager.transitionState.value)
    }

    @Test
    fun testRelease_cancelsTransitionAndRestoresVolume() = runTest(testDispatcher) {
        crossfadeManager.manualSkip(forward = true)
        shadowOf(Looper.getMainLooper()).idle()

        crossfadeManager.release()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Volume MUST restore to 1.0f upon release", 1.0f, player.volume, 0.001f)
        assertEquals("TransitionState MUST be IDLE upon release", TransitionState.IDLE, crossfadeManager.transitionState.value)
    }

    // -------------------------------------------------------------
    // PARTE A & D: LIFECYCLE & runBlocking REMOVAL VERIFICATION
    // -------------------------------------------------------------

    @Test
    fun testPlaybackCoordinator_persistPositionAsync_runsWithoutBlocking() = runTest(testDispatcher) {
        val job = coordinator.persistPositionAsync(54321L)
        assertTrue("Job must be created and active", job.isActive || job.isCompleted)
        job.join()

        val state = coordinator.playbackState.value
        assertEquals("Position should be updated asynchronously", 54321L, state.currentPositionMs)
    }

    @Test
    fun testProductionCode_doesNotContainRunBlockingInPlaybackService() {
        val serviceFile = File("src/main/java/io/github/yisus/avenor/PlaybackService.kt")
        val altFile = File("app/src/main/java/io/github/yisus/avenor/PlaybackService.kt")
        val target = if (serviceFile.exists()) serviceFile else altFile
        assertTrue("PlaybackService.kt file must exist", target.exists())

        val content = target.readText()
        assertFalse(
            "PlaybackService.kt MUST NOT contain runBlocking in production code",
            content.contains("runBlocking")
        )
    }
}
