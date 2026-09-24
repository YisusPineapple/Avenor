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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryScannerP13CTest {

    class FakeMediaProvider : ContentProvider() {
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

    class CountingMetadataExtractor(
        private val failureIds: Set<Long> = emptySet(),
        private val onExtractCallback: ((Long) -> Unit)? = null
    ) : MetadataExtractor {
        val callCount = AtomicInteger(0)
        val extractedUris = java.util.concurrent.CopyOnWriteArrayList<Uri>()

        override fun extract(context: Context, uri: Uri, filePath: String?): ExtendedMetadataExtractor.AudioSpecs {
            val id = ContentUris.parseId(uri)
            callCount.incrementAndGet()
            extractedUris.add(uri)
            onExtractCallback?.invoke(id)

            if (failureIds.contains(id)) {
                throw RuntimeException("Simulated corrupted media file for ID $id")
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
        FakeMediaProvider.reset()
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
    }

    @After
    fun tearDown() {
        database.close()
        FakeMediaProvider.reset()
    }

    private fun insertMediaStoreAudio(
        id: Long,
        title: String,
        artist: String = "Test Artist",
        album: String = "Test Album",
        path: String = "/storage/emulated/0/Music/track_$id.mp3",
        dateMod: Long = 1700000000L,
        size: Long = 5000000L,
        isMusic: Int = 1
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media._ID, id)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            put(MediaStore.Audio.Media.DATA, path)
            put(MediaStore.Audio.Media.DATE_MODIFIED, dateMod)
            put(MediaStore.Audio.Media.SIZE, size)
            put(MediaStore.Audio.Media.IS_MUSIC, isMusic)
            put(MediaStore.Audio.Media.DURATION, 200000L)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        }
        context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun createSampleSong(
        id: Long,
        title: String = "Existing Song",
        path: String = "/storage/emulated/0/Music/track_$id.mp3",
        dateMod: Long = 1700000000L,
        size: Long = 5000000L,
        isExcluded: Boolean = false
    ): Song {
        return Song(
            id = id,
            uri = "content://media/external/audio/media/$id",
            title = title,
            artist = "Existing Artist",
            album = "Existing Album",
            durationMs = 200000L,
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
            year = 2020,
            genre = "Rock",
            composer = "Composer",
            albumArtist = "Existing Artist",
            sortTitle = LibraryScanner.generateSortTitle(title),
            comment = "",
            isExcludedFromLibrary = isExcluded
        )
    }

    // =========================================================================
    // Test 1 — UNCHANGED: Un archivo sin cambios se clasifica UNCHANGED;
    // ExtendedMetadataExtractor NO se invoca.
    // =========================================================================
    @Test
    fun test1_unchangedSongDoesNotInvokeMetadataExtractor() = runTest(testDispatcher) {
        val songId = 101L
        // Insert into Room
        val existingSong = createSampleSong(id = songId, dateMod = 1700000000L, size = 5000000L)
        dao.insertSongs(listOf(existingSong))

        // Insert identical record into MediaStore
        insertMediaStoreAudio(id = songId, title = "Existing Song", dateMod = 1700000000L, size = 5000000L)

        val extractor = CountingMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals("UNCHANGED: total to process must be 0", 0, progress.total)
        assertEquals("Extractor must NOT be called for UNCHANGED songs", 0, extractor.callCount.get())
        assertEquals(0, progress.newCount)
        assertEquals(0, progress.modifiedCount)
    }

    // =========================================================================
    // Test 2 — NEW: Una canción nueva se clasifica NEW;
    // metadata pesada se procesa; termina persistida.
    // =========================================================================
    @Test
    fun test2_newSongExtractsMetadataAndPersistsToRoom() = runTest(testDispatcher) {
        val songId = 201L
        // MediaStore has new song, Room is empty
        insertMediaStoreAudio(id = songId, title = "Brand New Song")

        val extractor = CountingMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals("NEW: total to process must be 1", 1, progress.total)
        assertEquals("NEW: newCount must be 1", 1, progress.newCount)
        assertEquals("Extractor must be called once for NEW song", 1, extractor.callCount.get())

        val songInDb = dao.getSongById(songId)
        assertNotNull("NEW song must be persisted in Room", songInDb)
        assertEquals("Brand New Song", songInDb?.title)
        assertEquals(24, songInDb?.bitDepth)
        assertEquals("FLAC", songInDb?.codec)
    }

    // =========================================================================
    // Test 3 — MODIFIED: Un archivo con dateModified/size cambiado se clasifica MODIFIED;
    // metadata pesada se reprocesa; metadata actualizada llega a Room.
    // =========================================================================
    @Test
    fun test3_modifiedSongReprocessesMetadataAndUpdatesRoom() = runTest(testDispatcher) {
        val songId = 301L
        // Room has old dateModified
        val existingSong = createSampleSong(id = songId, title = "Original Title", dateMod = 1700000000L, size = 5000000L)
        dao.insertSongs(listOf(existingSong))

        // MediaStore has updated dateModified and size
        val updatedDateMod = 1700050000L
        val updatedSize = 6000000L
        insertMediaStoreAudio(id = songId, title = "Updated Title", dateMod = updatedDateMod, size = updatedSize)

        val extractor = CountingMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals("MODIFIED: total to process must be 1", 1, progress.total)
        assertEquals("MODIFIED: modifiedCount must be 1", 1, progress.modifiedCount)
        assertEquals(0, progress.newCount)
        assertEquals("Extractor must be invoked for MODIFIED song", 1, extractor.callCount.get())

        val updatedSongInDb = dao.getSongById(songId)
        assertNotNull(updatedSongInDb)
        assertEquals(updatedDateMod, updatedSongInDb?.dateModified)
        assertEquals(updatedSize, updatedSongInDb?.fileSize)
        assertEquals("Updated Title", updatedSongInDb?.title)
    }

    // =========================================================================
    // Test 4 — EXCLUDED: Una canción excluida NO entra en extracción;
    // NO se borra; conserva isExcludedFromLibrary.
    // =========================================================================
    @Test
    fun test4_excludedSongDoesNotExtractAndPreservesInRoom() = runTest(testDispatcher) {
        val songId = 401L
        val excludedPath = "/storage/emulated/0/Podcasts/episode1.mp3"

        // Song pre-exists in Room as not excluded
        val existingSong = createSampleSong(id = songId, path = excludedPath)
        dao.insertSongs(listOf(existingSong))

        // MediaStore has it at excluded path
        insertMediaStoreAudio(id = songId, title = "Podcast Episode", path = excludedPath)

        val extractor = CountingMetadataExtractor()
        val storage = InMemoryFolderExclusionStorage()
        storage.addExcludedFolder("/storage/emulated/0/Podcasts")
        val policy = FolderExclusionPolicy(storage)

        val scanner = LibraryScanner(context, dao, exclusionPolicy = policy, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals("EXCLUDED songs must not enter songsToProcess", 0, progress.total)
        assertEquals("Extractor must NOT be called for EXCLUDED songs", 0, extractor.callCount.get())

        val songInDb = dao.getSongById(songId)
        assertNotNull("Song must NOT be deleted from Room", songInDb)
        assertTrue("Song must be marked isExcludedFromLibrary = true", songInDb!!.isExcludedFromLibrary)
    }

    // =========================================================================
    // Test 5 — PHYSICAL DELETED: Una canción ausente de MediaStore se clasifica
    // physicalDeleted; sigue la eliminación normal.
    // =========================================================================
    @Test
    fun test5_physicallyDeletedSongDeletesNormallyFromRoom() = runTest(testDispatcher) {
        val songId = 501L
        // Song in Room
        val existingSong = createSampleSong(id = songId, title = "Deleted File Song")
        dao.insertSongs(listOf(existingSong))

        // MediaStore is empty (song physically removed from disk)
        val extractor = CountingMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals(1, progress.deletedCount)
        assertEquals("Extractor must NOT be called for physical deleted song", 0, extractor.callCount.get())
        assertNull("Song must be deleted from Room", dao.getSongById(songId))
    }

    // =========================================================================
    // Test 6 — ERROR ISOLATION: Si una canción falla metadata,
    // aumenta errorCount; otras canciones continúan.
    // =========================================================================
    @Test
    fun test6_corruptedSongIsolatesErrorAndOtherSongsSucceed() = runTest(testDispatcher) {
        val badId = 601L
        val goodId = 602L

        insertMediaStoreAudio(id = badId, title = "Corrupted Track")
        insertMediaStoreAudio(id = goodId, title = "Healthy Track")

        val extractor = CountingMetadataExtractor(failureIds = setOf(badId))
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals("Total songs to process was 2", 2, progress.total)
        assertEquals("Error count should increment for failing track", 1, progress.errorCount)
        assertEquals("Good track should be processed as new", 1, progress.newCount)

        // The healthy song must be in Room
        val healthySong = dao.getSongById(goodId)
        assertNotNull("Healthy song must be in Room", healthySong)
        assertEquals("Healthy Track", healthySong?.title)
    }

    // =========================================================================
    // Test 7 — CANCELLATION: Cancelar durante processing:
    // evita trabajos posteriores; propaga cancelación correctamente.
    // =========================================================================
    @Test
    fun test7_cancellationDuringProcessingAbortsAndPropagates() = runTest(testDispatcher) {
        for (i in 701L..710L) {
            insertMediaStoreAudio(id = i, title = "Track $i")
        }

        var cancelTriggered = false
        val extractor = CountingMetadataExtractor { id ->
            if (id == 703L) {
                cancelTriggered = true
                throw CancellationException("Simulated coroutine cancellation during heavy extraction")
            }
        }

        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        try {
            scanner.scan()
            fail("CancellationException must be propagated, not swallowed")
        } catch (e: CancellationException) {
            assertTrue("Cancellation occurred as expected", cancelTriggered)
            assertFalse("Progress isScanning must be false on cancellation", scanner.progress.value.isScanning)
        }
    }

    // =========================================================================
    // Test 8 — BATCHING: Una colección grande de candidatos se procesa en batches;
    // no se materializa una List<Song> global.
    // =========================================================================
    @Test
    fun test8_largeCandidateCollectionProcessedInBatches() = runTest(testDispatcher) {
        // Insert 120 tracks into MediaStore (> 2 batches of 50)
        for (i in 1L..120L) {
            insertMediaStoreAudio(id = 8000L + i, title = "Batch Track $i")
        }

        val extractor = CountingMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals("Total should be 120", 120, progress.total)
        assertEquals("All 120 songs should be new", 120, progress.newCount)
        assertEquals("All 120 songs should invoke extractor", 120, extractor.callCount.get())

        val totalPersisted = dao.getAllSongHeaders().count { it.id in 8001L..8120L }
        assertEquals("All 120 tracks must be persisted across batch iterations", 120, totalPersisted)
    }

    // =========================================================================
    // Test 9 — SORT TITLE: La separación de fases conserva generateSortTitle().
    // =========================================================================
    @Test
    fun test9_sortTitleGenerationIsPreservedDeterministic() {
        assertEquals("Beatles", LibraryScanner.generateSortTitle("The Beatles"))
        assertEquals("Hard Day's Night", LibraryScanner.generateSortTitle("A Hard Day's Night"))
        assertEquals("Evening With...", LibraryScanner.generateSortTitle("An Evening With..."))
        assertEquals("Radiohead", LibraryScanner.generateSortTitle("Radiohead"))
        assertEquals("Rolling Stones", LibraryScanner.generateSortTitle("   The Rolling Stones   "))
        assertEquals("", LibraryScanner.generateSortTitle("   "))
    }
}
