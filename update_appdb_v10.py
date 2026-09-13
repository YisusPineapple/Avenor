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
val themeStyle: String = "WARMTH" // WARMTH, AURORA, SOFT_UI, EXPRESSIVE
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
val isFirstLaunch: Boolean = true
)"""

content = content.replace(old_app_setting, new_app_setting)

# Update database version from 9 to 10
content = re.sub(r'version\s*=\s*9', 'version = 10', content)

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
