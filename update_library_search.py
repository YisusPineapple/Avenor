import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add states to LibraryScreen
library_old = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: PlaybackViewModel, onNavigateToPlaylist: (Screen.PlaylistDetails) -> Unit) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
"""

library_new = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: PlaybackViewModel, onNavigateToPlaylist: (Screen.PlaylistDetails) -> Unit) {
    val songs by viewModel.songs.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
"""

content = content.replace(library_old.strip(), library_new.strip())

# Add SearchBar right before LazyVerticalGrid
search_old = """
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else {
"""

search_new = """
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search songs, artists, albums...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true
            )
            
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else {
"""

content = content.replace(search_old.strip(), search_new.strip())

# Modify the All Songs section to use filteredSongs and include context menu
all_songs_old = """
            item(span = { GridItemSpan(maxLineSpan) }) { Text("All Songs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(songs.size) { index ->
                val song = songs[index]
                val isCurrent = currentSong?.id == song.id
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.playSongList(songs, index) },
                    colors = CardDefaults.cardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small)) {
                            AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = song.title, fontWeight = FontWeight.Bold, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(text = song.artist, style = MaterialTheme.typography.bodyMedium, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (isCurrent && isPlaying) { Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
"""

all_songs_new = """
            item(span = { GridItemSpan(maxLineSpan) }) { Text(if (searchQuery.isNotBlank()) "Search Results" else "All Songs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(filteredSongs.size) { index ->
                val song = filteredSongs[index]
                val isCurrent = currentSong?.id == song.id
                var showOptions by remember { mutableStateOf(false) }
                var showInfo by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.playSongList(filteredSongs, index) },
                    colors = CardDefaults.cardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small)) {
                            AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = song.title, fontWeight = FontWeight.Bold, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(text = song.artist, style = MaterialTheme.typography.bodyMedium, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (isCurrent && isPlaying) { Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary) }
                        
                        Box {
                            IconButton(onClick = { showOptions = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                DropdownMenuItem(text = { Text("Play Next") }, onClick = { viewModel.playNext(song); showOptions = false }, leadingIcon = { Icon(Icons.Default.SkipNext, null) })
                                DropdownMenuItem(text = { Text("Add to Queue") }, onClick = { viewModel.enqueue(song); showOptions = false }, leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) })
                                DropdownMenuItem(text = { Text("Song Info") }, onClick = { showInfo = true; showOptions = false }, leadingIcon = { Icon(Icons.Default.Info, null) })
                            }
                        }
                    }
                }
                
                if (showInfo) {
                    AlertDialog(
                        onDismissRequest = { showInfo = false },
                        title = { Text("Song Information") },
                        text = {
                            Column {
                                Text("Title: ${song.title}")
                                Text("Artist: ${song.artist}")
                                Text("Album: ${song.album}")
                                Text("Quality: ${song.bitDepth}-bit / ${song.sampleRate / 1000.0}kHz")
                                Text("Format: ${song.fileExtension.uppercase()}")
                                Text("Path: ${song.uri}")
                            }
                        },
                        confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } }
                    )
                }
            }
"""

content = content.replace(all_songs_old.strip(), all_songs_new.strip())

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)

