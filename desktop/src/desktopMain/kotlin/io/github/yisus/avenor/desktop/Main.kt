package io.github.yisus.avenor.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.yisus.avenor.shared.NowPlayingState
import io.github.yisus.avenor.shared.playback.PlaybackState
import javax.swing.JFileChooser

/**
 * Desktop entry point for Avenor Music Player.
 * Provides a responsive, desktop-optimized layout supporting local library,
 * queue management, and hardware DSP indicators connected to VLCJ via DesktopPlaybackController.
 */
fun main() {
    try {
        javax.swing.UIManager.setLookAndFeel(
            javax.swing.UIManager.getSystemLookAndFeelClassName()
        )
    } catch (_: Throwable) {
        // Si el L&F del sistema no está disponible, continuar con el default.
    }

    application {
        val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)
        val controller = remember { DesktopPlaybackController() }

        DisposableEffect(controller) {
            onDispose {
                controller.release()
            }
        }

        Window(
            onCloseRequest = {
                controller.release()
                exitApplication()
            },
            title = "Avenor - Local Audiophile Music Player",
            state = windowState
        ) {
            AvenorDesktopApp(controller = controller)
        }
    }
}

@Composable
fun AvenorDesktopApp(
    controller: DesktopPlaybackController = remember { DesktopPlaybackController() }
) {
    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    var currentScreen by remember { mutableStateOf("library") }
    var volume by remember { mutableStateOf(0.8f) }

    val playbackState by controller.playbackState.collectAsState()
    val currentMediaItem by controller.currentMediaItem.collectAsState()
    val currentPositionMs by controller.currentPositionFlow.collectAsState()
    val durationMs by controller.durationFlow.collectAsState()
    val vlcAvailability by controller.vlcAvailability.collectAsState()

    val isPlaying = playbackState is PlaybackState.Playing
    val isCrossfading by NowPlayingState.isCrossfading.collectAsState()
    val isDspActive by NowPlayingState.isDspActive.collectAsState()

    val statusSubtitle = when (val state = playbackState) {
        is PlaybackState.Playing -> currentMediaItem?.artist ?: "Playing • VLCJ Native Engine"
        is PlaybackState.Paused -> currentMediaItem?.artist ?: "Paused • Offline-First"
        is PlaybackState.Buffering -> "Buffering stream..."
        is PlaybackState.Error -> state.message
        is PlaybackState.Idle -> currentMediaItem?.artist ?: "Ready • Offline-First"
    }

    val sliderMaxMs = remember(durationMs, currentPositionMs) {
        maxOf(durationMs, currentPositionMs, 180_000L).toFloat()
    }

    if (vlcAvailability is VlcAvailability.Unavailable) {
        DesktopVlcMissingScreen(
            reason = (vlcAvailability as VlcAvailability.Unavailable).reason
        )
        return
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE06C53),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF3F2018),
            onPrimaryContainer = Color(0xFFFFDBD2),
            background = Color(0xFF141212),
            surface = Color(0xFF1C1A1A),
            surfaceVariant = Color(0xFF282525),
            onSurface = Color(0xFFECE0DF),
            onSurfaceVariant = Color(0xFFD0C3C1)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Desktop Sidebar Navigation
                NavigationRail(
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AVENOR",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isDspActive) "Audiophile Engine • DSP" else "Audiophile Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val navItems = listOf(
                            Triple("library", "Library", Icons.Default.LibraryMusic),
                            Triple("favorites", "Favorites", Icons.Default.Favorite),
                            Triple("playlists", "Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
                            Triple("queue", "Queue", Icons.AutoMirrored.Filled.QueueMusic),
                            Triple("lyrics", "Lyrics", Icons.Default.Lyrics),
                            Triple("equalizer", "Equalizer", Icons.Default.GraphicEq),
                            Triple("settings", "Settings", Icons.Default.Settings)
                        )

                        navItems.forEach { (id, label, icon) ->
                            val selected = currentScreen == id
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentScreen = id },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // Main Content & Bottom Player Bar
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Content Area
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)
                    ) {
                        when (currentScreen) {
                            "library" -> DesktopLibraryView(controller = controller)
                            "favorites" -> DesktopFavoritesView()
                            "playlists" -> DesktopPlaylistsView()
                            "queue" -> DesktopQueueView()
                            "lyrics" -> DesktopLyricsView()
                            "equalizer" -> DesktopEqView()
                            "settings" -> DesktopSettingsView()
                        }
                    }

                    // Bottom Playback Bar
                    Card(
                        modifier = Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Current track info
                            Box(
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.width(220.dp)) {
                                Text(
                                    text = currentMediaItem?.title ?: "Local Audio Stream",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = statusSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (playbackState is PlaybackState.Error) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isCrossfading) {
                                    Text(
                                        text = "Crossfading active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Center transport controls & timeline
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = {}) {
                                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { controller.skipToPrevious() }) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                                    }
                                    FilledIconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                controller.pause()
                                            } else {
                                                controller.play()
                                            }
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    IconButton(onClick = { controller.skipToNext() }) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Next")
                                    }
                                    IconButton(onClick = {}) {
                                        Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Slider(
                                    value = currentPositionMs.toFloat().coerceIn(0f, sliderMaxMs),
                                    onValueChange = { newPosition ->
                                        controller.seekTo(newPosition.toLong())
                                    },
                                    valueRange = 0f..sliderMaxMs,
                                    modifier = Modifier.fillMaxWidth().height(20.dp)
                                )
                            }

                            // Volume & DSP
                            Spacer(modifier = Modifier.width(24.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(180.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Slider(
                                    value = volume,
                                    onValueChange = {
                                        volume = it
                                        controller.setVolume(it)
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopLibraryView(controller: DesktopPlaybackController) {
    val queue by controller.queue.collectAsState()
    val currentMediaItem by controller.currentMediaItem.collectAsState()
    val isScanning by controller.isScanning.collectAsState()
    var selectedFolderPath by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Local Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Scan local directories recursively to populate audiophile music files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Select Music Folder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Supports FLAC, ALAC, WAV, MP3, M4A, OGG, OPUS, APE, WV, DSF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedFolderPath.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedFolderPath,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Select Music Folder"
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            isAcceptAllFileFilterUsed = false
                        }
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            val folderPath = chooser.selectedFile?.absolutePath.orEmpty()
                            if (folderPath.isNotBlank()) {
                                selectedFolderPath = folderPath
                                controller.scanAndPlayFolder(folderPath)
                            }
                        }
                    },
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanning) "Scanning..." else "Select Music Folder")
                }

                if (isScanning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f))
                }
            }
        }

        if (queue.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanned Tracks (${queue.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(queue, key = { it.id }) { item ->
                    val isCurrent = currentMediaItem?.id == item.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { controller.loadAndPlay(item) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isCurrent) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.bodySmall,
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
}

@Composable
fun DesktopFavoritesView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Favorites", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Your pinned tracks stored locally.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DesktopPlaylistsView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Playlists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Organize your local audio collection.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DesktopQueueView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Playback Queue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Current playing sequence.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DesktopLyricsView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Synchronized Lyrics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Embedded and local .lrc lyrics support.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DesktopEqView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("10-Band Equalizer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Hardware & DSP acoustic tuning.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DesktopSettingsView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Audio & App Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Configure gapless playback, crossfading, themes, and storage.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DesktopVlcMissingScreen(reason: String) {
    val primary = Color(0xFFE06C53)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "VLC Media Player no encontrado",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Avenor usa libVLC para reproducir audio en Windows y Linux. " +
                    "Instalá VLC y reiniciá Avenor.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Detalle técnico: $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { openVlcDownloadPage() },
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Text("Abrir página de descarga")
            }
        }
    }
}

private fun openVlcDownloadPage() {
    val url = "https://www.videolan.org/vlc/"
    try {
        if (java.awt.Desktop.isDesktopSupported()) {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI(url))
            }
        }
    } catch (_: Throwable) {
        // Silencioso: si no hay Desktop.browse disponible, el usuario abre el link a mano.
    }
}
