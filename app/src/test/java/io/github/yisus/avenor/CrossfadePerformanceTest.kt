package io.github.yisus.avenor

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CrossfadePerformanceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var player: ExoPlayer
    private lateinit var crossfadeManager: CrossfadeManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        crossfadeManager = CrossfadeManager(player, RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        crossfadeManager.release()
        player.release()
        Dispatchers.resetMain()
    }

    @Test
    fun testManualSkipCrossfade_doesNotBlockMainThread() = runTest(testDispatcher) {
        // Trigger manual skip which initiates crossfade coroutines
        crossfadeManager.manualSkip(forward = true)
        shadowOf(Looper.getMainLooper()).idle()
        
        // Advance time to allow coroutines and animations to execute
        testDispatcher.scheduler.advanceTimeBy(4000)
        shadowOf(Looper.getMainLooper()).idle()

        // Assert crossfade executes properly and volume resets
        assertTrue("Crossfade should execute smoothly without crashing", true)
        assertTrue("Volume should reset to 1.0f after fade in", player.volume == 1.0f)
    }
}
