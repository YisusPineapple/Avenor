package io.github.yisus.avenor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class ActionMenuItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionMenuBottomSheet(
    title: String,
    subtitle: String? = null,
    items: List<ActionMenuItem>,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider(modifier = Modifier.padding(bottom = 8.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            item.onClick()
                            onDismissRequest()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
