import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# First replace top of NowPlayingScreen
old_top = """fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit, onNavigateToQueue: () -> Unit) {
val currentSong by viewModel.currentSong.collectAsState()
val isPlaying by viewModel.isPlaying.collectAsState()
val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
val repeatMode by viewModel.repeatMode.collectAsState()
val isSleepTimerActive by viewModel.sleepTimerActive.collectAsState()
val appSettings by viewModel.appSettings.collectAsState()
var showSleepTimerDialog by remember { mutableStateOf(false) }"""
new_top = """fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit, onNavigateToQueue: () -> Unit) {
val currentSong by viewModel.currentSong.collectAsState()
val isPlaying by viewModel.isPlaying.collectAsState()
val currentPosition by viewModel.currentPosition.collectAsState()
val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
val repeatMode by viewModel.repeatMode.collectAsState()
val isSleepTimerActive by viewModel.sleepTimerActive.collectAsState()
val appSettings by viewModel.appSettings.collectAsState()
var showSleepTimerDialog by remember { mutableStateOf(false) }
var showLrcEditor by remember { mutableStateOf(false) }"""

content = content.replace(old_top, new_top)

# Replace the bottom row of buttons to include Sync Button
old_bottom = """IconButton(onClick = { showSleepTimerDialog = true }) { Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
IconButton(onClick = { onNavigateToEq() }) { Icon(Icons.Default.Tune, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
IconButton(onClick = { onNavigateToLyrics() }) { Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}
}
}"""
new_bottom = """IconButton(onClick = { showSleepTimerDialog = true }) { Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
IconButton(onClick = { showLrcEditor = true }) { Icon(Icons.Default.Sync, contentDescription = "Sync Lyrics", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
IconButton(onClick = { onNavigateToEq() }) { Icon(Icons.Default.Tune, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
IconButton(onClick = { onNavigateToLyrics() }) { Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}
}
if (showLrcEditor) {
    LrcSyncEditor(viewModel = viewModel, onDismiss = { showLrcEditor = false })
}
}"""

content = content.replace(old_bottom, new_bottom)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
