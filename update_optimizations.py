with open("app/src/main/java/io/github/yisus/nexo/SystemOptimizations.kt", "r") as f:
    content = f.read()

content = content.replace("fun CrossPlatformBridge(modifier: Modifier = Modifier)", "fun DesktopParityDashboard(modifier: Modifier = Modifier)")

manager_code = """
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key

object DesktopMediaKeyManager {
    fun handleMediaKeyEvent(event: KeyEvent, viewModel: PlaybackViewModel): Boolean {
        return if (DeviceProfileManager.isDesktop()) {
            when (event.key) {
                Key.MediaPlayPause, Key.Spacebar -> { viewModel.togglePlayPause(); true }
                Key.MediaNext, Key.DirectionRight -> { viewModel.skipToNext(); true }
                Key.MediaPrevious, Key.DirectionLeft -> { viewModel.skipToPrevious(); true }
                Key.MediaPlay -> { if (viewModel.isPlaying.value == false) viewModel.togglePlayPause(); true }
                Key.MediaPause -> { if (viewModel.isPlaying.value == true) viewModel.togglePlayPause(); true }
                else -> false
            }
        } else {
            false
        }
    }
}
"""

if "object DesktopMediaKeyManager" not in content:
    content = content.replace("object DeviceProfileManager {", manager_code + "\nobject DeviceProfileManager {")

with open("app/src/main/java/io/github/yisus/nexo/SystemOptimizations.kt", "w") as f:
    f.write(content)
