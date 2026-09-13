with open("app/src/main/java/io/github/yisus/nexo/SmartTrashWorker.kt", "r") as f:
    content = f.read()

old_logic = """            val threshold = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val expiredItems = dao.getExpiredTrashItems(threshold)"""

new_logic = """            val settings = dao.getSettingsSync()
            val purgeDays = settings?.trashPurgeDays ?: 30
            val threshold = System.currentTimeMillis() - (purgeDays * 24L * 60L * 60L * 1000L)
            val expiredItems = dao.getExpiredTrashItems(threshold)"""

content = content.replace(old_logic, new_logic)

with open("app/src/main/java/io/github/yisus/nexo/SmartTrashWorker.kt", "w") as f:
    f.write(content)
