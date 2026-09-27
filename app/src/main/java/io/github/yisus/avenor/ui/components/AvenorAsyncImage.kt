package io.github.yisus.avenor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size

/**
 * Memory-safe image loader that enforces explicit downsampling according to the user's
 * [resolution] preference ("LOW", "MEDIUM", "HIGH", "ORIGINAL") before decoding into RAM.
 */
@Composable
fun AvenorAsyncImage(
    model: Any?,
    resolution: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    colorFilter: ColorFilter? = null
) {
    val context = LocalContext.current

    val imageRequest = remember(context, model, resolution) {
        ImageRequest.Builder(context)
            .data(model)
            .apply {
                when (resolution.uppercase()) {
                    "LOW" -> size(256)
                    "MEDIUM" -> size(512)
                    "HIGH" -> size(1024)
                    "ORIGINAL" -> size(Size.ORIGINAL)
                    else -> size(1024)
                }
            }
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter
    )
}
