import re

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

# 1. Add LyricSyncState entity
lyric_sync_entity = """
@Entity(tableName = "lyric_sync")
data class LyricSyncState(
    @PrimaryKey val songId: Long,
    val offsetMs: Long
)
"""
content = content.replace('@Entity(tableName = "eq_presets")', lyric_sync_entity + '@Entity(tableName = "eq_presets")')

# 2. Add DAO methods
dao_methods = """
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyricOffset(state: LyricSyncState)
    
    @Query("SELECT offsetMs FROM lyric_sync WHERE songId = :songId")
    suspend fun getLyricOffset(songId: Long): Long?
"""
content = content.replace('fun getTotalListeningTimeMs(): Flow<Long?>', 'fun getTotalListeningTimeMs(): Flow<Long?>' + dao_methods)

# 3. Add to Database entities and version 8
content = content.replace('version = 7', 'version = 8')
content = content.replace('AppSetting::class]', 'AppSetting::class, LyricSyncState::class]')

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)


with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "r") as f:
    repo_content = f.read()

repo_methods = """
    suspend fun saveLyricOffset(songId: Long, offsetMs: Long) = dao.saveLyricOffset(LyricSyncState(songId, offsetMs))
    suspend fun getLyricOffset(songId: Long): Long? = dao.getLyricOffset(songId)
"""
repo_content = repo_content.replace('}', repo_methods + '\n}')

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "w") as f:
    f.write(repo_content)

