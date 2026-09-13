with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

content = content.replace(
    'val playedAt: Long = System.currentTimeMillis()',
    'val playedAt: Long = System.currentTimeMillis(),\nval skipped: Boolean = false,\nval timeOfDay: String = "MORNING" // MORNING, AFTERNOON, EVENING, NIGHT'
)
content = content.replace('version = 6', 'version = 7')

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
