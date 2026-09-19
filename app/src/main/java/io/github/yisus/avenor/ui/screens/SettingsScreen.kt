package io.github.yisus.avenor.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yisus.avenor.AppSetting
import io.github.yisus.avenor.DesktopParityDashboard
import io.github.yisus.avenor.PlaybackViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: PlaybackViewModel, onNavigateToTrash: () -> Unit = {}) {
    val appSettings by viewModel.appSettings.collectAsState()
    val settings = appSettings ?: AppSetting()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            DesktopParityDashboard(settings)
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Text("Visual Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pre-computed color palettes. Instantly applied via ThemeController.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Album Art Resolution", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Higher resolutions use more RAM.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            val resolutions = listOf("LOW", "MEDIUM", "HIGH", "ORIGINAL")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                resolutions.forEach { res ->
                                    FilterChip(
                                        selected = settings.albumArtResolution == res,
                                        onClick = { viewModel.saveSettings(settings.copy(albumArtResolution = res)) },
                                        label = { Text(res) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Smart Trash & Storage", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Auto-Purge Interval", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            val purgeDaysOptions = listOf(7, 15, 30)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                purgeDaysOptions.forEach { days ->
                                    FilterChip(
                                        selected = settings.trashPurgeDays == days,
                                        onClick = { viewModel.saveSettings(settings.copy(trashPurgeDays = days)) },
                                        label = { Text("$days Days") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    coroutineScope.launch { 
                                        viewModel.emptyTrashSecurely(context)
                                        Toast.makeText(context, "Trash Emptied Securely", Toast.LENGTH_SHORT).show()
                                    } 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Empty Trash Now")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onNavigateToTrash,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Recover Trash")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Now Playing Style", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val npStyles = listOf("CLASSIC", "EXPRESSIVE", "APPLE_MUSIC")
                            npStyles.forEach { style ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.saveSettings(settings.copy(nowPlayingStyle = style)) }.padding(vertical = 8.dp)) {
                                    RadioButton(selected = settings.nowPlayingStyle == style, onClick = { viewModel.saveSettings(settings.copy(nowPlayingStyle = style)) })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(style.replace("_", " "))
                                }
                            }
                        }
                    }

                    val themes = listOf("WARMTH" to "Warmth", "AURORA" to "Aurora", "SOFT_UI" to "Soft UI", "EXPRESSIVE" to "Expressive")
                    themes.chunked(2).forEach { rowThemes ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowThemes.forEach { (key, label) ->
                                FilterChip(
                                    selected = settings.themeStyle == key,
                                    onClick = { viewModel.saveSettings(settings.copy(themeStyle = key)) },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text("Performance Profile", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Eco completely stops visual GPU tasks to ensure stable 60FPS on low-RAM legacy hardware.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(selected = settings.performanceMode == "ECO", onClick = { viewModel.saveSettings(settings.copy(performanceMode = "ECO")) }, label = { Text("Eco") })
                        FilterChip(selected = settings.performanceMode == "BALANCED", onClick = { viewModel.saveSettings(settings.copy(performanceMode = "BALANCED")) }, label = { Text("Balanced") })
                        FilterChip(selected = settings.performanceMode == "VIVID", onClick = { viewModel.saveSettings(settings.copy(performanceMode = "VIVID")) }, label = { Text("Vivid") })
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text("Notification Actions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Customize the quick actions displayed in the Android Notification bar. This also syncs with the player UI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Show Like Button")
                        Switch(checked = settings.showLike, onCheckedChange = { viewModel.saveSettings(settings.copy(showLike = it)) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Show Shuffle Button")
                        Switch(checked = settings.showShuffle, onCheckedChange = { viewModel.saveSettings(settings.copy(showShuffle = it)) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Show Repeat Button")
                        Switch(checked = settings.showRepeat, onCheckedChange = { viewModel.saveSettings(settings.copy(showRepeat = it)) })
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text("System Information", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Memory Footprint strictly limited. All configurations run locally (Room).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OptimizationGuideScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Optimization Guide", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🚀 Performance Presets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Avenor runs an automated hardware benchmark. You can override it in Settings.\n- ECO: Ideal for <2GB RAM. Stops visual GPU tasks.\n- BALANCED: Standard fluid UI.\n- VIVID: Full 60/120FPS animations and real-time blur.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🧹 Background Processes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Audio crossfade operates off the main UI thread. However, on legacy dual-core devices, aggressive battery savers can starve the CPU. Disable battery optimizations for Avenor to ensure gapless transitions.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💾 Disk Usage", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Avenor utilizes an embedded SQLite database for lightning-fast metadata caching. A background VACUUM runs automatically if the file exceeds 5MB to prevent storage fragmentation.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("About Avenor", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Strictly offline, high-fidelity audio player.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Hardware Requirements", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            RequirementSection("Android (Native)", "Min: Quad-Core 1.2GHz, 1GB RAM (ECO Mode)\nRec: Octa-Core 1.8GHz+, 3GB RAM (VIVID Mode)")
            RequirementSection("Windows (JVM)", "Min: Dual-Core (Pentium), 4GB RAM (ECO Mode)\nRec: Core i3 / Ryzen 3, 8GB RAM (VIVID Mode)")
            RequirementSection("Linux (JVM)", "Min: Core 2 Duo, 2GB RAM (X11 / ECO Mode)\nRec: Core i3 / Ryzen 3, 4GB RAM (Wayland / VIVID Mode)")
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
