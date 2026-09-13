import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add removeFromQueue to ViewModel
vm_remove = """
    fun removeFromQueue(song: Song) {
        val current = _queue.value.toMutableList()
        val currentIndex = controller?.currentMediaItemIndex ?: -1
        
        val indexToRemove = current.indexOfFirst { it.id == song.id }
        if (indexToRemove != -1) {
            current.removeAt(indexToRemove)
            _queue.value = current
            currentPlayingList = current
            controller?.removeMediaItem(indexToRemove)
        }
    }
"""
content = content.replace("fun enqueue(song: Song) {", vm_remove + "\n    fun enqueue(song: Song) {")

# Update QueueScreen to include Remove
queue_remove_old = """
                                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                    DropdownMenuItem(text = { Text("Play Next") }, onClick = { viewModel.playNext(song); showOptions = false }, leadingIcon = { Icon(Icons.Default.SkipNext, null) })
                                    // Normally we would have remove from queue here, but needs ViewModel support
                                }
"""
queue_remove_new = """
                                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                    DropdownMenuItem(text = { Text("Play Next") }, onClick = { viewModel.playNext(song); showOptions = false }, leadingIcon = { Icon(Icons.Default.SkipNext, null) })
                                    DropdownMenuItem(text = { Text("Remove from Queue") }, onClick = { viewModel.removeFromQueue(song); showOptions = false }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                                    DropdownMenuItem(text = { Text("Song Info") }, onClick = { /* TODO */ showOptions = false }, leadingIcon = { Icon(Icons.Default.Info, null) })
                                }
"""
content = content.replace(queue_remove_old.strip(), queue_remove_new.strip())

# Add Delete icon import if needed
if "import androidx.compose.material.icons.filled.Delete" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Info", "import androidx.compose.material.icons.filled.Info\nimport androidx.compose.material.icons.filled.Delete")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
