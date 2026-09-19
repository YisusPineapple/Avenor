package io.github.yisus.avenor.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate

@Composable
fun AuroraBackground(performanceMode: String, content: @Composable () -> Unit) {
    val color1 = MaterialTheme.colorScheme.primaryContainer
    val color2 = MaterialTheme.colorScheme.surfaceVariant
    val bg = MaterialTheme.colorScheme.background

    if (performanceMode == "ECO") {
        // Zero graphics pipeline allocations
        Box(modifier = Modifier.fillMaxSize().background(bg)) { content() }
        return
    }

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by if (performanceMode == "VIVID") {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(35000, easing = LinearEasing))
        )
    } else remember { mutableStateOf(0f) }

    // Static Bitmap-based texture cache to eliminate shader-based background rendering
    var cachedBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(bg).drawWithCache {
        if (cachedBitmap == null || cachedBitmap?.width != size.width.toInt() || cachedBitmap?.height != size.height.toInt()) {
            val width = size.width.toInt().coerceAtLeast(1)
            val height = size.height.toInt().coerceAtLeast(1)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint1 = android.graphics.Paint().apply { color = android.graphics.Color.argb((0.15f * 255).toInt(), (color1.red * 255).toInt(), (color1.green * 255).toInt(), (color1.blue * 255).toInt()); isAntiAlias = true }
            val paint2 = android.graphics.Paint().apply { color = android.graphics.Color.argb((0.15f * 255).toInt(), (color2.red * 255).toInt(), (color2.green * 255).toInt(), (color2.blue * 255).toInt()); isAntiAlias = true }

            canvas.drawCircle(-width * 0.2f, -height * 0.2f, width * 1.2f, paint1)
            canvas.drawCircle(width * 1.1f, height * 1.1f, width * 0.9f, paint2)

            cachedBitmap = bitmap.asImageBitmap()
        }

        onDrawBehind {
            rotate(rotation) {
                cachedBitmap?.let { drawImage(it) }
            }
        }
    }) {
        content()
    }
}
