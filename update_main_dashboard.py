import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("DesktopParityDashboard()", "DesktopParityDashboard(settings)")

# Add Backup & Restore to SettingsScreen
old_settings_end = """                            )
                        }
                    }
                }
            }
        }
    }
}
"""

new_settings_add = """                            )
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Safely export and import your Nexo Audio database (Playlists, History, Settings). App will restart after restore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                    
                    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
                    ) { uri ->
                        uri?.let {
                            coroutineScope.kotlinx.coroutines.launch {
                                val success = BackupManager.exportBackup(context, it)
                                android.widget.Toast.makeText(context, if (success) "Backup exported successfully" else "Backup failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    
                    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let {
                            coroutineScope.kotlinx.coroutines.launch {
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
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
"""
content = content.replace(old_settings_end, new_settings_add)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
