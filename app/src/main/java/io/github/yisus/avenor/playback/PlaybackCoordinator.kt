package io.github.yisus.avenor.playback

import android.content.Context
import io.github.yisus.avenor.AppDatabase
import io.github.yisus.avenor.MusicDao
import io.github.yisus.avenor.PlaybackQueue
import io.github.yisus.avenor.QueueSong
import io.github.yisus.avenor.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaybackCoordinator(
    private val dao: MusicDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val mutex = Mutex()
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var lastSavedPositionMs: Long = -1L
    private var lastSavedPositionTimestamp: Long = 0L

    companion object {
        const val ACTIVE_QUEUE_ID = 1
        private const val POSITION_PERSIST_INTERVAL_MS = 5000L

        @Volatile
        private var INSTANCE: PlaybackCoordinator? = null

        fun getInstance(context: Context): PlaybackCoordinator {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val instance = PlaybackCoordinator(db.musicDao())
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun restorePersistedState(): PlaybackState = mutex.withLock {
        try {
            val queueRecord = dao.getPlaybackQueueSync(ACTIVE_QUEUE_ID)
            if (queueRecord == null) {
                return@withLock _playbackState.value
            }
            val songs = dao.getSongsForQueueSync(ACTIVE_QUEUE_ID)
            val currentIndex = queueRecord.currentIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
            val currentSong = if (songs.isNotEmpty() && currentIndex in songs.indices) {
                songs[currentIndex]
            } else null

            val restored = PlaybackState(
                queue = songs,
                currentSong = currentSong,
                currentIndex = if (songs.isNotEmpty()) currentIndex else -1,
                currentPositionMs = queueRecord.currentPositionMs,
                durationMs = currentSong?.durationMs ?: 0L,
                isPlaying = false, // Keep paused upon initial restore
                shuffleMode = queueRecord.shuffleMode,
                repeatMode = queueRecord.repeatMode,
                updatedAt = queueRecord.updatedAt,
                stateVersion = System.currentTimeMillis()
            )
            _playbackState.value = restored
            restored
        } catch (e: Exception) {
            android.util.Log.e("PlaybackCoordinator", "Error restoring persisted state", e)
            _playbackState.value
        }
    }

    suspend fun saveFullQueue(
        songs: List<Song>,
        currentIndex: Int,
        positionMs: Long = 0L,
        isPlaying: Boolean = false,
        shuffleMode: Boolean = false,
        repeatMode: Int = 0
    ) = mutex.withLock {
        val currentSong = if (songs.isNotEmpty() && currentIndex in songs.indices) {
            songs[currentIndex]
        } else null

        val newState = PlaybackState(
            queue = songs,
            currentSong = currentSong,
            currentIndex = currentIndex,
            currentPositionMs = positionMs,
            durationMs = currentSong?.durationMs ?: 0L,
            isPlaying = isPlaying,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            updatedAt = System.currentTimeMillis(),
            stateVersion = _playbackState.value.stateVersion + 1
        )
        _playbackState.value = newState

        try {
            val queueRecord = PlaybackQueue(
                id = ACTIVE_QUEUE_ID,
                name = "ACTIVE_QUEUE",
                currentSongId = currentSong?.id,
                currentIndex = currentIndex,
                currentPositionMs = positionMs,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode,
                isPlaying = isPlaying,
                updatedAt = newState.updatedAt
            )
            val queueSongs = songs.mapIndexed { index, song ->
                QueueSong(
                    queueId = ACTIVE_QUEUE_ID,
                    songId = song.id,
                    positionIndex = index
                )
            }
            dao.saveFullQueue(queueRecord, queueSongs)
        } catch (e: Exception) {
            android.util.Log.e("PlaybackCoordinator", "Error saving full queue", e)
        }
    }

    suspend fun enqueue(song: Song) = mutex.withLock {
        val current = _playbackState.value
        val updatedQueue = current.queue.toMutableList().apply { add(song) }
        saveInternal(updatedQueue, current.currentIndex, current.currentPositionMs, current.isPlaying, current.shuffleMode, current.repeatMode)
    }

    suspend fun enqueueAll(songs: List<Song>) = mutex.withLock {
        val current = _playbackState.value
        val updatedQueue = current.queue.toMutableList().apply { addAll(songs) }
        saveInternal(updatedQueue, current.currentIndex, current.currentPositionMs, current.isPlaying, current.shuffleMode, current.repeatMode)
    }

    suspend fun playNext(song: Song) = mutex.withLock {
        val current = _playbackState.value
        val updatedQueue = current.queue.toMutableList()
        val insertIndex = (current.currentIndex + 1).coerceIn(0, updatedQueue.size)
        updatedQueue.add(insertIndex, song)
        saveInternal(updatedQueue, current.currentIndex, current.currentPositionMs, current.isPlaying, current.shuffleMode, current.repeatMode)
    }

    suspend fun removeFromQueue(index: Int) = mutex.withLock {
        val current = _playbackState.value
        if (index !in current.queue.indices) return@withLock

        val updatedQueue = current.queue.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            updatedQueue.isEmpty() -> -1
            index < current.currentIndex -> current.currentIndex - 1
            index == current.currentIndex -> current.currentIndex.coerceAtMost(updatedQueue.size - 1)
            else -> current.currentIndex
        }
        saveInternal(updatedQueue, newIndex, if (index == current.currentIndex) 0L else current.currentPositionMs, current.isPlaying, current.shuffleMode, current.repeatMode)
    }

    suspend fun reorderQueue(fromIndex: Int, toIndex: Int) = mutex.withLock {
        val current = _playbackState.value
        if (fromIndex !in current.queue.indices || toIndex !in current.queue.indices || fromIndex == toIndex) return@withLock

        val updatedQueue = current.queue.toMutableList()
        val item = updatedQueue.removeAt(fromIndex)
        updatedQueue.add(toIndex, item)

        val newCurrentIndex = when (current.currentIndex) {
            fromIndex -> toIndex
            in (fromIndex + 1)..toIndex -> current.currentIndex - 1
            in toIndex until fromIndex -> current.currentIndex + 1
            else -> current.currentIndex
        }
        saveInternal(updatedQueue, newCurrentIndex, current.currentPositionMs, current.isPlaying, current.shuffleMode, current.repeatMode)
    }

    private suspend fun saveInternal(
        songs: List<Song>,
        currentIndex: Int,
        positionMs: Long,
        isPlaying: Boolean,
        shuffleMode: Boolean,
        repeatMode: Int
    ) {
        val currentSong = if (songs.isNotEmpty() && currentIndex in songs.indices) {
            songs[currentIndex]
        } else null

        val newState = PlaybackState(
            queue = songs,
            currentSong = currentSong,
            currentIndex = currentIndex,
            currentPositionMs = positionMs,
            durationMs = currentSong?.durationMs ?: 0L,
            isPlaying = isPlaying,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            updatedAt = System.currentTimeMillis(),
            stateVersion = _playbackState.value.stateVersion + 1
        )
        _playbackState.value = newState

        try {
            val queueRecord = PlaybackQueue(
                id = ACTIVE_QUEUE_ID,
                name = "ACTIVE_QUEUE",
                currentSongId = currentSong?.id,
                currentIndex = currentIndex,
                currentPositionMs = positionMs,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode,
                isPlaying = isPlaying,
                updatedAt = newState.updatedAt
            )
            val queueSongs = songs.mapIndexed { index, song ->
                QueueSong(
                    queueId = ACTIVE_QUEUE_ID,
                    songId = song.id,
                    positionIndex = index
                )
            }
            dao.saveFullQueue(queueRecord, queueSongs)
        } catch (e: Exception) {
            android.util.Log.e("PlaybackCoordinator", "Error saving internal queue", e)
        }
    }

    suspend fun updatePosition(positionMs: Long, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSavedPositionTimestamp < POSITION_PERSIST_INTERVAL_MS || Math.abs(positionMs - lastSavedPositionMs) < 1000)) {
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
            return
        }

        mutex.withLock {
            lastSavedPositionTimestamp = now
            lastSavedPositionMs = positionMs
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
            try {
                dao.updatePlaybackPosition(ACTIVE_QUEUE_ID, positionMs, now)
            } catch (e: Exception) {
                android.util.Log.e("PlaybackCoordinator", "Error updating position in database", e)
            }
        }
    }

    suspend fun updateTrackTransition(
        mediaId: String?,
        currentIndex: Int
    ) = mutex.withLock {
        val currentState = _playbackState.value
        val songId = mediaId?.toLongOrNull()
        val currentSong = currentState.queue.getOrNull(currentIndex)
            ?: currentState.queue.find { it.id == songId }

        val updated = currentState.copy(
            currentSong = currentSong,
            currentIndex = currentIndex,
            currentPositionMs = 0L,
            durationMs = currentSong?.durationMs ?: 0L,
            updatedAt = System.currentTimeMillis(),
            stateVersion = currentState.stateVersion + 1
        )
        _playbackState.value = updated

        try {
            dao.updatePlaybackState(
                queueId = ACTIVE_QUEUE_ID,
                songId = songId,
                currentIndex = currentIndex,
                positionMs = 0L,
                isPlaying = updated.isPlaying,
                shuffleMode = updated.shuffleMode,
                repeatMode = updated.repeatMode,
                updatedAt = updated.updatedAt
            )
        } catch (e: Exception) {
            android.util.Log.e("PlaybackCoordinator", "Error updating track transition", e)
        }
    }

    suspend fun updatePlaybackParams(
        isPlaying: Boolean? = null,
        shuffleMode: Boolean? = null,
        repeatMode: Int? = null
    ) = mutex.withLock {
        val current = _playbackState.value
        val newPlaying = isPlaying ?: current.isPlaying
        val newShuffle = shuffleMode ?: current.shuffleMode
        val newRepeat = repeatMode ?: current.repeatMode

        val updated = current.copy(
            isPlaying = newPlaying,
            shuffleMode = newShuffle,
            repeatMode = newRepeat,
            updatedAt = System.currentTimeMillis(),
            stateVersion = current.stateVersion + 1
        )
        _playbackState.value = updated

        try {
            dao.updatePlaybackState(
                queueId = ACTIVE_QUEUE_ID,
                songId = updated.currentSong?.id,
                currentIndex = updated.currentIndex,
                positionMs = updated.currentPositionMs,
                isPlaying = newPlaying,
                shuffleMode = newShuffle,
                repeatMode = newRepeat,
                updatedAt = updated.updatedAt
            )
        } catch (e: Exception) {
            android.util.Log.e("PlaybackCoordinator", "Error updating playback params", e)
        }
    }

    suspend fun clearQueue() = mutex.withLock {
        _playbackState.value = PlaybackState()
        try {
            dao.clearQueueSongs(ACTIVE_QUEUE_ID)
            dao.insertPlaybackQueue(PlaybackQueue(id = ACTIVE_QUEUE_ID, name = "ACTIVE_QUEUE"))
        } catch (e: Exception) {
            android.util.Log.e("PlaybackCoordinator", "Error clearing queue", e)
        }
    }
}
