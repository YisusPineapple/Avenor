import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    main = f.read()

old_dialog = """if (showRename) {
    AlertDialog(
        onDismissRequest = { showRename = false },
        title = { Text("Rename Track") },
        text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { viewModel.renameSong(song.id.toInt(), renameText); showRename = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
    )
}"""

new_dialog = """if (showRename) {
    RenameDialog(
        initialName = song.title,
        onDismiss = { showRename = false },
        onRename = { newName -> 
            viewModel.renameSong(song.id.toInt(), newName)
            showRename = false
        }
    )
}"""

main = main.replace(old_dialog, new_dialog)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(main)

