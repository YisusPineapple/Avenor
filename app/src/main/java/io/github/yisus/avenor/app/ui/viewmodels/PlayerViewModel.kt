package io.github.yisus.avenor.app.ui.viewmodels

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yisus.avenor.AppDatabase
import io.github.yisus.avenor.AppSetting
import io.github.yisus.avenor.DatabaseRepository
import io.github.yisus.avenor.EqPreset
import io.github.yisus.avenor.PlaybackService
import io.github.yisus.avenor.Song
import io.github.yisus.avenor.dsp.EqPreferences
import io.github.yisus.avenor.dsp.EqPresetDefinitions
import io.github.yisus.avenor.lyrics.LyricLine
import io.github.yisus.avenor.lyrics.LyricsRepository
import io.github.yisus.avenor.playback.PlaybackCoordinator
import io.github.yisus.avenor.shared.playback.AvenorPlaybackController
import io.github.yisus.avenor.shared.playback.CurrentMediaItem
import io.github.yisus.avenor.shared.playback.PlaybackState
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: AvenorPlaybackController,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val playbackCoordinator = PlaybackCoordinator.getInstance(appContext)
    val coordinatorState = playbackCoordinator.playbackState
    val dbRepo = DatabaseRepository(AppDatabase.getDatabase(appContext).musicDao())

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    val playbackState: StateFlow<PlaybackState> = controller.playbackState
    val currentMediaItem: StateFlow<CurrentMediaItem?> = controller.currentMediaItem

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()
    private var sleepTimerJob: Job? = null

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    val lyricsOffsetMs = MutableStateFlow(0L)

    private val _eqBands = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f))
    val eqBands: StateFlow<List<Float>> = _eqBands.asStateFlow()

    val eqPresets: StateFlow<List<EqPreset>> = dbRepo.eqPresets.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val defaultEqPresets: List<EqPreset>
        get() = EqPresetDefinitions.DEFAULT_PRESETS

    val appSettings: StateFlow<AppSetting?> = dbRepo.appSettings.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val favoriteSongIds: StateFlow<List<Long>> = dbRepo.favoriteSongIds.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            playbackCoordinator.restorePersistedState()
        }

        viewModelScope.launch {
            playbackCoordinator.playbackState.collect { state ->
                _queue.value = state.queue
                if (state.currentSong != null) {
                    _currentSong.value = state.currentSong
                }
                _isPlaying.value = state.isPlaying
                _isShuffleEnabled.value = state.shuffleMode
                _repeatMode.value = state.repeatMode
                if (_currentPosition.value == 0L && state.currentPositionMs > 0L) {
                    _currentPosition.value = state.currentPositionMs
                }
            }
        }

        viewModelScope.launch {
            controller.playbackState.collect { state ->
                when (state) {
                    is PlaybackState.Playing -> _isPlaying.value = true
                    is PlaybackState.Paused -> _isPlaying.value = false
                    else -> Unit
                }
            }
        }

        connectMediaController()
    }

    private fun connectMediaController() {
        try {
            val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            val future = MediaController.Builder(appContext, sessionToken).buildAsync()
            mediaControllerFuture = future
            viewModelScope.launch {
                try {
                    val mc = future.await()
                    mediaController = mc
                    mc.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            val mediaId = mediaItem?.mediaId
                            val mediaIdLong = mediaId?.toLongOrNull()
                            val queueSong = playbackCoordinator.playbackState.value.queue.find {
                                it.id.toString() == mediaId
                            }
                            if (queueSong != null) {
                                _currentSong.value = queueSong
                                processTrackTransition(queueSong, appContext, mediaItem)
                            } else if (mediaIdLong != null) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    val resolved = dbRepo.getSongById(mediaIdLong)
                                    withContext(Dispatchers.Main) {
                                        _currentSong.value = resolved
                                        resolved?.let { processTrackTransition(it, appContext, mediaItem) }
                                    }
                                }
                            }
                        }

                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            _isShuffleEnabled.value = shuffleModeEnabled
                        }

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            _repeatMode.value = repeatMode
                        }
                    })

                    val eqPrefs = EqPreferences(appContext)
                    val initialBands = eqPrefs.getBands()
                    _eqBands.value = initialBands.toList()
                    _eqBands.value.forEachIndexed { index, level -> updateEqBand(index, level) }

                    appSettings.value?.let { s ->
                        updateNotificationPrefs(s.showLike, s.showShuffle, s.showRepeat)
                        val crossfadeArgs = Bundle().apply {
                            putBoolean("enabled", s.crossfadeEnabled)
                            putBoolean("autoMixEnabled", s.autoMixEnabled)
                        }
                        mc.sendCustomCommand(SessionCommand("SET_CROSSFADE_CONFIG", Bundle.EMPTY), crossfadeArgs)

                        val passthroughArgs = Bundle().apply {
                            putBoolean("bitPerfectPassthrough", s.bitPerfectPassthrough)
                        }
                        mc.sendCustomCommand(SessionCommand("SET_PASSTHROUGH_CONFIG", Bundle.EMPTY), passthroughArgs)
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    fun togglePlayPause() {
        val currentlyPlaying = mediaController?.isPlaying ?: _isPlaying.value
        if (currentlyPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        _isPlaying.value = true
        controller.play()
        mediaController?.play()
        viewModelScope.launch {
            playbackCoordinator.updatePlaybackParams(isPlaying = true)
        }
    }

    fun pause() {
        _isPlaying.value = false
        controller.pause()
        mediaController?.pause()
        viewModelScope.launch {
            playbackCoordinator.updatePlaybackParams(isPlaying = false)
        }
    }

    fun skipToNext() {
        val mc = mediaController
        if (mc != null) {
            mc.sendCustomCommand(SessionCommand("ACTION_SKIP_NEXT", Bundle.EMPTY), Bundle.EMPTY)
        } else {
            controller.skipToNext()
        }
    }

    fun skipToPrevious() {
        val mc = mediaController
        if (mc != null) {
            mc.sendCustomCommand(SessionCommand("ACTION_SKIP_PREV", Bundle.EMPTY), Bundle.EMPTY)
        } else {
            controller.skipToPrevious()
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        val args = Bundle().apply {
            putBoolean("enabled", enabled)
        }
        mediaController?.sendCustomCommand(SessionCommand("SET_CROSSFADE_CONFIG", Bundle.EMPTY), args)
    }

    fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
        viewModelScope.launch {
            playbackCoordinator.updatePosition(positionMs, force = true)
        }
    }

    fun toggleShuffle() {
        val currentShuffle = mediaController?.shuffleModeEnabled ?: _isShuffleEnabled.value
        val newShuffle = !currentShuffle
        mediaController?.shuffleModeEnabled = newShuffle
        _isShuffleEnabled.value = newShuffle
        viewModelScope.launch {
            playbackCoordinator.updatePlaybackParams(shuffleMode = newShuffle)
        }
    }

    fun toggleRepeat() {
        val currentRepeat = mediaController?.repeatMode ?: _repeatMode.value
        val newRepeat = when (currentRepeat) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = newRepeat
        _repeatMode.value = newRepeat
        viewModelScope.launch {
            playbackCoordinator.updatePlaybackParams(repeatMode = newRepeat)
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            _sleepTimerActive.value = true
            sleepTimerJob = viewModelScope.launch {
                delay(minutes * 60 * 1000L)
                pause()
                _sleepTimerActive.value = false
            }
        } else {
            _sleepTimerActive.value = false
        }
    }

    fun updatePosition() {
        val pos = mediaController?.currentPosition ?: controller.currentPositionMs
        _currentPosition.value = pos
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            dbRepo.toggleFavorite(songId)
        }
    }

    fun playSongList(songList: List<Song>, startIndex: Int) {
        val validIndex = if (startIndex in songList.indices) startIndex else 0
        _queue.value = songList
        if (songList.isNotEmpty() && validIndex in songList.indices) {
            _currentSong.value = songList[validIndex]
        }
        val mediaItems = songList.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }
        mediaController?.setMediaItems(mediaItems, validIndex, 0L)
        mediaController?.prepare()
        mediaController?.play()
        val coordState = playbackCoordinator.playbackState.value
        viewModelScope.launch {
            playbackCoordinator.saveFullQueue(
                songs = songList,
                currentIndex = validIndex,
                positionMs = 0L,
                isPlaying = true,
                shuffleMode = coordState.shuffleMode,
                repeatMode = coordState.repeatMode
            )
        }
    }

    fun playNext(song: Song) {
        val current = _queue.value.toMutableList()
        val currentIndex = mediaController?.currentMediaItemIndex ?: 0
        if (current.isNotEmpty() && currentIndex in current.indices) {
            current.add(currentIndex + 1, song)
            _queue.value = current
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
            mediaController?.addMediaItem(currentIndex + 1, mediaItem)
            viewModelScope.launch {
                playbackCoordinator.playNext(song)
            }
        } else {
            playSongList(listOf(song), 0)
        }
    }

    fun removeFromQueue(song: Song) {
        val current = _queue.value.toMutableList()
        val indexToRemove = current.indexOfFirst { it.id == song.id }
        if (indexToRemove != -1) {
            current.removeAt(indexToRemove)
            _queue.value = current
            mediaController?.removeMediaItem(indexToRemove)
            viewModelScope.launch {
                playbackCoordinator.removeFromQueue(indexToRemove)
            }
        }
    }

    fun loadLyricsForSong(song: Song, context: Context = appContext) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            val repo = LyricsRepository(context)
            _currentLyrics.value = repo.getLyricsForSong(song)
            _isLoadingLyrics.value = false
        }
    }

    fun setLyricsOffset(offset: Long) {
        lyricsOffsetMs.value = offset
    }

    fun saveLyricsOffset() {
        _currentSong.value?.let {
            viewModelScope.launch { dbRepo.saveLyricOffset(it.id, lyricsOffsetMs.value) }
        }
    }

    fun updateEqBand(index: Int, level: Float) {
        val clampedLevel = level.coerceIn(-12.0f, 12.0f)
        val newBands = _eqBands.value.toMutableList()
        if (index in 0 until newBands.size) {
            newBands[index] = clampedLevel
            _eqBands.value = newBands

            val args = Bundle().apply {
                putShort("band", index.toShort())
                putShort("level", (clampedLevel * 100).toInt().toShort())
                putFloat("levelDb", clampedLevel)
            }
            mediaController?.sendCustomCommand(SessionCommand("SET_EQ_BAND", Bundle.EMPTY), args)
        }
    }

    fun saveEqPreset(name: String) = viewModelScope.launch {
        dbRepo.saveEqPreset(name, _eqBands.value)
    }

    fun applyEqPreset(preset: EqPreset) {
        val presetBands = EqPresetDefinitions.parseBands(preset.bands)
        presetBands.forEachIndexed { index, level -> updateEqBand(index, level) }
        val args = Bundle().apply {
            putString("presetName", preset.name)
            putFloatArray("bands", presetBands)
        }
        mediaController?.sendCustomCommand(SessionCommand("SET_EQ_CONFIG", Bundle.EMPTY), args)
    }

    private fun resolveAutoEq(song: Song, genreId3: String) {
        if (appSettings.value?.autoEq != true) return
        val text = "${song.title} ${song.artist} ${song.album} $genreId3".lowercase()
        val preset = when {
            text.contains("rock") || text.contains("metal") || text.contains("punk") -> defaultEqPresets.find { it.name == "Rock" }
            text.contains("lo-fi") || text.contains("chill") || text.contains("ambient") -> defaultEqPresets.find { it.name == "Lo-Fi" }
            text.contains("rap") || text.contains("hip hop") || text.contains("trap") -> defaultEqPresets.find { it.name == "Rap" }
            text.contains("reggae") || text.contains("dub") || text.contains("ska") -> defaultEqPresets.find { it.name == "Reggae" }
            text.contains("classical") || text.contains("orchestra") || text.contains("symphony") -> defaultEqPresets.find { it.name == "Classical" }
            text.contains("dance") || text.contains("club") -> defaultEqPresets.find { it.name == "Dance" }
            text.contains("electronic") || text.contains("techno") || text.contains("house") || text.contains("edm") -> defaultEqPresets.find { it.name == "Electronic" }
            text.contains("jazz") || text.contains("blues") -> defaultEqPresets.find { it.name == "Jazz" }
            text.contains("pop") || text.contains("top 40") -> defaultEqPresets.find { it.name == "Pop" }
            text.contains("r&b") || text.contains("soul") || text.contains("funk") -> defaultEqPresets.find { it.name == "R&B" }
            text.contains("acoustic") || text.contains("folk") || text.contains("indie") -> defaultEqPresets.find { it.name == "Acoustic" }
            text.contains("latin") || text.contains("salsa") || text.contains("reggaeton") -> defaultEqPresets.find { it.name == "Latin" }
            text.contains("vocal") || text.contains("podcast") || text.contains("speech") -> defaultEqPresets.find { it.name == "Vocal Booster" }
            else -> defaultEqPresets.find { it.name == "Flat" }
        }
        preset?.let { applyEqPreset(it) }
    }

    fun updateNotificationPrefs(showLike: Boolean, showShuffle: Boolean, showRepeat: Boolean) {
        val args = Bundle().apply {
            putBoolean("showLike", showLike)
            putBoolean("showShuffle", showShuffle)
            putBoolean("showRepeat", showRepeat)
        }
        mediaController?.sendCustomCommand(SessionCommand("SET_NOTIFICATION_PREFS", Bundle.EMPTY), args)
    }

    fun saveSettings(setting: AppSetting) {
        viewModelScope.launch {
            dbRepo.saveSettings(setting)
            updateNotificationPrefs(setting.showLike, setting.showShuffle, setting.showRepeat)
            val crossfadeArgs = Bundle().apply {
                putBoolean("enabled", setting.crossfadeEnabled)
                putBoolean("autoMixEnabled", setting.autoMixEnabled)
            }
            mediaController?.sendCustomCommand(SessionCommand("SET_CROSSFADE_CONFIG", Bundle.EMPTY), crossfadeArgs)
            val passthroughArgs = Bundle().apply {
                putBoolean("bitPerfectPassthrough", setting.bitPerfectPassthrough)
            }
            mediaController?.sendCustomCommand(SessionCommand("SET_PASSTHROUGH_CONFIG", Bundle.EMPTY), passthroughArgs)
            if (setting.autoEq) {
                val mediaItem = mediaController?.currentMediaItem
                val genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
                _currentSong.value?.let { resolveAutoEq(it, genre) }
            }
        }
    }

    private fun processTrackTransition(currentTrack: Song, context: Context, mediaItem: MediaItem?) {
        viewModelScope.launch { dbRepo.recordPlay(currentTrack.id) }
        viewModelScope.launch {
            val savedOffset = dbRepo.getLyricOffset(currentTrack.id) ?: 0L
            lyricsOffsetMs.value = savedOffset
        }
        loadLyricsForSong(currentTrack, context)
        val id3Genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
        resolveAutoEq(currentTrack, id3Genre)
    }

    fun getCurrentPosition(): Long = _currentPosition.value

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
