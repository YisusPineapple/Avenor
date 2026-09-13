package io.github.yisus.nexo

import kotlinx.coroutines.flow.Flow

class DatabaseRepository(val dao: MusicDao) {
    val allSongs: Flow<List<Song>> = dao.getAllSongs()
    val allPlaylists: Flow<List<Playlist>> = dao.getAllPlaylists()
    val recentHistory: Flow<List<Song>> = dao.getRecentHistory()
    val dailyMix: Flow<List<Song>> = dao.getDailyMix()
    val eqPresets: Flow<List<EqPreset>> = dao.getAllEqPresets()
    val appSettings: Flow<AppSetting?> = dao.getSettings()
    val topSongs: Flow<List<Song>> = dao.getTopSongs()
    val topArtist: Flow<TopArtistResult?> = dao.getTopArtist()
    val totalListeningTimeMs: Flow<Long?> = dao.getTotalListeningTimeMs()

    suspend fun syncLocalSongs(songs: List<Song>) = dao.insertSongs(songs)
    
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
