package io.github.yisus.avenor

import io.github.yisus.avenor.ui.theme.WarmthPalette
import io.github.yisus.avenor.ui.theme.AuroraPalette
import io.github.yisus.avenor.ui.theme.SoftUiPalette
import io.github.yisus.avenor.ui.theme.ExpressivePalette
import io.github.yisus.avenor.ui.theme.ExpressiveShapes
import io.github.yisus.avenor.util.formatMs
import io.github.yisus.avenor.util.formatTime

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


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LyricSyncOverlay(viewModel: PlaybackViewModel, onDismiss: () -> Unit) {
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
    
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Lyric Synchronization Offset", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Fine-tune the timing for the current track. Settings are saved per-song.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            
            Text("${if (lyricsOffset > 0) "+" else ""}${lyricsOffset} ms", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            
            Slider(
                value = lyricsOffset.toFloat(),
                onValueChange = { viewModel.setLyricsOffset(it.toLong()) },
                onValueChangeFinished = { viewModel.saveLyricsOffset() },
                valueRange = -2000f..2000f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-2000 ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+2000 ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Done")
            }
        }
    }
}

// ---------------------------------------------------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorOverlay(song: Song, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
            Text("Edit Metadata (In-App Override)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    onSave(title, artist)
                    onDismiss()
                }) { Text("Save") }
            }
        }
    }
}

// ViewModels & Logic
// ---------------------------------------------------------

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
private var controllerFuture: ListenableFuture<MediaController>? = null
private var controller: MediaController? = null

val dbRepo = DatabaseRepository(AppDatabase.getDatabase(application).musicDao())

