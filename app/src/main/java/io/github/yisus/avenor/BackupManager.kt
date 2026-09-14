package io.github.yisus.avenor

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BackupManager {
    val jsonFormat = Json { 
        prettyPrint = false
        ignoreUnknownKeys = true 
    }

    suspend fun exportBackupToJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
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
                    val dbFile = context.getDatabasePath("avenor_database")
                    if (dbFile.exists()) {
                        AppDatabase.getDatabase(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                        zout.putNextEntry(ZipEntry("avenor_database.db"))
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

    suspend fun importBackupFromJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
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
            if (jsonString.isEmpty()) return@withContext false

            val importData = jsonFormat.decodeFromString<DatabaseExport>(jsonString)
            val dao = AppDatabase.getDatabase(context).musicDao()

            // Clear and insert
            dao.clearPlaylistSongs()
            dao.clearHistory()
            dao.clearPlaylists()
            dao.clearSongs()
            dao.clearEqPresets()
            dao.clearLyricOffsets()
            dao.clearPlaybackQueues()
            dao.clearQueueSongs()
            dao.clearTrashItems()

            dao.insertSongs(importData.songs)
            importData.playlists.forEach { dao.insertPlaylist(it) }
            importData.history.forEach { dao.insertHistory(it) }
            importData.playlistSongs.forEach { dao.insertSongToPlaylist(it) }
            importData.eqPresets.forEach { dao.insertEqPreset(it) }
            importData.settings?.let { dao.saveSettings(it) }
            importData.lyricOffsets.forEach { dao.saveLyricOffset(it) }
            importData.playbackQueues.forEach { dao.insertPlaybackQueue(it) }
            importData.queueSongs.let { if(it.isNotEmpty()) dao.insertQueueSongs(it) }
            importData.trashItems.forEach { dao.insertTrashItem(it) }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // Auto-backup to internal storage for WorkManager
    suspend fun createAutoBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
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
            val file = File(context.filesDir, "avenor_auto_backup.json")
            file.writeText(jsonString, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
