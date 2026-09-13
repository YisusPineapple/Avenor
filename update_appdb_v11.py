import re

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

# Update AppSetting
old_app_setting = """@Entity(tableName = "app_settings")
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
val isFirstLaunch: Boolean = true
)"""

new_app_setting = """@Entity(tableName = "app_settings")
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
)"""

content = content.replace(old_app_setting, new_app_setting)

# Update database version from 10 to 11
content = re.sub(r'version\s*=\s*10', 'version = 11', content)

# Add rename DAOs
dao_methods = """
    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun renamePlaylist(id: Int, newName: String)

    @Query("UPDATE playback_queues SET name = :newName WHERE id = :id")
    suspend fun renamePlaybackQueue(id: Int, newName: String)
"""
if "renamePlaylist" not in content:
    content = content.replace("suspend fun clearTrashItems()", "suspend fun clearTrashItems()\n" + dao_methods)


with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)