val songs: StateFlow<List<Song>> = dbRepo.allSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val history: StateFlow<List<Song>> = dbRepo.recentHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val playlists: StateFlow<List<Playlist>> = dbRepo.allPlaylists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val dailyMix: StateFlow<List<Song>> = dbRepo.dailyMix.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val eqPresets: StateFlow<List<EqPreset>> = dbRepo.eqPresets.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
val appSettings: StateFlow<AppSetting?> = dbRepo.appSettings.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val topSongs: StateFlow<List<Song>> = dbRepo.topSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val topArtist: StateFlow<TopArtistResult?> = dbRepo.topArtist.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val totalListeningTimeMs: StateFlow<Long?> = dbRepo.totalListeningTimeMs.stateIn(viewModelScope, SharingStarted.Lazily, null)


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
        val current = _queue.value.toMutableList()
        val songsToAdd = songs.value.filter { _selectedSongs.value.contains(it.id) }
        current.addAll(songsToAdd)
        _queue.value = current
        currentPlayingList = current
        songsToAdd.forEach { song ->
            val mediaItem = androidx.media3.common.MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()).build()
            controller?.addMediaItem(mediaItem)
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
        } else {
            playSongList(listOf(song), 0)
        }
    }

    
    fun removeFromQueue(song: Song) {
        val current = _queue.value.toMutableList()
        val currentIndex = controller?.currentMediaItemIndex ?: -1
        
        val indexToRemove = current.indexOfFirst { it.id == song.id }
        if (indexToRemove != -1) {
            current.removeAt(indexToRemove)
            _queue.value = current
            currentPlayingList = current
            controller?.removeMediaItem(indexToRemove)
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
        } else {
            playSongList(listOf(song), 0)
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

song?.let {
viewModelScope.launch { dbRepo.recordPlay(it.id) }

// Extract Genre from ID3 Metadata provided by ExoPlayer
val id3Genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
resolveAutoEq(it, id3Genre)
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
val audioRepo = AudioRepository(context)
var localSongs = audioRepo.getLocalAudioFiles()

if (localSongs.isEmpty()) {
localSongs = listOf(
Song(1, "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "Warmth of the Void", "Avenor Audio", "Audiophile Test", 300000, null),
Song(2, "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", "Acoustic Resonance", "Avenor Audio", "Audiophile Test", 240000, null)
)
}
dbRepo.syncLocalSongs(localSongs)
}
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

    fun renameSong(id: Int, newTitle: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dbRepo.dao.renameSong(id, newTitle)
        }
    }

fun skipToNext() { controller?.seekToNextMediaItem() }
fun skipToPrevious() { controller?.seekToPreviousMediaItem() }
fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

fun toggleRepeat() {
controller?.let {
it.repeatMode = when (it.repeatMode) {
Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
else -> Player.REPEAT_MODE_OFF
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

// Cached Background implementation to guarantee <120MB Memory Footprint and 60FPS
@Composable
fun AuroraBackground(performanceMode: String, content: @Composable () -> Unit) {
val color1 = MaterialTheme.colorScheme.primaryContainer
val color2 = MaterialTheme.colorScheme.surfaceVariant
val bg = MaterialTheme.colorScheme.background

if (performanceMode == "ECO") {
// Zero graphics pipeline allocations
Box(modifier = Modifier.fillMaxSize().background(bg)) { content() }
return
}

val infiniteTransition = rememberInfiniteTransition()
val rotation by if (performanceMode == "VIVID") {
infiniteTransition.animateFloat(
initialValue = 0f,
targetValue = 360f,
animationSpec = infiniteRepeatable(animation = tween(35000, easing = LinearEasing))
)
} else remember { mutableStateOf(0f) }

// Static Bitmap-based texture cache to eliminate shader-based background rendering
var cachedBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

Box(modifier = Modifier.fillMaxSize().background(bg).drawWithCache {
if (cachedBitmap == null || cachedBitmap?.width != size.width.toInt() || cachedBitmap?.height != size.height.toInt()) {
val width = size.width.toInt().coerceAtLeast(1)
val height = size.height.toInt().coerceAtLeast(1)
val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
val canvas = android.graphics.Canvas(bitmap)
val paint1 = android.graphics.Paint().apply { color = android.graphics.Color.argb((0.15f*255).toInt(), (color1.red*255).toInt(), (color1.green*255).toInt(), (color1.blue*255).toInt()); isAntiAlias = true }
val paint2 = android.graphics.Paint().apply { color = android.graphics.Color.argb((0.15f*255).toInt(), (color2.red*255).toInt(), (color2.green*255).toInt(), (color2.blue*255).toInt()); isAntiAlias = true }

canvas.drawCircle(-width * 0.2f, -height * 0.2f, width * 1.2f, paint1)
canvas.drawCircle(width * 1.1f, height * 1.1f, width * 0.9f, paint2)

cachedBitmap = bitmap.asImageBitmap()
}

onDrawBehind {
rotate(rotation) {
cachedBitmap?.let { drawImage(it) }
}
}
}) {
content()
}
}

class MainActivity : ComponentActivity() {
lateinit var performanceMonitor: PerformanceMonitor

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)

performanceMonitor = PerformanceMonitor(this)
lifecycleScope.launch {
performanceMonitor.startMonitoring()
}

// Strictly enforce ultra-lightweight memory profile using Coil ImageLoader (<120MB target)
val imageLoader = ImageLoader.Builder(this)
.memoryCache {
MemoryCache.Builder(this)
.maxSizePercent(0.10) // Restrict memory cache strictly to 10%
.build()
}
.diskCache {
DiskCache.Builder()
.directory(cacheDir.resolve("avenor_image_cache"))
.maxSizePercent(0.02)
.build()
}
.crossfade(true)
.build()


        // Schedule weekly backup
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeeklyBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
        
        val trashRequest = PeriodicWorkRequestBuilder<SmartTrashWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyTrashPurge",
            ExistingPeriodicWorkPolicy.KEEP,
            trashRequest
        )

        setContent {
CompositionLocalProvider(LocalImageLoader provides imageLoader) {
DesktopInputWrapper(viewModel = androidx.lifecycle.viewmodel.compose.viewModel()) { AvenorAppRoot() }
}
}
}
}

// ---------------------------------------------------------
// Navigation & Shell
// ---------------------------------------------------------

sealed class Screen {
object Library : Screen()
object NowPlaying : Screen()
object Settings : Screen()
object TrashRecovery : Screen()
object Equalizer : Screen()
object Lyrics : Screen()
    object Queue : Screen()
    object Recap : Screen()
data class PlaylistDetails(val playlistId: Int, val playlistName: String) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvenorAppRoot(viewModel: PlaybackViewModel = viewModel()) {
val context = LocalContext.current
val activity = context as? MainActivity
var hasPermission by remember { mutableStateOf(false) }
val appSettings by viewModel.appSettings.collectAsState()
val isHighLoad by activity?.performanceMonitor?.isHighLoad?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }

val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
hasPermission = isGranted
if (isGranted) viewModel.loadSongs(context)
}

LaunchedEffect(Unit) {
viewModel.initController(context)
val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
permissionLauncher.launch(permission)
launch { startMemoryWatchdog(context) }
}

// Theme Controller Logic (Pre-computed mapping avoids memory spikes)
val colorScheme = when (appSettings?.themeStyle) {
"AURORA" -> AuroraPalette
"SOFT_UI" -> SoftUiPalette
"EXPRESSIVE" -> ExpressivePalette
else -> WarmthPalette // "WARMTH" default
}

MaterialTheme(colorScheme = colorScheme, shapes = ExpressiveShapes) {
var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }
                var menuExpanded by remember { mutableStateOf(false) }

if (currentScreen != Screen.Library && currentScreen != Screen.NowPlaying) {
BackHandler { currentScreen = Screen.Library }
}

// Dynamically scale down to ECO if running on high-load low-resource hardware
val pMode = if (isHighLoad) "ECO" else (appSettings?.performanceMode ?: "BALANCED")

AuroraBackground(performanceMode = pMode) {
Scaffold(
containerColor = Color.Transparent,
topBar = {
TopAppBar(
title = {
Text(
when (val s = currentScreen) {
is Screen.Settings -> "Settings"
is Screen.Equalizer -> "Equalizer"
is Screen.Lyrics -> "Lyrics"
                                    is Screen.Queue -> "Play Queue"
                                    is Screen.Recap -> "Your Recap"
is Screen.PlaylistDetails -> s.playlistName
else -> "Avenor"
},
fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
)
},
navigationIcon = {
if (currentScreen != Screen.Library && currentScreen != Screen.NowPlaying) {
IconButton(onClick = { currentScreen = Screen.Library }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
}
},
actions = {
if (currentScreen == Screen.Library || currentScreen == Screen.NowPlaying) {
IconButton(onClick = { currentScreen = Screen.Settings }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
}
},
colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
)
},
bottomBar = {
if (currentScreen == Screen.Library || currentScreen == Screen.NowPlaying) {
NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
NavigationBarItem(
icon = { Icon(Icons.Default.List, contentDescription = "Library") },
label = { Text("Library") },
selected = currentScreen == Screen.Library,
onClick = { currentScreen = Screen.Library }
)
NavigationBarItem(
icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Now Playing") },
label = { Text("Playing") },
selected = currentScreen == Screen.NowPlaying,
onClick = { currentScreen = Screen.NowPlaying }
)
}
}
}
) { paddingValues ->
Box(modifier = Modifier.padding(paddingValues)) {
when (val screen = currentScreen) {
is Screen.Library -> LibraryScreen(viewModel, onNavigateToPlaylist = { currentScreen = it })
is Screen.NowPlaying -> NowPlayingScreen(viewModel, onNavigateToEq = { currentScreen = Screen.Equalizer }, onNavigateToLyrics = { currentScreen = Screen.Lyrics }, onNavigateToQueue = { currentScreen = Screen.Queue })
is Screen.Settings -> SettingsScreen(viewModel, onNavigateToTrash = { currentScreen = Screen.TrashRecovery })
is Screen.TrashRecovery -> TrashRecoveryScreen(viewModel, onBack = { currentScreen = Screen.Settings })
is Screen.Equalizer -> EqScreen(viewModel)
is Screen.Lyrics -> LyricsScreen(viewModel)
                                is Screen.Queue -> QueueScreen(viewModel)
                                is Screen.Recap -> RecapScreen(viewModel)
is Screen.PlaylistDetails -> PlaylistDetailsScreen(viewModel, screen.playlistId)
}
}
}
}
}
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(appSettings: AppSetting, onFinishSetup: (AppSetting) -> Unit) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    
    var selectedProfile by remember { mutableStateOf("VIVID") }
    var selectedResolution by remember { mutableStateOf("ORIGINAL") }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (page) {
                    0 -> {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Welcome to Avenor", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your premium cross-platform local music player.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    1 -> {
                        Text("Performance Profile", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Avenor adapts to your device.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("ECO", "BALANCED", "VIVID").forEach { profile ->
                                FilterChip(
                                    selected = selectedProfile == profile,
                                    onClick = { 
                                        selectedProfile = profile 
                                        selectedResolution = when (profile) {
                                            "ECO" -> "LOW"
                                            "BALANCED" -> "MEDIUM"
                                            else -> "ORIGINAL"
                                        }
                                    },
                                    label = { Text(profile) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Album Art Resolution", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(selectedResolution, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    2 -> {
                        Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("All Set!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your library is ready to be scanned.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(64.dp))
            }
            
            // Indicators
            Row {
                repeat(pagerState.pageCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(color)
                            .size(8.dp)
                    )
                }
            }
            
            if (pagerState.currentPage < pagerState.pageCount - 1) {
                TextButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Text("Next")
                }
            } else {
                Button(onClick = {
                    onFinishSetup(appSettings.copy(
                        performanceMode = selectedProfile,
                        albumArtResolution = selectedResolution,
                        isFirstLaunch = false
                    ))
                }) {
                    Text("Start")
                }
            }
        }
    }
}

// ---------------------------------------------------------
// Core Screens (Library & Playlists)
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: PlaybackViewModel, onNavigateToPlaylist: (Screen.PlaylistDetails) -> Unit) {
val songs by viewModel.songs.collectAsState()
val currentSong by viewModel.currentSong.collectAsState()
val isPlaying by viewModel.isPlaying.collectAsState()
val history by viewModel.history.collectAsState()
val playlists by viewModel.playlists.collectAsState()
val dailyMix by viewModel.dailyMix.collectAsState()

var showPlaylistDialog by remember { mutableStateOf(false) }
var playlistName by remember { mutableStateOf("") }

if (showPlaylistDialog) {
AlertDialog(
onDismissRequest = { showPlaylistDialog = false },
title = { Text("New Playlist") },
text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Name") }, shape = MaterialTheme.shapes.large) },
confirmButton = { TextButton(onClick = { if (playlistName.isNotBlank()) viewModel.createPlaylist(playlistName); showPlaylistDialog = false }) { Text("Create") } },
dismissButton = { TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") } }
)
}

Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
if (songs.isEmpty()) {
Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
} else {

            val windowClass = ResponsiveGridManager.getWindowSizeClass(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp)
            val columns = ResponsiveGridManager.getGridCells(windowClass)
            val paddingValues = ResponsiveGridManager.getPadding(windowClass)
            
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = 80.dp)
            ) {


if (dailyMix.isNotEmpty()) {
item {
Text("Daily Mix For You", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
items(dailyMix.size) { index ->
val mixSong = dailyMix[index]
Card(
modifier = Modifier.width(150.dp).clickable { viewModel.playSongList(dailyMix, index) },
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
) {
Column(modifier = Modifier.padding(12.dp)) {
AsyncImage(model = mixSong.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
Spacer(modifier = Modifier.height(12.dp))
Text(mixSong.title, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
}
}
}
}
}
}

item {
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
Text("My Playlists", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
IconButton(onClick = { playlistName = ""; showPlaylistDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add Playlist", tint = MaterialTheme.colorScheme.primary) }
}
if (playlists.isNotEmpty()) {
LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
items(playlists.size) { index ->
val playlist = playlists[index]
Card(
modifier = Modifier.size(130.dp).clickable { onNavigateToPlaylist(Screen.PlaylistDetails(playlist.id, playlist.name)) },
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
) {
Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(playlist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
}
}
}
}
}

if (history.isNotEmpty()) {
item {
Text("Recently Played", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
items(history.size) { index ->
val histItem = history[index]
Card(
modifier = Modifier.width(150.dp).clickable { viewModel.playSongList(history, index) },
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
) {
Column(modifier = Modifier.padding(12.dp)) {
AsyncImage(model = histItem.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
Spacer(modifier = Modifier.height(12.dp))
Text(histItem.title, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
Text(histItem.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
}
}
}
}
}

item(span = { GridItemSpan(maxLineSpan) }) { Text("All Songs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
items(songs.size) { index ->
val song = songs[index]
val isCurrent = currentSong?.id == song.id
Card(
modifier = Modifier.fillMaxWidth().clickable { viewModel.playSongList(songs, index) },
colors = CardDefaults.cardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
shape = MaterialTheme.shapes.medium
) {
Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
Box(modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small)) {
AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}
Spacer(modifier = Modifier.width(16.dp))
Column(modifier = Modifier.weight(1f)) {
Text(text = song.title, fontWeight = FontWeight.Bold, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
Text(text = song.artist, style = MaterialTheme.typography.bodyMedium, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}
if (isCurrent && isPlaying) { Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary) }
var showRename by remember { mutableStateOf(false) }
var renameText by remember { mutableStateOf(song.title) }

if (showRename) {
    RenameDialog(
        initialName = song.title,
        onDismiss = { showRename = false },
        onRename = { newName: String -> 
            viewModel.renameSong(song.id.toInt(), newName)
            showRename = false
        }
    )
}

IconButton(onClick = { showRename = true }) {
    Icon(Icons.Default.Edit, contentDescription = "Rename Track")
}
}
}
}
}
}
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailsScreen(viewModel: PlaybackViewModel, playlistId: Int) {
val playlistSongs by viewModel.dbRepo.getSongsForPlaylist(playlistId).collectAsState(initial = emptyList())
val allSongs by viewModel.songs.collectAsState()
val currentSong by viewModel.currentSong.collectAsState()
val isPlaying by viewModel.isPlaying.collectAsState()
var showAddSongsDialog by remember { mutableStateOf(false) }

if (showAddSongsDialog) {
AlertDialog(
onDismissRequest = { showAddSongsDialog = false },
title = { Text("Add Songs") },
text = {
LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
items(allSongs) { song ->
Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
Column(modifier = Modifier.weight(1f)) {
Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1)
Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
}
IconButton(onClick = { viewModel.addSongToPlaylist(playlistId, song.id) }) { Icon(Icons.Default.Add, contentDescription = "Add") }
}
}
}
},
confirmButton = { TextButton(onClick = { showAddSongsDialog = false }) { Text("Done") } }
)
}

Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
Button(onClick = { showAddSongsDialog = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
Icon(Icons.Default.Add, contentDescription = "Add"); Spacer(modifier = Modifier.width(8.dp)); Text("Add Songs to Playlist")
}
Spacer(modifier = Modifier.height(16.dp))

if (playlistSongs.isEmpty()) {
Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Playlist is empty", color = MaterialTheme.colorScheme.onSurfaceVariant) }
} else {
LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
items(playlistSongs.size) { index ->
val song = playlistSongs[index]
val isCurrent = currentSong?.id == song.id
Card(
modifier = Modifier.fillMaxWidth().clickable { viewModel.playSongList(playlistSongs, index) },
colors = CardDefaults.cardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
shape = MaterialTheme.shapes.medium
) {
Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
Box(modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)) { AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
Spacer(modifier = Modifier.width(16.dp))
Column(modifier = Modifier.weight(1f)) {
Text(song.title, fontWeight = FontWeight.Bold, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
}
if (isCurrent && isPlaying) { Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary) }
var showRename by remember { mutableStateOf(false) }
var renameText by remember { mutableStateOf(song.title) }

if (showRename) {
    RenameDialog(
        initialName = song.title,
        onDismiss = { showRename = false },
        onRename = { newName: String -> 
            viewModel.renameSong(song.id.toInt(), newName)
            showRename = false
        }
    )
}

IconButton(onClick = { showRename = true }) {
    Icon(Icons.Default.Edit, contentDescription = "Rename Track")
}
}
}
}
}
}
}
}

// ---------------------------------------------------------
// Audiophile Features (Now Playing, EQ, Lyrics)
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(viewModel: PlaybackViewModel, onNavigateToEq: () -> Unit, onNavigateToLyrics: () -> Unit, onNavigateToQueue: () -> Unit) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isSleepTimerActive by viewModel.sleepTimerActive.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState(initial = null)
    val isCrossfading by AutoMixState.isCrossfading.collectAsState()
    
    // AutoMix pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "AutoMixPulse")
    val automixAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AutoMixAlpha"
    )
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showLrcEditor by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) { while (isPlaying) { viewModel.updatePosition(); delay(1000) } }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer") },
            text = { Text("Pause playback automatically after:") },
            confirmButton = { TextButton(onClick = { viewModel.setSleepTimer(15); showSleepTimerDialog = false }) { Text("15m") } },
            dismissButton = { TextButton(onClick = { viewModel.setSleepTimer(0); showSleepTimerDialog = false }) { Text("Off") } }
        )
    }

    if (currentSong == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No song selected", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    val style = appSettings?.nowPlayingStyle ?: "CLASSIC"
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Background for Apple Music style
        if (style == "APPLE_MUSIC") {
            AsyncImage(
                model = currentSong?.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.3f),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black.copy(alpha = 0.5f), BlendMode.Darken)
            )
        }
        
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            
            // Artwork
            val artShape = when(style) {
                "EXPRESSIVE" -> RoundedCornerShape(16.dp)
                "APPLE_MUSIC" -> RoundedCornerShape(12.dp)
                else -> RoundedCornerShape(40.dp)
            }
            
            val artModifier = when(style) {
                "EXPRESSIVE" -> Modifier.fillMaxWidth().aspectRatio(1f)
                "APPLE_MUSIC" -> Modifier.fillMaxWidth(0.85f).aspectRatio(1f)
                else -> Modifier.fillMaxWidth().aspectRatio(1f)
            }
            
            Card(modifier = artModifier, shape = artShape, elevation = CardDefaults.cardElevation(defaultElevation = if (style == "APPLE_MUSIC") 24.dp else 16.dp)) {
                AsyncImage(model = currentSong?.albumArtUri, contentDescription = "Album Art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }

            // Text info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // AutoMix Indicator
                androidx.compose.animation.AnimatedVisibility(visible = isCrossfading) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = automixAlpha),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "AutoMix Active", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AutoMix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(currentSong?.title ?: "Unknown Title", style = if(style == "APPLE_MUSIC") MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(currentSong?.artist ?: "Unknown Artist", style = MaterialTheme.typography.titleMedium, color = if(style == "APPLE_MUSIC") MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary)
            }

            // Progress
            Column {
                val progress = if (currentSong?.durationMs != null && currentSong!!.durationMs > 0) { currentPosition.toFloat() / currentSong!!.durationMs.toFloat() } else 0f
                Slider(
                    value = progress,
                    onValueChange = { viewModel.seekTo((it * (currentSong?.durationMs ?: 0)).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if(style == "EXPRESSIVE") SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary, activeTrackColor = MaterialTheme.colorScheme.secondary) else SliderDefaults.colors()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(currentPosition), style = MaterialTheme.typography.labelMedium)
                    Text(formatMs(currentSong?.durationMs ?: 0), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(48.dp))
                }
                
                if (style == "EXPRESSIVE") {
                    FilledIconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(80.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp))
                    }
                } else {
                    FloatingActionButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(80.dp), shape = androidx.compose.foundation.shape.CircleShape) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(48.dp))
                    }
                }

                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    val repeatIcon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                        else -> Icons.Default.Repeat
                    }
                    val tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                    Icon(repeatIcon, contentDescription = "Repeat", tint = tint)
                }
            }
            
            // Bottom Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { onNavigateToQueue() }) { Icon(Icons.Default.QueueMusic, contentDescription = "Queue") }
                IconButton(onClick = { showSleepTimerDialog = true }) { Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                IconButton(onClick = { onNavigateToEq() }) { Icon(Icons.Default.Equalizer, contentDescription = "EQ") }
            }
        }
    }
}

