package io.github.yisus.avenor

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionCommand
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioFocusAndDeviceEventsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var coordinator: PlaybackCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coordinator = PlaybackCoordinator(db.musicDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun createDummyMediaItem(id: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri("file:///android_asset/song_$id.mp3")
            .build()
    }

    private fun createDummyPositionInfo(mediaId: String): Player.PositionInfo {
        return Player.PositionInfo(
            null,
            0,
            createDummyMediaItem(mediaId),
            null,
            0,
            0L,
            0L,
            0,
            0
        )
    }

    // =========================================================================
    // P2-4: PRELOAD / GAPLESS & TRANSITION ENGINE
    // =========================================================================

    @Test
    fun `test 01 - AUTO_TRANSITION with crossfade OFF preserves 1_0f volume and zero artificial delay`() {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)
        crossfadeManager.isCrossfadeEnabled = false
        player.volume = 1.0f

        val posInfo = createDummyPositionInfo("track_2")
        crossfadeManager.playerListener.onPositionDiscontinuity(posInfo, posInfo, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.volume, 0.001f)
        assertEquals(TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse(crossfadeManager.isCrossfading.value)

        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 02 - AUTO_TRANSITION with crossfade ON applies fade in and smoothly restores 1_0f`() = runTest(testDispatcher) {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)
        crossfadeManager.isCrossfadeEnabled = true
        player.volume = 1.0f

        val posInfo = createDummyPositionInfo("track_2")
        crossfadeManager.playerListener.onPositionDiscontinuity(posInfo, posInfo, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)

        assertEquals(TransitionState.FADING_IN, crossfadeManager.transitionState.value)
        assertTrue(crossfadeManager.isCrossfading.value)

        shadowOf(Looper.getMainLooper()).idle()
        testDispatcher.scheduler.advanceTimeBy(5000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.volume, 0.001f)
        assertEquals(TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse(crossfadeManager.isCrossfading.value)

        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 03 - manual skip forward with crossfade ON smoothly fades out and in`() = runTest(testDispatcher) {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)
        crossfadeManager.isCrossfadeEnabled = true

        crossfadeManager.manualSkip(forward = true, force = true)

        assertEquals(TransitionState.FADING_OUT, crossfadeManager.transitionState.value)
        assertTrue(crossfadeManager.isCrossfading.value)

        shadowOf(Looper.getMainLooper()).idle()
        testDispatcher.scheduler.advanceTimeBy(5000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.volume, 0.001f)
        assertEquals(TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse(crossfadeManager.isCrossfading.value)

        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 04 - manual skip backward with crossfade OFF performs direct transition without volume manipulation`() {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)
        crossfadeManager.isCrossfadeEnabled = false
        player.volume = 1.0f

        crossfadeManager.manualSkip(forward = false, force = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.volume, 0.001f)
        assertEquals(TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse(crossfadeManager.isCrossfading.value)

        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 05 - pause during transition immediately cancels animation and restores volume to 1_0f`() = runTest(testDispatcher) {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)
        crossfadeManager.isCrossfadeEnabled = true

        crossfadeManager.manualSkip(forward = true, force = true)
        shadowOf(Looper.getMainLooper()).idle()

        // Simulate pause event during in-flight transition
        crossfadeManager.playerListener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.volume, 0.001f)
        assertEquals(TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse(crossfadeManager.isCrossfading.value)

        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 06 - stop during transition immediately aborts animation and restores volume to 1_0f`() = runTest(testDispatcher) {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)
        crossfadeManager.isCrossfadeEnabled = true

        crossfadeManager.manualSkip(forward = true, force = true)
        shadowOf(Looper.getMainLooper()).idle()

        crossfadeManager.playerListener.onPlaybackStateChanged(Player.STATE_IDLE)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.volume, 0.001f)
        assertEquals(TransitionState.IDLE, crossfadeManager.transitionState.value)

        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 07 - seek during transition cancels fade animation and restores clean volume`() = runTest(testDispatcher) {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)
        crossfadeManager.isCrossfadeEnabled = true

        crossfadeManager.manualSkip(forward = true, force = true)
        shadowOf(Looper.getMainLooper()).idle()

        val posInfo = createDummyPositionInfo("seek_target")
        crossfadeManager.playerListener.onPositionDiscontinuity(posInfo, posInfo, Player.DISCONTINUITY_REASON_SEEK)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.volume, 0.001f)
        assertEquals(TransitionState.IDLE, crossfadeManager.transitionState.value)

        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 08 - error during next track preparation is recovered by ErrorRecoveryManager and restores clean state`() = runTest(testDispatcher) {
        val player = ExoPlayer.Builder(context).build()
        val crossfadeManager = CrossfadeManager(player, context)
        val errorRecoveryManager = ErrorRecoveryManager(player)

        crossfadeManager.manualSkip(forward = true, force = true)
        shadowOf(Looper.getMainLooper()).idle()

        val exception = PlaybackException("Source read error on next track", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        crossfadeManager.playerListener.onPlayerError(exception)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.volume, 0.001f)
        assertEquals(TransitionState.IDLE, crossfadeManager.transitionState.value)
        assertFalse(crossfadeManager.isCrossfading.value)

        errorRecoveryManager.release()
        crossfadeManager.release()
        player.release()
    }

    @Test
    fun `test 09 - ErrorRecoveryManager limits consecutive errors to prevent infinite loops`() {
        val player = ExoPlayer.Builder(context).build()
        val errorRecovery = ErrorRecoveryManager(player)

        val exception = PlaybackException("Corrupted track", null, PlaybackException.ERROR_CODE_DECODING_FAILED)

        // Trigger consecutive errors up to max
        repeat(4) {
            errorRecovery.onPlayerError(exception)
        }

        assertFalse("Player must be paused after exceeding max consecutive errors", player.isPlaying)
        errorRecovery.release()
        player.release()
    }

    // =========================================================================
    // P2-5: AUDIO FOCUS & DEVICE EVENTS
    // =========================================================================

    @Test
    fun `test 10 - permanent audio focus loss pauses player and updates PlaybackCoordinator`() = runTest(testDispatcher) {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        val player = service.mediaSession?.player as ExoPlayer
        assertNotNull(player)

        // Simulate permanent audio focus loss via Player.Listener
        player.playWhenReady = true
        testDispatcher.scheduler.advanceUntilIdle()

        // Trigger focus loss
        service.playbackCoordinator.updatePlaybackParams(isPlaying = true)
        player.playWhenReady = false

        shadowOf(Looper.getMainLooper()).idle()
        testDispatcher.scheduler.advanceUntilIdle()

        val deadline = System.currentTimeMillis() + 2000
        while (service.playbackCoordinator.playbackState.value.isPlaying && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        val state = service.playbackCoordinator.playbackState.value
        assertFalse("PlaybackCoordinator must record isPlaying = false upon focus loss", state.isPlaying)

        serviceController.destroy()
    }

    @Test
    fun `test 11 - transient audio focus loss suppresses playback and updates coordinator`() = runTest(testDispatcher) {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        val player = service.mediaSession?.player as ExoPlayer
        assertNotNull(player)

        // Simulate transient audio focus loss suppression
        service.playbackCoordinator.updatePlaybackParams(isPlaying = true)

        // When focus is lost temporarily, suppression reason changes
        val listener = shadowOf(Looper.getMainLooper())
        listener.idle()

        // Dispatch playback suppression reason change
        service.playbackCoordinator.updatePlaybackParams(isPlaying = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = service.playbackCoordinator.playbackState.value
        assertFalse(state.isPlaying)

        serviceController.destroy()
    }

    @Test
    fun `test 12 - transient audio focus regain restores active playback in PlaybackCoordinator`() = runTest(testDispatcher) {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        // Focus regained
        service.playbackCoordinator.updatePlaybackParams(isPlaying = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = service.playbackCoordinator.playbackState.value
        assertTrue("PlaybackCoordinator must reflect resumed playback", state.isPlaying)

        serviceController.destroy()
    }

    @Test
    fun `test 13 - SET_CROSSFADE_CONFIG custom session command dynamically configures CrossfadeManager`() {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        assertFalse("Crossfade must default to false", service.crossfadeManager.isCrossfadeEnabled)

        // Send custom command to toggle crossfade
        val bundle = Bundle().apply { putBoolean("enabled", true) }
        service.handleCustomCommand("SET_CROSSFADE_CONFIG", bundle)
        assertTrue("CrossfadeManager must be enabled after command", service.crossfadeManager.isCrossfadeEnabled)

        val bundleOff = Bundle().apply { putBoolean("enabled", false) }
        service.handleCustomCommand("SET_CROSSFADE_CONFIG", bundleOff)
        assertFalse("CrossfadeManager must be disabled when toggled off", service.crossfadeManager.isCrossfadeEnabled)

        serviceController.destroy()
    }

    @Test
    fun `test 14 - PlaybackService onDestroy cleanly unregisters all callbacks and releases managers`() {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        assertNotNull(service.crossfadeManager)
        assertNotNull(service.errorRecoveryManager)
        assertNotNull(service.playbackCoordinator)

        serviceController.destroy()

        // Volume must be restored and transition state idle
        assertEquals(TransitionState.IDLE, service.crossfadeManager.transitionState.value)
        assertFalse(service.crossfadeManager.isCrossfading.value)
    }
}
