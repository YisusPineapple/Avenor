import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    main = f.read()

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

if "fun RenameDialog" not in main:
    main = main.replace("@Composable\nfun AppRoot", rename_dialog + "\n@Composable\nfun AppRoot")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(main)

