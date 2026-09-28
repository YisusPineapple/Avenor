package io.github.yisus.avenor

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Regression test for task 0.1.
 *
 * Before 0.1, DISCONTINUITY_REASON_AUTO_TRANSITION with isCrossfadeEnabled = true
 * entered the dual-player "ghost player" path and silently skipped a full track
 * from the queue. After 0.1 the auto-transition path is hard-wired to
 * cancelAndReset() regardless of isCrossfadeEnabled. Manual skip crossfade is
 * covered separately and must remain untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CrossfadeAutoTransitionRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var player: ExoPlayer
    private lateinit var crossfadeManager: DualPlayerCrossfadeManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        crossfadeManager = DualPlayerCrossfadeManager(player, RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        crossfadeManager.release()
        player.release()
        Dispatchers.resetMain()
    }

    private fun createDummyMediaItem(id: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri("file:///android_asset/dummy_$id.mp3")
            .build()

    private fun createDummyPositionInfo(): Player.PositionInfo =
        Player.PositionInfo(
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

    @Test
    fun testAutoTransition_withCrossfadeEnabled_doesNotFade() = runTest(testDispatcher) {
        // Arrange: crossfade explicitly enabled (worst-case before 0.1)
        crossfadeManager.isCrossfadeEnabled = true
        player.volume = 1.0f
        val posInfo = createDummyPositionInfo()

        // Act: dispatch AUTO_TRANSITION discontinuity
        crossfadeManager.playerListener.onPositionDiscontinuity(
            posInfo,
            posInfo,
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION
        )
        shadowOf(Looper.getMainLooper()).idle()

        // Assert immediate post-dispatch invariants
        assertEquals(
            "Volume must stay at 1.0f on AUTO_TRANSITION regardless of isCrossfadeEnabled",
            1.0f,
            player.volume,
            0.001f
        )
        assertEquals(
            "transitionState must remain IDLE after AUTO_TRANSITION",
            TransitionState.IDLE,
            crossfadeManager.transitionState.value
        )
        assertFalse(
            "isCrossfading must be false after AUTO_TRANSITION",
            crossfadeManager.isCrossfading.value
        )

        // Assert: advance scheduler past any plausible fade window. If a
        // ValueAnimator had been scheduled (pre-0.1 bug), volume would drift
        // and/or transitionState would flip through FADING_*.
        testDispatcher.scheduler.advanceTimeBy(5_000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "Volume must still be 1.0f after scheduler advance — no fade was scheduled",
            1.0f,
            player.volume,
            0.001f
        )
        assertEquals(
            "transitionState must still be IDLE after scheduler advance",
            TransitionState.IDLE,
            crossfadeManager.transitionState.value
        )
        assertFalse(
            "isCrossfading must still be false after scheduler advance",
            crossfadeManager.isCrossfading.value
        )
    }
}
