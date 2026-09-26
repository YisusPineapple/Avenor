package io.github.yisus.avenor.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.github.yisus.avenor.shared.playback.AvenorPlaybackController
import io.github.yisus.avenor.shared.playback.CurrentMediaItem
import io.github.yisus.avenor.shared.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Media3ControllerImpl(
    private val player: Player
) : AvenorPlaybackController {

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<CurrentMediaItem?>(null)
    override val currentMediaItem: StateFlow<CurrentMediaItem?> = _currentMediaItem.asStateFlow()

    override val currentPositionMs: Long
        get() = player.currentPosition

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState(playbackState, player.playWhenReady)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updatePlaybackState(player.playbackState, playWhenReady)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = if (mediaItem != null) {
                CurrentMediaItem(
                    id = mediaItem.mediaId,
                    title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                    artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                    uri = mediaItem.localConfiguration?.uri?.toString()
                        ?: mediaItem.mediaMetadata.artworkUri?.toString()
                        .orEmpty()
                )
            } else {
                null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = PlaybackState.Error(
                message = error.message ?: "Playback error",
                cause = error
            )
        }
    }

    init {
        player.addListener(playerListener)
    }

    private fun updatePlaybackState(state: Int, playWhenReady: Boolean) {
        _playbackState.value = when (state) {
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> if (playWhenReady) PlaybackState.Playing else PlaybackState.Paused
            Player.STATE_ENDED -> PlaybackState.Paused
            Player.STATE_IDLE -> PlaybackState.Idle
            else -> PlaybackState.Idle
        }
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun skipToNext() {
        player.seekToNextMediaItem()
    }

    override fun skipToPrevious() {
        player.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun release() {
        player.removeListener(playerListener)
    }
}
