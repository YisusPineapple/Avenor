package io.github.yisus.avenor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.compose.LocalImageLoader
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce

@dagger.hilt.android.lifecycle.HiltViewModel
class PlaybackViewModel @javax.inject.Inject constructor(application: Application) : AndroidViewModel(application) {
private var controllerFuture: ListenableFuture<MediaController>? = null
private var controller: MediaController? = null

val dbRepo = DatabaseRepository(AppDatabase.getDatabase(application).musicDao())
val audioRepo = AudioRepository(application, dbRepo.dao)
val scanProgress: StateFlow<ScanProgress> = audioRepo.scanProgress
val songsCount: StateFlow<Int> = dbRepo.songsCount.stateIn(viewModelScope, SharingStarted.Lazily, 0)
val playbackCoordinator = io.github.yisus.avenor.playback.PlaybackCoordinator.getInstance(application)
val playbackState = playbackCoordinator.playbackState

init {
    viewModelScope.launch {
        playbackCoordinator.restorePersistedState()
    }
    viewModelScope.launch {
        playbackCoordinator.playbackState.collect { state ->
            _queue.value = state.queue
            currentPlayingList = state.queue
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
}

val songs: StateFlow<List<Song>> = dbRepo.allSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val history: StateFlow<List<Song>> = dbRepo.recentHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val playlists: StateFlow<List<Playlist>> = dbRepo.allPlaylists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val dailyMix: StateFlow<List<Song>> = dbRepo.dailyMix.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val eqPresets: StateFlow<List<EqPreset>> = dbRepo.eqPresets.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val appSettings: StateFlow<AppSetting?> = dbRepo.appSettings.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val topSongs: StateFlow<List<Song>> = dbRepo.topSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val topArtist: StateFlow<TopArtistResult?> = dbRepo.topArtist.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val totalListeningTimeMs: StateFlow<Long?> = dbRepo.totalListeningTimeMs.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val favoriteSongs: StateFlow<List<Song>> = dbRepo.favoriteSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favoriteSongIds: StateFlow<List<Long>> = dbRepo.favoriteSongIds.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentLyrics = MutableStateFlow<List<io.github.yisus.avenor.lyrics.LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<io.github.yisus.avenor.lyrics.LyricLine>> = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            dbRepo.toggleFavorite(songId)
        }
    }

    fun loadLyricsForSong(song: Song, context: android.content.Context) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            val repo = io.github.yisus.avenor.lyrics.LyricsRepository(context)
            _currentLyrics.value = repo.getLyricsForSong(song)
            _isLoadingLyrics.value = false
        }
    }


private val _isPlaying = MutableStateFlow(false)
val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

private val _currentSong = MutableStateFlow<Song?>(null)
val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

private val _currentPosition = MutableStateFlow(0L)
    val lyricsOffsetMs = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentMetadata = MutableStateFlow<String?>(null)
    val currentMetadata: StateFlow<String?> = _currentMetadata.asStateFlow()

private val _isShuffleEnabled = MutableStateFlow(false)
val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

private val _sleepTimerActive = MutableStateFlow(false)
val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()
private var sleepTimerJob: Job? = null

