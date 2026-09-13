import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Add Search and Queue States to PlaybackViewModel
vm_add = """
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredSongs = combine(songs, _searchQuery) { list, query ->
        if (query.isBlank()) list else list.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) || it.album.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun playNext(song: Song) {
        val current = _queue.value.toMutableList()
        val currentIndex = controller?.currentMediaItemIndex ?: 0
        if (current.isNotEmpty() && currentIndex >= 0 && currentIndex < current.size) {
            current.add(currentIndex + 1, song)
            _queue.value = current
            currentPlayingList = current
            val mediaItem = androidx.media3.common.MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()).build()
            controller?.addMediaItem(currentIndex + 1, mediaItem)
        } else {
            playSongList(listOf(song), 0)
        }
    }

    fun enqueue(song: Song) {
        val current = _queue.value.toMutableList()
        if (current.isNotEmpty()) {
            current.add(song)
            _queue.value = current
            currentPlayingList = current
            val mediaItem = androidx.media3.common.MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()).build()
            controller?.addMediaItem(mediaItem)
        } else {
            playSongList(listOf(song), 0)
        }
    }
"""

content = content.replace("private var currentPlayingList: List<Song> = emptyList()", "private var currentPlayingList: List<Song> = emptyList()\n" + vm_add)

# 2. Update playSongList to update _queue
play_song_list = """
    fun playSongList(songList: List<Song>, startIndex: Int) {
        currentPlayingList = songList
"""
play_song_list_new = """
    fun playSongList(songList: List<Song>, startIndex: Int) {
        currentPlayingList = songList
        _queue.value = songList
"""
content = content.replace(play_song_list, play_song_list_new)

# Add combine import if missing
if "import kotlinx.coroutines.flow.combine" not in content:
    content = content.replace("import kotlinx.coroutines.flow.StateFlow", "import kotlinx.coroutines.flow.StateFlow\nimport kotlinx.coroutines.flow.combine")

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
