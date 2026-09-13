with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

content = content.replace(
    '@Insert\nsuspend fun insertHistory(history: ListeningHistory)',
    '@Insert\nsuspend fun insertHistory(history: ListeningHistory): Long\n\n@Query("UPDATE listening_history SET skipped = :skipped WHERE id = :historyId")\nsuspend fun updateHistorySkipped(historyId: Long, skipped: Boolean)'
)

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "r") as f:
    content = f.read()

content = content.replace(
    'suspend fun recordPlay(songId: Long, skipped: Boolean = false) {',
    'suspend fun recordPlay(songId: Long, skipped: Boolean = false): Long {'
)
content = content.replace(
    'dao.insertHistory(ListeningHistory(songId = songId, skipped = skipped, timeOfDay = timeOfDay))',
    'return dao.insertHistory(ListeningHistory(songId = songId, skipped = skipped, timeOfDay = timeOfDay))'
)
content = content.replace('}', '}\n\nsuspend fun updateHistorySkipped(historyId: Long, skipped: Boolean) = dao.updateHistorySkipped(historyId, skipped)', 1)

with open("app/src/main/java/io/github/yisus/nexo/DatabaseRepository.kt", "w") as f:
    f.write(content)

