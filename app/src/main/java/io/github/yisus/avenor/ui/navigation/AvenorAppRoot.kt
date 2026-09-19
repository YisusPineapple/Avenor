package io.github.yisus.avenor.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yisus.avenor.MainActivity
import io.github.yisus.avenor.PlaybackViewModel
import io.github.yisus.avenor.startMemoryWatchdog
import io.github.yisus.avenor.ui.components.AuroraBackground
import io.github.yisus.avenor.ui.screens.EqScreen
import io.github.yisus.avenor.ui.screens.LibraryScreen
import io.github.yisus.avenor.ui.screens.LyricsScreen
import io.github.yisus.avenor.ui.screens.NowPlayingScreen
import io.github.yisus.avenor.ui.screens.PlaylistDetailsScreen
import io.github.yisus.avenor.ui.screens.QueueScreen
import io.github.yisus.avenor.ui.screens.RecapScreen
import io.github.yisus.avenor.ui.screens.SettingsScreen
import io.github.yisus.avenor.ui.screens.TrashRecoveryScreen
import io.github.yisus.avenor.ui.theme.AuroraPalette
import io.github.yisus.avenor.ui.theme.ExpressivePalette
import io.github.yisus.avenor.ui.theme.ExpressiveShapes
import io.github.yisus.avenor.ui.theme.SoftUiPalette
import io.github.yisus.avenor.ui.theme.WarmthPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvenorAppRoot(viewModel: PlaybackViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    var hasPermission by remember { mutableStateOf(false) }
    val appSettings by viewModel.appSettings.collectAsState()
    val isHighLoad by activity?.performanceMonitor?.isHighLoad?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (isGranted) viewModel.loadSongs(context)
    }

    LaunchedEffect(Unit) {
        viewModel.initController(context)
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionLauncher.launch(permission)
        launch { startMemoryWatchdog(context) }
    }

    // Theme Controller Logic (Pre-computed mapping avoids memory spikes)
    val colorScheme = when (appSettings?.themeStyle) {
        "AURORA" -> AuroraPalette
        "SOFT_UI" -> SoftUiPalette
        "EXPRESSIVE" -> ExpressivePalette
        else -> WarmthPalette // "WARMTH" default
    }

    MaterialTheme(colorScheme = colorScheme, shapes = ExpressiveShapes) {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }

        if (currentScreen != Screen.Library && currentScreen != Screen.NowPlaying) {
            BackHandler { currentScreen = Screen.Library }
        }

        // Dynamically scale down to ECO if running on high-load low-resource hardware
        val pMode = if (isHighLoad) "ECO" else (appSettings?.performanceMode ?: "BALANCED")

        AuroraBackground(performanceMode = pMode) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                when (val s = currentScreen) {
                                    is Screen.Settings -> "Settings"
                                    is Screen.Equalizer -> "Equalizer"
                                    is Screen.Lyrics -> "Lyrics"
                                    is Screen.Queue -> "Play Queue"
                                    is Screen.Recap -> "Your Recap"
                                    is Screen.PlaylistDetails -> s.playlistName
                                    else -> "Avenor"
                                },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        navigationIcon = {
                            if (currentScreen != Screen.Library && currentScreen != Screen.NowPlaying) {
                                IconButton(onClick = { currentScreen = Screen.Library }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            if (currentScreen == Screen.Library || currentScreen == Screen.NowPlaying) {
                                IconButton(onClick = { currentScreen = Screen.Settings }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                },
                bottomBar = {
                    if (currentScreen == Screen.Library || currentScreen == Screen.NowPlaying) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.List, contentDescription = "Library") },
                                label = { Text("Library") },
                                selected = currentScreen == Screen.Library,
                                onClick = { currentScreen = Screen.Library }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Now Playing") },
                                label = { Text("Playing") },
                                selected = currentScreen == Screen.NowPlaying,
                                onClick = { currentScreen = Screen.NowPlaying }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    when (val screen = currentScreen) {
                        is Screen.Library -> LibraryScreen(viewModel, onNavigateToPlaylist = { currentScreen = it })
                        is Screen.NowPlaying -> NowPlayingScreen(
                            viewModel,
                            onNavigateToEq = { currentScreen = Screen.Equalizer },
                            onNavigateToLyrics = { currentScreen = Screen.Lyrics },
                            onNavigateToQueue = { currentScreen = Screen.Queue }
                        )
                        is Screen.Settings -> SettingsScreen(viewModel, onNavigateToTrash = { currentScreen = Screen.TrashRecovery })
                        is Screen.TrashRecovery -> TrashRecoveryScreen(viewModel, onBack = { currentScreen = Screen.Settings })
                        is Screen.Equalizer -> EqScreen(viewModel)
                        is Screen.Lyrics -> LyricsScreen(viewModel)
                        is Screen.Queue -> QueueScreen(viewModel)
                        is Screen.Recap -> RecapScreen(viewModel)
                        is Screen.PlaylistDetails -> PlaylistDetailsScreen(viewModel, screen.playlistId)
                    }
                }
            }
        }
    }
}
