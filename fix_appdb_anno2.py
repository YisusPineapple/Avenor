import re

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

old_anno = """@Database(entities = [Song::class, Playlist::class, ListeningHistory::class, PlaylistSongCrossRef::class, EqPreset::class, AppSetting::class, LyricOffset::class],
version = 8,"""
new_anno = """@Database(entities = [Song::class, Playlist::class, ListeningHistory::class, PlaylistSongCrossRef::class, EqPreset::class, AppSetting::class, LyricOffset::class, PlaybackQueue::class, QueueSong::class, TrashItem::class],
version = 9,"""

# We need to make sure the regex replaces it regardless of whitespace
content = re.sub(r'@Database\(entities\s*=\s*\[Song::class,\s*Playlist::class,\s*ListeningHistory::class,\s*PlaylistSongCrossRef::class,\s*EqPreset::class,\s*AppSetting::class,\s*LyricOffset::class\],\s*version\s*=\s*8,', new_anno, content)

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
