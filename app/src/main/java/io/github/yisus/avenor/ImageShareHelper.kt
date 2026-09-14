package io.github.yisus.avenor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ImageShareHelper {
    fun shareBitmap(context: Context, bitmap: Bitmap, shareText: String) {
        try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/recap.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val imagePath = File(context.cacheDir, "images")
            val newFile = File(imagePath, "recap.png")
            val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newFile)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "image/png"
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Recap"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
