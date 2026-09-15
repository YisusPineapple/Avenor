package io.github.yisus.avenor.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state flow for Cross-Platform Compose Multiplatform (KMP).
 * This acts as the single source of truth for the Now Playing UI, allowing 
 * desktop and mobile clients to reactively pulse their indicators when 
 * the underlying audio engine (Media3 or Rust FFI) is applying DSP transitions.
 */
object NowPlayingState {
    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    // Extendible for future Rust DSP engine states (e.g., ReplayGain active, EQ active)
    private val _isDspActive = MutableStateFlow(false)
    val isDspActive: StateFlow<Boolean> = _isDspActive.asStateFlow()

    fun setCrossfading(active: Boolean) {
        _isCrossfading.value = active
    }

    fun setDspActive(active: Boolean) {
        _isDspActive.value = active
    }
}
