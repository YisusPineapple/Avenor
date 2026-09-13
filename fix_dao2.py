with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

if "suspend fun deleteSong(song: Song)" not in content:
    content = content.replace("@Update\n    suspend fun updateSong(song: Song)", "@Update\n    suspend fun updateSong(song: Song)\n\n    @Delete\n    suspend fun deleteSong(song: Song)")

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
