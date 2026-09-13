with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

# Add import
if "import kotlinx.serialization.Serializable" not in content:
    content = content.replace("import androidx.room.*", "import androidx.room.*\nimport kotlinx.serialization.Serializable")

# Add @Serializable to entities
entities = ["data class Song", "data class Playlist", "data class ListeningHistory", "data class PlaylistSongCrossRef", "data class LyricOffset", "data class EqPreset", "data class AppSetting", "data class TopArtistResult"]
for entity in entities:
    content = content.replace(entity, "@Serializable\n" + entity)

# To export database state, we need a data class wrapping everything
wrapper = """
@Serializable
data class DatabaseExport(
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val history: List<ListeningHistory>,
    val playlistSongs: List<PlaylistSongCrossRef>,
    val eqPresets: List<EqPreset>,
    val settings: AppSetting?,
    val lyricOffsets: List<LyricOffset>
)
"""
if "data class DatabaseExport" not in content:
    content = content.replace("@Dao", wrapper + "\n@Dao")

# We need DAOs to fetch everything at once for export.
# Let's add them to MusicDao
dao_methods = """
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
"""
if "getAllSongsSync" not in content:
    content = content.replace("interface MusicDao {", "interface MusicDao {\n" + dao_methods)

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
