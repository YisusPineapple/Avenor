import re

with open("app/src/main/java/io/github/yisus/nexo/CrossfadeManager.kt", "r") as f:
    content = f.read()

# Add context requirement or just read from static variable if we can. 
# We need device tier to scale crossfade duration.
content = content.replace("class CrossfadeManager(private val player: ExoPlayer) {", "class CrossfadeManager(private val player: ExoPlayer, private val context: android.content.Context) {")
content = content.replace("private val crossfadeDurationMs = 3000L", "private val baseCrossfadeDurationMs = 3000L")

old_apply_fade = "val duration = if (fromManual) 1000L else crossfadeDurationMs"
new_apply_fade = """
                val tier = PerformanceBenchmark.evaluateDeviceTier(context)
                val scale = when(tier) {
                    "ECO" -> 0.5f // Faster crossfade on low end to free thread
                    "BALANCED" -> 1.0f
                    "VIVID" -> 1.5f
                    else -> 1.0f
                }
                val duration = if (fromManual) (1000L * scale).toLong() else (baseCrossfadeDurationMs * scale).toLong()
"""

content = content.replace(old_apply_fade, new_apply_fade)

with open("app/src/main/java/io/github/yisus/nexo/CrossfadeManager.kt", "w") as f:
    f.write(content)
