package io.github.yisus.nexo


import kotlinx.coroutines.launch

import kotlinx.coroutines.GlobalScope

import kotlinx.coroutines.Dispatchers


import android.content.Context

import androidx.room.*
import kotlinx.serialization.Serializable

import androidx.room.migration.Migration

import androidx.sqlite.db.SupportSQLiteDatabase

import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
@Serializable
data class Song(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtUri: String?,
    val bitDepth: Int = 16,
    val sampleRate: Int = 44100,
    val mimeType: String = "audio/mpeg",
    val fileExtension: String = "mp3"
)

@Entity(tableName = "playlists")
@Serializable
data class Playlist(
@PrimaryKey(autoGenerate = true) val id: Int = 0,
val name: String,
val createdAt: Long = System.currentTimeMillis()
)

@Entity(
tableName = "listening_history",
foreignKeys = [ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)],
indices = [Index("songId")]
)
@Serializable
data class ListeningHistory(
@PrimaryKey(autoGenerate = true) val id: Int = 0,
val songId: Long,
val playedAt: Long = System.currentTimeMillis(),
val skipped: Boolean = false,
val timeOfDay: String = "MORNING" // MORNING, AFTERNOON, EVENING, NIGHT
)

@Entity(
tableName = "playlist_songs",
primaryKeys = ["playlistId", "songId"],
foreignKeys = [
ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
],
indices = [Index("songId"), Index("playlistId")]
)
@Serializable
data class PlaylistSongCrossRef(
val playlistId: Int,
val songId: Long,
val addedAt: Long = System.currentTimeMillis()
)


@Entity(tableName = "lyric_offset")
@Serializable
data class LyricOffset(
    @PrimaryKey val songId: Long,
    val offsetMs: Long
)
@Entity(tableName = "eq_presets")
@Serializable
data class EqPreset(
@PrimaryKey(autoGenerate = true) val id: Int = 0,
val name: String,
val bands: String
)

@Entity(tableName = "app_settings")
@Serializable
data class AppSetting(
@PrimaryKey val id: Int = 1,
val performanceMode: String = "VIVID", // ECO, BALANCED, VIVID
val autoEq: Boolean = false,
val showLike: Boolean = true,
val showShuffle: Boolean = true,
val showRepeat: Boolean = true,
val autoFillQueue: Boolean = false,
val themeStyle: String = "WARMTH", // WARMTH, AURORA, SOFT_UI, EXPRESSIVE
val albumArtResolution: String = "HIGH", // LOW, MEDIUM, HIGH, ORIGINAL
val isFirstLaunch: Boolean = true,
val nowPlayingStyle: String = "CLASSIC", // CLASSIC, EXPRESSIVE, APPLE_MUSIC
val trashPurgeDays: Int = 30 // 7, 15, 30
)



