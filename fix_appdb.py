import re

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

# Fix Song entity
content = re.sub(
    r'@Entity\(tableName = "songs"\)\s*data class Song\([^)]+\)',
    """@Entity(tableName = "songs")
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
)""",
    content
)

# Fix migrations
mig_code = """val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `themeStyle` TEXT NOT NULL DEFAULT 'WARMTH'")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `bitDepth` INTEGER NOT NULL DEFAULT 16")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `sampleRate` INTEGER NOT NULL DEFAULT 44100")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `mimeType` TEXT NOT NULL DEFAULT 'audio/mpeg'")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `fileExtension` TEXT NOT NULL DEFAULT 'mp3'")
    }
}"""

content = re.sub(
    r'val MIGRATION_4_5 = object : Migration\(4, 5\) \{.*?\}\}',
    mig_code,
    content,
    flags=re.DOTALL
)

# Fix Dao
dao_additions = """
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSetting?>

    @Query("SELECT songs.* FROM songs INNER JOIN listening_history h ON songs.id = h.songId GROUP BY songs.id ORDER BY COUNT(h.id) DESC LIMIT :limit")
    fun getTopSongs(limit: Int = 5): Flow<List<Song>>
    
    @Query("SELECT artist, COUNT(h.id) as playCount FROM songs INNER JOIN listening_history h ON songs.id = h.songId GROUP BY artist ORDER BY playCount DESC LIMIT 1")
    fun getTopArtist(): Flow<TopArtistResult?>
    
    @Query("SELECT SUM(songs.durationMs) FROM songs INNER JOIN listening_history h ON songs.id = h.songId")
    fun getTotalListeningTimeMs(): Flow<Long?>
}

data class TopArtistResult(val artist: String, val playCount: Int)
"""
content = re.sub(
    r'@Query\("SELECT \* FROM app_settings WHERE id = 1"\)\s*fun getSettings\(\): Flow<AppSetting\?>\s*\}',
    dao_additions,
    content,
    flags=re.DOTALL
)

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
