import re

with open("app/src/main/java/io/github/yisus/nexo/SystemOptimizations.kt", "r") as f:
    content = f.read()

dashboard_old = """@Composable
fun DesktopParityDashboard(modifier: Modifier = Modifier) {
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
}"""

dashboard_new = """import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                                val text = \"\"\"
                                    Nexo Audio Diagnostics
                                    OS: ${System.getProperty("os.name")}
                                    JVM: ${System.getProperty("java.vm.name")}
                                    Cores: ${Runtime.getRuntime().availableProcessors()}
                                    Max Memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB
                                    Performance Mode: ${appSettings.performanceMode}
                                    Is Desktop: ${DeviceProfileManager.isDesktop()}
                                \"\"\".trimIndent()
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
                    onClick = { diagnosticsLauncher.launch("nexo_diagnostics.txt") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download Diagnostics")
                }
            }
        }
    }
}"""

content = content.replace(dashboard_old, dashboard_new)

with open("app/src/main/java/io/github/yisus/nexo/SystemOptimizations.kt", "w") as f:
    f.write(content)
