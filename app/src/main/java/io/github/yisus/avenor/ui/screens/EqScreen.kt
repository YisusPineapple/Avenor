package io.github.yisus.avenor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yisus.avenor.PlaybackViewModel

@Composable
fun OptimizedVerticalSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.graphicsLayer { rotationZ = 270f; transformOrigin = TransformOrigin(0f, 0f) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqScreen(viewModel: PlaybackViewModel) {
    val bands by viewModel.eqBands.collectAsState()
    val presets by viewModel.eqPresets.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

    var showPresetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Save Preset") },
            text = { OutlinedTextField(value = presetName, onValueChange = { presetName = it }, label = { Text("Name") }, shape = MaterialTheme.shapes.large) },
            confirmButton = { TextButton(onClick = { if (presetName.isNotBlank()) viewModel.saveEqPreset(presetName); showPresetDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showPresetDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Graphic Equalizer", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Fine-tune frequencies seamlessly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUTO", style = MaterialTheme.typography.labelMedium, color = if (appSettings?.autoEq == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(
                    checked = appSettings?.autoEq == true,
                    onCheckedChange = { st -> appSettings?.let { viewModel.saveSettings(it.copy(autoEq = st)) } }
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
            Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                bands.forEachIndexed { index, value ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("${if (value > 0) "+" else ""}${value.toInt()}dB", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 12.dp))
                        OptimizedVerticalSlider(value = value, onValueChange = { viewModel.updateEqBand(index, it) }, modifier = Modifier.weight(1f).width(40.dp))
                        Text(labels[index], style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                Button(onClick = { presetName = ""; showPresetDialog = true }) { Text("Save") }
            }
            items(viewModel.defaultEqPresets) { preset ->
                AssistChip(onClick = { viewModel.applyEqPreset(preset) }, label = { Text(preset.name) }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant))
            }
            items(presets) { preset ->
                AssistChip(onClick = { viewModel.applyEqPreset(preset) }, label = { Text(preset.name) }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer))
            }
        }
    }
}
