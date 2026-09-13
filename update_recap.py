import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

recap_old = """
                    RecapExportCard(
                        topArtist = topArtist?.artist ?: "Unknown",
                        topSong = topSongs.firstOrNull()?.title ?: "Unknown",
                        totalMinutes = totalMins,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            val shareText = "My Nexo Recap:\\nTop Artist: ${topArtist?.artist}\\nListening Time: $totalMins Minutes\\nTop Song: ${topSongs.firstOrNull()?.title}\\n#NexoAudio"
                            val sendIntent: android.content.Intent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                        }, 
                        modifier = Modifier.fillMaxWidth().height(64.dp),
"""

recap_new = """
                    val graphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
                    val coroutineScope = rememberCoroutineScope()
                    
                    RecapExportCard(
                        topArtist = topArtist?.artist ?: "Unknown",
                        topSong = topSongs.firstOrNull()?.title ?: "Unknown",
                        totalMinutes = totalMins,
                        modifier = Modifier.fillMaxWidth()
                            .drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            }
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                    val shareText = "My Nexo Recap:\\nTop Artist: ${topArtist?.artist}\\nListening Time: $totalMins Minutes\\nTop Song: ${topSongs.firstOrNull()?.title}\\n#NexoAudio"
                                    ImageShareHelper.shareBitmap(context, bitmap, shareText)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }, 
                        modifier = Modifier.fillMaxWidth().height(64.dp),
"""

if recap_old.strip() in content:
    content = content.replace(recap_old.strip(), recap_new.strip())
else:
    print("Recap not matched!")

if "import androidx.compose.ui.graphics.layer.drawLayer" not in content:
    content = content.replace("import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.layer.drawLayer\nimport androidx.compose.ui.graphics.asAndroidBitmap")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
