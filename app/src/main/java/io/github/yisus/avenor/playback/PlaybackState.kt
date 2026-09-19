package io.github.yisus.avenor.playback

import io.github.yisus.avenor.Song

/**
 * Immutable domain representation of the playback state.
 * Single source of truth consumed by UI and synchronized with Media3 and Room.
 */
data class PlaybackState(
    val queue: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val currentIndex: Int = -1,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val stateVersion: Long = 0L
)
