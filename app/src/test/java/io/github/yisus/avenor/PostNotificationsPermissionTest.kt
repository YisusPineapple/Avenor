package io.github.yisus.avenor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import io.github.yisus.avenor.ui.navigation.shouldRequestPostNotifications
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PostNotificationsPermissionTest {

    @Test
    fun testManifest_declaresPostNotificationsPermission() {
        val context = RuntimeEnvironment.getApplication()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val fromPackageManager = packageInfo.requestedPermissions?.toList().orEmpty()

        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }

        val fromManifestXml = if (manifestFile != null) {
            val doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(manifestFile)
            val nodes = doc.getElementsByTagName("uses-permission")
            (0 until nodes.length).mapNotNull { idx ->
                nodes.item(idx)?.attributes?.getNamedItem("android:name")?.nodeValue
            }
        } else {
            emptyList()
        }

        val requested = (fromPackageManager + fromManifestXml).toSet()
        assertTrue(
            "AndroidManifest must declare POST_NOTIFICATIONS for SDK 33+",
            requested.contains(Manifest.permission.POST_NOTIFICATIONS)
        )
    }

    @Test
    fun testMainActivity_launchesWithoutPostNotificationsGranted() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(
            "Must request POST_NOTIFICATIONS on SDK 33+ when not granted",
            shouldRequestPostNotifications(app, Build.VERSION_CODES.TIRAMISU)
        )
        assertFalse(
            "Must not request POST_NOTIFICATIONS on SDK < 33",
            shouldRequestPostNotifications(app, Build.VERSION_CODES.S_V2)
        )

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(
            "Must not re-request POST_NOTIFICATIONS when already granted",
            shouldRequestPostNotifications(app, Build.VERSION_CODES.TIRAMISU)
        )
    }
}
