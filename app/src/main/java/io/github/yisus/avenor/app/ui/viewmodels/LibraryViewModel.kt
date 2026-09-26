package io.github.yisus.avenor.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.yisus.avenor.app.data.repository.LocalAudioRepository
import io.github.yisus.avenor.app.data.scanner.ScannedAudioItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LocalAudioRepository
) : ViewModel() {

    private val _audioList = MutableStateFlow<List<ScannedAudioItem>>(emptyList())
    val audioList: StateFlow<List<ScannedAudioItem>> = _audioList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAudio()
    }

    fun loadAudio() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _audioList.value = repository.getAllLocalAudio()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
