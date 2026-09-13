import re

trash_screen = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashRecoveryScreen(viewModel: PlaybackViewModel, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<io.github.yisus.nexo.TrashItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        items = viewModel.dbRepo.dao.getAllTrashItemsSync()
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash Recovery") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (loaded) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Trash is empty")
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(padding)) {
                    items(items.size) { index ->
                        val item = items[index]
                        var songTitle by remember { mutableStateOf("Unknown") }
                        LaunchedEffect(item.songId) {
                            val song = viewModel.dbRepo.dao.getSongById(item.songId.toInt())
                            songTitle = song?.title ?: "Unknown"
                        }
                        
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(songTitle) },
                            supportingContent = { Text("Will be deleted permanently") },
                            trailingContent = {
                                TextButton(onClick = {
                                    coroutineScope.launch {
                                        viewModel.dbRepo.dao.deleteTrashItem(item.songId)
                                        items = viewModel.dbRepo.dao.getAllTrashItemsSync()
                                    }
                                }) {
                                    Text("Recover")
                                }
                            }
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
"""

rename_dialog = """
@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    var isError by remember { mutableStateOf(false) }
    val illegalChars = "[\\\\\\\\/:*?\\"<>|]".toRegex()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = { 
            Column {
                OutlinedTextField(
                    value = text, 
                    onValueChange = { 
                        text = it
                        isError = illegalChars.containsMatchIn(it)
                    }, 
                    singleLine = true,
                    isError = isError,
                    supportingText = {
                        if (isError) Text("Contains illegal characters")
                    }
                )
            }
        },
        confirmButton = { 
            TextButton(
                onClick = { 
                    if (!isError && text.isNotBlank()) {
                        onRename(text)
                        onDismiss()
                    }
                },
                enabled = !isError && text.isNotBlank()
            ) { Text("Save") } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
"""

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "a") as f:
    f.write("\n" + trash_screen + "\n" + rename_dialog + "\n")
