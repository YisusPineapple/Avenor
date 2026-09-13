with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

content = content.replace("@Serializable\n@Serializable\ndata class PlaylistSongCrossRef", "@Serializable\ndata class PlaylistSongCrossRef")

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
