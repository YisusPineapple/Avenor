with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "r") as f:
    content = f.read()

old_record = "suspend fun recordPlay(songId: Long) = dao.insertHistory(ListeningHistory(songId = songId))"
new_record = """suspend fun recordPlay(songId: Long, skipped: Boolean = false) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeOfDay = when(hour) {
            in 5..11 -> "MORNING"
            in 12..16 -> "AFTERNOON"
            in 17..20 -> "EVENING"
            else -> "NIGHT"
        }
        dao.insertHistory(ListeningHistory(songId = songId, skipped = skipped, timeOfDay = timeOfDay))
    }"""

content = content.replace(old_record, new_record)

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "w") as f:
    f.write(content)
