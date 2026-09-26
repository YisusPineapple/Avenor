package io.github.yisus.avenor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Explicit state model for audio track transitions.
 */
enum class TransitionState {
    IDLE,
    FADING_OUT,
    TRANSITIONING,
    FADING_IN,
    CANCELLED
}

/**
 * Single-player Volume Envelope / Transition Engine.
 *
 * Architecture & Operational Semantics:
 * - Operates on a single [ExoPlayer] instance by modulating [player.volume] (software output gain [0.0f .. 1.0f]).
 * - This component is NOT a dual-player mixer and does NOT perform concurrent overlapping playback
 *   of two audio streams (no true two-track crossfade).
 * - Crossfade OFF: During automatic track transitions ([Player.DISCONTINUITY_REASON_AUTO_TRANSITION]),
 *   [player.volume] is NOT modified in any way (stays at 1.0f). The transition proceeds with the natural
 *   continuity provided by Media3's internal playlist pipeline, without artificial volume dips or silence.
 * - Crossfade ON (AUTO_TRANSITION): Because only one player exists, Track A finishes before Track B starts;
 *   this engine applies a smooth fade-in curve to Track B's volume. Track A and Track B are not mixed simultaneously.
 * - Manual Skip: When enabled or forced, performs a volume envelope dip (fade-out of current track -> seek
 *   to target track -> fade-in of target track, i.e., a dip-to-silence transition).
 * - Invariant: [player.volume] MUST be cleanly restored to 1.0f whenever a transition finishes,
 *   is cancelled, or when playback pauses, stops, encounters an error, or the service is destroyed.
 * - ReplayGain and limiting are handled downstream in dedicated AudioProcessors, completely isolated
 *   from this volume envelope engine.
 */
@OptIn(UnstableApi::class)
class CrossfadeManager(
    private val player: ExoPlayer,
    private val context: Context
) {
    private val TAG = "CrossfadeManager"
    private var fadeAnimator: ValueAnimator? = null
    private val baseCrossfadeDurationMs = 3000L
    private val handler = Handler(Looper.getMainLooper())

    var isCrossfadeEnabled: Boolean = false

    private val _transitionState = MutableStateFlow(TransitionState.IDLE)
    val transitionState: StateFlow<TransitionState> = _transitionState.asStateFlow()

    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    internal val playerListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            when (reason) {
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
                    if (isCrossfadeEnabled) {
                        applyFadeIn(fromManual = false)
                    } else {
                        // Natural Gapless transition: do NOT alter volume or schedule animations
                        cancelAndReset()
                    }
                }
                Player.DISCONTINUITY_REASON_SEEK -> {
                    // Manual seek cancels any in-flight transition fade
                    if (_transitionState.value != TransitionState.IDLE) {
                        cancelAndReset()
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && _transitionState.value != TransitionState.IDLE) {
                // Playback paused during transition: cancel and restore safe volume
                cancelAndReset()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if ((playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) &&
                _transitionState.value != TransitionState.IDLE
            ) {
                // Playback stopped or completed: cancel and restore safe volume
                cancelAndReset()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error during transition: ${error.message}")
            cancelAndReset()
        }
    }

    init {
        player.addListener(playerListener)
    }

    /**
     * Safely cancels any active transition animation and restores player volume to 1.0f.
     */
    fun cancelAndReset() {
        val resetAction = {
            fadeAnimator?.cancel()
            fadeAnimator = null
            try {
                player.volume = 1f
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting volume to 1f: ${e.message}")
            }
            _isCrossfading.value = false
            AutoMixState.setCrossfading(false)
            _transitionState.value = TransitionState.IDLE
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            resetAction()
        } else {
            handler.post(resetAction)
        }
    }

    /**
     * Initiates a manual skip transition.
     * When [force] or [isCrossfadeEnabled] is true, applies a dip-to-silence transition
     * (smoothly fades out current track, seeks to target track, and fades in new track).
     * When neither is true, performs immediate seek without altering volume.
     */
    fun manualSkip(forward: Boolean, force: Boolean = true) {
        val action = {
            if (!force && !isCrossfadeEnabled) {
                try {
                    if (forward && player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                    } else if (!forward && player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during direct manual skip: ${e.message}")
                }
                cancelAndReset()
            } else {
                fadeAnimator?.cancel()
                _transitionState.value = TransitionState.FADING_OUT
                _isCrossfading.value = true
                AutoMixState.setCrossfading(true)

                val currentVol = player.volume
                fadeAnimator = ValueAnimator.ofFloat(currentVol, 0f).apply {
                    duration = 500L
                    interpolator = LinearInterpolator()
                    addUpdateListener { animation ->
                        try {
                            player.volume = animation.animatedValue as Float
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to set volume: ${e.message}")
                        }
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        private var isCancelled = false

                        override fun onAnimationCancel(animation: Animator) {
                            isCancelled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (isCancelled) return
                            _transitionState.value = TransitionState.TRANSITIONING
                            try {
                                if (forward && player.hasNextMediaItem()) {
                                    player.seekToNextMediaItem()
                                } else if (!forward && player.hasPreviousMediaItem()) {
                                    player.seekToPreviousMediaItem()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception during manual skip seek: ${e.message}")
                            } finally {
                                applyFadeIn(fromManual = true)
                            }
                        }
                    })
                    start()
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }

    private fun applyFadeIn(fromManual: Boolean = false) {
        val action = {
            fadeAnimator?.cancel()
            _transitionState.value = TransitionState.FADING_IN
            _isCrossfading.value = true
            AutoMixState.setCrossfading(true)

            val tier = PerformanceBenchmark.evaluateDeviceTier(context)
            val scale = when (tier) {
                "ECO" -> 0.5f
                "BALANCED" -> 1.0f
                "VIVID" -> 1.5f
                else -> 1.0f
            }

            try {
                if (tier == "VIVID" || tier == "BALANCED") {
                    player.setSeekParameters(SeekParameters.EXACT)
                } else {
                    player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting seek parameters: ${e.message}")
            }

            val duration = if (fromManual) (1000L * scale).toLong() else (baseCrossfadeDurationMs * scale).toLong()

            try {
                player.volume = 0f
            } catch (e: Exception) {
                Log.e(TAG, "Error setting volume to 0: ${e.message}")
            }

            fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    try {
                        player.volume = animation.animatedValue as Float
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update volume during fade in: ${e.message}")
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var isCancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        isCancelled = true
                        _transitionState.value = TransitionState.CANCELLED
                        _isCrossfading.value = false
                        AutoMixState.setCrossfading(false)
                        player.volume = 1f
                        _transitionState.value = TransitionState.IDLE
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (isCancelled) return
                        _isCrossfading.value = false
                        AutoMixState.setCrossfading(false)
                        player.volume = 1f
                        _transitionState.value = TransitionState.IDLE
                    }
                })
                start()
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }

    /**
     * Releases listeners and cancels any active animations.
     */
    fun release() {
        try {
            player.removeListener(playerListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing player listener: ${e.message}")
        }
        cancelAndReset()
    }
}
