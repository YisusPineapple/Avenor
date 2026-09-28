package io.github.yisus.avenor

import android.app.Service
import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression tests for task 0.6.
 *
 * Audits that PlaybackService can be created and started on Android 14 (API 34),
 * where foreground services of type mediaPlayback have stricter lifecycle
 * requirements. Media3 1.2.1's MediaSessionService manages startForeground()
 * internally; these tests verify that Avenor's onStartCommand override and
 * custom MediaNotificationProvider do not crash on the stricter SDK.
 *
 * Before 0.6 this scenario had no test coverage; the audit is the first
 * automated check that Avenor's foreground service survives API 34 lifecycle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServiceApi34Test {

    @Test
    fun testServiceOnCreate_atSdk34_createsMediaSessionWithoutCrash() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        assertNotNull(service)
        assertNotNull(service.mediaSession)
        assertNotNull(service.playbackCoordinator)

        shadowOf(Looper.getMainLooper()).idle()
        controller.destroy()
    }

    @Test
    fun testOnStartCommand_widgetPlayPauseAction_atSdk34_doesNotCrash() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val intent = Intent(service, PlaybackService::class.java).apply {
            action = AvenorWidgetProvider.ACTION_PLAY_PAUSE
        }

        val result = service.onStartCommand(intent, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "onStartCommand must return a valid START_* constant",
            result == Service.START_STICKY ||
                result == Service.START_NOT_STICKY ||
                result == Service.START_REDELIVER_INTENT
        )

        controller.destroy()
    }

    @Test
    fun testOnStartCommand_widgetNextAction_atSdk34_doesNotCrash() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val intent = Intent(service, PlaybackService::class.java).apply {
            action = AvenorWidgetProvider.ACTION_NEXT
        }

        val result = service.onStartCommand(intent, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "onStartCommand must return a valid START_* constant",
            result == Service.START_STICKY ||
                result == Service.START_NOT_STICKY ||
                result == Service.START_REDELIVER_INTENT
        )

        controller.destroy()
    }
}
