import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Fix PlaybackViewModel
old_adjust = "fun adjustLyricsOffset(delta: Long) { lyricsOffsetMs.value += delta }"
new_adjust = """fun setLyricsOffset(offset: Long) { 
        lyricsOffsetMs.value = offset 
    }
    
    fun saveLyricsOffset() {
        _currentSong.value?.let {
            viewModelScope.launch { dbRepo.saveLyricOffset(it.id, lyricsOffsetMs.value) }
        }
    }"""
content = content.replace(old_adjust, new_adjust)

# Fetch offset when song changes
old_transition = """                    song?.let {
                        viewModelScope.launch { currentHistoryId = dbRepo.recordPlay(it.id) }
                        
                        // Extract Genre from ID3 Metadata provided by ExoPlayer
                        val id3Genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
                        resolveAutoEq(it, id3Genre)
                    }"""
new_transition = """                    song?.let {
                        viewModelScope.launch { 
                            currentHistoryId = dbRepo.recordPlay(it.id) 
                            lyricsOffsetMs.value = dbRepo.getLyricOffset(it.id) ?: 0L
                        }
                        
                        // Extract Genre from ID3 Metadata provided by ExoPlayer
                        val id3Genre = mediaItem?.mediaMetadata?.genre?.toString() ?: ""
                        resolveAutoEq(it, id3Genre)
                    }"""
content = content.replace(old_transition, new_transition)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)

