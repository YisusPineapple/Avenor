import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Schedule WorkManager
work_imports = """
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
"""
if "import androidx.work.WorkManager" not in content:
    content = content.replace("import android.os.Bundle", work_imports + "\nimport android.os.Bundle")

work_init = """
        // Schedule weekly backup
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeeklyBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
"""
if "WorkManager.getInstance" not in content:
    content = content.replace("setContent {", work_init + "\n        setContent {")

# Modify settings screen
old_backup = """                    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
                    ) { uri ->
                        uri?.let {
                            coroutineScope.launch {
                                val success = BackupManager.exportBackup(context, it)
                                android.widget.Toast.makeText(context, if (success) "Backup exported successfully" else "Backup failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    
                    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let {
                            coroutineScope.launch {
                                val success = BackupManager.importBackup(context, it)
                                android.widget.Toast.makeText(context, if (success) "Restored! Please restart the app." else "Restore failed", android.widget.Toast.LENGTH_LONG).show()
                                if (success) {
                                    kotlinx.coroutines.delay(2000)
                                    kotlin.system.exitProcess(0)
                                }
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { exportLauncher.launch("nexo_backup.db") }) {
                            Text("Export Backup")
                        }
                        Button(onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }) {
                            Text("Import Backup")
                        }
                    }"""

new_backup = """
                    var isProcessing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    
                    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        uri?.let {
                            isProcessing = true
                            coroutineScope.launch {
                                val success = BackupManager.exportBackupToJson(context, it)
                                isProcessing = false
                                android.widget.Toast.makeText(context, if (success) "JSON Backup exported successfully" else "Backup failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    
                    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let {
                            isProcessing = true
                            coroutineScope.launch {
                                val success = BackupManager.importBackupFromJson(context, it)
                                isProcessing = false
                                android.widget.Toast.makeText(context, if (success) "Restored from JSON! Restarting..." else "Restore failed. Invalid format?", android.widget.Toast.LENGTH_LONG).show()
                                if (success) {
                                    kotlinx.coroutines.delay(2000)
                                    kotlin.system.exitProcess(0)
                                }
                            }
                        }
                    }
                    
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = { exportLauncher.launch("nexo_backup.json") }) {
                                Text("Export JSON")
                            }
                            Button(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                                Text("Import JSON")
                            }
                        }
                    }"""
content = content.replace(old_backup, new_backup)
content = content.replace("Safely export and import your Nexo Audio database (Playlists, History, Settings). App will restart after restore.", "Safely export and import your Nexo Audio database in a cross-platform JSON format. Solves Android/Linux/Windows compatibility issues. App will restart after restore.")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
