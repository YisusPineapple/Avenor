import re

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "r") as f:
    content = f.read()

# Replace json backup strings
content = content.replace('application/json', 'application/zip')
content = content.replace('nexo_backup.json', 'nexo_backup.zip')
content = content.replace('JSON Backup exported successfully', 'ZIP Backup exported successfully')
content = content.replace('Restored from JSON! Restarting...', 'Restored from ZIP! Restarting...')

# Add Smart Trash Worker schedule
old_work = """        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeeklyBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )"""

new_work = """        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeeklyBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
        
        val trashRequest = PeriodicWorkRequestBuilder<SmartTrashWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyTrashPurge",
            ExistingPeriodicWorkPolicy.KEEP,
            trashRequest
        )"""
content = content.replace(old_work, new_work)

with open("app/src/main/java/io/github/yisus/nexo/MainActivity.kt", "w") as f:
    f.write(content)
