package io.github.yisus.avenor

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ScanProgress(
    val isScanning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val newCount: Int = 0,
    val modifiedCount: Int = 0,
    val deletedCount: Int = 0,
    val errorCount: Int = 0
)

class LibraryScanner(
    private val context: Context,
    private val dao: MusicDao
) {
    companion object {
        private const val TAG = "LibraryScanner"
        private const val BATCH_SIZE = 50

        /**
         * Generates a deterministic sortTitle by trimming and stripping common leading articles
         * ("The ", "A ", "An ") case-insensitively, providing a stable sort key.
         */
        fun generateSortTitle(title: String): String {
            val trimmed = title.trim()
            val lower = trimmed.lowercase()
            return when {
                lower.startsWith("the ") -> trimmed.substring(4).trim()
                lower.startsWith("a ") -> trimmed.substring(2).trim()
                lower.startsWith("an ") -> trimmed.substring(3).trim()
                else -> trimmed
            }.ifEmpty { trimmed }
        }
    }

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    data class MediaStoreItemHeader(
        val id: Long,
        val dateModified: Long,
        val size: Long
    )

    suspend fun scan(): ScanProgress = withContext(Dispatchers.IO) {
        _progress.value = ScanProgress(isScanning = true)
        var newCount = 0
        var modifiedCount = 0
        var deletedCount = 0
        var errorCount = 0

        try {
            ensureActive()
            val existingHeaders = dao.getAllSongHeaders().associateBy { it.id }

            val mediaStoreHeaders = mutableMapOf<Long, MediaStoreItemHeader>()
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val headerProjection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.SIZE
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            context.contentResolver.query(
                collection,
                headerProjection,
                selection,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dateMod = cursor.getLong(dateModCol)
                    val size = cursor.getLong(sizeCol)
                    mediaStoreHeaders[id] = MediaStoreItemHeader(id, dateMod, size)
                }
            }

            ensureActive()

            // 1. Identify DELETED songs (in Room, not in MediaStore)
            val deletedIds = existingHeaders.keys.filter { !mediaStoreHeaders.containsKey(it) }
            if (deletedIds.isNotEmpty()) {
                deletedIds.chunked(500).forEach { batch ->
                    ensureActive()
                    dao.deleteSongsByIds(batch)
                }
                deletedCount = deletedIds.size
            }

            // 2. Identify NEW and MODIFIED songs
            val songsToProcess = mutableListOf<Long>()
            for ((id, msHeader) in mediaStoreHeaders) {
                val existing = existingHeaders[id]
                if (existing == null) {
                    songsToProcess.add(id)
                } else if (existing.dateModified != msHeader.dateModified || existing.fileSize != msHeader.size) {
                    songsToProcess.add(id)
                }
            }

            val totalToProcess = songsToProcess.size
            _progress.value = ScanProgress(
                isScanning = true,
                current = 0,
                total = totalToProcess,
                newCount = 0,
                modifiedCount = 0,
                deletedCount = deletedCount,
                errorCount = 0
            )

            // 3. Process NEW and MODIFIED songs in batches
            if (songsToProcess.isNotEmpty()) {
                val fullProjection = getProjection()

                for (chunk in songsToProcess.chunked(BATCH_SIZE)) {
                    ensureActive()
                    val batchSongs = mutableListOf<Song>()
                    val inClause = chunk.joinToString(",") { "?" }
                    val chunkSelection = "${MediaStore.Audio.Media._ID} IN ($inClause)"
                    val selectionArgs = chunk.map { it.toString() }.toTypedArray()

                    context.contentResolver.query(
                        collection,
                        fullProjection,
                        chunkSelection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            ensureActive()
                            try {
                                val song = extractSongFromCursor(cursor)
                                if (existingHeaders.containsKey(song.id)) {
                                    modifiedCount++
                                } else {
                                    newCount++
                                }
                                batchSongs.add(song)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing media item", e)
                                errorCount++
                            }
                        }
                    }

                    if (batchSongs.isNotEmpty()) {
                        dao.insertSongs(batchSongs)
                    }

                    _progress.value = ScanProgress(
                        isScanning = true,
                        current = (newCount + modifiedCount + errorCount).coerceAtMost(totalToProcess),
                        total = totalToProcess,
                        newCount = newCount,
                        modifiedCount = modifiedCount,
                        deletedCount = deletedCount,
                        errorCount = errorCount
                    )
                }
            }

            val finalProgress = ScanProgress(
                isScanning = false,
                current = totalToProcess,
                total = totalToProcess,
                newCount = newCount,
                modifiedCount = modifiedCount,
                deletedCount = deletedCount,
                errorCount = errorCount
            )
            _progress.value = finalProgress
            finalProgress
        } catch (e: Exception) {
            Log.e(TAG, "Library scan failed or cancelled", e)
            val errorProgress = ScanProgress(
                isScanning = false,
                current = 0,
                total = 0,
                newCount = newCount,
                modifiedCount = modifiedCount,
                deletedCount = deletedCount,
                errorCount = errorCount + 1
            )
            _progress.value = errorProgress
            errorProgress
        }
    }

    private fun getProjection(): Array<String> {
        val list = mutableListOf(
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(MediaStore.Audio.Media.DISC_NUMBER)
            list.add(MediaStore.Audio.Media.ALBUM_ARTIST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list.add(MediaStore.Audio.Media.GENRE)
            list.add(MediaStore.Audio.Media.BITRATE)
        }
        return list.toTypedArray()
    }

    private fun extractSongFromCursor(cursor: Cursor): Song {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val title = cursor.getStringOrNull(MediaStore.Audio.Media.TITLE) ?: "Unknown Title"
        val artist = cursor.getStringOrNull(MediaStore.Audio.Media.ARTIST) ?: "Unknown Artist"
        val album = cursor.getStringOrNull(MediaStore.Audio.Media.ALBUM) ?: "Unknown Album"
        val albumId = cursor.getLongOrNull(MediaStore.Audio.Media.ALBUM_ID) ?: 0L
        val duration = cursor.getLongOrNull(MediaStore.Audio.Media.DURATION) ?: 0L
        val filePath = cursor.getStringOrNull(MediaStore.Audio.Media.DATA)
        val size = cursor.getLongOrNull(MediaStore.Audio.Media.SIZE) ?: 0L
        val dateMod = cursor.getLongOrNull(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0L
        val dateAdd = cursor.getLongOrNull(MediaStore.Audio.Media.DATE_ADDED) ?: 0L
        val mimeType = cursor.getStringOrNull(MediaStore.Audio.Media.MIME_TYPE) ?: "audio/mpeg"

        val rawTrack = cursor.getIntOrNull(MediaStore.Audio.Media.TRACK) ?: 0
        var trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack
        var discNumber = if (rawTrack >= 1000) rawTrack / 1000 else 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val discCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
            if (discCol != -1 && !cursor.isNull(discCol)) {
                val disc = cursor.getInt(discCol)
                if (disc > 0) discNumber = disc
            }
        }

        val year = cursor.getIntOrNull(MediaStore.Audio.Media.YEAR) ?: 0
        val composer = cursor.getStringOrNull(MediaStore.Audio.Media.COMPOSER) ?: ""

        var albumArtist = artist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val aaCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            if (aaCol != -1 && !cursor.isNull(aaCol)) {
                val aa = cursor.getString(aaCol)
                if (!aa.isNullOrBlank()) albumArtist = aa
            }
        }

        var genre = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            if (genreCol != -1 && !cursor.isNull(genreCol)) {
                genre = cursor.getString(genreCol) ?: ""
            }
        }

        var bitrate = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val brCol = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
            if (brCol != -1 && !cursor.isNull(brCol)) {
                bitrate = cursor.getLong(brCol)
            }
        }

        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val albumArtUri = if (albumId > 0) "content://media/external/audio/albumart/$albumId" else null

        // Extract technical specs using ExtendedMetadataExtractor
        val specs = try {
            ExtendedMetadataExtractor.extract(context, contentUri, filePath)
        } catch (e: Exception) {
            ExtendedMetadataExtractor.AudioSpecs(mimeType = mimeType)
        }

        val finalBitrate = if (bitrate > 0) bitrate else specs.bitrate
        val sortTitle = generateSortTitle(title)

        return Song(
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
            bitrate = finalBitrate,
            channels = specs.channels,
            fileSize = size,
            dateModified = dateMod,
            dateAdded = dateAdd,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            genre = genre,
            composer = composer,
            albumArtist = albumArtist,
            sortTitle = sortTitle,
            comment = "",
            replayGainTrack = null,
            replayGainAlbum = null,
            artworkWidth = 0,
            artworkHeight = 0,
            artworkMimeType = ""
        )
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val col = getColumnIndex(columnName)
        return if (col != -1 && !isNull(col)) getString(col) else null
    }

    private fun Cursor.getLongOrNull(columnName: String): Long? {
        val col = getColumnIndex(columnName)
        return if (col != -1 && !isNull(col)) getLong(col) else null
    }

    private fun Cursor.getIntOrNull(columnName: String): Int? {
        val col = getColumnIndex(columnName)
        return if (col != -1 && !isNull(col)) getInt(col) else null
    }
}
