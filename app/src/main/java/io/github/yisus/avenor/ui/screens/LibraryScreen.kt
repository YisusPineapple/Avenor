package io.github.yisus.avenor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.yisus.avenor.PlaybackViewModel
import io.github.yisus.avenor.ResponsiveGridManager
import io.github.yisus.avenor.ui.components.RenameDialog
import io.github.yisus.avenor.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: PlaybackViewModel, onNavigateToPlaylist: (Screen.PlaylistDetails) -> Unit) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val history by viewModel.history.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val dailyMix by viewModel.dailyMix.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("New Playlist") },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Name") }, shape = MaterialTheme.shapes.large) },
            confirmButton = { TextButton(onClick = { if (playlistName.isNotBlank()) viewModel.createPlaylist(playlistName); showPlaylistDialog = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Local Music Found",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add audio files (.mp3, .flac, .wav, .m4a, .ogg) to your device storage to begin playback.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.loadSongs(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Storage")
                    }
                }
            }
        } else {
            val windowClass = ResponsiveGridManager.getWindowSizeClass(LocalConfiguration.current.screenWidthDp.dp)
            val columns = ResponsiveGridManager.getGridCells(windowClass)
            val paddingValues = ResponsiveGridManager.getPadding(windowClass)
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = 80.dp)
            ) {
                if (dailyMix.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Daily Mix For You", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                            items(dailyMix.size) { index ->
                                val mixSong = dailyMix[index]
                                Card(
                                    modifier = Modifier.width(150.dp).clickable { viewModel.playSongList(dailyMix, index) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        AsyncImage(model = mixSong.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(mixSong.title, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("My Playlists", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { playlistName = ""; showPlaylistDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add Playlist", tint = MaterialTheme.colorScheme.primary) }
                        }
                        if (playlists.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(playlists.size) { index ->
                                    val playlist = playlists[index]
                                    Card(
                                        modifier = Modifier.size(130.dp).clickable { onNavigateToPlaylist(Screen.PlaylistDetails(playlist.id, playlist.name)) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(playlist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }

                if (favoriteSongs.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("Favorite Tracks", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                                items(favoriteSongs.size) { index ->
                                    val favSong = favoriteSongs[index]
                                    Card(
                                        modifier = Modifier.width(150.dp).clickable { viewModel.playSongList(favoriteSongs, index) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            AsyncImage(model = favSong.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(favSong.title, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                            Text(favSong.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("Recently Played", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                                items(history.size) { index ->
                                    val histItem = history[index]
                                    Card(
                                        modifier = Modifier.width(150.dp).clickable { viewModel.playSongList(history, index) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            AsyncImage(model = histItem.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(histItem.title, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                            Text(histItem.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) { Text("All Songs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                items(songs.size) { index ->
                    val song = songs[index]
                    val isCurrent = currentSong?.id == song.id
                    val isFav = favoriteSongIds.contains(song.id)
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

                            IconButton(onClick = { viewModel.toggleFavorite(song.id) }) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isFav) "Remove Favorite" else "Add Favorite",
                                    tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            var showRename by remember { mutableStateOf(false) }

                            if (showRename) {
                                RenameDialog(
                                    initialName = song.title,
                                    onDismiss = { showRename = false },
                                    onRename = { newName: String -> 
                                        viewModel.renameSong(song.id.toInt(), newName)
                                        showRename = false
                                    }
                                )
                            }

                            IconButton(onClick = { showRename = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename Track")
                            }
                        }
                    }
                }
            }
        }
    }
}
