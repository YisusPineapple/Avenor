package io.github.yisus.avenor

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class AudioRepository(private val context: Context, dao: MusicDao? = null) {
    private val musicDao: MusicDao = dao ?: AppDatabase.getDatabase(context).musicDao()
    val scanner = LibraryScanner(context, musicDao)
    val scanProgress: StateFlow<ScanProgress> = scanner.progress

    suspend fun scanLibrary(): ScanProgress = scanner.scan()

    suspend fun getLocalAudioFiles(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.COMPOSER
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateModColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val dateAddColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val composerColumn = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val size = if (sizeColumn != -1 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else 0L
                val dateMod = if (dateModColumn != -1 && !cursor.isNull(dateModColumn)) cursor.getLong(dateModColumn) else 0L
                val dateAdd = if (dateAddColumn != -1 && !cursor.isNull(dateAddColumn)) cursor.getLong(dateAddColumn) else 0L
                val mimeType = if (mimeTypeColumn != -1 && !cursor.isNull(mimeTypeColumn)) cursor.getString(mimeTypeColumn) ?: "audio/mpeg" else "audio/mpeg"
                val rawTrack = if (trackColumn != -1 && !cursor.isNull(trackColumn)) cursor.getInt(trackColumn) else 0
                val trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack
                val discNumber = if (rawTrack >= 1000) rawTrack / 1000 else 1
                val year = if (yearColumn != -1 && !cursor.isNull(yearColumn)) cursor.getInt(yearColumn) else 0
                val composer = if (composerColumn != -1 && !cursor.isNull(composerColumn)) cursor.getString(composerColumn) ?: "" else ""
                
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val albumArtUri = "content://media/external/audio/albumart/$albumId"
                val filePath = try { cursor.getString(dataColumn) } catch(e: Exception) { null }
                
                val specs = try {
                    ExtendedMetadataExtractor.extract(context, contentUri, filePath)
                } catch (e: Exception) {
                    ExtendedMetadataExtractor.AudioSpecs(mimeType = mimeType)
                }
                
                songs.add(
                    Song(
                        id = id,
                        uri = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        albumArtUri = albumArtUri,
                        bitDepth = specs.bitDepth,
                        sampleRate = specs.sampleRate,
                        mimeType = if (specs.mimeType.isNotBlank()) specs.mimeType else mimeType,
                        fileExtension = specs.fileExtension,
                        codec = specs.codec,
                        bitrate = specs.bitrate,
                        channels = specs.channels,
                        fileSize = size,
                        dateModified = dateMod,
                        dateAdded = dateAdd,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        year = year,
                        genre = "",
                        composer = composer,
                        albumArtist = artist
                    )
                )
            }
        }
        return@withContext songs
    }
}
