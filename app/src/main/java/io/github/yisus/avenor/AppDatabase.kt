package io.github.yisus.avenor


import kotlinx.coroutines.launch

import kotlinx.coroutines.GlobalScope

import kotlinx.coroutines.Dispatchers


import android.content.Context

import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.serialization.Serializable

import androidx.room.migration.Migration

import androidx.sqlite.db.SupportSQLiteDatabase

import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "songs",
    indices = [
        Index("title"),
        Index("artist"),
        Index("album"),
        Index("genre"),
        Index("dateAdded"),
        Index(value = ["artist", "album"])
    ]
)
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
    val fileExtension: String = "mp3",
    val codec: String = "MP3",
    val bitrate: Long = 0L,
    val channels: Int = 2,
    val fileSize: Long = 0L,
    val dateModified: Long = 0L,
    val dateAdded: Long = 0L,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val composer: String = "",
    val albumArtist: String = "",
    val sortTitle: String = "",
    val comment: String = "",
    val replayGainTrack: Float? = null,
    val replayGainAlbum: Float? = null,
    val artworkWidth: Int = 0,
    val artworkHeight: Int = 0,
    val artworkMimeType: String = ""
)

data class SongHeader(
    val id: Long,
    val dateModified: Long,
    val fileSize: Long
)

