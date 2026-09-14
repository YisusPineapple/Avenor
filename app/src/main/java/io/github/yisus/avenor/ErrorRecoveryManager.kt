package io.github.yisus.avenor

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class ErrorRecoveryManager(private val player: ExoPlayer) : Player.Listener {
    private val TAG = "ErrorRecoveryManager"
    private var consecutiveErrors = 0
    private val MAX_ERRORS = 3

    init {
        player.addListener(this)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        Log.e(TAG, "Playback error detected: ${error.errorCodeName}", error)
        
        consecutiveErrors++
        
        if (consecutiveErrors > MAX_ERRORS) {
            Log.e(TAG, "Too many consecutive errors. Pausing playback to prevent loop.")
            player.pause()
            consecutiveErrors = 0
            return
        }

        // Attempt recovery: skip corrupted track
        if (player.hasNextMediaItem()) {
            Log.i(TAG, "Auto-skipping corrupted track to next item...")
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            Log.w(TAG, "No next track available. Stopping playback.")
            player.stop()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            // Reset error count on successful playback
            consecutiveErrors = 0
        }
    }
}
