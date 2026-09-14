package io.github.yisus.avenor

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri

object SmartTrashManager {
    suspend fun moveToTrash(context: Context, song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashDir = File(context.filesDir, ".trash")
            if (!trashDir.exists()) trashDir.mkdirs()
            
            // Just simulate a logical move for safety if it's external, 
            // or literally copy and delete if permitted.
            // In a real app we'd use MediaStore to move it to trash (Android 11+)
            
            // Update DAO
            AppDatabase.getDatabase(context).musicDao().deleteSong(song)
            true
        } catch(e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
