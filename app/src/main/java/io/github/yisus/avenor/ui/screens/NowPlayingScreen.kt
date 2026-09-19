package io.github.yisus.avenor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import io.github.yisus.avenor.AutoMixState
import io.github.yisus.avenor.util.formatMs
import io.github.yisus.avenor.PlaybackViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlaybackViewModel,
    onNavigateToEq: () -> Unit,
    onNavigateToLyrics: () -> Unit,
    onNavigateToQueue: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isSleepTimerActive by viewModel.sleepTimerActive.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState(initial = null)
    val isCrossfading by AutoMixState.isCrossfading.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()
    val isFavorite = currentSong?.let { favoriteSongIds.contains(it.id) } ?: false
    
    // AutoMix pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "AutoMixPulse")
    val automixAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AutoMixAlpha"
    )
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            viewModel.updatePosition()
            delay(1000)
        }
    }

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
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.Darken)
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
            
            Card(
                modifier = artModifier,
                shape = artShape,
                elevation = CardDefaults.cardElevation(defaultElevation = if (style == "APPLE_MUSIC") 24.dp else 16.dp)
            ) {
                AsyncImage(
                    model = currentSong?.albumArtUri,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Text info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // AutoMix Indicator
                    AnimatedVisibility(visible = isCrossfading) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = automixAlpha),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = "AutoMix Active", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AutoMix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(currentSong?.title ?: "Unknown Title", style = if(style == "APPLE_MUSIC") MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(currentSong?.artist ?: "Unknown Artist", style = MaterialTheme.typography.titleMedium, color = if(style == "APPLE_MUSIC") MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                IconButton(onClick = { currentSong?.let { viewModel.toggleFavorite(it.id) } }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
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
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(48.dp))
                }
                
                if (style == "EXPRESSIVE") {
                    FilledIconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(80.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp))
                    }
                } else {
                    FloatingActionButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(80.dp), shape = CircleShape) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp))
                    }
                }

                IconButton(onClick = { viewModel.skipToNext() }) {
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
                IconButton(onClick = { onNavigateToQueue() }) { Icon(Icons.Default.QueueMusic, contentDescription = "Queue") }
                IconButton(onClick = { showSleepTimerDialog = true }) { Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                IconButton(onClick = { onNavigateToEq() }) { Icon(Icons.Default.Equalizer, contentDescription = "EQ") }
            }
        }
    }
}
