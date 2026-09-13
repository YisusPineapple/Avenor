import re

# AppDatabase.kt
with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

content = content.replace("LyricSyncState", "LyricOffset")
content = content.replace("lyric_sync", "lyric_offset")

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)

# DatabaseRepository.kt
with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "r") as f:
    content = f.read()

content = content.replace("LyricSyncState", "LyricOffset")

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "w") as f:
    f.write(content)

