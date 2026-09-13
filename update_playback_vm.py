import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Add currentHistoryId
content = content.replace(
    'private var playerListener: Player.Listener? = null',
    'private var playerListener: Player.Listener? = null\n    private var currentHistoryId: Long? = null'
)

# Update onMediaItemTransition
old_transition = """                    song?.let {
                        viewModelScope.launch { dbRepo.recordPlay(it.id) }
                        
                        // Extract Genre from ID3 Metadata provided by ExoPlayer
                        val id3Genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
                        resolveAutoEq(it, id3Genre)
                    }"""
                    
new_transition = """                    song?.let {
                        viewModelScope.launch { currentHistoryId = dbRepo.recordPlay(it.id) }
                        
                        // Extract Genre from ID3 Metadata provided by ExoPlayer
                        val id3Genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
                        resolveAutoEq(it, id3Genre)
                    }"""
content = content.replace(old_transition, new_transition)

# Update skipToNext
old_next = """    fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }"""
new_next = """    fun skipToNext() {
        viewModelScope.launch { currentHistoryId?.let { dbRepo.updateHistorySkipped(it, true) } }
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }"""
content = content.replace(old_next, new_next)

# Update skipToPrevious
old_prev = """    fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }"""
new_prev = """    fun skipToPrevious() {
        viewModelScope.launch { currentHistoryId?.let { dbRepo.updateHistorySkipped(it, true) } }
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }"""
content = content.replace(old_prev, new_prev)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
