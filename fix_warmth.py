import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Update WarmthPalette to be perfectly matching "Warm Terracotta/Cream Palette" as requested (they want light or dark?) "Material You interface with a warm terracotta/cream palette". Cream implies a light theme, but let's make it a nice warm terracotta. Actually, `darkColorScheme` was used. We can leave it as dark mode but with those tones, or switch it to light if `Cream` is the background. Let's make it a light color scheme if they specified Cream palette, but wait, `darkColorScheme` was hardcoded.

new_warmth = """val WarmthPalette = lightColorScheme(
    primary = Color(0xFFC05746), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF3B0900),
    background = Color(0xFFF5EBE6), onBackground = Color(0xFF261D1A),
    surface = Color(0xFFFFF8F6), onSurface = Color(0xFF261D1A),
    surfaceVariant = Color(0xFFF4DED8), onSurfaceVariant = Color(0xFF53433F)
)"""
old_warmth = """val WarmthPalette = darkColorScheme(
    primary = Color(0xFFFFB59D), onPrimary = Color(0xFF5E1700),
    primaryContainer = Color(0xFF862200), onPrimaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF161211), onBackground = Color(0xFFEDE0DB),
    surface = Color(0xFF261D1A), onSurface = Color(0xFFEDE0DB),
    surfaceVariant = Color(0xFF53433F), onSurfaceVariant = Color(0xFFD8C2BB)
)"""
content = content.replace(old_warmth, new_warmth)
if "import androidx.compose.material3.lightColorScheme" not in content:
    content = content.replace("import androidx.compose.material3.darkColorScheme", "import androidx.compose.material3.darkColorScheme\nimport androidx.compose.material3.lightColorScheme")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
