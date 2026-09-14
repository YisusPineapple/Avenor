package io.github.yisus.avenor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.imageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext


import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key

object DesktopMediaKeyManager {
    fun handleMediaKeyEvent(event: KeyEvent, viewModel: PlaybackViewModel): Boolean {
        return if (DeviceProfileManager.isDesktop()) {
            when (event.key) {
                Key.MediaPlayPause, Key.Spacebar -> { viewModel.togglePlayPause(); true }
                Key.MediaNext, Key.DirectionRight -> { viewModel.skipToNext(); true }
                Key.MediaPrevious, Key.DirectionLeft -> { viewModel.skipToPrevious(); true }
                Key.MediaPlay -> { if (viewModel.isPlaying.value == false) viewModel.togglePlayPause(); true }
                Key.MediaPause -> { if (viewModel.isPlaying.value == true) viewModel.togglePlayPause(); true }
                else -> false
            }
        } else {
            false
        }
    }
}

object DeviceProfileManager {
    fun isDesktop(): Boolean {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""
        val vmName = System.getProperty("java.vm.name")?.lowercase() ?: ""
        return (osName.contains("win") || osName.contains("mac") || osName.contains("nix") || osName.contains("nux")) && !vmName.contains("dalvik")
    }

    fun getOptimalBufferMs(performanceMode: String): Int {
        if (isDesktop()) {
            // Desktop Audio Quality optimized for high-latency stacks (ASIO/WASAPI/PulseAudio)
            return 100000 
        }
        val cores = Runtime.getRuntime().availableProcessors()
        
        return when (performanceMode) {
            "ECO" -> 15000 
            "VIVID" -> if (cores >= 8) 50000 else 30000 
            else -> 30000 
        }
    }
}

suspend fun startMemoryWatchdog(context: Context) {
    if (DeviceProfileManager.isDesktop()) {
        // Desktop JVM handles memory differently; skip strict 120MB limits.
        return
    }
    while (coroutineContext.isActive) {
        val runtime = Runtime.getRuntime()
        val usedMem = runtime.totalMemory() - runtime.freeMemory()
        val limit = 120L * 1024 * 1024 
        
        if (usedMem > limit) {
            Log.d("MemoryWatchdog", "Memory exceeded 120MB (${usedMem / 1024 / 1024}MB), clearing caches.")
            context.imageLoader.memoryCache?.clear()
            System.gc()
        }
        delay(5000) 
    }
}


@Composable
fun DesktopParityDashboard(appSettings: AppSetting, modifier: Modifier = Modifier) {
    if (DeviceProfileManager.isDesktop()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        
        val diagnosticsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(it)?.use { out ->
                                val text = """
                                    Avenor Audio Diagnostics
                                    OS: ${System.getProperty("os.name")}
                                    JVM: ${System.getProperty("java.vm.name")}
                                    Cores: ${Runtime.getRuntime().availableProcessors()}
                                    Max Memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB
                                    Performance Mode: ${appSettings.performanceMode}
                                    Is Desktop: ${DeviceProfileManager.isDesktop()}
                                """.trimIndent()
                                out.write(text.toByteArray())
                            }
                        } catch(e: Exception) { e.printStackTrace() }
                    }
                }
            }
        }

        Card(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DesktopWindows, contentDescription = "Desktop", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Desktop Environment Detected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("VIVID mode is optimized for mobile CPU scaling. Using high-latency Desktop Audio Quality (WASAPI/PulseAudio).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { diagnosticsLauncher.launch("avenor_diagnostics.txt") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download Diagnostics")
                }
            }
        }
    }
}

@Composable
fun RecapTemplateEngine(recapType: String, primaryColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surface)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.2f),
            size = size,
            cornerRadius = CornerRadius(32.dp.toPx())
        )
        
        drawCircle(
            color = primaryColor.copy(alpha = 0.5f),
            radius = canvasWidth * 0.3f,
            center = androidx.compose.ui.geometry.Offset(canvasWidth * 0.8f, canvasHeight * 0.2f)
        )
    }
}
