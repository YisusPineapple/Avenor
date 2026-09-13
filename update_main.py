with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Replace onKeyEvent implementation
old_key_event = """            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.Spacebar, Key.Enter, Key.MediaPlayPause -> { viewModel.togglePlayPause(); true }
                        Key.DirectionRight, Key.MediaNext -> { viewModel.skipToNext(); true }
                        Key.DirectionLeft, Key.MediaPrevious -> { viewModel.skipToPrevious(); true }
                        Key.MediaPlay -> { if (viewModel.isPlaying.value == false) viewModel.togglePlayPause(); true }
                        Key.MediaPause -> { if (viewModel.isPlaying.value == true) viewModel.togglePlayPause(); true }
                        else -> false
                    }
                } else false
            }"""

new_key_event = """            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    if (DeviceProfileManager.isDesktop()) {
                        DesktopMediaKeyManager.handleMediaKeyEvent(event, viewModel)
                    } else {
                        when (event.key) {
                            Key.Spacebar, Key.Enter, Key.MediaPlayPause -> { viewModel.togglePlayPause(); true }
                            Key.DirectionRight, Key.MediaNext -> { viewModel.skipToNext(); true }
                            Key.DirectionLeft, Key.MediaPrevious -> { viewModel.skipToPrevious(); true }
                            Key.MediaPlay -> { if (viewModel.isPlaying.value == false) viewModel.togglePlayPause(); true }
                            Key.MediaPause -> { if (viewModel.isPlaying.value == true) viewModel.togglePlayPause(); true }
                            else -> false
                        }
                    }
                } else false
            }"""
content = content.replace(old_key_event, new_key_event)

# Add DesktopParityDashboard to SettingsScreen
old_settings = """fun SettingsScreen(viewModel: PlaybackViewModel) {
val appSettings by viewModel.appSettings.collectAsState()
val settings = appSettings ?: AppSetting()

LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {"""

new_settings = """fun SettingsScreen(viewModel: PlaybackViewModel) {
val appSettings by viewModel.appSettings.collectAsState()
val settings = appSettings ?: AppSetting()

LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
item {
    DesktopParityDashboard()
    Spacer(modifier = Modifier.height(16.dp))
}"""
content = content.replace(old_settings, new_settings)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