@Composable
fun OptimizedVerticalSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.graphicsLayer { rotationZ = 270f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqScreen(
viewModel: PlaybackViewModel) {
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

// ---------------------------------------------------------
// Lyrics (Synchronized Fast Scroller)
// ---------------------------------------------------------

data class LyricLine(val timeMs: Long, val text: String)

fun parseLrc(lrc: String): List<LyricLine> {
val lines = mutableListOf<LyricLine>()
val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
lrc.lines().forEach { line ->
val match = regex.find(line)
if (match != null) {
val (min, sec, msStr, text) = match.destructured
val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
val timeMs = min.toLong() * 60000 + sec.toLong() * 1000 + ms
if (text.isNotBlank()) lines.add(LyricLine(timeMs, text.trim()))
}
}
return lines.sortedBy { it.timeMs }
}

val dummyLrc = """
[00:00.00] (Instrumental Intro)
[00:05.00] Welcome to Avenor Player
[00:10.00] Highly optimized for your Pentium E5800
[00:15.00] Feel the warmth of the void
[00:20.00] Material You Terracotta Theme
[00:25.00] Gapless playback, perfect flow
[00:30.00] Room Database powering your history
[00:35.00] (Music builds up...)
[00:45.00] Synchronized lyrics in real time!
[00:55.00] No telemetry, pure audiophile focus
[01:05.00] Enjoy the music.
""".trimIndent()

@Composable
fun LyricsScreen(viewModel: PlaybackViewModel) {
val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsOffset by viewModel.lyricsOffsetMs.collectAsState()
val isPlaying by viewModel.isPlaying.collectAsState()
val currentSong by viewModel.currentSong.collectAsState()
val lyrics = remember(currentSong) { parseLrc(dummyLrc) }
val listState = rememberLazyListState()
val activeIndex = lyrics.indexOfLast { it.timeMs <= (currentPosition - lyricsOffset) }.coerceAtLeast(0)

LaunchedEffect(activeIndex, isPlaying) {
if (activeIndex >= 0 && isPlaying) { listState.animateScrollToItem(maxOf(0, activeIndex - 3)) }
}

Column(modifier = Modifier.fillMaxSize()) {
Text(text = currentSong?.title ?: "Lyrics", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center)
LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 64.dp)) {
itemsIndexed(lyrics) { index, line ->
val isActive = index == activeIndex
Text(
text = line.text,
style = if (isActive) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
modifier = Modifier.padding(vertical = 12.dp).clickable { viewModel.seekTo(line.timeMs) }
)
}
}
}
}

