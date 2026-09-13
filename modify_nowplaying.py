import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add Metadata state
old_nowplaying_states = """
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
"""

new_nowplaying_states = """
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val currentMetadata by viewModel.currentMetadata.collectAsState()
"""
content = content.replace(old_nowplaying_states.strip(), new_nowplaying_states.strip())

# Add BoxWithConstraints and update layout to be responsive and show metadata badge
old_nowplaying_body = """
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
"""

new_nowplaying_body = """
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        val isLandscape = maxWidth > maxHeight
        
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                // Album Art Side
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(16.dp)) {
                    val artUri = currentSong?.albumArtUri
                    if (artUri != null) {
                        AsyncImage(model = artUri, contentDescription = "Album Art", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                // Controls Side
                Column(modifier = Modifier.weight(1f).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (currentMetadata != null) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(currentMetadata!!, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(currentSong?.title ?: "No Track", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(currentSong?.artist ?: "Unknown Artist", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(currentPosition), style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(currentSong?.durationMs ?: 0L), style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = if (currentSong?.durationMs ?: 0L > 0L) (currentPosition.toFloat() / currentSong!!.durationMs.toFloat()) else 0f,
                        onValueChange = { viewModel.seekTo((it * (currentSong?.durationMs ?: 0L)).toLong()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.skipToPrevious() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(48.dp)) }
                        IconButton(onClick = { if (isPlaying) viewModel.pause() else viewModel.play() }) {
                            Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, contentDescription = "Play/Pause", modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.skipToNext() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(48.dp)) }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
"""
content = content.replace(old_nowplaying_body.strip(), new_nowplaying_body.strip())

# Now we need to close the BoxWithConstraints block. I will find the end of NowPlayingScreen.
# It ends with:
#        }
#    }
#}
#
#@Composable
#fun EqScreen(viewModel: PlaybackViewModel) {

old_nowplaying_end = """
            }
        }
    }
}

@Composable
fun EqScreen(viewModel: PlaybackViewModel) {
"""

new_nowplaying_end = """
            }
        }
        
        if (!isLandscape) {
            if (currentMetadata != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp)) {
                        Text(currentMetadata!!, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
fun EqScreen(viewModel: PlaybackViewModel) {
"""

content = content.replace(old_nowplaying_end.strip(), new_nowplaying_end.strip())

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)

