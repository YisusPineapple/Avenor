package io.github.yisus.avenor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.yisus.avenor.app.ui.viewmodels.PlayerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsScreen(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val lyricsOffset by playerViewModel.lyricsOffsetMs.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val lyrics by playerViewModel.currentLyrics.collectAsState()
    val isLoadingLyrics by playerViewModel.isLoadingLyrics.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(currentSong) {
        currentSong?.let { playerViewModel.loadLyricsForSong(it, context) }
    }

    val effectivePosition = currentPosition - lyricsOffset

    val activeIndex = remember(lyrics, effectivePosition) {
        if (lyrics.isEmpty()) -1
        else lyrics.indexOfLast { it.timeMs <= effectivePosition }.coerceAtLeast(0)
    }

    LaunchedEffect(activeIndex, isPlaying) {
        if (activeIndex >= 0 && isPlaying) {
            listState.animateScrollToItem(maxOf(0, activeIndex - 3))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = currentSong?.title ?: "Lyrics",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center
        )

        if (isLoadingLyrics) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (lyrics.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No synchronized lyrics found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Place a .lrc file in the same folder as the song or embed ID3 lyrics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val activeColor = MaterialTheme.colorScheme.primary
            val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            val activeStyle = MaterialTheme.typography.headlineMedium
            val inactiveStyle = MaterialTheme.typography.titleMedium

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 64.dp)
            ) {
                itemsIndexed(
                    items = lyrics,
                    key = { index, line -> "${index}_${line.timeMs}" }
                ) { index, line ->
                    val isActive = index == activeIndex
                    val lineStyle = if (isActive) activeStyle else inactiveStyle
                    val lineWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                    if (line.syllables.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clickable { playerViewModel.seekTo(line.timeMs) }
                        ) {
                            line.syllables.forEach { syllable ->
                                val isSyllablePassed = currentPosition >= syllable.timeMs
                                Text(
                                    text = syllable.text,
                                    style = lineStyle,
                                    fontWeight = lineWeight,
                                    color = if (isSyllablePassed) activeColor else inactiveColor
                                )
                            }
                        }
                    } else {
                        Text(
                            text = line.text,
                            style = lineStyle,
                            fontWeight = lineWeight,
                            color = if (isActive) activeColor else inactiveColor,
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .clickable { playerViewModel.seekTo(line.timeMs) }
                        )
                    }
                }
            }
        }
    }
}

