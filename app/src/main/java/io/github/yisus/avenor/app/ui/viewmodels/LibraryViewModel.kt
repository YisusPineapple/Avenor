package io.github.yisus.avenor.app.ui.viewmodels

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yisus.avenor.AppDatabase
import io.github.yisus.avenor.AppSetting
import io.github.yisus.avenor.AudioRepository
import io.github.yisus.avenor.DatabaseRepository
import io.github.yisus.avenor.PlaybackService
import io.github.yisus.avenor.Playlist
import io.github.yisus.avenor.ScanProgress
import io.github.yisus.avenor.Song
import io.github.yisus.avenor.SongSortOrder
import io.github.yisus.avenor.TopArtistResult
import io.github.yisus.avenor.app.data.repository.LocalAudioRepository
import io.github.yisus.avenor.app.data.scanner.ScannedAudioItem
import io.github.yisus.avenor.playback.PlaybackCoordinator
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LocalAudioRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val dbRepo = DatabaseRepository(AppDatabase.getDatabase(appContext).musicDao())
    val audioRepo = AudioRepository(appContext, dbRepo.dao)
    val playbackCoordinator = PlaybackCoordinator.getInstance(appContext)
    val playbackState = playbackCoordinator.playbackState

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _audioList = MutableStateFlow<List<ScannedAudioItem>>(emptyList())
    val audioList: StateFlow<List<ScannedAudioItem>> = _audioList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val scanProgress: StateFlow<ScanProgress> = audioRepo.scanProgress
    val songsCount: StateFlow<Int> = dbRepo.songsCount.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val history: StateFlow<List<Song>> = dbRepo.recentHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val playlists: StateFlow<List<Playlist>> = dbRepo.allPlaylists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dailyMix: StateFlow<List<Song>> = dbRepo.dailyMix.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favoriteSongs: StateFlow<List<Song>> = dbRepo.favoriteSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favoriteSongIds: StateFlow<List<Long>> = dbRepo.favoriteSongIds.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val topSongs: StateFlow<List<Song>> = dbRepo.topSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val topArtist: StateFlow<TopArtistResult?> = dbRepo.topArtist.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val totalListeningTimeMs: StateFlow<Long?> = dbRepo.totalListeningTimeMs.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val appSettings: StateFlow<AppSetting?> = dbRepo.appSettings.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SongSortOrder.TITLE)
    val sortOrder: StateFlow<SongSortOrder> = _sortOrder.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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

    fun getSongsForPlaylist(playlistId: Int): Flow<List<Song>> = dbRepo.getSongsForPlaylist(playlistId)

    fun getPagedSongsForSelection(query: String): Flow<PagingData<Song>> {
        return Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 10, enablePlaceholders = false)
        ) {
            if (query.isNotBlank()) {
                dbRepo.searchPagedSongs(query.trim())
            } else {
                dbRepo.getPagedSongs(SongSortOrder.TITLE)
            }
        }.flow.cachedIn(viewModelScope)
    }

    init {
        connectMediaController()
    }

    private fun connectMediaController() {
        try {
            val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            val future = MediaController.Builder(appContext, sessionToken).buildAsync()
            mediaControllerFuture = future
            viewModelScope.launch {
                try {
                    mediaController = future.await()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    fun updateSortOrder(order: SongSortOrder) {
        _sortOrder.value = order
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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

    fun loadSongs(context: Context = appContext) {
        viewModelScope.launch {
            audioRepo.scanLibrary()
        }
    }

    fun playFromLibrary(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentOrder = _sortOrder.value
            val currentQuery = _searchQuery.value
            val contextSongs = dbRepo.getPlaybackContextSongs(currentOrder, currentQuery)
            val startIndex = contextSongs.indexOfFirst { it.id == song.id }.let {
                if (it >= 0) it else 0
            }
            val targetList = if (contextSongs.isNotEmpty()) contextSongs else listOf(song)
            withContext(Dispatchers.Main) {
                playSongList(targetList, startIndex)
            }
        }
    }

    fun playSong(song: Song) {
        playFromLibrary(song)
    }

    fun playSongList(songList: List<Song>, startIndex: Int) {
        val validIndex = if (startIndex in songList.indices) startIndex else 0
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

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            dbRepo.toggleFavorite(songId)
        }
    }

    fun renameSong(id: Long, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dbRepo.dao.renameSong(id, newTitle)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            dbRepo.createPlaylist(name)
        }
    }

    fun addSongToPlaylist(playlistId: Int, songId: Long) {
        viewModelScope.launch {
            dbRepo.addSongToPlaylist(playlistId, songId)
        }
    }

    fun addSongsToPlaylist(playlistId: Int, songIds: Collection<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            songIds.forEach { songId ->
                dbRepo.addSongToPlaylist(playlistId, songId)
            }
        }
    }

    suspend fun emptyTrashSecurely(context: Context = appContext) {
        val allItems = dbRepo.dao.getExpiredTrashItems(Long.MAX_VALUE)
        allItems.forEach { item ->
            try {
                val uri = Uri.parse(dbRepo.dao.getSongById(item.songId)?.uri ?: "")
                if (uri.scheme == "file") {
                    val file = File(uri.path!!)
                    if (file.exists()) file.delete()
                } else if (uri.scheme == "content") {
                    context.contentResolver.delete(uri, null, null)
                }
            } catch (_: Exception) {
            }
        }
        dbRepo.dao.clearTrashItems()
    }

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

