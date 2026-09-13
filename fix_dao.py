with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

if "@Update\n    suspend fun updateSong(song: Song)" not in content:
    content = content.replace("@Query(\"SELECT * FROM songs ORDER BY title ASC\")", "@Update\n    suspend fun updateSong(song: Song)\n\n    @Query(\"SELECT * FROM songs ORDER BY title ASC\")")

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "r") as f:
    content = f.read()

if "suspend fun updateSong(song: Song)" not in content:
    content = content.replace("suspend fun insertSongs(songs: List<Song>)", "suspend fun updateSong(song: Song) = dao.updateSong(song)\n    suspend fun insertSongs(songs: List<Song>)")

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "w") as f:
    f.write(content)
