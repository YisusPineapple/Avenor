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
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.yisus.avenor.DesktopMediaKeyManager
import io.github.yisus.avenor.DeviceProfileManager
import io.github.yisus.avenor.app.ui.viewmodels.PlayerViewModel

@Composable
fun DesktopInputWrapper(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
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
                        DesktopMediaKeyManager.handleMediaKeyEvent(event, playerViewModel)
                    } else {
                        when (event.key) {
                            Key.Spacebar, Key.Enter, Key.MediaPlayPause -> { playerViewModel.togglePlayPause(); true }
                            Key.DirectionRight, Key.MediaNext -> { playerViewModel.skipToNext(); true }
                            Key.DirectionLeft, Key.MediaPrevious -> { playerViewModel.skipToPrevious(); true }
                            Key.MediaPlay -> { if (playerViewModel.isPlaying.value == false) playerViewModel.togglePlayPause(); true }
                            Key.MediaPause -> { if (playerViewModel.isPlaying.value == true) playerViewModel.togglePlayPause(); true }
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
                                playerViewModel.skipToNext()
                            } else if (deltaY < 0) {
                                playerViewModel.skipToPrevious()
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
}
