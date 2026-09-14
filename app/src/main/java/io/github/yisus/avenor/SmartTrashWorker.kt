package io.github.yisus.avenor

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import androidx.core.net.toUri

class SmartTrashWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d("SmartTrashWorker", "Running automated trash purge...")
        try {
            val dao = AppDatabase.getDatabase(applicationContext).musicDao()
            val settings = dao.getSettingsSync()
            val purgeDays = settings?.trashPurgeDays ?: 30
            val threshold = System.currentTimeMillis() - (purgeDays * 24L * 60L * 60L * 1000L)
            val expiredItems = dao.getExpiredTrashItems(threshold)
            
            expiredItems.forEach { item ->
                try {
                    val uri = Uri.parse(dao.getSongById(item.songId.toInt())?.uri ?: "")
                    if (uri.scheme == "file") {
                        val file = File(uri.path!!)
                        if (file.exists()) file.delete()
                    } else if (uri.scheme == "content") {
                        applicationContext.contentResolver.delete(uri, null, null)
                    }
                } catch(e: Exception) {
                    Log.e("SmartTrashWorker", "Could not delete file: ${dao.getSongById(item.songId.toInt())?.uri ?: ""}", e)
                }
                Log.d("SmartTrashWorker", "Purging song ID: ${item.songId}")
                dao.deleteTrashItem(item.songId)
            }
            
            return Result.success()
        } catch(e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
