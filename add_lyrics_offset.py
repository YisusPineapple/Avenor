import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add lyricsOffsetMs to PlaybackViewModel
if 'var lyricsOffsetMs = MutableStateFlow(0L)' not in content:
    content = content.replace(
        'private val _currentPosition = MutableStateFlow(0L)',
        'private val _currentPosition = MutableStateFlow(0L)\n    val lyricsOffsetMs = MutableStateFlow(0L)'
    )
    content = content.replace(
        'fun togglePlayPause()',
        'fun adjustLyricsOffset(delta: Long) { lyricsOffsetMs.value += delta }\n\n    fun togglePlayPause()'
    )

old_lyrics = """fun LyricsScreen(viewModel: PlaybackViewModel) {
    val currentSong by viewModel.currentSong.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()"""

new_lyrics = """fun LyricsScreen(viewModel: PlaybackViewModel) {
    val currentSong by viewModel.currentSong.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()"""

content = content.replace(old_lyrics, new_lyrics)

# Update activeIndex
old_index = 'val activeIndex = lyrics.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)'
new_index = 'val activeIndex = lyrics.indexOfLast { it.timeMs <= (currentPosition - lyricsOffset) }.coerceAtLeast(0)'
content = content.replace(old_index, new_index)

# Add control UI
old_ui = """                Text(
                    text = "Lyrics for ${currentSong?.title}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )"""
new_ui = """                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lyrics for ${currentSong?.title}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.adjustLyricsOffset(-500L) }) { Icon(Icons.Default.Remove, "Delay") }
                        Text("${if (lyricsOffset > 0) "+" else ""}${lyricsOffset}ms", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { viewModel.adjustLyricsOffset(500L) }) { Icon(Icons.Default.Add, "Advance") }
                    }
                }"""
content = content.replace(old_ui, new_ui)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
