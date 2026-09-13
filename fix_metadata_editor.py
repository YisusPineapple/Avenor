import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

editor_code = """
@Composable
fun MetadataEditorOverlay(song: Song, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
            Text("Edit Metadata (In-App Override)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    onSave(title, artist)
                    onDismiss()
                }) { Text("Save") }
            }
        }
    }
}
"""
if "fun MetadataEditorOverlay" not in content:
    content = content.replace("// ViewModels & Logic", editor_code + "\n// ViewModels & Logic")

# Add a function to PlaybackViewModel
viewmodel_update = """
    fun updateSongMetadata(song: Song, newTitle: String, newArtist: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedSong = song.copy(title = newTitle, artist = newArtist)
            // Need a dao update method, but we can use insertSongs with REPLACE or similar
            // Actually let's just use a quick raw query or add it to DAO later.
            // For now, let's assume we add an updateSong to MusicDao
            dbRepo.updateSong(updatedSong)
        }
    }
"""
# I'll update the DAO in AppDatabase too.

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
