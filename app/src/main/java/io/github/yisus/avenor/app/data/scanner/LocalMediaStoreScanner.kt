package io.github.yisus.avenor.app.data.scanner

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedAudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String
)

class LocalMediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Suppress("DEPRECATION")
    suspend fun scanLocalAudio(): List<ScannedAudioItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val results = mutableListOf<ScannedAudioItem>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val title = cursor.getString(titleIndex).orEmpty()
                val artist = cursor.getString(artistIndex).orEmpty()
                val album = cursor.getString(albumIndex).orEmpty()
                val duration = cursor.getLong(durationIndex)
                val path = cursor.getString(dataIndex).orEmpty()

                results.add(
                    ScannedAudioItem(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        path = path
                    )
                )
            }
        }

        results
    }
}
