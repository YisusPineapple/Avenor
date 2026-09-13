import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add logic for empty trash
new_empty_trash = """
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
                                viewModel.dbRepo.dao.clearTrashItems()
                                android.widget.Toast.makeText(context, "Trash Emptied", android.widget.Toast.LENGTH_SHORT).show()
                            } 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Empty Trash Now")
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
"""

if "Smart Trash & Storage" not in content:
    content = content.replace('val themes = listOf("WARMTH" to "Warmth", "AURORA" to "Aurora", "SOFT_UI" to "Soft UI", "EXPRESSIVE" to "Expressive")', new_empty_trash + '\n            val themes = listOf("WARMTH" to "Warmth", "AURORA" to "Aurora", "SOFT_UI" to "Soft UI", "EXPRESSIVE" to "Expressive")')

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
