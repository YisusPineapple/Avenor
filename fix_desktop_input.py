import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add Media Keys
old_keys = """                        Key.Spacebar, Key.Enter -> { viewModel.togglePlayPause(); true }
                        Key.DirectionRight -> { viewModel.skipToNext(); true }
                        Key.DirectionLeft -> { viewModel.skipToPrevious(); true }
                        else -> false"""

new_keys = """                        Key.Spacebar, Key.Enter, Key.MediaPlayPause -> { viewModel.togglePlayPause(); true }
                        Key.DirectionRight, Key.MediaNext -> { viewModel.skipToNext(); true }
                        Key.DirectionLeft, Key.MediaPrevious -> { viewModel.skipToPrevious(); true }
                        Key.MediaPlay -> { if (viewModel.isPlaying.value == false) viewModel.togglePlayPause(); true }
                        Key.MediaPause -> { if (viewModel.isPlaying.value == true) viewModel.togglePlayPause(); true }
                        else -> false"""
                        
content = content.replace(old_keys, new_keys)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
