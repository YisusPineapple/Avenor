package io.github.yisus.avenor.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.yisus.avenor.shared.playback.AvenorPlaybackController
import io.github.yisus.avenor.shared.playback.CurrentMediaItem
import io.github.yisus.avenor.shared.playback.PlaybackState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: AvenorPlaybackController
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = controller.playbackState
    val currentMediaItem: StateFlow<CurrentMediaItem?> = controller.currentMediaItem

    fun play() {
        controller.play()
    }

    fun pause() {
        controller.pause()
    }

    fun skipToNext() {
        controller.skipToNext()
    }

    fun skipToPrevious() {
        controller.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = controller.currentPositionMs
}
