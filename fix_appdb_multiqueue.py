import re

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

# 1. Add New Entities
new_entities = """
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
"""
if "data class PlaybackQueue" not in content:
    content = content.replace("@Serializable\ndata class DatabaseExport", new_entities + "\n@Serializable\ndata class DatabaseExport")

# 2. Update DatabaseExport
old_export = """@Serializable
data class DatabaseExport(
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val history: List<ListeningHistory>,
    val playlistSongs: List<PlaylistSongCrossRef>,
    val eqPresets: List<EqPreset>,
    val settings: AppSetting?,
    val lyricOffsets: List<LyricOffset>
)"""

new_export = """@Serializable
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
)"""
content = content.replace(old_export, new_export)

# 3. Add DAOs
dao_methods = """
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
"""
if "fun getAllQueues()" not in content:
    content = content.replace('suspend fun clearLyricOffsets()', 'suspend fun clearLyricOffsets()\n' + dao_methods)

# 4. Update @Database version and entities
old_db_anno = "@Database(entities = [Song::class, Playlist::class, ListeningHistory::class, PlaylistSongCrossRef::class, EqPreset::class, AppSetting::class, LyricOffset::class],\nversion = 8,"
new_db_anno = "@Database(entities = [Song::class, Playlist::class, ListeningHistory::class, PlaylistSongCrossRef::class, EqPreset::class, AppSetting::class, LyricOffset::class, PlaybackQueue::class, QueueSong::class, TrashItem::class],\nversion = 9,"
content = content.replace(old_db_anno, new_db_anno)

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
