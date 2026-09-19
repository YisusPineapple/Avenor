package io.github.yisus.avenor

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

enum class SongSortOrder {
    TITLE,
    ARTIST,
    ALBUM,
    DATE_ADDED,
    DURATION
}

class DatabaseRepository(val dao: MusicDao) {
    val songsCount: Flow<Int> = dao.getSongsCount()
    val allSongs: Flow<List<Song>> = dao.getAllSongs()
    val allPlaylists: Flow<List<Playlist>> = dao.getAllPlaylists()
    val recentHistory: Flow<List<Song>> = dao.getRecentHistory()
    val dailyMix: Flow<List<Song>> = dao.getDailyMix()
    val eqPresets: Flow<List<EqPreset>> = dao.getAllEqPresets()
    val appSettings: Flow<AppSetting?> = dao.getSettings()
    val topSongs: Flow<List<Song>> = dao.getTopSongs()
    val topArtist: Flow<TopArtistResult?> = dao.getTopArtist()
    val totalListeningTimeMs: Flow<Long?> = dao.getTotalListeningTimeMs()
    val favoriteSongs: Flow<List<Song>> = dao.getFavoriteSongs()
    val favoriteSongIds: Flow<List<Long>> = dao.getAllFavoriteSongIds()

    fun getPagedSongs(sortOrder: SongSortOrder = SongSortOrder.TITLE): PagingSource<Int, Song> {
        return when (sortOrder) {
            SongSortOrder.TITLE -> dao.getPagedSongs()
            SongSortOrder.ARTIST -> dao.getPagedSongsByArtist()
            SongSortOrder.ALBUM -> dao.getPagedSongsByAlbum()
            SongSortOrder.DATE_ADDED -> dao.getPagedSongsByDateAdded()
            SongSortOrder.DURATION -> dao.getPagedSongsByDuration()
        }
    }

    fun searchPagedSongs(query: String): PagingSource<Int, Song> = dao.searchPagedSongs(query)

    fun getPagedFavorites(): PagingSource<Int, Song> = dao.getPagedFavorites()

    fun getPagedSongsForPlaylist(playlistId: Int): PagingSource<Int, Song> = dao.getPagedSongsForPlaylist(playlistId)

    fun isFavorite(songId: Long): Flow<Boolean> = dao.isFavorite(songId)
    suspend fun isFavoriteSync(songId: Long): Boolean = dao.isFavoriteSync(songId)

    suspend fun toggleFavorite(songId: Long): Boolean {
        val isFav = dao.isFavoriteSync(songId)
        if (isFav) {
            dao.removeFavorite(songId)
            return false
        } else {
            dao.addFavorite(Favorite(songId = songId))
            return true
        }
    }

    suspend fun reconcileSongs(currentStorageSongs: List<Song>) {
        val existingHeaders = dao.getAllSongHeaders().associateBy { it.id }
        val currentIds = currentStorageSongs.map { it.id }.toSet()
        val toDeleteIds = existingHeaders.keys.filter { !currentIds.contains(it) }

        if (toDeleteIds.isNotEmpty()) {
            toDeleteIds.chunked(500).forEach { batch ->
                dao.deleteSongsByIds(batch)
            }
        }

        val toInsertOrUpdate = currentStorageSongs.filter { song ->
            val existing = existingHeaders[song.id]
            existing == null || existing.dateModified != song.dateModified || existing.fileSize != song.fileSize
        }

        if (toInsertOrUpdate.isNotEmpty()) {
            toInsertOrUpdate.chunked(50).forEach { batch ->
                dao.insertSongs(batch)
            }
        }
    }

    suspend fun syncLocalSongs(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            songs.chunked(50).forEach { batch ->
                dao.insertSongs(batch)
            }
        }
    }
    
    suspend fun recordPlay(songId: Long, skipped: Boolean = false): Long {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeOfDay = when(hour) {
            in 5..11 -> "MORNING"
            in 12..16 -> "AFTERNOON"
            in 17..20 -> "EVENING"
            else -> "NIGHT"
        }
        return dao.insertHistory(ListeningHistory(songId = songId, skipped = skipped, timeOfDay = timeOfDay))
    }
    
    suspend fun updateHistorySkipped(historyId: Long, skipped: Boolean) = dao.updateHistorySkipped(historyId, skipped)
    
    suspend fun createPlaylist(name: String) = dao.insertPlaylist(Playlist(name = name))
    fun getSongsForPlaylist(playlistId: Int): Flow<List<Song>> = dao.getSongsForPlaylist(playlistId)
    suspend fun addSongToPlaylist(playlistId: Int, songId: Long) = dao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = songId))
    suspend fun removeSongFromPlaylist(playlistId: Int, songId: Long) = dao.removeSongFromPlaylist(playlistId, songId)
    suspend fun saveEqPreset(name: String, bands: List<Float>) = dao.insertEqPreset(EqPreset(name = name, bands = bands.joinToString(",")))
    suspend fun saveSettings(setting: AppSetting) = dao.saveSettings(setting)
    
    suspend fun saveLyricOffset(songId: Long, offsetMs: Long) = dao.saveLyricOffset(LyricOffset(songId, offsetMs))
    suspend fun getLyricOffset(songId: Long): Long? = dao.getLyricOffset(songId)
}