@Entity(tableName = "playback_queues")
@Serializable
data class PlaybackQueue(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val currentSongId: Long? = null,
    val currentPositionMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "queue_songs",
    primaryKeys = ["queueId", "songId"],
    foreignKeys = [
        ForeignKey(entity = PlaybackQueue::class, parentColumns = ["id"], childColumns = ["queueId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("songId"), Index("queueId")]
)
@Serializable
data class QueueSong(
    val queueId: Int,
    val songId: Long,
    val positionIndex: Int
)

@Entity(tableName = "smart_trash")
@Serializable
data class TrashItem(
    @PrimaryKey val songId: Long,
    val deletedAt: Long = System.currentTimeMillis()
)

@Serializable
data class DatabaseExport(
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val history: List<ListeningHistory>,
    val playlistSongs: List<PlaylistSongCrossRef>,
    val eqPresets: List<EqPreset>,
    val settings: AppSetting?,
    val lyricOffsets: List<LyricOffset>,
    val playbackQueues: List<PlaybackQueue> = emptyList(),
    val queueSongs: List<QueueSong> = emptyList(),
    val trashItems: List<TrashItem> = emptyList()
)

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Int): Song?


    @Query("SELECT * FROM lyric_offset")
    suspend fun getAllLyricOffsets(): List<LyricOffset>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsSync(): List<Song>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsSync(): List<Playlist>

    @Query("SELECT * FROM listening_history")
    suspend fun getAllHistorySync(): List<ListeningHistory>

    @Query("SELECT * FROM playlist_songs")
    suspend fun getAllPlaylistSongsSync(): List<PlaylistSongCrossRef>

    @Query("SELECT * FROM eq_presets")
    suspend fun getAllEqPresetsSync(): List<EqPreset>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsSync(): AppSetting?

    @Query("DELETE FROM songs")
    suspend fun clearSongs()
    
    @Query("DELETE FROM playlists")
    suspend fun clearPlaylists()
    
    @Query("DELETE FROM listening_history")
    suspend fun clearHistory()
    
    @Query("DELETE FROM playlist_songs")
    suspend fun clearPlaylistSongs()
    
    @Query("DELETE FROM eq_presets")
    suspend fun clearEqPresets()
    
    @Query("DELETE FROM lyric_offset")
    suspend fun clearLyricOffsets()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackQueue(queue: PlaybackQueue): Long

    @Query("SELECT * FROM playback_queues ORDER BY updatedAt DESC")
    fun getAllQueues(): Flow<List<PlaybackQueue>>

    @Query("SELECT * FROM playback_queues")
    suspend fun getAllQueuesSync(): List<PlaybackQueue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSongs(songs: List<QueueSong>)

    @Query("SELECT songs.* FROM songs INNER JOIN queue_songs ON songs.id = queue_songs.songId WHERE queue_songs.queueId = :queueId ORDER BY queue_songs.positionIndex ASC")
    suspend fun getSongsForQueueSync(queueId: Int): List<Song>

    @Query("SELECT * FROM queue_songs")
    suspend fun getAllQueueSongsSync(): List<QueueSong>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashItem)

    @Query("SELECT * FROM smart_trash")
    suspend fun getAllTrashItemsSync(): List<TrashItem>

    @Query("SELECT * FROM smart_trash WHERE deletedAt < :threshold")
    suspend fun getExpiredTrashItems(threshold: Long): List<TrashItem>

    @Query("DELETE FROM smart_trash WHERE songId = :songId")
    suspend fun deleteTrashItem(songId: Long)

    @Query("DELETE FROM playback_queues")
    suspend fun clearPlaybackQueues()

    @Query("DELETE FROM queue_songs")
    suspend fun clearQueueSongs()

    @Query("DELETE FROM smart_trash")
    suspend fun clearTrashItems()

    @Query("UPDATE songs SET title = :newTitle WHERE id = :id")
    suspend fun renameSong(id: Int, newTitle: String)

    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun renamePlaylist(id: Int, newName: String)

    @Query("UPDATE playback_queues SET name = :newName WHERE id = :id")
    suspend fun renamePlaybackQueue(id: Int, newName: String)



@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertSongs(songs: List<Song>)

@Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("SELECT * FROM songs ORDER BY title ASC")
fun getAllSongs(): Flow<List<Song>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertPlaylist(playlist: Playlist)

@Query("SELECT * FROM playlists ORDER BY createdAt DESC")
fun getAllPlaylists(): Flow<List<Playlist>>

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertSongToPlaylist(crossRef: PlaylistSongCrossRef)

@Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
suspend fun removeSongFromPlaylist(playlistId: Int, songId: Long)

@Query("SELECT songs.* FROM songs INNER JOIN playlist_songs ON songs.id = playlist_songs.songId WHERE playlist_songs.playlistId = :playlistId ORDER BY playlist_songs.addedAt ASC")
fun getSongsForPlaylist(playlistId: Int): Flow<List<Song>>

@Insert
suspend fun insertHistory(history: ListeningHistory): Long

@Query("UPDATE listening_history SET skipped = :skipped WHERE id = :historyId")
suspend fun updateHistorySkipped(historyId: Long, skipped: Boolean)

@Query("SELECT songs.* FROM songs INNER JOIN listening_history ON songs.id = listening_history.songId ORDER BY listening_history.playedAt DESC LIMIT :limit")
fun getRecentHistory(limit: Int = 20): Flow<List<Song>>

@Query("SELECT * FROM (SELECT songs.*, 1 as priority FROM songs WHERE artist IN (SELECT s.artist FROM songs s JOIN listening_history h ON s.id = h.songId GROUP BY s.artist ORDER BY COUNT(h.id) DESC LIMIT 5) UNION SELECT songs.*, 2 as priority FROM songs) GROUP BY id ORDER BY priority ASC, RANDOM() LIMIT 15")
fun getDailyMix(): Flow<List<Song>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertEqPreset(preset: EqPreset)

@Query("SELECT * FROM eq_presets ORDER BY name ASC")
fun getAllEqPresets(): Flow<List<EqPreset>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun saveSettings(setting: AppSetting)


    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSetting?>

    @Query("SELECT songs.* FROM songs INNER JOIN listening_history h ON songs.id = h.songId GROUP BY songs.id ORDER BY COUNT(h.id) DESC LIMIT :limit")
    fun getTopSongs(limit: Int = 5): Flow<List<Song>>
    
    @Query("SELECT artist, COUNT(h.id) as playCount FROM songs INNER JOIN listening_history h ON songs.id = h.songId GROUP BY artist ORDER BY playCount DESC LIMIT 1")
    fun getTopArtist(): Flow<TopArtistResult?>
    
    @Query("SELECT SUM(songs.durationMs) FROM songs INNER JOIN listening_history h ON songs.id = h.songId")
    fun getTotalListeningTimeMs(): Flow<Long?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyricOffset(state: LyricOffset)
    
    @Query("SELECT offsetMs FROM lyric_offset WHERE songId = :songId")
    suspend fun getLyricOffset(songId: Long): Long?

}

