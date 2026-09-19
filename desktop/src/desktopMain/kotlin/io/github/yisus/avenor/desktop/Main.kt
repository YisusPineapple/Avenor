package io.github.yisus.avenor.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.yisus.avenor.shared.NowPlayingState

/**
 * Desktop entry point for Avenor Music Player.
 * Provides a responsive, desktop-optimized layout supporting local library,
 * queue management, and hardware DSP indicators.
 */
fun main() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Avenor - Local Audiophile Music Player",
        state = windowState
    ) {
        AvenorDesktopApp()
    }
}

@Composable
fun AvenorDesktopApp() {
    var currentScreen by remember { mutableStateOf("library") }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(45f) }
    var volume by remember { mutableStateOf(0.8f) }
    val isCrossfading by NowPlayingState.isCrossfading.collectAsState()
    val isDspActive by NowPlayingState.isDspActive.collectAsState()

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
                            text = "Audiophile Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val navItems = listOf(
                            Triple("library", "Library", Icons.Default.LibraryMusic),
                            Triple("favorites", "Favorites", Icons.Default.Favorite),
                            Triple("playlists", "Playlists", Icons.Default.PlaylistPlay),
                            Triple("queue", "Queue", Icons.Default.QueueMusic),
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
                            "library" -> DesktopLibraryView()
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
                                    text = "Local Audio Stream",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Ready • Offline-First",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    IconButton(onClick = {}) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                                    }
                                    FilledIconButton(
                                        onClick = { isPlaying = !isPlaying },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    IconButton(onClick = {}) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Next")
                                    }
                                    IconButton(onClick = {}) {
                                        Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Slider(
                                    value = currentPosition,
                                    onValueChange = { currentPosition = it },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.fillMaxWidth().height(20.dp)
                                )
                            }

                            // Volume & DSP
                            Spacer(modifier = Modifier.width(24.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(180.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Slider(
                                    value = volume,
                                    onValueChange = { volume = it },
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
fun DesktopLibraryView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Local Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Scan local directories to populate music files.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Select Music Folder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Supports MP3, FLAC, WAV, OGG, OPUS, M4A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
