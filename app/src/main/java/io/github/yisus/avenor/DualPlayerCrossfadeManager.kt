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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.yisus.avenor.audio.AvenorRenderersFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
 * Dual-Player ("Ghost Player") Equal-Power Crossfade Engine.
 *
 * Implements constant-power (equal-power) crossfading between two concurrent [ExoPlayer] instances
 * on capable devices ("BALANCED" and "VIVID" tiers), while falling back to a single-player linear
 * volume envelope on memory-constrained devices ("ECO" tier).
 *
 * Equal-Power Gain Law:
 * - Incoming track (`primaryPlayer`): $G_{in}(t) = \sin\left(t \cdot \frac{\pi}{2}\right)$
 * - Outgoing track (`fadeoutPlayer`): $G_{out}(t) = \cos\left(t \cdot \frac{\pi}{2}\right)$
 * where $t \in [0.0, 1.0]$, preserving constant acoustic power ($G_{in}^2 + G_{out}^2 = 1$).
 */
@OptIn(UnstableApi::class)
class DualPlayerCrossfadeManager(
    private val context: Context,
    private val primaryPlayer: ExoPlayer,
    private val renderersFactory: AvenorRenderersFactory = AvenorRenderersFactory(context)
) {
    constructor(
        primaryPlayer: ExoPlayer,
        context: Context,
        renderersFactory: AvenorRenderersFactory = AvenorRenderersFactory(context)
    ) : this(context, primaryPlayer, renderersFactory)
    companion object {
        private const val TAG = "DualPlayerCrossfade"
        private const val DEFAULT_CROSSFADE_DURATION_MS = 3000L
        private const val MANUAL_SKIP_CROSSFADE_DURATION_MS = 1500L
        private const val ECO_FADE_OUT_DURATION_MS = 400L
        private const val ECO_FADE_IN_DURATION_MS = 600L
    }

    internal var fadeoutPlayer: ExoPlayer? = null
    private var fadeAnimator: ValueAnimator? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Suppresses re-entrant auto-transition triggers when [primaryPlayer] is programmatically
     * advanced during an active transition.
     */
    private var suppressDiscontinuityCallback: Boolean = false

    var isCrossfadeEnabled: Boolean = false
    var crossfadeDurationMs: Long = DEFAULT_CROSSFADE_DURATION_MS

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
            if (suppressDiscontinuityCallback) return

            when (reason) {
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
                    if (isCrossfadeEnabled) {
                        onAutoTransition()
                    } else {
                        cancelAndReset()
                    }
                }
                Player.DISCONTINUITY_REASON_SEEK -> {
                    if (_transitionState.value != TransitionState.IDLE) {
                        cancelAndReset()
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && _transitionState.value != TransitionState.IDLE) {
                cancelAndReset()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if ((playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) &&
                _transitionState.value != TransitionState.IDLE
            ) {
                cancelAndReset()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Primary player error during crossfade: ${error.message}", error)
            cancelAndReset()
        }
    }

    init {
        try {
            primaryPlayer.addListener(playerListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach listener to primaryPlayer: ${e.message}", e)
        }
    }

    /**
     * Initiates a manual skip transition (Next / Previous).
     *
     * - Evaluates device hardware tier via [PerformanceBenchmark.evaluateDeviceTier].
     * - "ECO": Executes a lightweight linear fade on [primaryPlayer] without allocating a Ghost Player.
     * - "BALANCED" / "VIVID": Spawns [fadeoutPlayer] at the outgoing track's position, advances
     *   [primaryPlayer] to the target track, and applies an Equal-Power ($\sin/\cos$) crossfade.
     */
    fun manualSkip(forward: Boolean, force: Boolean = true) {
        runOnMainThread {
            if (!force && !isCrossfadeEnabled) {
                try {
                    suppressDiscontinuityCallback = true
                    if (forward && primaryPlayer.hasNextMediaItem()) {
                        primaryPlayer.seekToNextMediaItem()
                    } else if (!forward && primaryPlayer.hasPreviousMediaItem()) {
                        primaryPlayer.seekToPreviousMediaItem()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during direct manual skip: ${e.message}", e)
                } finally {
                    suppressDiscontinuityCallback = false
                }
                cancelAndReset()
                return@runOnMainThread
            }

            // Cancel any in-flight animation and release any previous Ghost Player to prevent spam leaks
            cancelActiveAnimationAndReleaseGhost()

            val tier = try {
                PerformanceBenchmark.evaluateDeviceTier(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to evaluate device tier, defaulting to ECO: ${e.message}")
                "ECO"
            }

            if (tier == "ECO") {
                executeEcoLinearManualSkip(forward)
            } else {
                // "BALANCED" or "VIVID"
                executeDualPlayerEqualPowerTransition(
                    isManualSkip = true,
                    forward = forward,
                    durationMs = MANUAL_SKIP_CROSSFADE_DURATION_MS
                )
            }
        }
    }

    /**
     * Initiates an automatic track transition crossfade.
     *
     * - Evaluates device hardware tier via [PerformanceBenchmark.evaluateDeviceTier].
     * - "ECO": Applies a linear fade on [primaryPlayer] without instantiating [fadeoutPlayer].
     * - "BALANCED" / "VIVID": Spawns [fadeoutPlayer] using [renderersFactory], seeks to the outgoing
     *   position, advances [primaryPlayer] if needed, and runs an Equal-Power ($\sin/\cos$) crossfade.
     */
    fun onAutoTransition() {
        runOnMainThread {
            cancelActiveAnimationAndReleaseGhost()

            val tier = try {
                PerformanceBenchmark.evaluateDeviceTier(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to evaluate device tier, defaulting to ECO: ${e.message}")
                "ECO"
            }

            if (tier == "ECO") {
                executeEcoLinearAutoTransition()
            } else {
                // "BALANCED" or "VIVID"
                val scale = if (tier == "VIVID") 1.25f else 1.0f
                val targetDuration = (crossfadeDurationMs * scale).toLong().coerceAtLeast(500L)
                executeDualPlayerEqualPowerTransition(
                    isManualSkip = false,
                    forward = true,
                    durationMs = targetDuration
                )
            }
        }
    }

    /**
     * Core Equal-Power Dual-Player ("Ghost Player") transition for BALANCED and VIVID tiers.
     */
    private fun executeDualPlayerEqualPowerTransition(
        isManualSkip: Boolean,
        forward: Boolean,
        durationMs: Long
    ) {
        _transitionState.value = TransitionState.TRANSITIONING
        _isCrossfading.value = true
        AutoMixState.setCrossfading(true)

        try {
            val currentMediaItem = primaryPlayer.currentMediaItem
            val currentPosition = primaryPlayer.currentPosition.coerceAtLeast(0L)

            if (currentMediaItem != null) {
                // a) Instantiate fadeoutPlayer using the same renderersFactory to preserve EQ / ReplayGain
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()

                val extractorsFactory = renderersFactory.buildExtractorsFactory()
                val ghostPlayer = ExoPlayer.Builder(context, renderersFactory)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractorsFactory))
                    .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
                    .build()

                fadeoutPlayer = ghostPlayer

                // b) Assign currentMediaItem and seek to currentPosition of primaryPlayer
                ghostPlayer.setMediaItem(currentMediaItem)
                ghostPlayer.seekTo(currentPosition)
                ghostPlayer.volume = 1.0f
                ghostPlayer.prepare()

                // c) Start playback on fadeoutPlayer
                ghostPlayer.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing fadeoutPlayer (Ghost Player): ${e.message}", e)
            releaseFadeoutPlayerSafely()
        }

        // d) Advance primaryPlayer to the next/previous track
        try {
            suppressDiscontinuityCallback = true
            primaryPlayer.setSeekParameters(SeekParameters.EXACT)
            if (isManualSkip) {
                if (forward) {
                    if (primaryPlayer.hasNextMediaItem()) {
                        primaryPlayer.seekToNextMediaItem()
                    } else {
                        primaryPlayer.seekToNext()
                    }
                } else {
                    if (primaryPlayer.hasPreviousMediaItem()) {
                        primaryPlayer.seekToPreviousMediaItem()
                    } else {
                        primaryPlayer.seekToPrevious()
                    }
                }
            } else {
                if (primaryPlayer.hasNextMediaItem()) {
                    primaryPlayer.seekToNextMediaItem()
                }
            }
            primaryPlayer.volume = 0.0f
            if (!primaryPlayer.isPlaying) {
                primaryPlayer.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error advancing primaryPlayer during transition: ${e.message}", e)
        } finally {
            suppressDiscontinuityCallback = false
        }

        // e) Start ValueAnimator from 0f to 1f with Equal-Power (sin/cos) gain law
        _transitionState.value = TransitionState.FADING_IN
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = (animation.animatedValue as Float).coerceIn(0f, 1f)
                val phase = fraction * (PI / 2.0)

                // f) Equal-Power PRO math:
                // primaryPlayer.volume = sin(fraction * PI / 2)
                // fadeoutPlayer.volume = cos(fraction * PI / 2)
                val primaryGain = sin(phase).toFloat().coerceIn(0f, 1f)
                val fadeoutGain = cos(phase).toFloat().coerceIn(0f, 1f)

                try {
                    primaryPlayer.volume = primaryGain
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating primaryPlayer volume: ${e.message}")
                }

                try {
                    fadeoutPlayer?.volume = fadeoutGain
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating fadeoutPlayer volume: ${e.message}")
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var isCancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    isCancelled = true
                    // g) Release fadeoutPlayer and null it out to free RAM
                    releaseFadeoutPlayerSafely()
                    restorePrimaryPlayerVolumeSafely()
                    _isCrossfading.value = false
                    AutoMixState.setCrossfading(false)
                    _transitionState.value = TransitionState.IDLE
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (isCancelled) return
                    // g) Release fadeoutPlayer and null it out to free RAM
                    releaseFadeoutPlayerSafely()
                    restorePrimaryPlayerVolumeSafely()
                    _isCrossfading.value = false
                    AutoMixState.setCrossfading(false)
                    _transitionState.value = TransitionState.IDLE
                }
            })
            start()
        }
    }

    /**
     * ECO tier fallback for manual skip: simple linear fade-out -> skip -> linear fade-in on [primaryPlayer]
     * without instantiating [fadeoutPlayer].
     */
    private fun executeEcoLinearManualSkip(forward: Boolean) {
        _transitionState.value = TransitionState.FADING_OUT
        _isCrossfading.value = true
        AutoMixState.setCrossfading(true)

        val startVol = try {
            primaryPlayer.volume.coerceIn(0f, 1f)
        } catch (e: Exception) {
            1.0f
        }

        fadeAnimator = ValueAnimator.ofFloat(startVol, 0f).apply {
            duration = ECO_FADE_OUT_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                try {
                    primaryPlayer.volume = (animation.animatedValue as Float).coerceIn(0f, 1f)
                } catch (e: Exception) {
                    Log.e(TAG, "ECO fade-out volume error: ${e.message}")
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
                        suppressDiscontinuityCallback = true
                        primaryPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        if (forward) {
                            if (primaryPlayer.hasNextMediaItem()) {
                                primaryPlayer.seekToNextMediaItem()
                            } else {
                                primaryPlayer.seekToNext()
                            }
                        } else {
                            if (primaryPlayer.hasPreviousMediaItem()) {
                                primaryPlayer.seekToPreviousMediaItem()
                            } else {
                                primaryPlayer.seekToPrevious()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ECO manual skip seek error: ${e.message}", e)
                    } finally {
                        suppressDiscontinuityCallback = false
                        executeEcoLinearAutoTransition()
                    }
                }
            })
            start()
        }
    }

    /**
     * ECO tier fallback for auto-transition / post-skip fade-in: simple linear ramp 0f -> 1f on [primaryPlayer].
     */
    private fun executeEcoLinearAutoTransition() {
        _transitionState.value = TransitionState.FADING_IN
        _isCrossfading.value = true
        AutoMixState.setCrossfading(true)

        try {
            primaryPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            primaryPlayer.volume = 0f
        } catch (e: Exception) {
            Log.e(TAG, "ECO error setting initial volume to 0f: ${e.message}")
        }

        fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ECO_FADE_IN_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                try {
                    primaryPlayer.volume = (animation.animatedValue as Float).coerceIn(0f, 1f)
                } catch (e: Exception) {
                    Log.e(TAG, "ECO fade-in volume error: ${e.message}")
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var isCancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    isCancelled = true
                    restorePrimaryPlayerVolumeSafely()
                    _isCrossfading.value = false
                    AutoMixState.setCrossfading(false)
                    _transitionState.value = TransitionState.IDLE
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (isCancelled) return
                    restorePrimaryPlayerVolumeSafely()
                    _isCrossfading.value = false
                    AutoMixState.setCrossfading(false)
                    _transitionState.value = TransitionState.IDLE
                }
            })
            start()
        }
    }

    /**
     * Cancels any active transition, releases [fadeoutPlayer], restores [primaryPlayer] volume to 1.0f,
     * and resets [AutoMixState].
     */
    fun cancelAndReset() {
        runOnMainThread {
            cancelActiveAnimationAndReleaseGhost()
            restorePrimaryPlayerVolumeSafely()
            _isCrossfading.value = false
            AutoMixState.setCrossfading(false)
            _transitionState.value = TransitionState.IDLE
        }
    }

    /**
     * Releases all listeners, active animations, and the Ghost Player instance.
     */
    fun release() {
        runOnMainThread {
            try {
                primaryPlayer.removeListener(playerListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing playerListener from primaryPlayer: ${e.message}")
            }
            cancelAndReset()
        }
    }

    private fun cancelActiveAnimationAndReleaseGhost() {
        try {
            fadeAnimator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling fadeAnimator: ${e.message}")
        } finally {
            fadeAnimator = null
        }
        releaseFadeoutPlayerSafely()
    }

    private fun releaseFadeoutPlayerSafely() {
        val ghost = fadeoutPlayer
        fadeoutPlayer = null
        if (ghost != null) {
            try {
                ghost.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping fadeoutPlayer: ${e.message}")
            }
            try {
                ghost.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing fadeoutPlayer: ${e.message}")
            }
        }
    }

    private fun restorePrimaryPlayerVolumeSafely() {
        try {
            primaryPlayer.volume = 1.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring primaryPlayer volume to 1.0f: ${e.message}")
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
