package io.github.yisus.nexo

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d("BackupWorker", "Running automated weekly JSON backup...")
        val success = BackupManager.createAutoBackup(applicationContext)
        return if (success) {
            Log.d("BackupWorker", "Automated backup successful.")
            Result.success()
        } else {
            Log.e("BackupWorker", "Automated backup failed.")
            Result.retry()
        }
    }
}