data class PlaybackSongItem(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtUri: String?,
    val codec: String = "MP3",
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16
) {
    fun toSong(): Song = Song(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        albumArtUri = albumArtUri,
        codec = codec,
        sampleRate = sampleRate,
        bitDepth = bitDepth
    )
}

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
    @PrimaryKey val id: Int = 1,
    val name: String = "ACTIVE_QUEUE",
    val currentSongId: Long? = null,
    val currentIndex: Int = 0,
    val currentPositionMs: Long = 0,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = 0,
    val isPlaying: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "queue_songs",
    foreignKeys = [
        ForeignKey(entity = PlaybackQueue::class, parentColumns = ["id"], childColumns = ["queueId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("songId"), Index("queueId")]
)
@Serializable
data class QueueSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queueId: Int = 1,
    val songId: Long,
    val positionIndex: Int
)

@Entity(tableName = "smart_trash")
@Serializable
data class TrashItem(
    @PrimaryKey val songId: Long,
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
@Serializable
data class Favorite(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
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
    val trashItems: List<TrashItem> = emptyList(),
    val favorites: List<Favorite> = emptyList()
)

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<Song>

    @Query("SELECT id, dateModified, fileSize FROM songs")
    suspend fun getAllSongHeaders(): List<SongHeader>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongsCountSync(): Int

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getPagedSongs(): PagingSource<Int, Song>

    @Query("SELECT * FROM songs ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    fun getPagedSongsByArtist(): PagingSource<Int, Song>

    @Query("SELECT * FROM songs ORDER BY album COLLATE NOCASE ASC, discNumber ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    fun getPagedSongsByAlbum(): PagingSource<Int, Song>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getPagedSongsByDateAdded(): PagingSource<Int, Song>

    @Query("SELECT * FROM songs ORDER BY durationMs DESC")
    fun getPagedSongsByDuration(): PagingSource<Int, Song>

    @Query("""
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%' 
           OR genre LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchPagedSongs(query: String): PagingSource<Int, Song>

    @Query("SELECT id, uri, title, artist, album, durationMs, albumArtUri, codec, sampleRate, bitDepth FROM songs ORDER BY title COLLATE NOCASE ASC")
    suspend fun getPlaybackSongsByTitle(): List<PlaybackSongItem>

    @Query("SELECT id, uri, title, artist, album, durationMs, albumArtUri, codec, sampleRate, bitDepth FROM songs ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    suspend fun getPlaybackSongsByArtist(): List<PlaybackSongItem>

    @Query("SELECT id, uri, title, artist, album, durationMs, albumArtUri, codec, sampleRate, bitDepth FROM songs ORDER BY album COLLATE NOCASE ASC, discNumber ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    suspend fun getPlaybackSongsByAlbum(): List<PlaybackSongItem>

    @Query("SELECT id, uri, title, artist, album, durationMs, albumArtUri, codec, sampleRate, bitDepth FROM songs ORDER BY dateAdded DESC")
    suspend fun getPlaybackSongsByDateAdded(): List<PlaybackSongItem>

    @Query("SELECT id, uri, title, artist, album, durationMs, albumArtUri, codec, sampleRate, bitDepth FROM songs ORDER BY durationMs DESC")
    suspend fun getPlaybackSongsByDuration(): List<PlaybackSongItem>

    @Query("""
        SELECT id, uri, title, artist, album, durationMs, albumArtUri, codec, sampleRate, bitDepth FROM songs 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%' 
           OR genre LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
    """)
    suspend fun searchPlaybackSongs(query: String): List<PlaybackSongItem>

    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN favorites ON songs.id = favorites.songId 
        ORDER BY favorites.addedAt DESC
    """)
    fun getPagedFavorites(): PagingSource<Int, Song>

    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN playlist_songs ON songs.id = playlist_songs.songId 
        WHERE playlist_songs.playlistId = :playlistId 
        ORDER BY playlist_songs.addedAt ASC
    """)
    fun getPagedSongsForPlaylist(playlistId: Int): PagingSource<Int, Song>


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

    @Query("SELECT * FROM playback_queues WHERE id = :queueId")
    fun getPlaybackQueue(queueId: Int = 1): Flow<PlaybackQueue?>

    @Query("SELECT * FROM playback_queues WHERE id = :queueId")
    suspend fun getPlaybackQueueSync(queueId: Int = 1): PlaybackQueue?

    @Query("SELECT * FROM playback_queues ORDER BY updatedAt DESC")
    fun getAllQueues(): Flow<List<PlaybackQueue>>

    @Query("SELECT * FROM playback_queues")
    suspend fun getAllQueuesSync(): List<PlaybackQueue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSongs(songs: List<QueueSong>)

    @Query("SELECT * FROM queue_songs WHERE queueId = :queueId ORDER BY positionIndex ASC")
    suspend fun getQueueSongsSync(queueId: Int = 1): List<QueueSong>

    @Query("SELECT songs.* FROM songs INNER JOIN queue_songs ON songs.id = queue_songs.songId WHERE queue_songs.queueId = :queueId ORDER BY queue_songs.positionIndex ASC")
    suspend fun getSongsForQueueSync(queueId: Int = 1): List<Song>

    @Query("UPDATE playback_queues SET currentPositionMs = :positionMs, updatedAt = :updatedAt WHERE id = :queueId")
    suspend fun updatePlaybackPosition(queueId: Int = 1, positionMs: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE playback_queues SET currentSongId = :songId, currentIndex = :currentIndex, currentPositionMs = :positionMs, isPlaying = :isPlaying, shuffleMode = :shuffleMode, repeatMode = :repeatMode, updatedAt = :updatedAt WHERE id = :queueId")
    suspend fun updatePlaybackState(queueId: Int = 1, songId: Long?, currentIndex: Int, positionMs: Long, isPlaying: Boolean, shuffleMode: Boolean, repeatMode: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM queue_songs WHERE queueId = :queueId")
    suspend fun clearQueueSongs(queueId: Int = 1)

    @Transaction
    suspend fun saveFullQueue(queue: PlaybackQueue, items: List<QueueSong>) {
        insertPlaybackQueue(queue)
        clearQueueSongs(queue.id)
        if (items.isNotEmpty()) {
            insertQueueSongs(items)
        }
    }

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
    suspend fun renameSong(id: Long, newTitle: String)

    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun renamePlaylist(id: Int, newName: String)

    @Query("UPDATE playback_queues SET name = :newName WHERE id = :id")
    suspend fun renamePlaybackQueue(id: Int, newName: String)



@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertSongs(songs: List<Song>)

@Update
suspend fun updateSongs(songs: List<Song>)

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

    // --- Favorites ---
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavoriteSync(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    @Query("SELECT songs.* FROM songs INNER JOIN favorites ON songs.id = favorites.songId ORDER BY favorites.addedAt DESC")
    fun getFavoriteSongs(): Flow<List<Song>>

    @Query("SELECT songId FROM favorites")
    fun getAllFavoriteSongIds(): Flow<List<Long>>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesSync(): List<Favorite>

    @Query("DELETE FROM favorites")
    suspend fun clearFavorites()

    @Transaction
    suspend fun restoreDatabase(export: DatabaseExport) {
        clearPlaylistSongs()
        clearHistory()
        clearPlaylists()
        clearSongs()
        clearEqPresets()
        clearLyricOffsets()
        clearPlaybackQueues()
        clearQueueSongs()
        clearTrashItems()
        clearFavorites()

        insertSongs(export.songs)
        export.playlists.forEach { insertPlaylist(it) }
        export.playlistSongs.forEach { insertSongToPlaylist(it) }
        export.eqPresets.forEach { insertEqPreset(it) }
        export.lyricOffsets.forEach { saveLyricOffset(it) }
        export.settings?.let { saveSettings(it) }
        export.playbackQueues.forEach { insertPlaybackQueue(it) }
        insertQueueSongs(export.queueSongs)
        export.trashItems.forEach { insertTrashItem(it) }
        export.favorites.forEach { addFavorite(it) }
    }
}

@Serializable
data class TopArtistResult(val artist: String, val playCount: Int)

val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { } }
val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { } }
val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) { } }
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `themeStyle` TEXT NOT NULL DEFAULT 'WARMTH'")
    }
}
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorites` (
                `songId` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`),
                FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_songId` ON `favorites` (`songId`)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playback_queues` ADD COLUMN `currentIndex` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playback_queues` ADD COLUMN `shuffleMode` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playback_queues` ADD COLUMN `repeatMode` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playback_queues` ADD COLUMN `isPlaying` INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `queue_songs_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `queueId` INTEGER NOT NULL,
                `songId` INTEGER NOT NULL,
                `positionIndex` INTEGER NOT NULL,
                FOREIGN KEY(`queueId`) REFERENCES `playback_queues`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO `queue_songs_new` (`queueId`, `songId`, `positionIndex`) SELECT `queueId`, `songId`, `positionIndex` FROM `queue_songs`")
        db.execSQL("DROP TABLE `queue_songs`")
        db.execSQL("ALTER TABLE `queue_songs_new` RENAME TO `queue_songs`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_songs_songId` ON `queue_songs` (`songId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_songs_queueId` ON `queue_songs` (`queueId`)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `codec` TEXT NOT NULL DEFAULT 'MP3'")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `bitrate` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `channels` INTEGER NOT NULL DEFAULT 2")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `fileSize` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `dateModified` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `dateAdded` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `trackNumber` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `discNumber` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `year` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `genre` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `composer` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `albumArtist` TEXT NOT NULL DEFAULT ''")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_title` ON `songs` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artist` ON `songs` (`artist`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_album` ON `songs` (`album`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_genre` ON `songs` (`genre`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_dateAdded` ON `songs` (`dateAdded`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artist_album` ON `songs` (`artist`, `album`)")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `sortTitle` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `comment` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `replayGainTrack` REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `replayGainAlbum` REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `artworkWidth` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `artworkHeight` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `artworkMimeType` TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [
        Song::class,
        Playlist::class,
        ListeningHistory::class,
        PlaylistSongCrossRef::class,
        EqPreset::class,
        AppSetting::class,
        LyricOffset::class,
        PlaybackQueue::class,
        QueueSong::class,
        TrashItem::class,
        Favorite::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private val dbScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "avenor_database")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            dbScope.launch {
                                val dao = INSTANCE?.musicDao()
                                if (dao != null) {
                                    EqPresetValidator.predefinedPresets.forEach { preset ->
                                        dao.insertEqPreset(preset)
                                    }
                                    val tier = PerformanceBenchmark.evaluateDeviceTier(context)
                                    dao.saveSettings(AppSetting(performanceMode = tier))
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            val dbFile = context.getDatabasePath("avenor_database")
                            if (dbFile.exists() && dbFile.length() > 5L * 1024 * 1024) {
                                dbScope.launch {
                                    try {
                                        INSTANCE?.openHelper?.writableDatabase?.execSQL("VACUUM")
                                    } catch (e: Exception) {
                                        android.util.Log.e("AppDatabase", "VACUUM failed", e)
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
