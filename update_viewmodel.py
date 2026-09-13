import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Replace recordPlay to handle skipped and timeOfDay
old_record = """    private fun recordPlay(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(getApplication()).musicDao()
            dao.insertHistory(ListeningHistory(songId = songId))
        }
    }"""
    
new_record = """    private fun recordPlay(songId: Long, skipped: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(getApplication()).musicDao()
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val timeOfDay = when(hour) {
                in 5..11 -> "MORNING"
                in 12..16 -> "AFTERNOON"
                in 17..20 -> "EVENING"
                else -> "NIGHT"
            }
            dao.insertHistory(ListeningHistory(songId = songId, skipped = skipped, timeOfDay = timeOfDay))
        }
    }"""

content = content.replace(old_record, new_record)

# Now, we need to track if a song was skipped.
# When we call `playNext()` or `skipToNext()` or `skipToPrevious()` or play a new song, we should check how much of the current song was played.
# Let's just track skipped if it was played less than 50% or 30 seconds.
# But `recordPlay` is called when?
