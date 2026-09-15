import re

with open('app/src/main/java/io/github/yisus/avenor/MainActivity.kt', 'r') as f:
    content = f.read()

# Add isCrossfading state
replacement_state = """fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit, onNavigateToQueue: () -> Unit) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isSleepTimerActive by viewModel.sleepTimerActive.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState(initial = null)
    val isCrossfading by AutoMixState.isCrossfading.collectAsState()
    
    // AutoMix pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "AutoMixPulse")
    val automixAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AutoMixAlpha"
    )"""

content = re.sub(
    r'fun NowPlayingScreen.*?val appSettings by viewModel\.appSettings\.collectAsState\(initial = null\)',
    replacement_state,
    content,
    flags=re.DOTALL
)

# Insert the AutoMix indicator above the title
target_text_info = """            // Text info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {"""

replacement_text_info = """            // Text info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // AutoMix Indicator
                androidx.compose.animation.AnimatedVisibility(visible = isCrossfading) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = automixAlpha),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "AutoMix Active", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AutoMix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }"""

content = content.replace(target_text_info, replacement_text_info)

with open('app/src/main/java/io/github/yisus/avenor/MainActivity.kt', 'w') as f:
    f.write(content)
