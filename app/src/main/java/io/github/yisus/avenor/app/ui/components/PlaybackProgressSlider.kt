package io.github.yisus.avenor.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.yisus.avenor.app.ui.viewmodels.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlaybackProgressSlider(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    var currentPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentPositionMs = viewModel.getCurrentPosition()
            delay(500L)
        }
    }

    Slider(
        value = currentPositionMs.toFloat().coerceIn(0f, 100f),
        onValueChange = { newValue ->
            val targetMs = newValue.toLong()
            currentPositionMs = targetMs
            viewModel.seekTo(targetMs)
        },
        valueRange = 0f..100f,
        modifier = modifier
            .fillMaxWidth()
            .testTag("playback_progress_slider")
    )
}