@Serializable
data class TopArtistResult(val artist: String, val playCount: Int)


val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { /* ... */ } }
val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { /* ... */ } }
val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) { /* ... */ } }
val MIGRATION_4_5 = object : Migration(4, 5) {
override fun migrate(db: SupportSQLiteDatabase) {
db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `themeStyle` TEXT NOT NULL DEFAULT 'WARMTH'")
}
}

@Database(
entities = [Song::class, Playlist::class, ListeningHistory::class, PlaylistSongCrossRef::class, EqPreset::class, AppSetting::class, LyricOffset::class, PlaybackQueue::class, QueueSong::class, TrashItem::class],
version = 11,
exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
abstract fun musicDao(): MusicDao
companion object {
@Volatile private var INSTANCE: AppDatabase? = null
fun getDatabase(context: Context): AppDatabase {
return INSTANCE ?: synchronized(this) {
val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "nexo_database")
.fallbackToDestructiveMigration() // Handle prior dummy migrations cleanly for legacy safety
.addCallback(object : RoomDatabase.Callback() {

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val dao = INSTANCE?.musicDao()
                            if (dao != null) {
                                EqPresetValidator.predefinedPresets.forEach { preset ->
                                    dao.insertEqPreset(preset)
                                }
                                
                                // Apply benchmark tier on first launch
                                val tier = PerformanceBenchmark.evaluateDeviceTier(context)
                                dao.saveSettings(AppSetting(performanceMode = tier))
                            }
                        }
                    }
                    
                    override fun onOpen(db: SupportSQLiteDatabase) {

super.onOpen(db)
// Run Vacuum if file > 5MB to optimize low-end storage
val dbFile = context.getDatabasePath("nexo_database")
if (dbFile.exists() && dbFile.length() > 5L * 1024 * 1024) {
kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
try {
// Room's raw queries or openHelper can execute VACUUM
INSTANCE?.openHelper?.writableDatabase?.execSQL("VACUUM")
} catch (e: Exception) {
e.printStackTrace()
}
}
}
}
})
.build()
INSTANCE = instance
instance
}
}
}
}
