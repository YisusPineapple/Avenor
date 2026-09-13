import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add _currentMetadata
state_flow_code = """
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentMetadata = MutableStateFlow<String?>(null)
    val currentMetadata: StateFlow<String?> = _currentMetadata.asStateFlow()
"""
content = re.sub(r'private val _currentPosition = MutableStateFlow\(0L\)\s*val currentPosition: StateFlow<Long> = _currentPosition\.asStateFlow\(\)', state_flow_code.strip(), content)

# Modify Player.Listener
old_listener = """
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
"""

new_listener = """
            controller?.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    val format = tracks.groups.firstOrNull()?.getTrackFormat(0)
                    format?.let {
                        val sampleRate = it.sampleRate
                        val bitRate = it.bitrate
                        val mimeType = it.sampleMimeType?.replace("audio/", "")?.uppercase() ?: "UNKNOWN"
                        
                        // Heuristic for Bit Depth
                        val bitDepth = when (it.pcmEncoding) {
                            androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
                            androidx.media3.common.C.ENCODING_PCM_32BIT -> 32
                            androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
                            else -> 16
                        }
                        
                        val brStr = if (bitRate > 0) "${bitRate / 1000} kbps • " else ""
                        val srStr = if (sampleRate > 0) "${sampleRate / 1000.0} kHz" else ""
                        val label = if (bitDepth >= 24 && sampleRate >= 48000) "Hi-Res" else if (bitDepth == 16 && sampleRate == 44100) "CD Quality" else "Lossy"
                        
                        _currentMetadata.value = "$mimeType • $brStr$bitDepth-bit • $srStr | $label"
                    } ?: run {
                        _currentMetadata.value = null
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
"""

content = content.replace(old_listener.strip(), new_listener.strip())

# Add new Screens to MainActivity sealed class
old_screen = """
sealed class Screen {
    object Library : Screen()
    object NowPlaying : Screen()
    object Settings : Screen()
    object Equalizer : Screen()
    object Lyrics : Screen()
    data class PlaylistDetails(val playlistId: Int, val playlistName: String) : Screen()
}
"""

new_screen = """
sealed class Screen {
    object Library : Screen()
    object NowPlaying : Screen()
    object Settings : Screen()
    object Equalizer : Screen()
    object Lyrics : Screen()
    object About : Screen()
    object OptimizationGuide : Screen()
    data class PlaylistDetails(val playlistId: Int, val playlistName: String) : Screen()
}
"""

content = content.replace(old_screen.strip(), new_screen.strip())

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
