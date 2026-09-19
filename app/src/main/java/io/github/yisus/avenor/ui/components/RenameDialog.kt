package io.github.yisus.avenor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    var isError by remember { mutableStateOf(false) }
    val illegalChars = "[\\\\/:*?\"<>|]".toRegex()

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
