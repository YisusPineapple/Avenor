import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add Expanded State for Menu
state_code = """
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }
                var menuExpanded by remember { mutableStateOf(false) }
"""
content = re.sub(r'var currentScreen by remember \{ mutableStateOf<Screen>\(Screen\.Library\) \}', state_code.strip(), content)

# Modify TopAppBar Actions
old_actions = """
                            actions = {
                                if (currentScreen == Screen.Library || currentScreen == Screen.NowPlaying) {
                                    IconButton(onClick = { currentScreen = Screen.Settings }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                                }
                            },
"""

new_actions = """
                            actions = {
                                if (currentScreen == Screen.Library || currentScreen == Screen.NowPlaying) {
                                    IconButton(onClick = { currentScreen = Screen.Settings }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Optimization Guide") },
                                            onClick = { currentScreen = Screen.OptimizationGuide; menuExpanded = false },
                                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("About") },
                                            onClick = { currentScreen = Screen.About; menuExpanded = false },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                        )
                                    }
                                }
                            },
"""
content = content.replace(old_actions.strip(), new_actions.strip())

# Add screens to Scaffold body
old_screens = """
                        when (val screen = currentScreen) {
                            is Screen.Library -> LibraryScreen(viewModel, onNavigateToPlaylist = { currentScreen = it })
                            is Screen.NowPlaying -> NowPlayingScreen(viewModel, onNavigateToEq = { currentScreen = Screen.Equalizer }, onNavigateToLyrics = { currentScreen = Screen.Lyrics })
                            is Screen.Settings -> SettingsScreen(viewModel)
                            is Screen.Equalizer -> EqScreen(viewModel)
                            is Screen.Lyrics -> LyricsScreen(viewModel)
                            is Screen.PlaylistDetails -> PlaylistDetailsScreen(viewModel, screen.playlistId)
                        }
"""

new_screens = """
                        when (val screen = currentScreen) {
                            is Screen.Library -> LibraryScreen(viewModel, onNavigateToPlaylist = { currentScreen = it })
                            is Screen.NowPlaying -> NowPlayingScreen(viewModel, onNavigateToEq = { currentScreen = Screen.Equalizer }, onNavigateToLyrics = { currentScreen = Screen.Lyrics })
                            is Screen.Settings -> SettingsScreen(viewModel)
                            is Screen.Equalizer -> EqScreen(viewModel)
                            is Screen.Lyrics -> LyricsScreen(viewModel)
                            is Screen.About -> AboutScreen()
                            is Screen.OptimizationGuide -> OptimizationGuideScreen()
                            is Screen.PlaylistDetails -> PlaylistDetailsScreen(viewModel, screen.playlistId)
                        }
"""
content = content.replace(old_screens.strip(), new_screens.strip())

# Add title formatting for new screens
old_title_when = """
                                        when (val s = currentScreen) {
                                            is Screen.Settings -> "Settings"
                                            is Screen.Equalizer -> "Equalizer"
                                            is Screen.Lyrics -> "Lyrics"
                                            is Screen.PlaylistDetails -> s.playlistName
                                            else -> "Nexo"
                                        }
"""
new_title_when = """
                                        when (val s = currentScreen) {
                                            is Screen.Settings -> "Settings"
                                            is Screen.Equalizer -> "Equalizer"
                                            is Screen.Lyrics -> "Lyrics"
                                            is Screen.About -> "About"
                                            is Screen.OptimizationGuide -> "Optimization Guide"
                                            is Screen.PlaylistDetails -> s.playlistName
                                            else -> "Nexo"
                                        }
"""
content = content.replace(old_title_when.strip(), new_title_when.strip())

# Add new Composable Screens at the end of the file
new_composables = """

// ---------------------------------------------------------
// Optimization Guide & About Screens
// ---------------------------------------------------------

@Composable
fun OptimizationGuideScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Optimization Guide", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🚀 Performance Presets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Nexo runs an automated hardware benchmark. You can override it in Settings.\\n- ECO: Ideal for <2GB RAM. Stops visual GPU tasks.\\n- BALANCED: Standard fluid UI.\\n- VIVID: Full 60/120FPS animations and real-time blur.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🧹 Background Processes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Audio crossfade operates off the main UI thread. However, on legacy dual-core devices, aggressive battery savers can starve the CPU. Disable battery optimizations for Nexo to ensure gapless transitions.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💾 Disk Usage", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Nexo utilizes an embedded SQLite database for lightning-fast metadata caching. A background VACUUM runs automatically if the file exceeds 5MB to prevent storage fragmentation.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("About Nexo", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Strictly offline, high-fidelity audio player.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Hardware Requirements", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            RequirementSection("Android (Native)", "Min: Quad-Core 1.2GHz, 1GB RAM (ECO Mode)\\nRec: Octa-Core 1.8GHz+, 3GB RAM (VIVID Mode)")
            RequirementSection("Windows (JVM)", "Min: Dual-Core (Pentium), 4GB RAM (ECO Mode)\\nRec: Core i3 / Ryzen 3, 8GB RAM (VIVID Mode)")
            RequirementSection("Linux (JVM)", "Min: Core 2 Duo, 2GB RAM (X11 / ECO Mode)\\nRec: Core i3 / Ryzen 3, 4GB RAM (Wayland / VIVID Mode)")
        }
    }
}

@Composable
fun RequirementSection(title: String, desc: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
"""

content += new_composables

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
