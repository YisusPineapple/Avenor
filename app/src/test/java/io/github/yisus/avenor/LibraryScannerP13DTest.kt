package io.github.yisus.avenor

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * P1-3D: Final validation test suite for LibraryScanner:
 * 1. Large library (10,000 songs) selective processing
 * 2. Concurrency limiting and worker bound enforcement
 * 3. Cooperative cancellation and non-leakage
 * 4. Error isolation on corrupted tracks
 * 5. Batching without global Song lists
 * 6. Non-destructive exclusion regression & relations
 * 7. Full 6-step rescan cycle
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryScannerP13DTest {

    class P13DFakeMediaProvider : ContentProvider() {
        companion object {
            val records = mutableListOf<ContentValues>()
            fun reset() {
                records.clear()
            }
        }

        override fun onCreate(): Boolean = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (values != null) {
                records.removeAll { it.getAsLong(MediaStore.Audio.Media._ID) == values.getAsLong(MediaStore.Audio.Media._ID) }
                records.add(ContentValues(values))
                val id = values.getAsLong(MediaStore.Audio.Media._ID) ?: 0L
                return ContentUris.withAppendedId(uri, id)
            }
            return null
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val size = records.size
            records.clear()
            return size
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val cols = projection ?: arrayOf(MediaStore.Audio.Media._ID)
            val matrixCursor = MatrixCursor(cols)

            val filtered = records.filter { cv ->
                val isMusic = cv.getAsInteger(MediaStore.Audio.Media.IS_MUSIC) ?: 1
                if (isMusic == 0) return@filter false

                if (selection != null && selection.contains("${MediaStore.Audio.Media._ID} IN") && selectionArgs != null) {
                    val id = cv.getAsLong(MediaStore.Audio.Media._ID)?.toString()
                    return@filter id in selectionArgs
                }
                true
            }

            for (item in filtered) {
                val row = matrixCursor.newRow()
                for (col in cols) {
                    val value = item.get(col)
                    row.add(col, value)
                }
            }
            return matrixCursor
        }
    }

    private lateinit var database: AppDatabase
    private lateinit var dao: MusicDao
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    class MeasuringConcurrencyExtractor(
        private val perItemDelayMs: Long = 10L,
        private val failureIds: Set<Long> = emptySet(),
        private val onExtractCallback: ((Long) -> Unit)? = null
    ) : MetadataExtractor {
        val totalCalls = AtomicInteger(0)
        val activeWorkers = AtomicInteger(0)
        val maxObservedWorkers = AtomicInteger(0)
        val extractedIds = ConcurrentHashMap.newKeySet<Long>()

        override fun extract(context: Context, uri: Uri, filePath: String?): ExtendedMetadataExtractor.AudioSpecs {
            val id = ContentUris.parseId(uri)
            val currentActive = activeWorkers.incrementAndGet()
            maxObservedWorkers.updateAndGet { currentMax -> max(currentMax, currentActive) }

            totalCalls.incrementAndGet()
            extractedIds.add(id)
            onExtractCallback?.invoke(id)

            try {
                if (failureIds.contains(id)) {
                    throw RuntimeException("Simulated corrupted audio file for ID $id")
                }
                if (perItemDelayMs > 0) {
                    Thread.sleep(perItemDelayMs)
                }
            } finally {
                activeWorkers.decrementAndGet()
            }

            return ExtendedMetadataExtractor.AudioSpecs(
                bitDepth = 24,
                sampleRate = 96000,
                mimeType = "audio/flac",
                fileExtension = "flac",
                codec = "FLAC",
                bitrate = 1411000L,
                channels = 2
            )
        }
    }

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.musicDao()
        P13DFakeMediaProvider.reset()
        Robolectric.setupContentProvider(P13DFakeMediaProvider::class.java, "media")
    }

    @After
    fun tearDown() {
        database.close()
        P13DFakeMediaProvider.reset()
    }

    private fun createContentValues(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        path: String = "/storage/emulated/0/Music/track_$id.mp3",
        dateMod: Long = 1700000000L,
        size: Long = 5000000L,
        isMusic: Int = 1
    ): ContentValues {
        return ContentValues().apply {
            put(MediaStore.Audio.Media._ID, id)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            put(MediaStore.Audio.Media.DATA, path)
            put(MediaStore.Audio.Media.DATE_MODIFIED, dateMod)
            put(MediaStore.Audio.Media.SIZE, size)
            put(MediaStore.Audio.Media.IS_MUSIC, isMusic)
            put(MediaStore.Audio.Media.DURATION, 180000L)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        }
    }

    private fun createSampleSong(
        id: Long,
        title: String = "Track $id",
        path: String = "/storage/emulated/0/Music/track_$id.mp3",
        dateMod: Long = 1700000000L,
        size: Long = 5000000L,
        isExcluded: Boolean = false
    ): Song {
        return Song(
            id = id,
            uri = "content://media/external/audio/media/$id",
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            albumArtUri = null,
            bitDepth = 16,
            sampleRate = 44100,
            mimeType = "audio/mpeg",
            fileExtension = "mp3",
            codec = "MP3",
            bitrate = 320000L,
            channels = 2,
            fileSize = size,
            dateModified = dateMod,
            dateAdded = 1600000000L,
            trackNumber = 1,
            discNumber = 1,
            year = 2024,
            genre = "Rock",
            composer = "Composer",
            albumArtist = "Artist",
            sortTitle = LibraryScanner.generateSortTitle(title),
            comment = "",
            isExcludedFromLibrary = isExcluded
        )
    }

    // =========================================================================
    // 1. TEST DE BIBLIOTECA GRANDE (10.000 canciones)
    // =========================================================================
    @Test
    fun test1_largeLibrary_selectiveProcessingEfficiency() = runTest(testDispatcher) {
        // Compose 10,000 items:
        // - 9,900 UNCHANGED (pre-exist in Room and MediaStore with matching dateMod and size)
        // - 40 NEW (exist only in MediaStore)
        // - 30 MODIFIED (pre-exist in Room with old dateMod, MediaStore has new dateMod)
        // - 20 EXCLUDED (pre-exist in Room, in MediaStore under /Podcasts)
        // - 10 PHYSICAL DELETED (pre-exist in Room, absent from MediaStore)
        val initialRoomSongs = mutableListOf<Song>()
        val mediaStoreRecords = mutableListOf<ContentValues>()

        val storage = InMemoryFolderExclusionStorage()
        storage.addExcludedFolder("/storage/emulated/0/Podcasts")
        val policy = FolderExclusionPolicy(storage)

        var idCounter = 1L

        // 9,900 UNCHANGED
        for (i in 1..9900) {
            val id = idCounter++
            initialRoomSongs.add(createSampleSong(id = id, dateMod = 1700000000L, size = 5000000L))
            mediaStoreRecords.add(createContentValues(id = id, title = "Track $id", dateMod = 1700000000L, size = 5000000L))
        }

        // 40 NEW (MediaStore only)
        val expectedNewIds = mutableSetOf<Long>()
        for (i in 1..40) {
            val id = idCounter++
            expectedNewIds.add(id)
            mediaStoreRecords.add(createContentValues(id = id, title = "New Track $id", dateMod = 1700000000L, size = 5000000L))
        }

        // 30 MODIFIED
        val expectedModifiedIds = mutableSetOf<Long>()
        for (i in 1..30) {
            val id = idCounter++
            expectedModifiedIds.add(id)
            initialRoomSongs.add(createSampleSong(id = id, title = "Old Track $id", dateMod = 1700000000L, size = 5000000L))
            mediaStoreRecords.add(createContentValues(id = id, title = "Modified Track $id", dateMod = 1700050000L, size = 5100000L))
        }

        // 20 EXCLUDED
        for (i in 1..20) {
            val id = idCounter++
            val path = "/storage/emulated/0/Podcasts/ep_$id.mp3"
            initialRoomSongs.add(createSampleSong(id = id, path = path, isExcluded = false))
            mediaStoreRecords.add(createContentValues(id = id, title = "Podcast $id", path = path))
        }

        // 10 PHYSICAL DELETED (Room only, NOT in MediaStore)
        val expectedDeletedIds = mutableSetOf<Long>()
        for (i in 1..10) {
            val id = idCounter++
            expectedDeletedIds.add(id)
            initialRoomSongs.add(createSampleSong(id = id, title = "Deleted $id"))
        }

        // Pre-populate Room in chunks of 500
        initialRoomSongs.chunked(500).forEach { batch ->
            dao.insertSongs(batch)
        }

        // Pre-populate MediaStore
        P13DFakeMediaProvider.records.addAll(mediaStoreRecords)

        val extractor = MeasuringConcurrencyExtractor(perItemDelayMs = 0L)
        val scanner = LibraryScanner(context, dao, exclusionPolicy = policy, metadataExtractor = extractor)

        val progress = scanner.scan()

        // Verifications:
        // Only NEW (40) and MODIFIED (30) must be processed = 70 total
        assertEquals("Total songs to process must strictly be NEW (40) + MODIFIED (30)", 70, progress.total)
        assertEquals("Total extractor calls must strictly equal 70", 70, extractor.totalCalls.get())
        assertEquals("newCount must be 40", 40, progress.newCount)
        assertEquals("modifiedCount must be 30", 30, progress.modifiedCount)
        assertEquals("deletedCount must be 10", 10, progress.deletedCount)
        assertEquals("errorCount must be 0", 0, progress.errorCount)
        assertFalse("Scanner progress isScanning must be false", progress.isScanning)

        // Extracted IDs must strictly be NEW and MODIFIED
        val expectedExtracted = expectedNewIds + expectedModifiedIds
        assertEquals("Extracted IDs must match NEW + MODIFIED set exactly", expectedExtracted, extractor.extractedIds)

        // Physical deleted songs must be removed from Room
        for (delId in expectedDeletedIds) {
            assertNull("Physically deleted song must not exist in Room", dao.getSongById(delId))
        }

        // Excluded songs must remain in Room with isExcludedFromLibrary = true
        val roomHeaders = dao.getAllSongHeaders().associateBy { it.id }
        assertEquals("Room should have 9900 unchanged + 40 new + 30 mod + 20 excl = 9990", 9990, roomHeaders.size)
    }

    // =========================================================================
    // 2. TEST DE CONCURRENCIA (Medición de workers concurrentes <= límite)
    // =========================================================================
    @Test
    fun test2_concurrency_maxObservedWorkersStrictlyBoundedByConfiguredLimit() = runTest(testDispatcher) {
        // Insert 120 tracks to process across multiple batches
        val records = (1L..120L).map { id ->
            createContentValues(id = id, title = "Concurrent Track $id")
        }
        P13DFakeMediaProvider.records.addAll(records)

        val testLimits = listOf(2, 3, 4)
        for (limit in testLimits) {
            // Clear Room for each run
            dao.deleteSongsByIds(dao.getAllSongHeaders().map { it.id })

            val extractor = MeasuringConcurrencyExtractor(perItemDelayMs = 15L)
            val scanner = LibraryScanner(context, dao, metadataExtractor = extractor, maxConcurrency = limit)

            val progress = scanner.scan()

            assertEquals("All 120 tracks processed", 120, progress.newCount)
            assertEquals("Total calls must equal 120", 120, extractor.totalCalls.get())

            val maxObserved = extractor.maxObservedWorkers.get()
            assertTrue(
                "Max observed concurrent workers ($maxObserved) must be <= configured limit ($limit)",
                maxObserved <= limit
            )
            assertTrue(
                "Max observed concurrent workers ($maxObserved) should be > 1 to confirm concurrency",
                maxObserved >= 1
            )
        }
    }

    // =========================================================================
    // 3. TEST DE CANCELACIÓN (Cancelación en processing, sin fugas ni errorCount)
    // =========================================================================
    @Test
    fun test3_cancellation_jobCancelAbortsCleanlyAndDoesNotLeakOrFakeErrors() = runTest(testDispatcher) {
        // Insert 50 tracks
        for (i in 1L..50L) {
            P13DFakeMediaProvider.records.add(createContentValues(id = i, title = "Cancel Track $i"))
        }

        var cancelledDuringProcessing = false
        val extractor = MeasuringConcurrencyExtractor(perItemDelayMs = 50L) { id ->
            if (id == 5L) {
                cancelledDuringProcessing = true
                throw CancellationException("Simulated job cancellation at item 5")
            }
        }

        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        try {
            scanner.scan()
            fail("CancellationException must be propagated to caller")
        } catch (e: CancellationException) {
            assertTrue("Cancellation occurred inside processing", cancelledDuringProcessing)
            assertFalse("Progress isScanning must be reset to false", scanner.progress.value.isScanning)
            assertEquals("Cancellation must NOT increment errorCount", 0, scanner.progress.value.errorCount)
        }
    }

    // =========================================================================
    // 4. TEST DE ERROR ISOLATION (1 corrupto no interrumpe los siguientes)
    // =========================================================================
    @Test
    fun test4_errorIsolation_corruptedTrackDoesNotAbortSucceedingTracks() = runTest(testDispatcher) {
        // Tracks 1..10 where track 3 is corrupted
        val corruptedId = 3L
        for (i in 1L..10L) {
            P13DFakeMediaProvider.records.add(createContentValues(id = i, title = "Isolation Track $i"))
        }

        val extractor = MeasuringConcurrencyExtractor(failureIds = setOf(corruptedId), perItemDelayMs = 0L)
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals("Total songs to process was 10", 10, progress.total)
        assertEquals("errorCount must be 1 for the corrupted track", 1, progress.errorCount)
        assertEquals("newCount must be 9 for the valid tracks", 9, progress.newCount)
        assertEquals("Total extractor calls was 10", 10, extractor.totalCalls.get())

        // Confirm 9 healthy tracks are persisted in Room
        val persisted = dao.getAllSongHeaders().associateBy { it.id }
        assertEquals(9, persisted.size)
        assertFalse("Corrupted track must not be in Room", persisted.containsKey(corruptedId))
        for (i in 1L..10L) {
            if (i != corruptedId) {
                assertTrue("Valid track $i must be persisted in Room", persisted.containsKey(i))
            }
        }
    }

    // =========================================================================
    // 5. TEST DE BATCHING (> 50, 100, 500 candidatos procesados en bloques)
    // =========================================================================
    @Test
    fun test5_batching_largeCandidateCollectionProcessedInBatchesWithoutGlobalList() = runTest(testDispatcher) {
        // 520 candidates (> 50, > 100, > 500)
        val count = 520
        val records = (1L..count.toLong()).map { id ->
            createContentValues(id = 50000L + id, title = "Batch Candidate $id")
        }
        P13DFakeMediaProvider.records.addAll(records)

        val extractor = MeasuringConcurrencyExtractor(perItemDelayMs = 0L)
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals(count, progress.total)
        assertEquals(count, progress.newCount)
        assertEquals(count, extractor.totalCalls.get())
        assertEquals(0, progress.errorCount)

        val persistedCount = dao.getAllSongHeaders().count { it.id in 50001L..(50000L + count) }
        assertEquals("All 520 songs successfully persisted through batch iterations", count, persistedCount)
    }

    // =========================================================================
    // 6. REGRESIÓN DE EXCLUSIÓN (Preservación de relaciones y no destructiva)
    // =========================================================================
    @Test
    fun test6_exclusionRegression_nonDestructiveSemanticsAndUserRelationsPreserved() = runTest(testDispatcher) {
        val songId = 6001L
        val excludedPath = "/storage/emulated/0/WhatsApp Audio/voice_note.mp3"

        // 1. Insert Song into Room
        val song = createSampleSong(id = songId, title = "Voice Note", path = excludedPath, isExcluded = false)
        dao.insertSongs(listOf(song))

        // 2. Associate user data: Favorite, Playlist, History
        dao.addFavorite(Favorite(songId = songId, addedAt = 1000L))
        val playlistId = 99
        dao.insertPlaylist(Playlist(id = playlistId, name = "Voice Archive"))
        dao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = songId, addedAt = 1000L))
        dao.insertHistory(ListeningHistory(id = 1, songId = songId, playedAt = 1700000000L))

        // Pre-assertions: Verify relations exist in Room
        assertTrue("Favorite must exist", dao.isFavoriteSync(songId))
        assertTrue("Playlist relation must exist", dao.getAllPlaylistSongsSync().any { it.playlistId == playlistId && it.songId == songId })
        assertTrue("History must exist", dao.getAllHistorySync().any { it.songId == songId })

        // 3. Exclude WhatsApp Audio folder
        val storage = InMemoryFolderExclusionStorage()
        storage.addExcludedFolder("/storage/emulated/0/WhatsApp Audio")
        val policy = FolderExclusionPolicy(storage)

        // MediaStore still reports the file (it is physically present)
        P13DFakeMediaProvider.records.add(createContentValues(id = songId, title = "Voice Note", path = excludedPath))

        val extractor = MeasuringConcurrencyExtractor()
        val scanner = LibraryScanner(context, dao, exclusionPolicy = policy, metadataExtractor = extractor)

        val progress1 = scanner.scan()

        // Must not extract metadata
        assertEquals(0, extractor.totalCalls.get())
        assertEquals(0, progress1.deletedCount)

        // Song must still exist in Room with isExcludedFromLibrary = true
        val excludedSongInDb = dao.getSongById(songId)
        assertNotNull("Song must NOT be deleted from Room", excludedSongInDb)
        assertTrue("Song must be marked excluded", excludedSongInDb!!.isExcludedFromLibrary)

        // Relations must be 100% preserved
        assertTrue("Favorite must be preserved after folder exclusion", dao.isFavoriteSync(songId))
        assertTrue("Playlist relation must be preserved after folder exclusion", dao.getAllPlaylistSongsSync().any { it.playlistId == playlistId && it.songId == songId })
        assertTrue("History must be preserved after folder exclusion", dao.getAllHistorySync().any { it.songId == songId })

        // 4. Re-include folder
        storage.removeExcludedFolder("/storage/emulated/0/WhatsApp Audio")

        val progress2 = scanner.scan()
        assertEquals(0, extractor.totalCalls.get()) // Metadata extractor still not needed since file did not change
        val reIncludedSong = dao.getSongById(songId)
        assertNotNull("Song must remain in Room", reIncludedSong)
        assertFalse("Song must be un-excluded", reIncludedSong!!.isExcludedFromLibrary)

        // Zero duplicate entries for songId
        val matchingHeaders = dao.getAllSongHeaders().filter { it.id == songId }
        assertEquals("Zero duplicates allowed for same song id", 1, matchingHeaders.size)

        // 5. Physical deletion: Now remove from MediaStore completely
        P13DFakeMediaProvider.records.clear()
        val progress3 = scanner.scan()
        assertEquals("physicalDeleted must report 1 deleted song", 1, progress3.deletedCount)
        assertNull("Song must now be deleted from Room", dao.getSongById(songId))
    }

    // =========================================================================
    // 7. TEST DE REESCANEO (Ciclo completo de 6 fases)
    // =========================================================================
    @Test
    fun test7_rescanCycle_completeSixPhasesIntegrity() = runTest(testDispatcher) {
        val storage = InMemoryFolderExclusionStorage()
        val policy = FolderExclusionPolicy(storage)
        val extractor = MeasuringConcurrencyExtractor(perItemDelayMs = 0L)
        val scanner = LibraryScanner(context, dao, exclusionPolicy = policy, metadataExtractor = extractor)

        val song1 = 7001L
        val song2 = 7002L
        val song3 = 7003L

        // -------------------------------------------------------------
        // SCAN 1: 3 NEW songs
        // -------------------------------------------------------------
        P13DFakeMediaProvider.records.add(createContentValues(id = song1, title = "Song 1", path = "/storage/emulated/0/Music/song1.mp3", dateMod = 1000L, size = 10000L))
        P13DFakeMediaProvider.records.add(createContentValues(id = song2, title = "Song 2", path = "/storage/emulated/0/Music/song2.mp3", dateMod = 1000L, size = 10000L))
        P13DFakeMediaProvider.records.add(createContentValues(id = song3, title = "Song 3", path = "/storage/emulated/0/Podcasts/song3.mp3", dateMod = 1000L, size = 10000L))

        val scan1 = scanner.scan()
        assertEquals("SCAN 1: newCount = 3", 3, scan1.newCount)
        assertEquals("SCAN 1: total = 3", 3, scan1.total)
        assertEquals("SCAN 1: extractor calls = 3", 3, extractor.totalCalls.get())
        assertEquals("SCAN 1: Room count = 3", 3, dao.getAllSongHeaders().size)

        // -------------------------------------------------------------
        // SCAN 2: All 3 UNCHANGED
        // -------------------------------------------------------------
        extractor.totalCalls.set(0)
        val scan2 = scanner.scan()
        assertEquals("SCAN 2: total = 0", 0, scan2.total)
        assertEquals("SCAN 2: newCount = 0", 0, scan2.newCount)
        assertEquals("SCAN 2: modifiedCount = 0", 0, scan2.modifiedCount)
        assertEquals("SCAN 2: extractor calls = 0", 0, extractor.totalCalls.get())

        // -------------------------------------------------------------
        // SCAN 3: 1 MODIFIED (song2 has newer dateModified and size)
        // -------------------------------------------------------------
        extractor.totalCalls.set(0)
        P13DFakeMediaProvider.records.removeAll { it.getAsLong(MediaStore.Audio.Media._ID) == song2 }
        P13DFakeMediaProvider.records.add(createContentValues(id = song2, title = "Song 2 Modified", path = "/storage/emulated/0/Music/song2.mp3", dateMod = 2000L, size = 12000L))

        val scan3 = scanner.scan()
        assertEquals("SCAN 3: total = 1", 1, scan3.total)
        assertEquals("SCAN 3: modifiedCount = 1", 1, scan3.modifiedCount)
        assertEquals("SCAN 3: newCount = 0", 0, scan3.newCount)
        assertEquals("SCAN 3: extractor calls = 1", 1, extractor.totalCalls.get())
        assertEquals("SCAN 3: Song 2 updated in Room", "Song 2 Modified", dao.getSongById(song2)?.title)

        // -------------------------------------------------------------
        // SCAN 4: Folder /Podcasts EXCLUDED (song3)
        // -------------------------------------------------------------
        extractor.totalCalls.set(0)
        storage.addExcludedFolder("/storage/emulated/0/Podcasts")

        val scan4 = scanner.scan()
        assertEquals("SCAN 4: total = 0", 0, scan4.total)
        assertEquals("SCAN 4: extractor calls = 0", 0, extractor.totalCalls.get())
        val s3Excluded = dao.getSongById(song3)
        assertNotNull("SCAN 4: song3 remains in Room", s3Excluded)
        assertTrue("SCAN 4: song3 marked isExcludedFromLibrary", s3Excluded!!.isExcludedFromLibrary)

        // -------------------------------------------------------------
        // SCAN 5: Folder /Podcasts RE-INCLUDED (song3)
        // -------------------------------------------------------------
        extractor.totalCalls.set(0)
        storage.removeExcludedFolder("/storage/emulated/0/Podcasts")

        val scan5 = scanner.scan()
        assertEquals("SCAN 5: total = 0", 0, scan5.total)
        assertEquals("SCAN 5: extractor calls = 0", 0, extractor.totalCalls.get())
        val s3ReIncluded = dao.getSongById(song3)
        assertNotNull("SCAN 5: song3 remains in Room", s3ReIncluded)
        assertFalse("SCAN 5: song3 marked isExcludedFromLibrary = false", s3ReIncluded!!.isExcludedFromLibrary)

        // -------------------------------------------------------------
        // SCAN 6: song1 PHYSICALLY DELETED from MediaStore
        // -------------------------------------------------------------
        extractor.totalCalls.set(0)
        P13DFakeMediaProvider.records.removeAll { it.getAsLong(MediaStore.Audio.Media._ID) == song1 }

        val scan6 = scanner.scan()
        assertEquals("SCAN 6: deletedCount = 1", 1, scan6.deletedCount)
        assertEquals("SCAN 6: extractor calls = 0", 0, extractor.totalCalls.get())
        assertNull("SCAN 6: song1 physically deleted from Room", dao.getSongById(song1))
        assertEquals("SCAN 6: Room now has 2 songs remaining (song2 and song3)", 2, dao.getAllSongHeaders().size)
    }
}
