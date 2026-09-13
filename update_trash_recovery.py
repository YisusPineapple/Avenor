import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    main = f.read()

# Add to when (val screen = currentScreen)
main = main.replace("is Screen.Settings -> SettingsScreen(viewModel)", "is Screen.Settings -> SettingsScreen(viewModel, onNavigateToTrash = { currentScreen = Screen.TrashRecovery })\nis Screen.TrashRecovery -> TrashRecoveryScreen(viewModel, onBack = { currentScreen = Screen.Settings })")

# Update SettingsScreen signature
main = main.replace("fun SettingsScreen(viewModel: PlaybackViewModel)", "fun SettingsScreen(viewModel: PlaybackViewModel, onNavigateToTrash: () -> Unit = {})")

# Update Empty Trash button to have a Recover Trash button
recover_btn = """                        onClick = { 
                            coroutineScope.launch { 
                                viewModel.emptyTrashSecurely(context)
                                android.widget.Toast.makeText(context, "Trash Emptied Securely", android.widget.Toast.LENGTH_SHORT).show()
                            } 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Empty Trash Now")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToTrash,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Recover Trash")
                    }"""

main = re.sub(r'onClick = \{\s*coroutineScope\.launch \{\s*viewModel\.emptyTrashSecurely\(context\)\s*android\.widget\.Toast\.makeText\(context, "Trash Emptied Securely", android\.widget\.Toast\.LENGTH_SHORT\)\.show\(\)\s*\}\s*\},[\s\S]*?Text\("Empty Trash Now"\)\s*\}', recover_btn, main)


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

main = main.replace("@Composable\nfun AppRoot", trash_screen + "\n@Composable\nfun AppRoot")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(main)
