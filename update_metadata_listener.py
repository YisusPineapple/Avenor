import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Enhance Metadata badge dynamically based on new fields instead of relying just on ExoPlayer Tracks format since we saved it in DB.
old_listener = """
            controller?.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    val format = tracks.groups.firstOrNull()?.getTrackFormat(0)
                    format?.let {
                        val sampleRate = it.sampleRate
                        val bitRate = it.bitrate
                        val mimeType = it.sampleMimeType?.replace("audio/", "")?.uppercase() ?: "UNKNOWN"
                        
                        // Heuristic for Bit Depth
                        val bitDepth = when (it.pcmEncoding) {
                            androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
                            androidx.media3.common.C.ENCODING_PCM_32BIT -> 32
                            androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
                            else -> 16
                        }
                        
                        val brStr = if (bitRate > 0) "${bitRate / 1000} kbps • " else ""
                        val srStr = if (sampleRate > 0) "${sampleRate / 1000.0} kHz" else ""
                        val label = if (bitDepth >= 24 && sampleRate >= 48000) "Hi-Res" else if (bitDepth == 16 && sampleRate == 44100) "CD Quality" else "Lossy"
                        
                        _currentMetadata.value = "$mimeType • $brStr$bitDepth-bit • $srStr | $label"
                    } ?: run {
                        _currentMetadata.value = null
                    }
                }
"""

new_listener = """
            controller?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val mediaId = mediaItem?.mediaId
                    val song = currentPlayingList.find { it.id.toString() == mediaId } ?: songs.value.find { it.id.toString() == mediaId }
                    _currentSong.value = song
                    
                    song?.let {
                        viewModelScope.launch { dbRepo.recordPlay(it.id) }
                        val id3Genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
                        resolveAutoEq(it, id3Genre)
                        
                        // Extract metadata from our DB which has deeper analysis
                        val mimeType = it.mimeType.replace("audio/", "").uppercase()
                        val srStr = if (it.sampleRate > 0) "${it.sampleRate / 1000.0} kHz" else ""
                        
                        val label = if (it.bitDepth >= 24 || it.sampleRate > 48000) "Hi-Res"
                                    else if (it.mimeType.contains("flac") || it.mimeType.contains("alac")) "Lossless"
                                    else if (it.mimeType.contains("ac4") || it.mimeType.contains("eac3")) "Dolby Atmos"
                                    else if (it.bitDepth >= 16 && it.sampleRate >= 44100) "CD Quality"
                                    else "Lossy"
                                    
                        _currentMetadata.value = "${it.fileExtension.uppercase()} • ${it.bitDepth}-bit • $srStr | $label"
                    } ?: run {
                        _currentMetadata.value = null
                    }
                }
"""

content = content.replace(old_listener.strip(), new_listener.strip())

# We need to remove the duplicate onMediaItemTransition
content = re.sub(r'override fun onMediaItemTransition\(mediaItem: MediaItem\?, reason: Int\) \{.*?\n                        \}\n                    \}', '', content, flags=re.DOTALL, count=1)


# Add Recap flow
recap_flow = """
    val topSongs: StateFlow<List<Song>> = dbRepo.topSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val topArtist: StateFlow<TopArtistResult?> = dbRepo.topArtist.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val totalListeningTimeMs: StateFlow<Long?> = dbRepo.totalListeningTimeMs.stateIn(viewModelScope, SharingStarted.Lazily, null)
"""

content = content.replace("val appSettings: StateFlow<AppSetting?> = dbRepo.appSettings.stateIn(viewModelScope, SharingStarted.Lazily, null)", "val appSettings: StateFlow<AppSetting?> = dbRepo.appSettings.stateIn(viewModelScope, SharingStarted.Lazily, null)\n" + recap_flow)


# Also add ResponsiveGridManager concept inside the composables.
# We will create NexoWrapped screen for recap.
# We add Recap to Screen
old_screen = """
    object OptimizationGuide : Screen()
    data class PlaylistDetails(val playlistId: Int, val playlistName: String) : Screen()
"""
new_screen = """
    object OptimizationGuide : Screen()
    object Recap : Screen()
    data class PlaylistDetails(val playlistId: Int, val playlistName: String) : Screen()
"""
content = content.replace(old_screen, new_screen)

# Add dropdown item for Recap
old_dropdown = """
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("About") },
                                            onClick = { currentScreen = Screen.About; menuExpanded = false },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                        )
"""
new_dropdown = """
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("About") },
                                            onClick = { currentScreen = Screen.About; menuExpanded = false },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("My Recap") },
                                            onClick = { currentScreen = Screen.Recap; menuExpanded = false },
                                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                        )
"""
content = content.replace(old_dropdown, new_dropdown)

# Add Recap to When block
old_when_screen = """
                            is Screen.OptimizationGuide -> OptimizationGuideScreen()
"""
new_when_screen = """
                            is Screen.OptimizationGuide -> OptimizationGuideScreen()
                            is Screen.Recap -> RecapScreen(viewModel)
"""
content = content.replace(old_when_screen, new_when_screen)

# Recap Title
old_when_title = """
                                            is Screen.OptimizationGuide -> "Optimization Guide"
"""
new_when_title = """
                                            is Screen.OptimizationGuide -> "Optimization Guide"
                                            is Screen.Recap -> "My Recap"
"""
content = content.replace(old_when_title, new_when_title)

# Create RecapScreen Component
recap_screen_code = """
@Composable
fun RecapScreen(viewModel: PlaybackViewModel) {
    val topSongs by viewModel.topSongs.collectAsState()
    val topArtist by viewModel.topArtist.collectAsState()
    val totalTimeMs by viewModel.totalListeningTimeMs.collectAsState()
    val context = LocalContext.current

    val totalMins = (totalTimeMs ?: 0L) / 60000L

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val isWide = maxWidth > 600.dp
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Your Nexo Recap", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Listening Time", style = MaterialTheme.typography.titleMedium)
                        Text("$totalMins Minutes", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    }
                }
            }
            
            item {
                if (topArtist != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Top Artist", style = MaterialTheme.typography.titleMedium)
                            Text(topArtist!!.artist, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("${topArtist!!.playCount} Plays", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            
            item {
                Text("Top Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp))
            }
            
            itemsIndexed(topSongs) { index, song ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("#${index + 1}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                        Column {
                            Text(song.title, style = MaterialTheme.typography.titleMedium)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = {
                    val shareText = "My Nexo Recap:\\nTop Artist: ${topArtist?.artist}\\nListening Time: $totalMins Minutes\\nTop Song: ${topSongs.firstOrNull()?.title}\\n#NexoAudio"
                    val sendIntent: android.content.Intent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export & Share")
                }
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}
"""
content += recap_screen_code


with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
