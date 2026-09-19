package io.github.yisus.avenor.ui.navigation

sealed class Screen {
    object Library : Screen()
    object NowPlaying : Screen()
    object Settings : Screen()
    object TrashRecovery : Screen()
    object Equalizer : Screen()
    object Lyrics : Screen()
    object Queue : Screen()
    object Recap : Screen()
    data class PlaylistDetails(val playlistId: Int, val playlistName: String) : Screen()
}
