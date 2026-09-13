import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# I will add the LrcSyncEditor to LyricsScreen as requested: "Implement a UI overlay in the Now Playing screen with a fine-grained slider to nudge the synchronized lyrics forward or backward by milliseconds to correct alignment issues." wait, they said Now Playing screen, but also "LrcSyncEditor component that allows users to tap a button to adjust the timing offset of lyrics in real-time". Let's add a button in NowPlayingScreen that opens a bottom sheet or a dialog for LrcSyncEditor.

lrc_editor_composable = """
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LrcSyncEditor(viewModel: PlaybackViewModel, onDismiss: () -> Unit) {
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
    
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Lyric Synchronization Offset", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Fine-tune the timing for the current track. Settings are saved per-song.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            
            Text("${if (lyricsOffset > 0) "+" else ""}${lyricsOffset} ms", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            
            Slider(
                value = lyricsOffset.toFloat(),
                onValueChange = { viewModel.setLyricsOffset(it.toLong()) },
                onValueChangeFinished = { viewModel.saveLyricsOffset() },
                valueRange = -5000f..5000f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-5000 ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+5000 ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Done")
            }
        }
    }
}
"""

if "fun LrcSyncEditor" not in content:
    content = content.replace("// ---------------------------------------------------------", lrc_editor_composable + "\n// ---------------------------------------------------------", 1)

# Now, add state to NowPlayingScreen
if "var showLrcEditor by remember { mutableStateOf(false) }" not in content:
    old_now_playing_top = """fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit, onNavigateToQueue: () -> Unit) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isSleepTimerActive by viewModel.sleepTimerActive.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    var showSleepTimerDialog by remember { mutableStateOf(false) }"""

    new_now_playing_top = """fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit, onNavigateToQueue: () -> Unit) {
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
    content = content.replace(old_now_playing_top, new_now_playing_top)

# Also add the button in NowPlayingScreen (bottom icons)
old_bottom_icons = """        IconButton(onClick = { showSleepTimerDialog = true }) { Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = { onNavigateToEq() }) { Icon(Icons.Default.Tune, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = { onNavigateToLyrics() }) { Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }"""
new_bottom_icons = """        IconButton(onClick = { showSleepTimerDialog = true }) { Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = { showLrcEditor = true }) { Icon(Icons.Default.Sync, contentDescription = "Sync Lyrics", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = { onNavigateToEq() }) { Icon(Icons.Default.Tune, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = { onNavigateToLyrics() }) { Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    
    if (showLrcEditor) {
        LrcSyncEditor(viewModel = viewModel, onDismiss = { showLrcEditor = false })
    }"""
content = content.replace(old_bottom_icons, new_bottom_icons)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)

