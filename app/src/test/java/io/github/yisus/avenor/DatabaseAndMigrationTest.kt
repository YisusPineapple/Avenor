package io.github.yisus.avenor

import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.github.yisus.avenor.DatabaseExport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DatabaseAndMigrationTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MusicDao
    private lateinit var repo: DatabaseRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.musicDao()
        repo = DatabaseRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testMigration13To14AddsAllColumnsAndIndices() {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("test_migration_13_14.db")
        if (dbFile.exists()) dbFile.delete()

        // Create a raw SQLite DB simulating version 13
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_13_14.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `songs` (
                            `id` INTEGER NOT NULL, 
                            `uri` TEXT NOT NULL, 
                            `title` TEXT NOT NULL, 
                            `artist` TEXT NOT NULL, 
                            `album` TEXT NOT NULL, 
                            `durationMs` INTEGER NOT NULL, 
                            `albumArtUri` TEXT, 
                            `bitDepth` INTEGER NOT NULL, 
                            `sampleRate` INTEGER NOT NULL, 
                            `mimeType` TEXT NOT NULL, 
                            `fileExtension` TEXT NOT NULL, 
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO `songs` VALUES (101, 'content://audio/101', 'Legacy Song', 'Legacy Artist', 'Legacy Album', 200000, null, 16, 44100, 'audio/mpeg', 'mp3')")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
        val writableDb = helper.writableDatabase

        // Execute Migration 13 -> 14
        MIGRATION_13_14.migrate(writableDb)

        // Verify newly added columns exist and row preserved with defaults
        val cursor = writableDb.query("SELECT id, title, codec, bitrate, channels, fileSize, dateModified, dateAdded, trackNumber, discNumber, year, genre, composer, albumArtist FROM songs WHERE id = 101")
        assertTrue("Migrated row must exist", cursor.moveToFirst())
        assertEquals(101L, cursor.getLong(0))
        assertEquals("Legacy Song", cursor.getString(1))
        assertEquals("MP3", cursor.getString(2))
        assertEquals(0L, cursor.getLong(3))
        assertEquals(2, cursor.getInt(4))
        assertEquals(0L, cursor.getLong(5))
        assertEquals(0L, cursor.getLong(6))
        assertEquals(0L, cursor.getLong(7))
        assertEquals(0, cursor.getInt(8))
        assertEquals(0, cursor.getInt(9))
        assertEquals(0, cursor.getInt(10))
        assertEquals("", cursor.getString(11))
        assertEquals("", cursor.getString(12))
        assertEquals("", cursor.getString(13))
        cursor.close()

        // Verify indexes exist
        val indexCursor = writableDb.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='songs'")
        val indexNames = mutableSetOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(0))
        }
        indexCursor.close()

        assertTrue(indexNames.contains("index_songs_title"))
        assertTrue(indexNames.contains("index_songs_artist"))
        assertTrue(indexNames.contains("index_songs_album"))
        assertTrue(indexNames.contains("index_songs_genre"))
        assertTrue(indexNames.contains("index_songs_dateAdded"))
        assertTrue(indexNames.contains("index_songs_artist_album"))

        writableDb.close()
        dbFile.delete()
    }

    @Test
    fun testReconciliationDiffPreservesUnchangedAndUpdatesModified() = runTest(testDispatcher) {
        val initialSongs = listOf(
            Song(id = 1L, uri = "content://1", title = "Keep Me", artist = "A", album = "A", durationMs = 1000, albumArtUri = null, dateModified = 100L, fileSize = 1000L),
            Song(id = 2L, uri = "content://2", title = "Modify Me", artist = "B", album = "B", durationMs = 2000, albumArtUri = null, dateModified = 200L, fileSize = 2000L),
            Song(id = 3L, uri = "content://3", title = "Delete Me", artist = "C", album = "C", durationMs = 3000, albumArtUri = null, dateModified = 300L, fileSize = 3000L)
        )
        dao.insertSongs(initialSongs)
        assertEquals(3, dao.getSongsCountSync())

        // Reconcile:
        // - Song 1 is UNCHANGED (dateModified 100, fileSize 1000)
        // - Song 2 is MODIFIED (dateModified 250, fileSize 2500)
        // - Song 3 is DELETED (not in current storage)
        // - Song 4 is NEW
        val updatedStorageSongs = listOf(
            Song(id = 1L, uri = "content://1", title = "Keep Me", artist = "A", album = "A", durationMs = 1000, albumArtUri = null, dateModified = 100L, fileSize = 1000L),
            Song(id = 2L, uri = "content://2", title = "Modify Me (Updated)", artist = "B", album = "B", durationMs = 2200, albumArtUri = null, dateModified = 250L, fileSize = 2500L),
            Song(id = 4L, uri = "content://4", title = "Brand New Song", artist = "D", album = "D", durationMs = 4000, albumArtUri = null, dateModified = 400L, fileSize = 4000L)
        )

        repo.reconcileSongs(updatedStorageSongs)

        assertEquals(3, dao.getSongsCountSync())
        val s1 = dao.getSongById(1L)
        assertNotNull(s1)
        assertEquals("Keep Me", s1?.title)

        val s2 = dao.getSongById(2L)
        assertNotNull(s2)
        assertEquals("Modify Me (Updated)", s2?.title)
        assertEquals(250L, s2?.dateModified)

        val s3 = dao.getSongById(3L)
        assertEquals("Song 3 must be deleted", null, s3)

        val s4 = dao.getSongById(4L)
        assertNotNull("Song 4 must be inserted", s4)
        assertEquals("Brand New Song", s4?.title)
    }

    @Test
    fun testSongHeadersAndBatchDeletion() = runTest(testDispatcher) {
        val songs = (1L..10L).map { i ->
            Song(
                id = i,
                uri = "content://$i",
                title = "Song $i",
                artist = "Artist $i",
                album = "Album $i",
                durationMs = 180000L,
                albumArtUri = null,
                dateModified = i * 100,
                fileSize = i * 1024
            )
        }
        dao.insertSongs(songs)

        val headers = dao.getAllSongHeaders()
        assertEquals(10, headers.size)
        val headerMap = headers.associateBy { it.id }
        assertEquals(500L, headerMap[5L]?.dateModified)
        assertEquals(5120L, headerMap[5L]?.fileSize)

        // Delete in batch
        dao.deleteSongsByIds(listOf(2L, 4L, 6L))
        assertEquals(7, dao.getSongsCountSync())
        assertEquals(null, dao.getSongById(2L))
        assertEquals(null, dao.getSongById(4L))
        assertEquals(null, dao.getSongById(6L))
        assertNotNull(dao.getSongById(1L))
    }

    @Test
    fun testBackupAndRestorePreservesFullExtendedMetadata() = runTest(testDispatcher) {
        val hiResSong = Song(
            id = 999L,
            uri = "content://audio/hires",
            title = "Symphony No. 5",
            artist = "Beethoven",
            album = "Masterworks",
            durationMs = 450000L,
            albumArtUri = "content://art/999",
            bitDepth = 24,
            sampleRate = 96000,
            mimeType = "audio/flac",
            fileExtension = "flac",
            codec = "FLAC",
            bitrate = 2457600L,
            channels = 2,
            fileSize = 45000000L,
            dateModified = 1700000000L,
            dateAdded = 1690000000L,
            trackNumber = 1,
            discNumber = 1,
            year = 1808,
            genre = "Classical",
            composer = "Ludwig van Beethoven",
            albumArtist = "Vienna Philharmonic"
        )
        dao.insertSongs(listOf(hiResSong))

        val export = DatabaseExport(
            songs = dao.getAllSongsSync(),
            playlists = emptyList(),
            history = emptyList(),
            playlistSongs = emptyList(),
            eqPresets = emptyList(),
            settings = null,
            lyricOffsets = emptyList(),
            playbackQueues = emptyList(),
            queueSongs = emptyList(),
            trashItems = emptyList(),
            favorites = emptyList()
        )

        // Clear and restore
        dao.deleteSongsByIds(listOf(999L))
        assertEquals(0, dao.getSongsCountSync())

        dao.restoreDatabase(export)
        assertEquals(1, dao.getSongsCountSync())

        val restored = dao.getSongById(999L)
        assertNotNull(restored)
        assertEquals("FLAC", restored?.codec)
        assertEquals(24, restored?.bitDepth)
        assertEquals(96000, restored?.sampleRate)
        assertEquals(2457600L, restored?.bitrate)
        assertEquals("Classical", restored?.genre)
        assertEquals("Ludwig van Beethoven", restored?.composer)
        assertEquals("Vienna Philharmonic", restored?.albumArtist)
        assertEquals(1808, restored?.year)
    }
}
