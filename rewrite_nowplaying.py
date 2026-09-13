import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# We find the NowPlayingScreen block
start_sig = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun NowPlayingScreen("
end_sig = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun EqScreen("

start_idx = content.find(start_sig)
end_idx = content.find(end_sig, start_idx)

new_now_playing = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit, onNavigateToQueue: () -> Unit) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isSleepTimerActive by viewModel.sleepTimerActive.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState(initial = null)
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showLrcEditor by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) { while (isPlaying) { viewModel.updatePosition(); delay(1000) } }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer") },
            text = { Text("Pause playback automatically after:") },
            confirmButton = { TextButton(onClick = { viewModel.setSleepTimer(15); showSleepTimerDialog = false }) { Text("15m") } },
            dismissButton = { TextButton(onClick = { viewModel.setSleepTimer(0); showSleepTimerDialog = false }) { Text("Off") } }
        )
    }

    if (currentSong == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No song selected", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    val style = appSettings?.nowPlayingStyle ?: "CLASSIC"
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Background for Apple Music style
        if (style == "APPLE_MUSIC") {
            AsyncImage(
                model = currentSong?.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.3f),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.Darken)
            )
        }
        
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            
            // Artwork
            val artShape = when(style) {
                "EXPRESSIVE" -> RoundedCornerShape(16.dp)
                "APPLE_MUSIC" -> RoundedCornerShape(12.dp)
                else -> RoundedCornerShape(40.dp)
            }
            
            val artModifier = when(style) {
                "EXPRESSIVE" -> Modifier.fillMaxWidth().aspectRatio(1f)
                "APPLE_MUSIC" -> Modifier.fillMaxWidth(0.85f).aspectRatio(1f)
                else -> Modifier.fillMaxWidth().aspectRatio(1f)
            }
            
            Card(modifier = artModifier, shape = artShape, elevation = CardDefaults.cardElevation(defaultElevation = if (style == "APPLE_MUSIC") 24.dp else 16.dp)) {
                AsyncImage(model = currentSong?.albumArtUri, contentDescription = "Album Art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }

            // Text info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(currentSong?.title ?: "Unknown Title", style = if(style == "APPLE_MUSIC") MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(currentSong?.artist ?: "Unknown Artist", style = MaterialTheme.typography.titleMedium, color = if(style == "APPLE_MUSIC") MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary)
            }

            // Progress
            Column {
                val progress = if (currentSong?.durationMs != null && currentSong!!.durationMs > 0) { currentPosition.toFloat() / currentSong!!.durationMs.toFloat() } else 0f
                Slider(
                    value = progress,
                    onValueChange = { viewModel.seekTo((it * (currentSong?.durationMs ?: 0)).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if(style == "EXPRESSIVE") SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary, activeTrackColor = MaterialTheme.colorScheme.secondary) else SliderDefaults.colors()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(currentPosition), style = MaterialTheme.typography.labelMedium)
                    Text(formatMs(currentSong?.durationMs ?: 0), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { viewModel.skipPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(48.dp))
                }
                
                if (style == "EXPRESSIVE") {
                    FilledIconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(80.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp))
                    }
                } else {
                    FloatingActionButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(80.dp), shape = androidx.compose.foundation.shape.CircleShape) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp))
                    }
                }

                IconButton(onClick = { viewModel.skipNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    val repeatIcon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                        else -> Icons.Default.Repeat
                    }
                    val tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                    Icon(repeatIcon, contentDescription = "Repeat", tint = tint)
                }
            }
            
            // Bottom Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { onNavigateToQueue() }) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Queue") }
                IconButton(onClick = { showSleepTimerDialog = true }) { Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                IconButton(onClick = { onNavigateToEq() }) { Icon(Icons.Default.Equalizer, contentDescription = "EQ") }
            }
        }
    }
}
"""

content = content[:start_idx] + new_now_playing + content[end_idx:]

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
