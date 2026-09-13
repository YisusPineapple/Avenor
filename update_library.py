import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

library_old = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: PlaybackViewModel, onNavigateToPlaylist: (Screen.PlaylistDetails) -> Unit) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val history by viewModel.history.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val dailyMix by viewModel.dailyMix.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
"""

library_new = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: PlaybackViewModel, onNavigateToPlaylist: (Screen.PlaylistDetails) -> Unit) {
    val songs by viewModel.songs.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSongs by viewModel.selectedSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val history by viewModel.history.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val dailyMix by viewModel.dailyMix.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
"""

content = content.replace(library_old.strip(), library_new.strip())

search_old = """
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (songs.isEmpty()) {
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
        
        if (selectedSongs.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${selectedSongs.size} selected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row {
                    IconButton(onClick = { viewModel.enqueueSelected() }) { Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to Queue") }
                    IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, contentDescription = "Clear Selection") }
                }
            }
        }

        if (songs.isEmpty()) {
"""
content = content.replace(search_old.strip(), search_new.strip())

# Replace all songs rendering to support multi-select and action menu
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
                    val isSelected = selectedSongs.contains(song.id)
                    var showActionMenu by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .androidx.compose.foundation.ExperimentalFoundationApi::class.let {
                                androidx.compose.foundation.combinedClickable(
                                    onClick = {
                                        if (selectedSongs.isNotEmpty()) {
                                            viewModel.toggleSelection(song.id)
                                        } else {
                                            viewModel.playSongList(filteredSongs, index)
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedSongs.isEmpty()) {
                                            showActionMenu = true
                                        } else {
                                            viewModel.toggleSelection(song.id)
                                        }
                                    }
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
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
                            
                            IconButton(onClick = { showActionMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options") }
                        }
                    }
                    
                    if (showActionMenu) {
                        ActionMenuBottomSheet(
                            title = song.title,
                            subtitle = song.artist,
                            items = listOf(
                                ActionMenuItem("Play Next", Icons.Default.SkipNext) { viewModel.playNext(song) },
                                ActionMenuItem("Add to Queue", Icons.Default.PlaylistAdd) { viewModel.enqueue(song) },
                                ActionMenuItem("Select", Icons.Default.CheckCircle) { viewModel.toggleSelection(song.id) }
                            ),
                            onDismissRequest = { showActionMenu = false }
                        )
                    }
                }
"""
content = content.replace(all_songs_old.strip(), all_songs_new.strip())

if "import androidx.compose.foundation.combinedClickable" not in content:
    content = content.replace("import androidx.compose.foundation.clickable", "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable")

if "import androidx.compose.material.icons.filled.CheckCircle" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Close", "import androidx.compose.material.icons.filled.Close\nimport androidx.compose.material.icons.filled.CheckCircle")
    
if "import androidx.compose.material.icons.filled.Close" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.MoreVert", "import androidx.compose.material.icons.filled.MoreVert\nimport androidx.compose.material.icons.filled.Close\nimport androidx.compose.material.icons.filled.CheckCircle")

if "import androidx.compose.material.icons.filled.Search" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.MoreVert", "import androidx.compose.material.icons.filled.MoreVert\nimport androidx.compose.material.icons.filled.Search")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
