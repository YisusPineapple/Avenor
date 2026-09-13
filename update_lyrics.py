import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

lyrics_old = """
@Composable
fun LyricsScreen(viewModel: PlaybackViewModel) {
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val lyrics = remember(currentSong) { parseLrc(dummyLrc) }
    val listState = rememberLazyListState()
    val activeIndex = lyrics.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)

    LaunchedEffect(activeIndex, isPlaying) {
        if (activeIndex >= 0 && isPlaying) { listState.animateScrollToItem(maxOf(0, activeIndex - 3)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = currentSong?.title ?: "Lyrics", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 64.dp)) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeIndex
                Text(
                    text = line.text,
                    style = if (isActive) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 12.dp).clickable { viewModel.seekTo(line.timeMs) }
                )
            }
        }
    }
}
"""

lyrics_new = """
@Composable
fun LyricsScreen(viewModel: PlaybackViewModel) {
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val lyrics = remember(currentSong) { parseLrc(dummyLrc) }
    val listState = rememberLazyListState()
    val activeIndex = lyrics.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
    
    var syncMode by remember { mutableStateOf(true) }

    LaunchedEffect(activeIndex, isPlaying, syncMode) {
        if (syncMode && activeIndex >= 0 && isPlaying) { listState.animateScrollToItem(maxOf(0, activeIndex - 3)) }
    }

    val blurGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to Color.Transparent,
        0.15f to Color.Black,
        0.85f to Color.Black,
        1f to Color.Transparent
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = currentSong?.title ?: "Lyrics", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sync", style = MaterialTheme.typography.bodyMedium, color = if (syncMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = syncMode, onCheckedChange = { syncMode = it })
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState, 
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                    .androidx.compose.ui.graphics.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .androidx.compose.ui.draw.drawWithContent {
                        drawContent()
                        drawRect(brush = blurGradient, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                    }, 
                contentPadding = PaddingValues(vertical = 100.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == activeIndex && syncMode
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isActive) 1.1f else 1.0f,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
                    )
                    val alpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.4f,
                        animationSpec = androidx.compose.animation.core.tween(500)
                    )
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        modifier = Modifier.padding(vertical = 12.dp)
                            .androidx.compose.ui.graphics.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .clickable { viewModel.seekTo(line.timeMs) }
                    )
                }
            }
        }
    }
}
"""

content = content.replace(lyrics_old.strip(), lyrics_new.strip())

if "import androidx.compose.material3.Switch" not in content:
    content = content.replace("import androidx.compose.material3.Text", "import androidx.compose.material3.Text\nimport androidx.compose.material3.Switch")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
