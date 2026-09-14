package io.github.yisus.avenor

object SearchOptimizer {

    // Simple fuzzy matching by checking if all characters of query appear in sequence in the target
    private fun fuzzyMatch(query: String, target: String): Boolean {
        if (query.isEmpty()) return true
        if (target.isEmpty()) return false
        
        var qIdx = 0
        var tIdx = 0
        val lowerQuery = query.lowercase()
        val lowerTarget = target.lowercase()
        
        while (qIdx < lowerQuery.length && tIdx < lowerTarget.length) {
            if (lowerQuery[qIdx] == lowerTarget[tIdx]) {
                qIdx++
            }
            tIdx++
        }
        return qIdx == lowerQuery.length
    }

    fun filterSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        return songs.filter { 
            fuzzyMatch(query, it.title) || 
            fuzzyMatch(query, it.artist) || 
            fuzzyMatch(query, it.album) 
        }
    }
}
