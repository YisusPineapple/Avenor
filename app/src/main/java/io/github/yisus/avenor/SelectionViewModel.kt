package io.github.yisus.avenor

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelectionViewModel : ViewModel() {
    private val _selectedSongs = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongs: StateFlow<Set<Long>> = _selectedSongs.asStateFlow()

    val isSelectionModeActive: Boolean
        get() = _selectedSongs.value.isNotEmpty()

    fun toggleSelection(songId: Long) {
        val current = _selectedSongs.value.toMutableSet()
        if (current.contains(songId)) {
            current.remove(songId)
        } else {
            current.add(songId)
        }
        _selectedSongs.value = current
    }

    fun selectAll(songIds: List<Long>) {
        _selectedSongs.value = songIds.toSet()
    }

    fun clearSelection() {
        _selectedSongs.value = emptySet()
    }
}
