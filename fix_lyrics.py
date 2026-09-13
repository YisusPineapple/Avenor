import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Fix lyricsOffset
content = content.replace(
    'val currentPosition by viewModel.currentPosition.collectAsState()',
    'val currentPosition by viewModel.currentPosition.collectAsState()\n    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()'
)

# Fix Key import
if "import androidx.compose.ui.input.key.Key\n" not in content:
    content = content.replace(
        'import androidx.compose.ui.input.key.KeyEventType',
        'import androidx.compose.ui.input.key.KeyEventType\nimport androidx.compose.ui.input.key.Key'
    )

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
