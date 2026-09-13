import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Fix putText -> putExtra
content = content.replace("putText(android.content.Intent.EXTRA_TEXT, shareText)", "putExtra(android.content.Intent.EXTRA_TEXT, shareText)")

# Fix onNavigateToQueue in NowPlayingScreen if I missed one
now_playing_call_old = "is Screen.NowPlaying -> NowPlayingScreen(viewModel, onNavigateToEq = { currentScreen = Screen.Equalizer }, onNavigateToLyrics = { currentScreen = Screen.Lyrics })"
now_playing_call_new = "is Screen.NowPlaying -> NowPlayingScreen(viewModel, onNavigateToEq = { currentScreen = Screen.Equalizer }, onNavigateToLyrics = { currentScreen = Screen.Lyrics }, onNavigateToQueue = { currentScreen = Screen.Queue })"
content = content.replace(now_playing_call_old, now_playing_call_new)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
