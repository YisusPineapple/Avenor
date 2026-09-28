package io.github.yisus.avenor.desktop

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for task 0.8.
 *
 * Before 0.8, DesktopPlaybackController's try/catch in init only set
 * PlaybackState.Error when libVLC was missing. Main.kt showed that error as
 * small subtitle text in the bottom bar, with no dedicated screen and no
 * download link. 0.8 exposes an explicit VlcAvailability state flow that
 * Main.kt uses to render a dedicated "VLC not found" screen.
 *
 * These tests build the real controller. In CI/sandbox environments without
 * VLC installed, availability must be Unavailable. If VLC happens to be
 * installed, the unavailable-state assertion is skipped via Assume.
 */
class DesktopPlaybackControllerTest {

    private var controller: DesktopPlaybackController? = null

    @Before
    fun setup() {
        controller = null
    }

    @After
    fun tearDown() {
        controller?.release()
        controller = null
    }

    @Test
    fun testInit_doesNotThrowEvenWithoutVlc() {
        val c = DesktopPlaybackController()
        controller = c
        assertNotNull(c)
        assertNotNull(c.vlcAvailability.value)
    }

    @Test
    fun testInit_withoutVlcEnvironment_setsUnavailableState() {
        val c = DesktopPlaybackController()
        controller = c

        val status = c.vlcAvailability.value
        assumeTrue(
            "VLC detected in this environment; skipping Unavailable-state assertion",
            status is VlcAvailability.Unavailable
        )

        val unavailable = status as VlcAvailability.Unavailable
        assertTrue(
            "Unavailable.reason must not be blank",
            unavailable.reason.isNotBlank()
        )
    }

    @Test
    fun testRelease_isIdempotent() {
        val c = DesktopPlaybackController()
        c.release()
        c.release() // no debe lanzar
    }
}
