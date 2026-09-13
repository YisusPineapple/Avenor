import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("currentPlayingList = songList\n        val mediaItems", "currentPlayingList = songList\n        _queue.value = songList\n        val mediaItems")
content = content.replace("currentPlayingList = songList\nval mediaItems", "currentPlayingList = songList\n_queue.value = songList\nval mediaItems")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
