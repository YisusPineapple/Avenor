import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

now_playing_old = """fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit) {"""
now_playing_new = """fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit, onNavigateToQueue: () -> Unit) {"""
content = content.replace(now_playing_old, now_playing_new)

now_playing_actions_old = """
                    IconButton(onClick = onNavigateToEq, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Tune, contentDescription = "Equalizer") }
                    IconButton(onClick = onNavigateToLyrics, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Subject, contentDescription = "Lyrics") }
                }
"""
now_playing_actions_new = """
                    IconButton(onClick = onNavigateToEq, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Tune, contentDescription = "Equalizer") }
                    IconButton(onClick = onNavigateToLyrics, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Subject, contentDescription = "Lyrics") }
                    IconButton(onClick = onNavigateToQueue, modifier = Modifier.weight(1f)) { Icon(Icons.Default.QueueMusic, contentDescription = "Queue") }
                }
"""
content = content.replace(now_playing_actions_old, now_playing_actions_new)

queue_screen = """
@Composable
fun QueueScreen(viewModel: PlaybackViewModel) {
    val queue by viewModel.queue.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)) {
                itemsIndexed(queue) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    var showOptions by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.playSongList(queue, index) },
                        colors = CardDefaults.cardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)) {
                                AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = song.title, fontWeight = FontWeight.Bold, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                Text(text = song.artist, style = MaterialTheme.typography.bodyMedium, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (isCurrent && isPlaying) { Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary) }
                            
                            Box {
                                IconButton(onClick = { showOptions = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options") }
                                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                    DropdownMenuItem(text = { Text("Play Next") }, onClick = { viewModel.playNext(song); showOptions = false }, leadingIcon = { Icon(Icons.Default.SkipNext, null) })
                                    // Normally we would have remove from queue here, but needs ViewModel support
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

content = content + "\n" + queue_screen

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
