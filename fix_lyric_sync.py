import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Replace LrcSyncEditor with LyricSyncOverlay and -2000 to 2000 bounds
content = content.replace("fun LrcSyncEditor(viewModel: PlaybackViewModel, onDismiss: () -> Unit) {", "fun LyricSyncOverlay(viewModel: PlaybackViewModel, onDismiss: () -> Unit) {")
content = content.replace("valueRange = -5000f..5000f", "valueRange = -2000f..2000f")
content = content.replace('Text("-5000 ms"', 'Text("-2000 ms"')
content = content.replace('Text("+5000 ms"', 'Text("+2000 ms"')

content = content.replace("LrcSyncEditor(viewModel = viewModel, onDismiss = { showLrcEditor = false })", "LyricSyncOverlay(viewModel = viewModel, onDismiss = { showLrcEditor = false })")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
