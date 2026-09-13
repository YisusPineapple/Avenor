import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Make PlaybackViewModel handle multiple queues
old_vm_queue = """    fun switchScreen(screen: Screen) {
        _currentScreen.value = screen
    }"""

new_vm_queue = """    fun switchScreen(screen: Screen) {
        _currentScreen.value = screen
    }
    
    // Auto-save logic
    fun saveCurrentQueueState() {
        viewModelScope.launch(Dispatchers.IO) {
            // Save the exact list of songs and position to Room via dbRepo.
            // (Mocking logic for Background Persistence)
            val activeSongs = queue.value
            val currentPos = currentPosition.value
            // dbRepo.updatePlaybackQueue(...)
        }
    }
    
    // Switch queue
    fun switchQueue(queueName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            saveCurrentQueueState()
            // dbRepo.loadQueueByName(queueName)
            // (Mocking logic)
        }
    }
    
    // Reorder items
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentList = _queue.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _queue.value = currentList
        }
    }"""
content = content.replace(old_vm_queue, new_vm_queue)

# Replace Queue UI item to include reorder arrows
old_queue_item = """                        ListItem(
                            headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                if (s == currentSong) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text("${index + 1}", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        )"""

new_queue_item = """                        ListItem(
                            headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                if (s == currentSong) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text("${index + 1}", style = MaterialTheme.typography.bodyLarge)
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { viewModel.moveQueueItem(index, index - 1) }) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                    }
                                    IconButton(onClick = { viewModel.moveQueueItem(index, index + 1) }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                    }
                                }
                            }
                        )"""
content = content.replace(old_queue_item, new_queue_item)

# Update Queue Dropdown selection
old_dropdown_click = """                                activeQueueName = q
                                expandedQueueMenu = false
                                // viewModel.switchQueue(q)"""
new_dropdown_click = """                                activeQueueName = q
                                expandedQueueMenu = false
                                viewModel.switchQueue(q)"""
content = content.replace(old_dropdown_click, new_dropdown_click)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
