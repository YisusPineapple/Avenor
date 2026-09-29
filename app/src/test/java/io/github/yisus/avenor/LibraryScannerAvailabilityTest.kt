package io.github.yisus.avenor

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowEnvironment
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryScannerAvailabilityTest {

    class AvailabilityFakeMediaProvider : ContentProvider() {
        companion object {
            val records = mutableListOf<ContentValues>()
            fun reset() {
                records.clear()
            }
        }

        override fun onCreate(): Boolean = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (values != null) {
                val targetId = values.getAsLong(MediaStore.Audio.Media._ID)
                records.removeAll { it.getAsLong(MediaStore.Audio.Media._ID) == targetId }
                records.add(ContentValues(values))
                val id = targetId ?: 0L
                return ContentUris.withAppendedId(uri, id)
            }
            return null
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val size = records.size
            records.clear()
            return size
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0

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

                if (
                    selection != null &&
                    selection.contains("${MediaStore.Audio.Media._ID} IN") &&
                    selectionArgs != null
                ) {
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

    class StubMetadataExtractor : MetadataExtractor {
        val callCount = AtomicInteger(0)

        override fun extract(
            context: Context,
            uri: Uri,
            filePath: String?
        ): ExtendedMetadataExtractor.AudioSpecs {
            callCount.incrementAndGet()
            return ExtendedMetadataExtractor.AudioSpecs(
                bitDepth = 16,
                sampleRate = 44100,
                mimeType = "audio/mpeg",
                fileExtension = "mp3",
                codec = "MP3",
                bitrate = 320000L,
                channels = 2
            )
        }
    }

    private lateinit var database: AppDatabase
    private lateinit var dao: MusicDao
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        org.robolectric.shadows.ShadowEnvironment.setExternalStorageState(
            android.os.Environment.MEDIA_MOUNTED
        )
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.musicDao()
        AvailabilityFakeMediaProvider.reset()
        Robolectric.setupContentProvider(AvailabilityFakeMediaProvider::class.java, "media")
    }

    @After
    fun tearDown() {
        database.close()
        AvailabilityFakeMediaProvider.reset()
    }

    private fun createSong(
        id: Long,
        availability: Int = SongAvailability.AVAILABLE,
        isExcluded: Boolean = availability != SongAvailability.AVAILABLE,
        dateModified: Long = 1700000000L,
        size: Long = 5000000L
    ): Song = Song(
        id = id,
        uri = "content://media/external/audio/media/$id",
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        durationMs = 180000L,
        albumArtUri = null,
        fileSize = size,
        dateModified = dateModified,
        isExcludedFromLibrary = isExcluded,
        availability = availability
    )

    private fun insertMediaStore(
        id: Long,
        dateModified: Long = 1700000000L,
        size: Long = 5000000L
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media._ID, id)
            put(MediaStore.Audio.Media.TITLE, "Song $id")
            put(MediaStore.Audio.Media.ARTIST, "Artist")
            put(MediaStore.Audio.Media.ALBUM, "Album")
            put(MediaStore.Audio.Media.DATA, "/storage/emulated/0/Music/track_$id.mp3")
            put(MediaStore.Audio.Media.DATE_MODIFIED, dateModified)
            put(MediaStore.Audio.Media.SIZE, size)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.DURATION, 180000L)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        }
        context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    @Test
    fun testStorageUnmounted_abortsWithoutTouchingRoom() = runTest(testDispatcher) {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED)
        try {
            val songs = listOf(
                createSong(id = 1L, availability = SongAvailability.AVAILABLE),
                createSong(id = 2L, availability = SongAvailability.AVAILABLE),
                createSong(id = 3L, availability = SongAvailability.AVAILABLE)
            )
            dao.insertSongs(songs)

            val extractor = StubMetadataExtractor()
            val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

            val progress = scanner.scan()

            assertEquals(0, progress.deletedCount)
            assertFalse(progress.isScanning)
            assertEquals(0, extractor.callCount.get())

            for (id in listOf(1L, 2L, 3L)) {
                val songInDb = dao.getSongById(id)
                assertNotNull("Song $id must remain in Room", songInDb)
                assertEquals(SongAvailability.AVAILABLE, songInDb!!.availability)
                assertFalse(songInDb.isExcludedFromLibrary)
            }
        } finally {
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        }
    }

    @Test
    fun testMissingSongReappearsIsRestoredToAvailable() = runTest(testDispatcher) {
        val songId = 10L
        val missingSong = createSong(
            id = songId,
            availability = SongAvailability.MISSING,
            isExcluded = true,
            dateModified = 1700000000L,
            size = 5000000L
        )
        dao.insertSongs(listOf(missingSong))

        insertMediaStore(id = songId, dateModified = 1700000000L, size = 5000000L)

        val extractor = StubMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        val restored = dao.getSongById(songId)
        assertNotNull(restored)
        assertEquals(SongAvailability.AVAILABLE, restored!!.availability)
        assertFalse(restored.isExcludedFromLibrary)

        assertEquals(0, progress.deletedCount)
        assertEquals(0, progress.newCount)
        assertEquals(0, progress.modifiedCount)
        assertEquals(0, extractor.callCount.get())
    }

    @Test
    fun testAvailableSongAbsentFirstScan_marksMissing() = runTest(testDispatcher) {
        val absentSongId = 20L
        val otherSongId = 21L
        dao.insertSongs(listOf(createSong(id = absentSongId, availability = SongAvailability.AVAILABLE)))

        insertMediaStore(id = otherSongId)

        val extractor = StubMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals(0, progress.deletedCount)
        val absentSong = dao.getSongById(absentSongId)
        assertNotNull(absentSong)
        assertEquals(SongAvailability.MISSING, absentSong!!.availability)
        assertTrue(absentSong.isExcludedFromLibrary)
    }

    @Test
    fun testMissingSongAbsentSecondScan_deletesConfirmed() = runTest(testDispatcher) {
        val missingSongId = 30L
        val otherSongId = 31L
        dao.insertSongs(
            listOf(
                createSong(
                    id = missingSongId,
                    availability = SongAvailability.MISSING,
                    isExcluded = true
                )
            )
        )

        insertMediaStore(id = otherSongId)

        val extractor = StubMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()

        assertEquals(1, progress.deletedCount)
        assertNull(dao.getSongById(missingSongId))
    }

    @Test
    fun testDegradedState_missingSongsRecoverAfterSanityRestored() = runTest(testDispatcher) {
        val ids = listOf(101L, 102L, 103L)
        dao.insertSongs(ids.map { createSong(id = it, availability = SongAvailability.AVAILABLE) })

        val extractor = StubMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        // Scan A: MediaStore empty -> Gate 2 marks all as MISSING without deleting
        val progressA = scanner.scan()
        assertEquals(0, progressA.deletedCount)
        for (id in ids) {
            val s = dao.getSongById(id)
            assertNotNull(s)
            assertEquals(SongAvailability.MISSING, s!!.availability)
            assertTrue(s.isExcludedFromLibrary)
        }

        // Scan B: MediaStore repopulated with same dateModified/size -> all restored to AVAILABLE
        for (id in ids) {
            insertMediaStore(id = id, dateModified = 1700000000L, size = 5000000L)
        }
        val progressB = scanner.scan()
        assertEquals(0, progressB.deletedCount)
        for (id in ids) {
            val s = dao.getSongById(id)
            assertNotNull(s)
            assertEquals(SongAvailability.AVAILABLE, s!!.availability)
            assertFalse(s.isExcludedFromLibrary)
        }
    }
}
