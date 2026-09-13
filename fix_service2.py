import re

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "r") as f:
    content = f.read()

content = content.replace(
    'AppDatabase.getDatabase(this@PlaybackService).musicDao().getSettings().kotlinx.coroutines.flow.firstOrNull()',
    'AppDatabase.getDatabase(this@PlaybackService).musicDao().getSettings().firstOrNull()'
)

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "w") as f:
    f.write(content)
