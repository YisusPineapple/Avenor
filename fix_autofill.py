with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "r") as f:
    content = f.read()

if "val autoFillQueue: Boolean = false" not in content:
    content = content.replace("val showRepeat: Boolean = true,", "val showRepeat: Boolean = true,\n    val autoFillQueue: Boolean = false,")

with open("app/src/main/java/io/github/yisus/nexo/AppDatabase.kt", "w") as f:
    f.write(content)
