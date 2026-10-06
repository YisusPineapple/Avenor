package io.github.yisus.avenor.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import io.github.yisus.avenor.AutoMixState
import io.github.yisus.avenor.Song
import io.github.yisus.avenor.app.ui.viewmodels.PlayerViewModel
import io.github.yisus.avenor.metadata.CreditSplitter
import io.github.yisus.avenor.ui.components.AvenorAsyncImage
import io.github.yisus.avenor.util.formatMs
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
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
    val uiDensity = appSettings?.uiDensity ?: "RELAXED"
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Background for Apple Music style
        if (style == "APPLE_MUSIC") {
            AvenorAsyncImage(
                model = currentSong?.albumArtUri,
                resolution = appSettings?.albumArtResolution ?: "HIGH",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.3f),
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.Darken)
            )
        }

        val artShape = when(style) {
            "EXPRESSIVE" -> RoundedCornerShape(16.dp)
            "APPLE_MUSIC" -> RoundedCornerShape(12.dp)
            else -> RoundedCornerShape(40.dp)
        }
        
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .aspectRatio(1f),
                        shape = artShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (style == "APPLE_MUSIC") 24.dp else 16.dp)
                    ) {
                        AvenorAsyncImage(
                            model = currentSong?.albumArtUri,
                            resolution = appSettings?.albumArtResolution ?: "HIGH",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = "Album Art"
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    TrackTitleBlock(
                        currentSong = currentSong,
                        style = style,
                        isCrossfading = isCrossfading,
                        automixAlpha = automixAlpha,
                        isFavorite = isFavorite,
                        onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it.id) } }
                    )

                    if (uiDensity == "PRO") {
                        TechnicalSpecsPanel(
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            currentPosition = currentPosition
                        )
                    }

                    ProgressBlock(
                        currentSong = currentSong,
                        currentPosition = currentPosition,
                        style = style,
                        onSeek = { viewModel.seekTo(it) }
                    )

                    ControlsBlock(
                        isPlaying = isPlaying,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        style = style,
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onSkipToPrevious = { viewModel.skipToPrevious() },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipToNext = { viewModel.skipToNext() },
                        onToggleRepeat = { viewModel.toggleRepeat() }
                    )

                    BottomActionsBlock(
                        isSleepTimerActive = isSleepTimerActive,
                        onNavigateToQueue = onNavigateToQueue,
                        onShowSleepTimer = { showSleepTimerDialog = true },
                        onNavigateToEq = onNavigateToEq
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
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
                    AvenorAsyncImage(
                        model = currentSong?.albumArtUri,
                        resolution = appSettings?.albumArtResolution ?: "HIGH",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Album Art"
                    )
                }

                TrackTitleBlock(
                    currentSong = currentSong,
                    style = style,
                    isCrossfading = isCrossfading,
                    automixAlpha = automixAlpha,
                    isFavorite = isFavorite,
                    onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it.id) } }
                )

                if (uiDensity == "PRO") {
                    TechnicalSpecsPanel(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition
                    )
                }

                ProgressBlock(
                    currentSong = currentSong,
                    currentPosition = currentPosition,
                    style = style,
                    onSeek = { viewModel.seekTo(it) }
                )

                ControlsBlock(
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    style = style,
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onSkipToPrevious = { viewModel.skipToPrevious() },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSkipToNext = { viewModel.skipToNext() },
                    onToggleRepeat = { viewModel.toggleRepeat() }
                )

                BottomActionsBlock(
                    isSleepTimerActive = isSleepTimerActive,
                    onNavigateToQueue = onNavigateToQueue,
                    onShowSleepTimer = { showSleepTimerDialog = true },
                    onNavigateToEq = onNavigateToEq
                )
            }
        }
    }
}

@Composable
private fun TrackTitleBlock(
    currentSong: Song?,
    style: String,
    isCrossfading: Boolean,
    automixAlpha: Float,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
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
            val artists = remember(currentSong?.artist) {
                CreditSplitter.splitArtists(currentSong?.artist ?: "").ifEmpty { listOf("Unknown Artist") }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(artists) { artistName ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = if (style == "APPLE_MUSIC") {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    )
                }
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ProgressBlock(
    currentSong: Song?,
    currentPosition: Long,
    style: String,
    onSeek: (Long) -> Unit
) {
    Column {
        val progress = if (currentSong?.durationMs != null && currentSong.durationMs > 0) { currentPosition.toFloat() / currentSong.durationMs.toFloat() } else 0f
        Slider(
            value = progress,
            onValueChange = { onSeek((it * (currentSong?.durationMs ?: 0)).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = if(style == "EXPRESSIVE") SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary, activeTrackColor = MaterialTheme.colorScheme.secondary) else SliderDefaults.colors()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(currentPosition), style = MaterialTheme.typography.labelMedium)
            Text(formatMs(currentSong?.durationMs ?: 0), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ControlsBlock(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    style: String,
    onToggleShuffle: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipToNext: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggleShuffle) {
            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onSkipToPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(48.dp))
        }
        
        if (style == "EXPRESSIVE") {
            FilledIconButton(onClick = onTogglePlayPause, modifier = Modifier.size(80.dp), shape = RoundedCornerShape(24.dp)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp))
            }
        } else {
            FloatingActionButton(onClick = onTogglePlayPause, modifier = Modifier.size(80.dp), shape = CircleShape) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp))
            }
        }

        IconButton(onClick = onSkipToNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(48.dp))
        }
        IconButton(onClick = onToggleRepeat) {
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                else -> Icons.Default.Repeat
            }
            val tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
            Icon(repeatIcon, contentDescription = "Repeat", tint = tint)
        }
    }
}

