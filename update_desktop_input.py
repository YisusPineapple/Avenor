import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add imports if missing
imports = [
    "import androidx.compose.ui.input.pointer.pointerInput",
    "import androidx.compose.ui.input.pointer.PointerEventType",
    "import androidx.compose.ui.input.key.onKeyEvent",
    "import androidx.compose.ui.input.key.KeyEventType",
    "import androidx.compose.ui.input.key.type",
    "import androidx.compose.ui.input.key.key",
    "import androidx.compose.ui.input.key.Key",
    "import androidx.compose.ui.focus.FocusRequester",
    "import androidx.compose.ui.focus.focusRequester",
    "import androidx.compose.foundation.focusable",
]

for imp in imports:
    if imp not in content:
        content = content.replace("import androidx.compose.foundation.layout.*", f"import androidx.compose.foundation.layout.*\n{imp}")

# Wrap NexoAppRoot
content = content.replace(
    'NexoAppRoot()',
    'DesktopInputWrapper(viewModel = androidx.lifecycle.viewmodel.compose.viewModel()) { NexoAppRoot() }'
)

# Add DesktopInputWrapper composable
wrapper = """
@Composable
fun DesktopInputWrapper(viewModel: PlaybackViewModel, content: @Composable () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.Spacebar, Key.Enter -> { viewModel.togglePlayPause(); true }
                        Key.DirectionRight -> { viewModel.skipToNext(); true }
                        Key.DirectionLeft -> { viewModel.skipToPrevious(); true }
                        else -> false
                    }
                } else false
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (deltaY > 0) {
                                viewModel.skipToNext()
                            } else if (deltaY < 0) {
                                viewModel.skipToPrevious()
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
}
"""

if "fun DesktopInputWrapper" not in content:
    content = content + wrapper

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
