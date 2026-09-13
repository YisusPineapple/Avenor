import re

# Fix dao visibility in DatabaseRepository
with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "r") as f:
    repo_content = f.read()

repo_content = repo_content.replace("private val dao: MusicDao", "val dao: MusicDao")
with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "w") as f:
    f.write(repo_content)

# Restore missing UI components in MainActivity
with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

missing_functions = """
fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun OptimizedVerticalSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.graphicsLayer { rotationZ = 270f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqScreen(
"""
content = content.replace("@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun EqScreen(", missing_functions)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
