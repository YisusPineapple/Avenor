package io.github.yisus.avenor.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import io.github.yisus.avenor.DesktopMediaKeyManager
import io.github.yisus.avenor.DeviceProfileManager
import io.github.yisus.avenor.PlaybackViewModel

@Composable
fun DesktopInputWrapper(viewModel: PlaybackViewModel, content: @Composable () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    if (DeviceProfileManager.isDesktop()) {
                        DesktopMediaKeyManager.handleMediaKeyEvent(event, viewModel)
                    } else {
                        when (event.key) {
                            Key.Spacebar, Key.Enter, Key.MediaPlayPause -> { viewModel.togglePlayPause(); true }
                            Key.DirectionRight, Key.MediaNext -> { viewModel.skipToNext(); true }
                            Key.DirectionLeft, Key.MediaPrevious -> { viewModel.skipToPrevious(); true }
                            Key.MediaPlay -> { if (viewModel.isPlaying.value == false) viewModel.togglePlayPause(); true }
                            Key.MediaPause -> { if (viewModel.isPlaying.value == true) viewModel.togglePlayPause(); true }
                            else -> false
                        }
                    }
                } else false
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (deltaY > 0) {
                                viewModel.skipToNext()
                            } else if (deltaY < 0) {
                                viewModel.skipToPrevious()
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
}
