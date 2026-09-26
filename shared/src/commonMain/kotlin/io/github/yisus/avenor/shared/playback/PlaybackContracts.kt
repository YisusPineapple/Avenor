package io.github.yisus.avenor.shared.playback

import kotlinx.coroutines.flow.StateFlow

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Buffering : PlaybackState
    data object Playing : PlaybackState
    data object Paused : PlaybackState
    data class Error(val message: String, val cause: Throwable? = null) : PlaybackState
}

data class CurrentMediaItem(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String
)

interface AvenorPlaybackController {
    val playbackState: StateFlow<PlaybackState>
    val currentMediaItem: StateFlow<CurrentMediaItem?>
    val currentPositionMs: Long

    fun play()
    fun pause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
}