private var currentPlayingList: List<Song> = emptyList()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SongSortOrder.TITLE)
    val sortOrder: StateFlow<SongSortOrder> = _sortOrder.asStateFlow()

    fun updateSortOrder(order: SongSortOrder) {
        _sortOrder.value = order
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedSongs: Flow<PagingData<Song>> = combine(_sortOrder, _searchQuery.debounce(200)) { sort, query ->
        sort to query
    }.flatMapLatest { (sort, query) ->
        if (query.isNotBlank()) {
            Pager(PagingConfig(pageSize = 50, prefetchDistance = 20, enablePlaceholders = false)) {
                dbRepo.searchPagedSongs(query.trim())
            }.flow
        } else {
            Pager(PagingConfig(pageSize = 50, prefetchDistance = 20, enablePlaceholders = false)) {
                dbRepo.getPagedSongs(sort)
            }.flow
        }
    }.cachedIn(viewModelScope)

    val pagedFavorites: Flow<PagingData<Song>> = Pager(
        PagingConfig(pageSize = 50, prefetchDistance = 20, enablePlaceholders = false)
    ) {
        dbRepo.getPagedFavorites()
    }.flow.cachedIn(viewModelScope)

    fun getPagedSongsForPlaylist(playlistId: Int): Flow<PagingData<Song>> {
        return Pager(
            PagingConfig(pageSize = 50, prefetchDistance = 20, enablePlaceholders = false)
        ) {
            dbRepo.getPagedSongsForPlaylist(playlistId)
        }.flow.cachedIn(viewModelScope)
    }

    val filteredSongs = combine(songs, _searchQuery) { list, query ->
        SearchOptimizer.filterSongs(list, query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    
    private val _selectedSongs = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongs: StateFlow<Set<Long>> = _selectedSongs.asStateFlow()

    fun toggleSelection(songId: Long) {
        val current = _selectedSongs.value.toMutableSet()
        if (current.contains(songId)) current.remove(songId) else current.add(songId)
        _selectedSongs.value = current
    }

    fun clearSelection() { _selectedSongs.value = emptySet() }
    
    fun enqueueSelected() {
        val songsToAdd = songs.value.filter { _selectedSongs.value.contains(it.id) }
        val current = _queue.value.toMutableList()
        current.addAll(songsToAdd)
        _queue.value = current
        currentPlayingList = current
        songsToAdd.forEach { song ->
            val mediaItem = androidx.media3.common.MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()).build()
            controller?.addMediaItem(mediaItem)
        }
        viewModelScope.launch {
            playbackCoordinator.enqueueAll(songsToAdd)
        }
        clearSelection()
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun playNext(song: Song) {
        val current = _queue.value.toMutableList()
        val currentIndex = controller?.currentMediaItemIndex ?: 0
        if (current.isNotEmpty() && currentIndex >= 0 && currentIndex < current.size) {
            current.add(currentIndex + 1, song)
            _queue.value = current
            currentPlayingList = current
            val mediaItem = androidx.media3.common.MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()).build()
            controller?.addMediaItem(currentIndex + 1, mediaItem)
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
            currentPlayingList = current
            controller?.removeMediaItem(indexToRemove)
            viewModelScope.launch {
                playbackCoordinator.removeFromQueue(indexToRemove)
            }
        }
    }

    fun enqueue(song: Song) {
        val current = _queue.value.toMutableList()
        if (current.isNotEmpty()) {
            current.add(song)
            _queue.value = current
            currentPlayingList = current
            val mediaItem = androidx.media3.common.MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()).build()
            controller?.addMediaItem(mediaItem)
            viewModelScope.launch {
                playbackCoordinator.enqueue(song)
            }
        } else {
            playSongList(listOf(song), 0)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        controller?.moveMediaItem(fromIndex, toIndex)
        viewModelScope.launch {
            playbackCoordinator.reorderQueue(fromIndex, toIndex)
        }
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        viewModelScope.launch {
            playbackCoordinator.clearQueue()
        }
    }


private val _eqBands = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f))
val eqBands: StateFlow<List<Float>> = _eqBands.asStateFlow()

val defaultEqPresets = listOf(
EqPreset(-1, "Flat", "0,0,0,0,0"),
EqPreset(-2, "Rock", "4,2,-2,3,5"),
EqPreset(-3, "Lo-Fi", "5,4,-1,-3,-5"),
EqPreset(-4, "Reggae", "5,3,0,2,1"),
EqPreset(-5, "Rap", "6,2,-1,2,4"),
EqPreset(-6, "Classical", "5,3,-2,4,4"),
EqPreset(-7, "Dance", "6,0,2,4,1"),
EqPreset(-8, "Electronic", "4,3,-1,2,5"),
EqPreset(-9, "Jazz", "4,2,-2,2,3"),
EqPreset(-10, "Pop", "-1,2,5,1,-2"),
EqPreset(-11, "R&B", "3,1,0,3,3"),
EqPreset(-12, "Acoustic", "4,1,2,3,3"),
EqPreset(-13, "Bass Boost", "8,5,0,0,0"),
EqPreset(-14, "Treble Boost", "0,0,0,5,8"),
EqPreset(-15, "Latin", "5,2,-1,3,4"),
EqPreset(-16, "Vocal Booster", "-2,-1,5,3,-1")
)

fun updateEqBand(index: Int, level: Float) {
val newBands = _eqBands.value.toMutableList()
newBands[index] = level
_eqBands.value = newBands

val args = Bundle().apply {
putShort("band", index.toShort())
putShort("level", (level * 100).toInt().toShort())
}
controller?.sendCustomCommand(SessionCommand("SET_EQ_BAND", Bundle.EMPTY), args)
}

fun saveEqPreset(name: String) = viewModelScope.launch { dbRepo.saveEqPreset(name, _eqBands.value) }

fun applyEqPreset(preset: EqPreset) {
val presetBands = preset.bands.split(",").map { it.toFloatOrNull() ?: 0f }
presetBands.forEachIndexed { index, level -> updateEqBand(index, level) }
}

// Auto-EQ now checks MediaMetadata Genre from ID3 tags extracted natively by ExoPlayer!
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

fun initController(context: android.content.Context) {
val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

viewModelScope.launch {
controller = controllerFuture?.await()
controller?.addListener(object : Player.Listener {
override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
val mediaId = mediaItem?.mediaId
val song = currentPlayingList.find { it.id.toString() == mediaId } ?: songs.value.find { it.id.toString() == mediaId }
_currentSong.value = song

song?.let { currentTrack ->
    viewModelScope.launch { dbRepo.recordPlay(currentTrack.id) }
    viewModelScope.launch {
        val savedOffset = dbRepo.getLyricOffset(currentTrack.id) ?: 0L
        lyricsOffsetMs.value = savedOffset
    }
    loadLyricsForSong(currentTrack, context)

    // Extract Genre from ID3 Metadata provided by ExoPlayer
    val id3Genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
    resolveAutoEq(currentTrack, id3Genre)
}
}
override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { _isShuffleEnabled.value = shuffleModeEnabled }
override fun onRepeatModeChanged(repeatMode: Int) { _repeatMode.value = repeatMode }
})
// Apply Initial Flat EQ state to service
_eqBands.value.forEachIndexed { index, level -> updateEqBand(index, level) }

// Sync Notification Prefs
appSettings.value?.let { s ->
updateNotificationPrefs(s.showLike, s.showShuffle, s.showRepeat)
}
}
}

