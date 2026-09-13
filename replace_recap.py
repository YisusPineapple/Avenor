import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

recap_pattern = re.compile(r'fun RecapScreen\(viewModel: PlaybackViewModel\) \{.*?(?=\n@Composable\nfun QueueScreen)', re.DOTALL)
match = recap_pattern.search(content)
if match:
    new_recap = """fun RecapScreen(viewModel: PlaybackViewModel) {
    val topSongs by viewModel.topSongs.collectAsState()
    val topArtist by viewModel.topArtist.collectAsState()
    val totalTimeMs by viewModel.totalListeningTimeMs.collectAsState()
    val context = LocalContext.current

    val totalMins = (totalTimeMs ?: 0L) / 60000L
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.background
        )
    )

    val graphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(gradient)) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(1000)) + 
                    androidx.compose.animation.slideInVertically(initialOffsetY = { 100 }, animationSpec = androidx.compose.animation.core.tween(1000))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp)
            ) {
                item {
                    Text("Your Nexo Recap", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    RecapExportCard(
                        topArtist = topArtist?.artist ?: "Unknown",
                        topSong = topSongs.firstOrNull()?.title ?: "Unknown",
                        totalMinutes = totalMins,
                        modifier = Modifier.fillMaxWidth()
                            .androidx.compose.ui.draw.drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawContent()
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
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Export & Share Image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
"""
    content = content.replace(match.group(0), new_recap)
    
    if "import androidx.compose.ui.graphics.asAndroidBitmap" not in content:
        content = content.replace("import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.asAndroidBitmap\nimport androidx.compose.ui.graphics.layer.drawLayer")

    with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
        f.write(content)
else:
    print("Not found")