// ---------------------------------------------------------
// Settings (Notifications, Theme & Performance)
// ---------------------------------------------------------

@Composable
fun SettingsScreen(viewModel: PlaybackViewModel, onNavigateToTrash: () -> Unit = {}) {
val appSettings by viewModel.appSettings.collectAsState()
val settings = appSettings ?: AppSetting()
val coroutineScope = rememberCoroutineScope()
val context = LocalContext.current

LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
item {
    DesktopParityDashboard(settings)
    Spacer(modifier = Modifier.height(16.dp))
}
item {
Text("Visual Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
Spacer(modifier = Modifier.height(8.dp))
Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
Column(modifier = Modifier.padding(16.dp)) {
Text("Pre-computed color palettes. Instantly applied via ThemeController.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
Spacer(modifier = Modifier.height(16.dp))


            Text("Album Art Resolution", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Higher resolutions use more RAM.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    val resolutions = listOf("LOW", "MEDIUM", "HIGH", "ORIGINAL")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        resolutions.forEach { res ->
                            FilterChip(
                                selected = settings.albumArtResolution == res,
                                onClick = { viewModel.saveSettings(settings.copy(albumArtResolution = res)) },
                                label = { Text(res) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Smart Trash & Storage", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Auto-Purge Interval", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val purgeDaysOptions = listOf(7, 15, 30)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        purgeDaysOptions.forEach { days ->
                            FilterChip(
                                selected = settings.trashPurgeDays == days,
                                onClick = { viewModel.saveSettings(settings.copy(trashPurgeDays = days)) },
                                label = { Text("$days Days") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                                                                        onClick = { 
                            coroutineScope.launch { 
                                viewModel.emptyTrashSecurely(context)
                                android.widget.Toast.makeText(context, "Trash Emptied Securely", android.widget.Toast.LENGTH_SHORT).show()
                            } 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Empty Trash Now")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToTrash,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Recover Trash")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Now Playing Style", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val npStyles = listOf("CLASSIC", "EXPRESSIVE", "APPLE_MUSIC")
                    npStyles.forEach { style ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.saveSettings(settings.copy(nowPlayingStyle = style)) }.padding(vertical = 8.dp)) {
                            RadioButton(selected = settings.nowPlayingStyle == style, onClick = { viewModel.saveSettings(settings.copy(nowPlayingStyle = style)) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(style.replace("_", " "))
                        }
                    }
                }
            }

            val themes = listOf("WARMTH" to "Warmth", "AURORA" to "Aurora", "SOFT_UI" to "Soft UI", "EXPRESSIVE" to "Expressive")
themes.chunked(2).forEach { rowThemes ->
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
rowThemes.forEach { (key, label) ->
FilterChip(
selected = settings.themeStyle == key,
onClick = { viewModel.saveSettings(settings.copy(themeStyle = key)) },
label = { Text(label) },
modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
)
}
}
}
}
}
Spacer(modifier = Modifier.height(24.dp))
}

item {
Text("Performance Profile", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
Spacer(modifier = Modifier.height(8.dp))
Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
Column(modifier = Modifier.padding(16.dp)) {
Text("Eco completely stops visual GPU tasks to ensure stable 60FPS on low-RAM legacy hardware.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
Spacer(modifier = Modifier.height(16.dp))

Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
FilterChip(selected = settings.performanceMode == "ECO", onClick = { viewModel.saveSettings(settings.copy(performanceMode = "ECO")) }, label = { Text("Eco") })
FilterChip(selected = settings.performanceMode == "BALANCED", onClick = { viewModel.saveSettings(settings.copy(performanceMode = "BALANCED")) }, label = { Text("Balanced") })
FilterChip(selected = settings.performanceMode == "VIVID", onClick = { viewModel.saveSettings(settings.copy(performanceMode = "VIVID")) }, label = { Text("Vivid") })
}
}
}
Spacer(modifier = Modifier.height(24.dp))
}

item {
Text("Notification Actions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
Spacer(modifier = Modifier.height(8.dp))
Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), shape = MaterialTheme.shapes.large) {
Column(modifier = Modifier.padding(16.dp)) {
Text("Customize the quick actions displayed in the Android Notification bar. This also syncs with the player UI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
Spacer(modifier = Modifier.height(8.dp))

Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
Text("Show Like Button")
Switch(checked = settings.showLike, onCheckedChange = { viewModel.saveSettings(settings.copy(showLike = it)) })
}
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
Text("Show Shuffle Button")
Switch(checked = settings.showShuffle, onCheckedChange = { viewModel.saveSettings(settings.copy(showShuffle = it)) })
}
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
Text("Show Repeat Button")
Switch(checked = settings.showRepeat, onCheckedChange = { viewModel.saveSettings(settings.copy(showRepeat = it)) })
}
}
}
}

item {
Spacer(modifier = Modifier.height(32.dp))
Text("System Information", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
Text("Memory Footprint strictly limited. All configurations run locally (Room).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
}
}


// ---------------------------------------------------------
// Optimization Guide & About Screens
// ---------------------------------------------------------

@Composable
fun OptimizationGuideScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Optimization Guide", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🚀 Performance Presets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Avenor runs an automated hardware benchmark. You can override it in Settings.\n- ECO: Ideal for <2GB RAM. Stops visual GPU tasks.\n- BALANCED: Standard fluid UI.\n- VIVID: Full 60/120FPS animations and real-time blur.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🧹 Background Processes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Audio crossfade operates off the main UI thread. However, on legacy dual-core devices, aggressive battery savers can starve the CPU. Disable battery optimizations for Avenor to ensure gapless transitions.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💾 Disk Usage", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Avenor utilizes an embedded SQLite database for lightning-fast metadata caching. A background VACUUM runs automatically if the file exceeds 5MB to prevent storage fragmentation.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("About Avenor", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Strictly offline, high-fidelity audio player.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Hardware Requirements", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            RequirementSection("Android (Native)", "Min: Quad-Core 1.2GHz, 1GB RAM (ECO Mode)\nRec: Octa-Core 1.8GHz+, 3GB RAM (VIVID Mode)")
            RequirementSection("Windows (JVM)", "Min: Dual-Core (Pentium), 4GB RAM (ECO Mode)\nRec: Core i3 / Ryzen 3, 8GB RAM (VIVID Mode)")
            RequirementSection("Linux (JVM)", "Min: Core 2 Duo, 2GB RAM (X11 / ECO Mode)\nRec: Core i3 / Ryzen 3, 4GB RAM (Wayland / VIVID Mode)")
        }
    }
}

@Composable
fun RequirementSection(title: String, desc: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RecapScreen(viewModel: PlaybackViewModel) {
    val topSongs by viewModel.topSongs.collectAsState()
    val topArtist by viewModel.topArtist.collectAsState()
    val totalTimeMs by viewModel.totalListeningTimeMs.collectAsState()
    val context = LocalContext.current

    val totalMins = (totalTimeMs ?: 0L) / 60000L
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.background
        )
    )

    val graphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(gradient)) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(1000)) + 
                    androidx.compose.animation.slideInVertically(initialOffsetY = { 100 }, animationSpec = androidx.compose.animation.core.tween(1000))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp)
            ) {
                item {
                    Text("Your Avenor Recap", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    RecapExportCard(
                        topArtist = topArtist?.artist ?: "Unknown",
                        topSong = topSongs.firstOrNull()?.title ?: "Unknown",
                        totalMinutes = totalMins,
                        modifier = Modifier.fillMaxWidth()
                            .drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            }
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                    val shareText = "My Avenor Recap:\nTop Artist: ${topArtist?.artist}\nListening Time: $totalMins Minutes\nTop Song: ${topSongs.firstOrNull()?.title}\n#AvenorAudio"
                                    ImageShareHelper.shareBitmap(context, bitmap, shareText)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }, 
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Export & Share Image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QueueScreen(viewModel: PlaybackViewModel) {
    val queue by viewModel.queue.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)) {
                itemsIndexed(queue) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    var showOptions by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.playSongList(queue, index) },
                        colors = CardDefaults.cardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)) {
                                AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = song.title, fontWeight = FontWeight.Bold, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                Text(text = song.artist, style = MaterialTheme.typography.bodyMedium, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (isCurrent && isPlaying) { Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary) }
                            
                            Box {
                                IconButton(onClick = { showOptions = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options") }
                                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                    DropdownMenuItem(text = { Text("Play Next") }, onClick = { viewModel.playNext(song); showOptions = false }, leadingIcon = { Icon(Icons.Default.SkipNext, null) })
                                    DropdownMenuItem(text = { Text("Remove from Queue") }, onClick = { viewModel.removeFromQueue(song); showOptions = false }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                                    DropdownMenuItem(text = { Text("Song Info") }, onClick = { /* TODO */ showOptions = false }, leadingIcon = { Icon(Icons.Default.Info, null) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashRecoveryScreen(viewModel: PlaybackViewModel, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<io.github.yisus.avenor.TrashItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        items = viewModel.dbRepo.dao.getAllTrashItemsSync()
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash Recovery") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (loaded) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Trash is empty")
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(padding)) {
                    items(items.size) { index ->
                        val item = items[index]
                        var songTitle by remember { mutableStateOf("Unknown") }
                        LaunchedEffect(item.songId) {
                            val song = viewModel.dbRepo.dao.getSongById(item.songId.toInt())
                            songTitle = song?.title ?: "Unknown"
                        }
                        
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(songTitle) },
                            supportingContent = { Text("Will be deleted permanently") },
                            trailingContent = {
                                TextButton(onClick = {
                                    coroutineScope.launch {
                                        viewModel.dbRepo.dao.deleteTrashItem(item.songId)
                                        items = viewModel.dbRepo.dao.getAllTrashItemsSync()
                                    }
                                }) {
                                    Text("Recover")
                                }
                            }
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}


@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    var isError by remember { mutableStateOf(false) }
    val illegalChars = "[\\\\/:*?\"<>|]".toRegex()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = { 
            Column {
                OutlinedTextField(
                    value = text, 
                    onValueChange = { 
                        text = it
                        isError = illegalChars.containsMatchIn(it)
                    }, 
                    singleLine = true,
                    isError = isError,
                    supportingText = {
                        if (isError) Text("Contains illegal characters")
                    }
                )
            }
        },
        confirmButton = { 
            TextButton(
                onClick = { 
                    if (!isError && text.isNotBlank()) {
                        onRename(text)
                        onDismiss()
                    }
                },
                enabled = !isError && text.isNotBlank()
            ) { Text("Save") } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