fun loadSongs(context: android.content.Context) {
    viewModelScope.launch {
        audioRepo.scanLibrary()
    }
}

fun playSong(song: Song) {
    playSongList(listOf(song), 0)
}

fun playSongList(songList: List<Song>, startIndex: Int) {
currentPlayingList = songList
_queue.value = songList
val mediaItems = songList.map { song ->
MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
.setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()).build()
}
controller?.setMediaItems(mediaItems)
controller?.seekToDefaultPosition(startIndex)
controller?.prepare()
controller?.play()
viewModelScope.launch {
    playbackCoordinator.saveFullQueue(
        songs = songList,
        currentIndex = startIndex,
        positionMs = 0L,
        isPlaying = true,
        shuffleMode = _isShuffleEnabled.value,
        repeatMode = _repeatMode.value
    )
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

    fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }

    suspend fun emptyTrashSecurely(context: android.content.Context) {
        val allItems = dbRepo.dao.getExpiredTrashItems(Long.MAX_VALUE)
        allItems.forEach { item ->
            try {
                val uri = android.net.Uri.parse(dbRepo.dao.getSongById(item.songId.toInt())?.uri ?: "")
                if (uri.scheme == "file") {
                    val file = java.io.File(uri.path!!)
                    if (file.exists()) file.delete()
                } else if (uri.scheme == "content") {
                    context.contentResolver.delete(uri, null, null)
                }
            } catch(e: Exception) { }
        }
        dbRepo.dao.clearTrashItems()
    }

    fun renameSong(id: Long, newTitle: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dbRepo.dao.renameSong(id.toInt(), newTitle)
        }
    }

    fun renameSong(id: Int, newTitle: String) = renameSong(id.toLong(), newTitle)

fun skipToNext() { controller?.seekToNextMediaItem() }
fun skipToPrevious() { controller?.seekToPreviousMediaItem() }
fun seekTo(positionMs: Long) {
    controller?.seekTo(positionMs)
    _currentPosition.value = positionMs
    viewModelScope.launch {
        playbackCoordinator.updatePosition(positionMs, force = true)
    }
}

fun toggleShuffle() {
    controller?.let {
        val newShuffle = !it.shuffleModeEnabled
        it.shuffleModeEnabled = newShuffle
        _isShuffleEnabled.value = newShuffle
        viewModelScope.launch {
            playbackCoordinator.updatePlaybackParams(shuffleMode = newShuffle)
        }
    }
}

fun toggleRepeat() {
    controller?.let {
        val newRepeat = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        it.repeatMode = newRepeat
        _repeatMode.value = newRepeat
        viewModelScope.launch {
            playbackCoordinator.updatePlaybackParams(repeatMode = newRepeat)
        }
    }
}

fun setSleepTimer(minutes: Int) {
sleepTimerJob?.cancel()
if (minutes > 0) {
_sleepTimerActive.value = true
sleepTimerJob = viewModelScope.launch { delay(minutes * 60 * 1000L); controller?.pause(); _sleepTimerActive.value = false }
} else { _sleepTimerActive.value = false }
}

fun updateNotificationPrefs(showLike: Boolean, showShuffle: Boolean, showRepeat: Boolean) {
val args = Bundle().apply {
putBoolean("showLike", showLike)
putBoolean("showShuffle", showShuffle)
putBoolean("showRepeat", showRepeat)
}
controller?.sendCustomCommand(SessionCommand("SET_NOTIFICATION_PREFS", Bundle.EMPTY), args)
}

fun saveSettings(setting: AppSetting) {
viewModelScope.launch {
dbRepo.saveSettings(setting)
updateNotificationPrefs(setting.showLike, setting.showShuffle, setting.showRepeat)
if (setting.autoEq) {
val mediaItem = controller?.currentMediaItem
val genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
currentSong.value?.let { resolveAutoEq(it, genre) }
}
}
}

fun createPlaylist(name: String) = viewModelScope.launch { dbRepo.createPlaylist(name) }
fun addSongToPlaylist(playlistId: Int, songId: Long) = viewModelScope.launch { dbRepo.addSongToPlaylist(playlistId, songId) }
fun updatePosition() { controller?.let { _currentPosition.value = it.currentPosition } }

override fun onCleared() {
super.onCleared()
controllerFuture?.let { MediaController.releaseFuture(it) }
}
}