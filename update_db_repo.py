import re

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "r") as f:
    content = f.read()

new_methods = """
    val topSongs: Flow<List<Song>> = dao.getTopSongs()
    val topArtist: Flow<TopArtistResult?> = dao.getTopArtist()
    val totalListeningTimeMs: Flow<Long?> = dao.getTotalListeningTimeMs()
"""

content = content.replace("val appSettings: Flow<AppSetting?> = dao.getSettings()", "val appSettings: Flow<AppSetting?> = dao.getSettings()\n" + new_methods)

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "w") as f:
    f.write(content)
