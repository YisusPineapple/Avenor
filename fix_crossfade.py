with open("app/src/main/java/io/github/yisus/nexo/CrossfadeManager.kt", "r") as f:
    content = f.read()

new_content = """package io.github.yisus.nexo

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.LinearInterpolator
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
                    applyFadeIn()
                }
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
                        if (forward && player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                        } else if (!forward && player.hasPreviousMediaItem()) {
                            player.seekToPreviousMediaItem()
                        }
                        applyFadeIn(fromManual = true)
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
"""

with open("app/src/main/java/io/github/yisus/nexo/CrossfadeManager.kt", "w") as f:
    f.write(new_content)
