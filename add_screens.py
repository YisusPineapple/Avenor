import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

screens_old = """
sealed class Screen {
    object Library : Screen()
    object NowPlaying : Screen()
    object Settings : Screen()
    object Equalizer : Screen()
    object Lyrics : Screen()
    data class PlaylistDetails(val playlistId: Int, val playlistName: String) : Screen()
}
"""

screens_new = """
sealed class Screen {
    object Library : Screen()
    object NowPlaying : Screen()
    object Settings : Screen()
    object Equalizer : Screen()
    object Lyrics : Screen()
    object Queue : Screen()
    object Recap : Screen()
    data class PlaylistDetails(val playlistId: Int, val playlistName: String) : Screen()
}
"""

content = content.replace(screens_old.strip(), screens_new.strip())

when_title_old = """
                                when (val s = currentScreen) {
                                    is Screen.Settings -> "Settings"
                                    is Screen.Equalizer -> "Equalizer"
                                    is Screen.Lyrics -> "Lyrics"
                                    is Screen.PlaylistDetails -> s.playlistName
                                    else -> "Nexo"
                                }
"""

when_title_new = """
                                when (val s = currentScreen) {
                                    is Screen.Settings -> "Settings"
                                    is Screen.Equalizer -> "Equalizer"
                                    is Screen.Lyrics -> "Lyrics"
                                    is Screen.Queue -> "Play Queue"
                                    is Screen.Recap -> "Your Recap"
                                    is Screen.PlaylistDetails -> s.playlistName
                                    else -> "Nexo"
                                }
"""
content = content.replace(when_title_old.strip(), when_title_new.strip())

when_nav_old = """
                            when (val screen = currentScreen) {
                                is Screen.Library -> LibraryScreen(viewModel, onNavigateToPlaylist = { currentScreen = it })
                                is Screen.NowPlaying -> NowPlayingScreen(viewModel, onNavigateToEq = { currentScreen = Screen.Equalizer }, onNavigateToLyrics = { currentScreen = Screen.Lyrics })
                                is Screen.Settings -> SettingsScreen(viewModel)
                                is Screen.Equalizer -> EqScreen(viewModel)
                                is Screen.Lyrics -> LyricsScreen(viewModel)
                                is Screen.PlaylistDetails -> PlaylistDetailsScreen(viewModel, screen.playlistId)
                            }
"""

when_nav_new = """
                            when (val screen = currentScreen) {
                                is Screen.Library -> LibraryScreen(viewModel, onNavigateToPlaylist = { currentScreen = it })
                                is Screen.NowPlaying -> NowPlayingScreen(viewModel, onNavigateToEq = { currentScreen = Screen.Equalizer }, onNavigateToLyrics = { currentScreen = Screen.Lyrics }, onNavigateToQueue = { currentScreen = Screen.Queue })
                                is Screen.Settings -> SettingsScreen(viewModel)
                                is Screen.Equalizer -> EqScreen(viewModel)
                                is Screen.Lyrics -> LyricsScreen(viewModel)
                                is Screen.Queue -> QueueScreen(viewModel)
                                is Screen.Recap -> RecapScreen(viewModel)
                                is Screen.PlaylistDetails -> PlaylistDetailsScreen(viewModel, screen.playlistId)
                            }
"""
content = content.replace(when_nav_old.strip(), when_nav_new.strip())

# Add Recap to Library actions
lib_actions_old = """
                        actions = {
                            if (currentScreen == Screen.Library || currentScreen == Screen.NowPlaying) {
                                IconButton(onClick = { currentScreen = Screen.Settings }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                            }
                        },
"""

lib_actions_new = """
                        actions = {
                            if (currentScreen == Screen.Library) {
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(text = { Text("Settings") }, onClick = { currentScreen = Screen.Settings; menuExpanded = false }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                                        DropdownMenuItem(text = { Text("Nexo Recap") }, onClick = { currentScreen = Screen.Recap; menuExpanded = false }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
                                    }
                                }
                            } else if (currentScreen == Screen.NowPlaying) {
                                IconButton(onClick = { currentScreen = Screen.Settings }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                            }
                        },
"""
content = content.replace(lib_actions_old.strip(), lib_actions_new.strip())

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
