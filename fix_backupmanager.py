import re

with open("app/src/main/java/io/github/yisus/nexo/BackupManager.kt", "r") as f:
    content = f.read()

# Add Zip imports
if "java.util.zip.ZipOutputStream" not in content:
    content = content.replace("import java.io.File", "import java.io.File\nimport java.util.zip.ZipOutputStream\nimport java.util.zip.ZipEntry\nimport java.util.zip.ZipInputStream")

# Update BackupManager methods
old_export = """    suspend fun exportBackupToJson"""
new_export = """    suspend fun exportBackupToJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).musicDao()
            val exportData = DatabaseExport(
                songs = dao.getAllSongsSync(),
                playlists = dao.getAllPlaylistsSync(),
                history = dao.getAllHistorySync(),
                playlistSongs = dao.getAllPlaylistSongsSync(),
                eqPresets = dao.getAllEqPresetsSync(),
                settings = dao.getSettingsSync(),
                lyricOffsets = dao.getAllLyricOffsets(),
                playbackQueues = dao.getAllQueuesSync(),
                queueSongs = dao.getAllQueueSongsSync(),
                trashItems = dao.getAllTrashItemsSync()
            )
            val jsonString = jsonFormat.encodeToString(exportData)
            
            // Write ZIP file containing JSON + SQLite DB
            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                ZipOutputStream(outStream).use { zout ->
                    // Add JSON
                    zout.putNextEntry(ZipEntry("metadata.json"))
                    zout.write(jsonString.toByteArray(Charsets.UTF_8))
                    zout.closeEntry()
                    
                    // Add DB Files for deep snapshot restore fallback
                    val dbFile = context.getDatabasePath("nexo_database")
                    if (dbFile.exists()) {
                        AppDatabase.getDatabase(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                        zout.putNextEntry(ZipEntry("nexo_database.db"))
                        dbFile.inputStream().use { it.copyTo(zout) }
                        zout.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBackupFromJson"""
content = re.sub(r"    suspend fun exportBackupToJson.*?suspend fun importBackupFromJson", new_export, content, flags=re.DOTALL)

old_import = """    suspend fun importBackupFromJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext false"""
new_import = """    suspend fun importBackupFromJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            var jsonString = ""
            context.contentResolver.openInputStream(uri)?.use { input ->
                // Check if it's a zip or plain json
                try {
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while(entry != null) {
                            if (entry.name == "metadata.json") {
                                jsonString = zis.bufferedReader(Charsets.UTF_8).readText()
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                } catch(e: Exception) {
                    // Fallback to plain JSON read if it wasn't a zip
                    input.close()
                    jsonString = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() } ?: ""
                }
            }
            if (jsonString.isEmpty()) return@withContext false"""
content = content.replace(old_import, new_import)

# Insert the rest of the new entities in importBackupFromJson
old_import_insert = """            importData.settings?.let { dao.saveSettings(it) }
            importData.lyricOffsets.forEach { dao.saveLyricOffset(it) }"""
new_import_insert = """            importData.settings?.let { dao.saveSettings(it) }
            importData.lyricOffsets.forEach { dao.saveLyricOffset(it) }
            importData.playbackQueues.forEach { dao.insertPlaybackQueue(it) }
            importData.queueSongs.let { if(it.isNotEmpty()) dao.insertQueueSongs(it) }
            importData.trashItems.forEach { dao.insertTrashItem(it) }"""
content = content.replace(old_import_insert, new_import_insert)

# Also update the clear sequence
old_clear = """            dao.clearEqPresets()
            dao.clearLyricOffsets()"""
new_clear = """            dao.clearEqPresets()
            dao.clearLyricOffsets()
            dao.clearPlaybackQueues()
            dao.clearQueueSongs()
            dao.clearTrashItems()"""
content = content.replace(old_clear, new_clear)

# Also update auto-backup
old_auto = """                lyricOffsets = dao.getAllLyricOffsets()
            )
            val jsonString"""
new_auto = """                lyricOffsets = dao.getAllLyricOffsets(),
                playbackQueues = dao.getAllQueuesSync(),
                queueSongs = dao.getAllQueueSongsSync(),
                trashItems = dao.getAllTrashItemsSync()
            )
            val jsonString"""
content = content.replace(old_auto, new_auto)

with open("app/src/main/java/io/github/yisus/nexo/BackupManager.kt", "w") as f:
    f.write(content)
