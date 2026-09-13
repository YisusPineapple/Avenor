import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Fix the first when expression in TopAppBar
when_top_old = """
                                when (val s = currentScreen) {
                                    is Screen.Settings -> "Settings"
                                    is Screen.Equalizer -> "Equalizer"
                                    is Screen.Lyrics -> "Lyrics"
                                    is Screen.PlaylistDetails -> s.playlistName
                                    else -> "Nexo"
                                }
"""
when_top_new = """
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
if when_top_old.strip() in content:
    content = content.replace(when_top_old.strip(), when_top_new.strip())
else:
    # try line by line
    content = content.replace("is Screen.Lyrics -> \"Lyrics\"", "is Screen.Lyrics -> \"Lyrics\"\n                                    is Screen.Queue -> \"Play Queue\"\n                                    is Screen.Recap -> \"Your Recap\"")

# Fix the navigation when expression
nav_old = """
                            when (val screen = currentScreen) {
                                is Screen.Library -> LibraryScreen(viewModel, onNavigateToPlaylist = { currentScreen = it })
                                is Screen.NowPlaying -> NowPlayingScreen(viewModel, onNavigateToEq = { currentScreen = Screen.Equalizer }, onNavigateToLyrics = { currentScreen = Screen.Lyrics }, onNavigateToQueue = { currentScreen = Screen.Queue })
                                is Screen.Settings -> SettingsScreen(viewModel)
                                is Screen.Equalizer -> EqScreen(viewModel)
                                is Screen.Lyrics -> LyricsScreen(viewModel)
                                is Screen.PlaylistDetails -> PlaylistDetailsScreen(viewModel, screen.playlistId)
                            }
"""
if nav_old.strip() in content:
    content = content.replace(nav_old.strip(), nav_old.strip().replace("is Screen.Lyrics -> LyricsScreen(viewModel)", "is Screen.Lyrics -> LyricsScreen(viewModel)\n                                is Screen.Queue -> QueueScreen(viewModel)\n                                is Screen.Recap -> RecapScreen(viewModel)"))
else:
    content = content.replace("is Screen.Lyrics -> LyricsScreen(viewModel)", "is Screen.Lyrics -> LyricsScreen(viewModel)\n                                is Screen.Queue -> QueueScreen(viewModel)\n                                is Screen.Recap -> RecapScreen(viewModel)")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
