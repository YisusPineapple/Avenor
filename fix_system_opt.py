import re

with open("app/src/main/java/io/github/yisus/nexo/SystemOptimizations.kt", "r") as f:
    content = f.read()

new_content = """package io.github.yisus.nexo

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
fun CrossPlatformBridge(modifier: Modifier = Modifier) {
    if (DeviceProfileManager.isDesktop()) {
        Card(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DesktopWindows, contentDescription = "Desktop", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Desktop Environment Detected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("VIVID mode is optimized for mobile CPU scaling. Using high-latency Desktop Audio Quality (WASAPI/PulseAudio).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
"""

with open("app/src/main/java/io/github/yisus/nexo/SystemOptimizations.kt", "w") as f:
    f.write(new_content)
