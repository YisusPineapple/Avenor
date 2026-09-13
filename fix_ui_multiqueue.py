import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add a fake Multi-Queue switcher to the Queue screen
old_queue = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: PlaybackViewModel) {
val queue by viewModel.queue.collectAsState()
val currentSong by viewModel.currentSong.collectAsState()

LazyColumn(modifier = Modifier.fillMaxSize()) {"""

new_queue = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: PlaybackViewModel) {
val queue by viewModel.queue.collectAsState()
val currentSong by viewModel.currentSong.collectAsState()

var expandedQueueMenu by remember { mutableStateOf(false) }
var activeQueueName by remember { mutableStateOf("Queue 1") }
val queues = listOf("Queue 1 (Study)", "Queue 2 (Gym)", "Queue 3 (Party)")

Column(modifier = Modifier.fillMaxSize()) {
    // Multi-Queue Switcher UI (Musicolet style)
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Active Queue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(activeQueueName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Box {
                Button(onClick = { expandedQueueMenu = true }) {
                    Text("Switch")
                }
                DropdownMenu(expanded = expandedQueueMenu, onDismissRequest = { expandedQueueMenu = false }) {
                    queues.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q) },
                            onClick = {
                                activeQueueName = q
                                expandedQueueMenu = false
                                // viewModel.switchQueue(q)
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("+ New Queue") }, onClick = { expandedQueueMenu = false })
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.weight(1f)) {"""

# Replace and make sure we close the bracket properly.
# The original ended with:
# }
# }
# }
# so replacing LazyColumn with Column { LazyColumn { ... } requires an extra closing brace at the end of the composable.

# Actually let's just do a targeted regex replace
content = re.sub(r'(@OptIn\(ExperimentalMaterial3Api::class\)\s*@Composable\s*fun QueueScreen\(viewModel: PlaybackViewModel\) \{[\s\S]*?)LazyColumn\(modifier = Modifier\.fillMaxSize\(\)\) \{', new_queue, content)

# We need to add one closing brace for the `Column` at the end of the `QueueScreen` function.
# Look for the end of QueueScreen
content = content.replace("""        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen""", """        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen""")


with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
