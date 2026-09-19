package io.github.yisus.avenor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yisus.avenor.PlaybackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricSyncOverlay(viewModel: PlaybackViewModel, onDismiss: () -> Unit) {
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Fine-Tune Lyric Sync", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "${if (lyricsOffset > 0) "+" else ""}${lyricsOffset}ms",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilledIconButton(onClick = { viewModel.setLyricsOffset(lyricsOffset - 100) }) { Icon(Icons.Default.Remove, "-100ms") }
                FilledIconButton(onClick = { viewModel.setLyricsOffset(0) }) { Text("Reset") }
                FilledIconButton(onClick = { viewModel.setLyricsOffset(lyricsOffset + 100) }) { Icon(Icons.Default.Add, "+100ms") }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { viewModel.saveLyricsOffset(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("Save Offset for Song")
            }
        }
    }
}
