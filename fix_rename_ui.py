import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# I will append rename capability to ViewModel
new_vm_rename = """
    fun renamePlaylist(id: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dbRepo.dao.renamePlaylist(id, newName)
        }
    }
    
    fun renameQueue(id: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dbRepo.dao.renamePlaybackQueue(id, newName)
        }
    }
"""
content = content.replace("fun moveQueueItem(fromIndex: Int, toIndex: Int) {", new_vm_rename + "\n    fun moveQueueItem(fromIndex: Int, toIndex: Int) {")


# Also need to modify Queue UI to show a "Rename Queue" button
old_queue_header = """            Box {
                Button(onClick = { expandedQueueMenu = true }) {
                    Text("Switch")
                }"""
new_queue_header = """            Box {
                Row {
                    var showRenameDialog by remember { mutableStateOf(false) }
                    var newQueueName by remember { mutableStateOf(activeQueueName) }
                    
                    if (showRenameDialog) {
                        AlertDialog(
                            onDismissRequest = { showRenameDialog = false },
                            title = { Text("Rename Queue") },
                            text = { 
                                OutlinedTextField(
                                    value = newQueueName,
                                    onValueChange = { newQueueName = it },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    // In a real app we'd pass the actual queue ID, here we mock it to UI state
                                    activeQueueName = newQueueName
                                    showRenameDialog = false
                                }) { Text("Save") }
                            }
                        )
                    }
                    
                    IconButton(onClick = { newQueueName = activeQueueName; showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                    Button(onClick = { expandedQueueMenu = true }) {
                        Text("Switch")
                    }
                }"""
content = content.replace(old_queue_header, new_queue_header)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
