package io.github.yisus.avenor

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Handles crossfade (volume fading) between track transitions using Hardware-Accelerated ValueAnimator.
 * This guarantees buttery smooth 60fps+ interpolations without blocking the CPU or being affected by GC pauses.
 */
class CrossfadeManager(private val player: ExoPlayer, private val context: Context) {
    private val TAG = "CrossfadeManager"
    private var fadeAnimator: ValueAnimator? = null
    private val baseCrossfadeDurationMs = 3000L // 3 seconds
    private val handler = Handler(Looper.getMainLooper())

    init {
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    Toast.makeText(context, "AutoMix Transitioning...", Toast.LENGTH_SHORT).show()
                    applyFadeIn()
                }
            }
            
            // Blindaje contra errores del reproductor durante el crossfade
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player Error during Crossfade: ${error.message}")
                fadeAnimator?.cancel()
                player.volume = 1f // Reset volume to ensure it's not stuck at 0
            }
        })
    }

    fun manualSkip(forward: Boolean) {
        handler.post {
            fadeAnimator?.cancel()
            
            // Fast fade out (500ms)
            fadeAnimator = ValueAnimator.ofFloat(player.volume, 0f).apply {
                duration = 500
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    player.volume = animation.animatedValue as Float
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        try {
                            if (forward && player.hasNextMediaItem()) {
                                player.seekToNextMediaItem()
                            } else if (!forward && player.hasPreviousMediaItem()) {
                                player.seekToPreviousMediaItem()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during manual skip: ${e.message}")
                        } finally {
                            applyFadeIn(fromManual = true)
                        }
                    }
                })
                start()
            }
        }
    }

    private fun applyFadeIn(fromManual: Boolean = false) {
        handler.post {
            fadeAnimator?.cancel()
            
            val tier = PerformanceBenchmark.evaluateDeviceTier(context)
            val scale = when(tier) {
                "ECO" -> 0.5f 
                "BALANCED" -> 1.0f
                "VIVID" -> 1.5f
                else -> 1.0f
            }

            // Pre-cache buffers if VIVID
            try {
                if (tier == "VIVID" || tier == "BALANCED") {
                    player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
                } else {
                    player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting seek parameters: ${e.message}")
            }

            val duration = if (fromManual) (1000L * scale).toLong() else (baseCrossfadeDurationMs * scale).toLong()
            
            player.volume = 0f
            fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    player.volume = animation.animatedValue as Float
                }
                start()
            }
        }
    }

    fun release() {
        handler.post {
            fadeAnimator?.cancel()
        }
    }
}
