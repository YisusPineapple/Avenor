package io.github.yisus.avenor

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

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
    private val dao: MusicDao,
    val exclusionPolicy: FolderExclusionPolicy = FolderExclusionPolicy(
        storage = SharedPreferencesFolderExclusionStorage(context.applicationContext),
        noMediaDetector = DefaultNoMediaDetector()
    ),
    private val metadataExtractor: MetadataExtractor = ExtendedMetadataExtractor,
    private val maxConcurrency: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
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

    data class DiscoveredMediaRecord(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val duration: Long,
        val filePath: String?,
        val relativePath: String?,
        val size: Long,
        val dateModified: Long,
        val dateAdded: Long,
        val mimeType: String,
        val trackNumber: Int,
        val discNumber: Int,
        val year: Int,
        val composer: String,
        val albumArtist: String,
        val genre: String,
        val bitrate: Long
    )

    suspend fun scan(): ScanProgress = withContext(Dispatchers.IO) {
        _progress.value = ScanProgress(isScanning = true)
        exclusionPolicy.clearNoMediaCache()
        var newCount = 0
        var modifiedCount = 0
        var deletedCount = 0
        val errorCount = AtomicInteger(0)

        try {
            ensureActive()
            val existingHeaders = dao.getAllSongHeaders().associateBy { it.id }

            // =========================================================================
            // FASE 1 — LIGHTWEIGHT DISCOVERY (Cursor opens, reads minimal fields, closes)
            // =========================================================================
            val allMediaStoreIds = mutableSetOf<Long>()
            val activeMediaStoreHeaders = mutableMapOf<Long, MediaStoreItemHeader>()
            val excludedIds = mutableSetOf<Long>()

            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val headerProjectionList = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATA
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                headerProjectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
            val headerProjection = headerProjectionList.toTypedArray()
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
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    ensureActive()
                    val id = cursor.getLong(idCol)
                    allMediaStoreIds.add(id)
                    val dateMod = cursor.getLong(dateModCol)
                    val size = cursor.getLong(sizeCol)
                    val filePath = if (dataCol != -1 && !cursor.isNull(dataCol)) cursor.getString(dataCol) else null
                    val relativePath = if (relPathCol != -1 && !cursor.isNull(relPathCol)) cursor.getString(relPathCol) else null

                    // Exclude based on folder rules and .nomedia
                    if (exclusionPolicy.isExcluded(filePath, relativePath)) {
                        excludedIds.add(id)
                        continue
                    }

                    activeMediaStoreHeaders[id] = MediaStoreItemHeader(id, dateMod, size)
                }
            }

            ensureActive()

            // =========================================================================
            // FASE 2 — CLASIFICACIÓN
            // =========================================================================
            // 1. Identify PHYSICAL DELETED songs (in Room, but completely missing from MediaStore and not excluded)
            val physicalDeletedIds = existingHeaders.keys.filter { it !in allMediaStoreIds && it !in excludedIds }
            if (physicalDeletedIds.isNotEmpty()) {
                physicalDeletedIds.chunked(500).forEach { batch ->
                    ensureActive()
                    dao.deleteSongsByIds(batch)
                }
                deletedCount = physicalDeletedIds.size
            }

            // 2. Identify EXCLUDED songs (in Room, present in MediaStore, but matching excluded folder or .nomedia)
            val toMarkExcluded = existingHeaders.keys.filter {
                it in excludedIds && !(existingHeaders[it]?.isExcludedFromLibrary ?: false)
            }
            if (toMarkExcluded.isNotEmpty()) {
                toMarkExcluded.chunked(500).forEach { batch ->
                    ensureActive()
                    dao.updateSongsExcludedStatus(batch, true)
                }
            }

            // 3. Identify RE-INCLUDED songs (were previously marked excluded in Room, but now active)
            val toMarkReIncluded = activeMediaStoreHeaders.keys.filter {
                existingHeaders[it]?.isExcludedFromLibrary == true
            }
            if (toMarkReIncluded.isNotEmpty()) {
                toMarkReIncluded.chunked(500).forEach { batch ->
                    ensureActive()
                    dao.updateSongsExcludedStatus(batch, false)
                }
            }

            // 4. Identify NEW and MODIFIED songs to process
            val songsToProcess = mutableListOf<Long>()
            for ((id, msHeader) in activeMediaStoreHeaders) {
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

            // =========================================================================
            // FASE 3 — EXTRACCIÓN PESADA Y PERSISTENCIA POR LOTES
            // =========================================================================
            if (songsToProcess.isNotEmpty()) {
                val fullProjection = getProjection()
                val semaphore = Semaphore(maxConcurrency)

                for (chunk in songsToProcess.chunked(BATCH_SIZE)) {
                    ensureActive()
                    val inClause = chunk.joinToString(",") { "?" }
                    val chunkSelection = "${MediaStore.Audio.Media._ID} IN ($inClause)"
                    val selectionArgs = chunk.map { it.toString() }.toTypedArray()

                    // Read lightweight media records from MediaStore into temporary list, then close cursor immediately
                    val chunkRecords = mutableListOf<DiscoveredMediaRecord>()
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
                                chunkRecords.add(readDiscoveredMediaRecord(cursor))
                            } catch (e: Exception) {
                                Log.e(TAG, "Error reading record from cursor", e)
                                errorCount.incrementAndGet()
                            }
                        }
                    }

                    ensureActive()

                    // Heavy metadata extraction using structured concurrency and bounded Semaphore
                    val extractedSongs = coroutineScope {
                        chunkRecords.map { record ->
                            async {
                                semaphore.withPermit {
                                    ensureActive()
                                    try {
                                        extractSongFromRecord(record)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error extracting metadata for song ${record.id}", e)
                                        errorCount.incrementAndGet()
                                        null
                                    }
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }

                    ensureActive()

                    val batchSongsToInsert = mutableListOf<Song>()
                    val batchSongsToUpdate = mutableListOf<Song>()
                    for (song in extractedSongs) {
                        if (existingHeaders.containsKey(song.id)) {
                            modifiedCount++
                            batchSongsToUpdate.add(song)
                        } else {
                            newCount++
                            batchSongsToInsert.add(song)
                        }
                    }

                    if (batchSongsToInsert.isNotEmpty()) {
                        dao.insertSongs(batchSongsToInsert)
                    }
                    if (batchSongsToUpdate.isNotEmpty()) {
                        dao.updateSongs(batchSongsToUpdate)
                    }

                    val currentErrors = errorCount.get()
                    _progress.value = ScanProgress(
                        isScanning = true,
                        current = (newCount + modifiedCount + currentErrors).coerceAtMost(totalToProcess),
                        total = totalToProcess,
                        newCount = newCount,
                        modifiedCount = modifiedCount,
                        deletedCount = deletedCount,
                        errorCount = currentErrors
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
                errorCount = errorCount.get()
            )
            _progress.value = finalProgress
            finalProgress
        } catch (e: CancellationException) {
            _progress.value = _progress.value.copy(isScanning = false)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Library scan failed", e)
            val errorProgress = ScanProgress(
                isScanning = false,
                current = 0,
                total = 0,
                newCount = newCount,
                modifiedCount = modifiedCount,
                deletedCount = deletedCount,
                errorCount = errorCount.get() + 1
            )
            _progress.value = errorProgress
            errorProgress
        }
    }

    /**
     * Queries and parses all audio files from MediaStore matching the music filter.
     * Centralizes the MediaStore projection, selection, and Song mapping to avoid duplicate implementations.
     */
    suspend fun queryLocalAudioFiles(): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = getProjection()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val records = mutableListOf<DiscoveredMediaRecord>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val record = readDiscoveredMediaRecord(cursor)
                        if (!exclusionPolicy.isExcluded(record.filePath, record.relativePath)) {
                            records.add(record)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading record from cursor", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query local audio files", e)
        }

        val songs = mutableListOf<Song>()
        val semaphore = Semaphore(maxConcurrency)
        for (chunk in records.chunked(BATCH_SIZE)) {
            ensureActive()
            val chunkSongs = coroutineScope {
                chunk.map { record ->
                    async {
                        semaphore.withPermit {
                            ensureActive()
                            try {
                                extractSongFromRecord(record)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error extracting metadata for song ${record.id}", e)
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            songs.addAll(chunkSongs)
        }
        songs
    }

    fun getExcludedFolders(): Set<String> = exclusionPolicy.getExcludedFolders()

    fun addExcludedFolder(path: String) = exclusionPolicy.addExcludedFolder(path)

    fun removeExcludedFolder(path: String) = exclusionPolicy.removeExcludedFolder(path)

    fun setExcludedFolders(folders: Set<String>) = exclusionPolicy.setExcludedFolders(folders)

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
            list.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list.add(MediaStore.Audio.Media.GENRE)
            list.add(MediaStore.Audio.Media.BITRATE)
        }
        return list.toTypedArray()
    }

    private fun readDiscoveredMediaRecord(cursor: Cursor): DiscoveredMediaRecord {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val title = cursor.getStringOrNull(MediaStore.Audio.Media.TITLE) ?: "Unknown Title"
        val artist = cursor.getStringOrNull(MediaStore.Audio.Media.ARTIST) ?: "Unknown Artist"
        val album = cursor.getStringOrNull(MediaStore.Audio.Media.ALBUM) ?: "Unknown Album"
        val albumId = cursor.getLongOrNull(MediaStore.Audio.Media.ALBUM_ID) ?: 0L
        val duration = cursor.getLongOrNull(MediaStore.Audio.Media.DURATION) ?: 0L
        val filePath = cursor.getStringOrNull(MediaStore.Audio.Media.DATA)
        val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getStringOrNull(MediaStore.Audio.Media.RELATIVE_PATH)
        } else null
        val size = cursor.getLongOrNull(MediaStore.Audio.Media.SIZE) ?: 0L
        val dateMod = cursor.getLongOrNull(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0L
        val dateAdd = cursor.getLongOrNull(MediaStore.Audio.Media.DATE_ADDED) ?: 0L
        val mimeType = cursor.getStringOrNull(MediaStore.Audio.Media.MIME_TYPE) ?: "audio/mpeg"

        val rawTrack = cursor.getIntOrNull(MediaStore.Audio.Media.TRACK) ?: 0
        val trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack
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

        return DiscoveredMediaRecord(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            duration = duration,
            filePath = filePath,
            relativePath = relativePath,
            size = size,
            dateModified = dateMod,
            dateAdded = dateAdd,
            mimeType = mimeType,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            composer = composer,
            albumArtist = albumArtist,
            genre = genre,
            bitrate = bitrate
        )
    }

    private fun extractSongFromRecord(record: DiscoveredMediaRecord): Song {
        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, record.id)
        val albumArtUri = if (record.albumId > 0) "content://media/external/audio/albumart/${record.albumId}" else null

        // Extract technical specs using MetadataExtractor (throws on failure so caller can record error)
        val specs = metadataExtractor.extract(context, contentUri, record.filePath)

        val finalBitrate = if (record.bitrate > 0) record.bitrate else specs.bitrate
        val sortTitle = generateSortTitle(record.title)

        return Song(
            id = record.id,
            uri = contentUri.toString(),
            title = record.title,
            artist = record.artist,
            album = record.album,
            durationMs = record.duration,
            albumArtUri = albumArtUri,
            bitDepth = specs.bitDepth,
            sampleRate = specs.sampleRate,
            mimeType = if (specs.mimeType.isNotBlank()) specs.mimeType else record.mimeType,
            fileExtension = specs.fileExtension,
            codec = specs.codec,
            bitrate = finalBitrate,
            channels = specs.channels,
            fileSize = record.size,
            dateModified = record.dateModified,
            dateAdded = record.dateAdded,
            trackNumber = record.trackNumber,
            discNumber = record.discNumber,
            year = record.year,
            genre = record.genre,
            composer = record.composer,
            albumArtist = record.albumArtist,
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