@Composable
private fun BottomActionsBlock(
    isSleepTimerActive: Boolean,
    onNavigateToQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onNavigateToEq: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = onNavigateToQueue) { Icon(Icons.Default.QueueMusic, contentDescription = "Queue") }
        IconButton(onClick = onShowSleepTimer) { Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
        IconButton(onClick = onNavigateToEq) { Icon(Icons.Default.Equalizer, contentDescription = "EQ") }
    }
}

@Composable
fun TechnicalSpecsPanel(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Long
) {
    val codecText = remember(currentSong?.codec) {
        currentSong?.codec?.uppercase(Locale.US)?.takeIf { it.isNotBlank() } ?: "FLAC"
    }
    val bitrateText = remember(currentSong?.bitrate) {
        val rawBitrate = currentSong?.bitrate ?: 0L
        val kbps = when {
            rawBitrate >= 1000L -> rawBitrate / 1000L
            rawBitrate > 0L -> rawBitrate
            else -> 1411L
        }
        "$kbps kbps"
    }
    val sampleRateText = remember(currentSong?.sampleRate) {
        val sr = currentSong?.sampleRate?.takeIf { it > 0 } ?: 44100
        String.format(Locale.US, "%.1f kHz", sr / 1000.0)
    }
    val bitDepthText = remember(currentSong?.bitDepth) {
        val bd = currentSong?.bitDepth?.takeIf { it > 0 } ?: 16
        "${bd}-bit"
    }

    val trackBackgroundColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val leftChannelColor = MaterialTheme.colorScheme.primary
    val rightChannelColor = MaterialTheme.colorScheme.secondary
    val peakColor = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Fila 1 (Datos): Codec, Bitrate, Sample Rate y Bit Depth
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = codecText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = bitrateText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sampleRateText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = bitDepthText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Fila 2 (Vúmetro Ligero): Zero-Allocations en onDrawBehind
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .drawWithCache {
                        val barCornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        val gapPx = 4.dp.toPx()
                        val channelHeight = (size.height - gapPx) / 2f
                        val fullTrackSize = Size(size.width, channelHeight)
                        val rightTopLeft = Offset(0f, channelHeight + gapPx)

                        onDrawBehind {
                            val leftLevel: Float
                            val rightLevel: Float
                            if (isPlaying) {
                                val baseWave = abs(Math.sin(currentPosition * 0.01)).toFloat()
                                val jitterL = Random.nextFloat() * 0.25f
                                val jitterR = Random.nextFloat() * 0.25f
                                leftLevel = (baseWave * 0.72f + jitterL).coerceIn(0.05f, 1f)
                                rightLevel = (abs(Math.sin(currentPosition * 0.01 + 0.6)).toFloat() * 0.72f + jitterR).coerceIn(0.05f, 1f)
                            } else {
                                leftLevel = 0f
                                rightLevel = 0f
                            }

                            // Pistas de fondo (L y R)
                            drawRoundRect(
                                color = trackBackgroundColor,
                                topLeft = Offset.Zero,
                                size = fullTrackSize,
                                cornerRadius = barCornerRadius
                            )
                            drawRoundRect(
                                color = trackBackgroundColor,
                                topLeft = rightTopLeft,
                                size = fullTrackSize,
                                cornerRadius = barCornerRadius
                            )

                            // Barras activas del Vúmetro estéreo (solo cuando > 0f)
                            if (leftLevel > 0f) {
                                drawRoundRect(
                                    color = if (leftLevel > 0.88f) peakColor else leftChannelColor,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width * leftLevel, channelHeight),
                                    cornerRadius = barCornerRadius
                                )
                            }
                            if (rightLevel > 0f) {
                                drawRoundRect(
                                    color = if (rightLevel > 0.88f) peakColor else rightChannelColor,
                                    topLeft = rightTopLeft,
                                    size = Size(size.width * rightLevel, channelHeight),
                                    cornerRadius = barCornerRadius
                                )
                            }
                        }
                    }
            ) {}
        }
    }
}
