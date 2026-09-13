with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

# Completely replace the database annotation block since there might be multiple or it wasn't replaced properly.
old_anno = """@Database(
entities = [Song::class, Playlist::class, ListeningHistory::class, PlaylistSongCrossRef::class, EqPreset::class, AppSetting::class, LyricOffset::class],
version = 8,
exportSchema = false
)"""
new_anno = """@Database(
entities = [Song::class, Playlist::class, ListeningHistory::class, PlaylistSongCrossRef::class, EqPreset::class, AppSetting::class, LyricOffset::class, PlaybackQueue::class, QueueSong::class, TrashItem::class],
version = 9,
exportSchema = false
)"""

content = content.replace(old_anno, new_anno)

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
