import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

dynamic_color_logic = """
    var dynamicPalette by remember { mutableStateOf<ColorScheme?>(null) }
    val currentSong by viewModel.currentSong.collectAsState()
    
    LaunchedEffect(currentSong?.albumArtUri) {
        val uri = currentSong?.albumArtUri
        if (uri != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val loader = coil.imageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(uri)
                        .size(128)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                            val dominantColor = palette.getDominantColor(0)
                            if (dominantColor != 0) {
                                val c = Color(dominantColor)
                                val isDark = androidx.core.graphics.ColorUtils.calculateLuminance(dominantColor) < 0.5
                                val newScheme = if (isDark) {
                                    darkColorScheme(
                                        primary = c,
                                        onPrimary = Color.White,
                                        primaryContainer = c.copy(alpha = 0.5f),
                                        onPrimaryContainer = Color.White,
                                        background = Color(0xFF161211),
                                        onBackground = Color(0xFFEDE0DB),
                                        surface = Color(0xFF261D1A),
                                        onSurface = Color(0xFFEDE0DB),
                                        surfaceVariant = Color(0xFF53433F),
                                        onSurfaceVariant = Color(0xFFD8C2BB)
                                    )
                                } else {
                                    lightColorScheme(
                                        primary = c,
                                        onPrimary = Color.White,
                                        primaryContainer = c.copy(alpha = 0.3f),
                                        onPrimaryContainer = c.copy(alpha = 0.9f),
                                        background = Color(0xFFF5EBE6),
                                        onBackground = Color(0xFF261D1A),
                                        surface = Color(0xFFFFF8F6),
                                        onSurface = Color(0xFF261D1A),
                                        surfaceVariant = Color(0xFFF4DED8),
                                        onSurfaceVariant = Color(0xFF53433F)
                                    )
                                }
                                dynamicPalette = newScheme
                            } else { dynamicPalette = null }
                        } else { dynamicPalette = null }
                    } else { dynamicPalette = null }
                } catch (e: Exception) { dynamicPalette = null }
            }
        } else {
            dynamicPalette = null
        }
    }
"""

old_theme = """    // Theme Controller Logic (Pre-computed mapping avoids memory spikes)
    val colorScheme = when (appSettings?.themeStyle) {
        "AURORA" -> AuroraPalette
        "SOFT_UI" -> SoftUiPalette
        "EXPRESSIVE" -> ExpressivePalette
        else -> WarmthPalette // "WARMTH" default
    }"""
    
new_theme = dynamic_color_logic + """
    // Theme Controller Logic (Pre-computed mapping avoids memory spikes)
    val baseScheme = when (appSettings?.themeStyle) {
        "AURORA" -> AuroraPalette
        "SOFT_UI" -> SoftUiPalette
        "EXPRESSIVE" -> ExpressivePalette
        else -> WarmthPalette // "WARMTH" default
    }
    
    val colorScheme = if (appSettings?.themeStyle == "WARMTH" || appSettings?.themeStyle == null) (dynamicPalette ?: baseScheme) else baseScheme
"""

content = content.replace(old_theme, new_theme)

# Need to add coil.imageLoader import
if "import coil.imageLoader" not in content:
    content = content.replace("import coil.ImageLoader", "import coil.ImageLoader\nimport coil.imageLoader")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
