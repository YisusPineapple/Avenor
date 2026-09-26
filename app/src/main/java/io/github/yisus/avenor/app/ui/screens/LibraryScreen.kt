package io.github.yisus.avenor.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yisus.avenor.app.data.scanner.ScannedAudioItem
import io.github.yisus.avenor.app.ui.viewmodels.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSongClick: (ScannedAudioItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val audioList by viewModel.audioList.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("library_loading_indicator")
                )
            }
            audioList.isEmpty() -> {
                Text(
                    text = "No se encontraron canciones en el dispositivo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .testTag("library_empty_state")
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_song_list")
                ) {
                    items(
                        items = audioList,
                        key = { it.id }
                    ) { song ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongClick(song) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("library_song_item_${song.id}")
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
