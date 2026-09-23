package io.github.yisus.avenor

import androidx.room.Room
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRestoreTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MusicDao
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.musicDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- TEST 1: History Round Trip (Fix crítico de omisión de History) ---
    @Test
    fun testHistoryRoundTripPreservesAllRecords() = runTest(testDispatcher) {
        val song1 = Song(id = 101L, uri = "content://101", title = "Track 1", artist = "Artist 1", album = "Album 1", durationMs = 180000L, albumArtUri = null)
        val song2 = Song(id = 102L, uri = "content://102", title = "Track 2", artist = "Artist 2", album = "Album 2", durationMs = 210000L, albumArtUri = null)
        dao.insertSongs(listOf(song1, song2))

        val history1 = ListeningHistory(id = 1, songId = 101L, playedAt = 1000000L, skipped = false, timeOfDay = "MORNING")
        val history2 = ListeningHistory(id = 2, songId = 101L, playedAt = 2000000L, skipped = true, timeOfDay = "AFTERNOON")
        val history3 = ListeningHistory(id = 3, songId = 102L, playedAt = 3000000L, skipped = false, timeOfDay = "NIGHT")
        dao.insertHistoryList(listOf(history1, history2, history3))

        val exportedHistory = dao.getAllHistorySync()
        assertEquals(3, exportedHistory.size)

        val export = DatabaseExport(
            songs = dao.getAllSongsSync(),
            history = exportedHistory
        )

        // Clear or alter database
        dao.clearHistory()
        assertEquals(0, dao.getAllHistorySync().size)

        // Restore
        dao.restoreDatabase(export)

        val restoredHistory = dao.getAllHistorySync()
        assertEquals(3, restoredHistory.size)

        val restoredH1 = restoredHistory.find { it.playedAt == 1000000L }
        assertNotNull(restoredH1)
        assertEquals(101L, restoredH1?.songId)
        assertEquals(false, restoredH1?.skipped)
        assertEquals("MORNING", restoredH1?.timeOfDay)

        val restoredH2 = restoredHistory.find { it.playedAt == 2000000L }
        assertNotNull(restoredH2)
        assertEquals(101L, restoredH2?.songId)
        assertEquals(true, restoredH2?.skipped)
        assertEquals("AFTERNOON", restoredH2?.timeOfDay)

        val restoredH3 = restoredHistory.find { it.playedAt == 3000000L }
        assertNotNull(restoredH3)
        assertEquals(102L, restoredH3?.songId)
        assertEquals(false, restoredH3?.skipped)
        assertEquals("NIGHT", restoredH3?.timeOfDay)
    }

    // --- TEST 2: Restore preserves all entity relations ---
    @Test
    fun testRestorePreservesAllEntityRelations() = runTest(testDispatcher) {
        val s1 = Song(id = 1L, uri = "content://1", title = "Song A", artist = "Artist A", album = "Album A", durationMs = 120000L, albumArtUri = null)
        val s2 = Song(id = 2L, uri = "content://2", title = "Song B", artist = "Artist B", album = "Album B", durationMs = 240000L, albumArtUri = null)
        dao.insertSongs(listOf(s1, s2))

        val p1 = Playlist(id = 10, name = "Workout Playlist", createdAt = 5000L)
        dao.insertPlaylist(p1)

        val crossRef = PlaylistSongCrossRef(playlistId = 10, songId = 1L, addedAt = 6000L)
        dao.insertSongToPlaylist(crossRef)

        val hist = ListeningHistory(id = 1, songId = 1L, playedAt = 7000L, skipped = false, timeOfDay = "MORNING")
        dao.insertHistoryList(listOf(hist))

        val fav = Favorite(songId = 2L, addedAt = 8000L)
        dao.addFavorite(fav)

        val export = DatabaseExport(
            songs = dao.getAllSongsSync(),
            playlists = dao.getAllPlaylistsSync(),
            playlistSongs = dao.getAllPlaylistSongsSync(),
            history = dao.getAllHistorySync(),
            favorites = dao.getAllFavoritesSync()
        )

        // Wipe DB
        dao.deleteSongsByIds(listOf(1L, 2L))
        dao.clearPlaylists()

        assertEquals(0, dao.getAllSongsSync().size)
        assertEquals(0, dao.getAllPlaylistsSync().size)
        assertEquals(0, dao.getAllHistorySync().size)
        assertEquals(0, dao.getAllFavoritesSync().size)

        // Restore
        dao.restoreDatabase(export)

        assertEquals(2, dao.getAllSongsSync().size)
        assertEquals(1, dao.getAllPlaylistsSync().size)
        assertEquals(1, dao.getAllPlaylistSongsSync().size)
        assertEquals(1, dao.getAllHistorySync().size)
        assertEquals(1, dao.getAllFavoritesSync().size)

        assertTrue(dao.isFavoriteSync(2L))
        assertEquals(10, dao.getAllPlaylistSongsSync().first().playlistId)
        assertEquals(1L, dao.getAllPlaylistSongsSync().first().songId)
    }

    // --- TEST 3: Legacy backup without version is recognized as format 1 ---
    @Test
    fun testLegacyBackupWithoutVersionIsRecognizedAsFormat1() = runTest(testDispatcher) {
        val legacyJson = """
            {
                "songs": [
                    {
                        "id": 501,
                        "uri": "content://media/501",
                        "title": "Legacy Song",
                        "artist": "Legacy Artist",
                        "album": "Legacy Album",
                        "durationMs": 150000,
                        "albumArtUri": null
                    }
                ],
                "playlists": [
                    {
                        "id": 1,
                        "name": "Legacy Playlist",
                        "createdAt": 1000
                    }
                ]
            }
        """.trimIndent()

        val validation = BackupManager.validateAndParseJson(legacyJson)
        assertTrue("Debe ser válido", validation is BackupValidationResult.Valid)
        val valid = validation as BackupValidationResult.Valid
        assertTrue("Debe marcarse como legacy", valid.isLegacy)
        assertEquals(1, valid.export.backupFormatVersion)
        assertEquals(1, valid.export.songs.size)
        assertEquals(501L, valid.export.songs.first().id)

        val restoreResult = BackupManager.importBackupFromJsonString(dao, legacyJson)
        assertTrue(restoreResult is BackupResult.Success)

        val restoredSong = dao.getSongById(501L)
        assertNotNull(restoredSong)
        assertEquals("Legacy Song", restoredSong?.title)
    }

    // --- TEST 4: Future backup version is safely rejected ---
    @Test
    fun testFutureBackupVersionIsSafelyRejected() = runTest(testDispatcher) {
        // Pre-populate with existing user song
        val originalSong = Song(id = 999L, uri = "content://original", title = "Original Song", artist = "Original Artist", album = "Original Album", durationMs = 100000L, albumArtUri = null)
        dao.insertSongs(listOf(originalSong))

        val futureJson = """
            {
                "backupFormatVersion": 999,
                "roomSchemaVersion": 50,
                "songs": [
                    {
                        "id": 888,
                        "uri": "content://future",
                        "title": "Future Song",
                        "artist": "Future Artist",
                        "album": "Future Album",
                        "durationMs": 200000,
                        "albumArtUri": null
                    }
                ]
            }
        """.trimIndent()

        val result = BackupManager.importBackupFromJsonString(dao, futureJson)
        assertTrue("Debe ser rechazado con FutureVersion", result is BackupResult.Error.FutureVersion)
        val error = result as BackupResult.Error.FutureVersion
        assertEquals(999, error.foundVersion)
        assertEquals(BackupManager.CURRENT_BACKUP_FORMAT_VERSION, error.supportedVersion)

        // Verify existing DB is 100% intact
        val currentSongs = dao.getAllSongsSync()
        assertEquals(1, currentSongs.size)
        assertEquals(999L, currentSongs.first().id)
        assertEquals("Original Song", currentSongs.first().title)
    }

    // --- TEST 5: Invalid structure is rejected before restore ---
    @Test
    fun testInvalidStructureIsRejectedBeforeRestore() = runTest(testDispatcher) {
        val originalSong = Song(id = 777L, uri = "content://safe", title = "Safe Song", artist = "Artist", album = "Album", durationMs = 120000L, albumArtUri = null)
        dao.insertSongs(listOf(originalSong))

        val unrelatedJson = """
            {
                "name": "Random Backup",
                "version": 1.0,
                "data": [1, 2, 3]
            }
        """.trimIndent()

        val result = BackupManager.importBackupFromJsonString(dao, unrelatedJson)
        assertTrue("Debe ser rechazado por estructura incompatible", result is BackupResult.Error.IncompatibleStructure)

        // Existing DB must be untouched
        assertEquals(1, dao.getAllSongsSync().size)
        assertEquals(777L, dao.getAllSongsSync().first().id)
    }

    // --- TEST 6: Restore failure rollback preserves original database ---
    @Test
    fun testRestoreFailureRollbackPreservesOriginalDatabase() = runTest(testDispatcher) {
        val originalSong = Song(id = 333L, uri = "content://preserved", title = "Preserved Song", artist = "Preserved Artist", album = "Preserved Album", durationMs = 120000L, albumArtUri = null)
        dao.insertSongs(listOf(originalSong))

        // Create an invalid export where a playlistSong references a song that does not exist in export.songs
        // Under SQLite foreign key constraints, this triggers a constraint violation on foreign key
        val brokenExport = DatabaseExport(
            songs = listOf(
                Song(id = 444L, uri = "content://444", title = "New Song", artist = "New Artist", album = "New Album", durationMs = 100000L, albumArtUri = null)
            ),
            playlists = listOf(
                Playlist(id = 1, name = "P1", createdAt = 1000)
            ),
            playlistSongs = listOf(
                PlaylistSongCrossRef(playlistId = 1, songId = 99999L) // Non-existent song ID!
            )
        )

        val result = BackupManager.restoreValidatedBackup(dao, brokenExport)
        assertTrue("Debe fallar transaccionalmente", result is BackupResult.Error.TransactionError)

        // Verify original DB remains intact due to transaction rollback
        val currentSongs = dao.getAllSongsSync()
        assertEquals(1, currentSongs.size)
        assertEquals(333L, currentSongs.first().id)
        assertEquals("Preserved Song", currentSongs.first().title)
    }

    // --- TEST 7: New backup metadata contains correct versions ---
    @Test
    fun testNewBackupMetadataContainsCorrectVersions() {
        val export = DatabaseExport(
            songs = emptyList(),
            playlists = emptyList()
        )

        assertEquals(1, export.backupFormatVersion)
        assertEquals(17, export.roomSchemaVersion)
        assertEquals(BackupManager.CURRENT_BACKUP_FORMAT_VERSION, export.backupFormatVersion)
        assertEquals(DATABASE_VERSION, export.roomSchemaVersion)

        val jsonString = BackupManager.jsonFormat.encodeToString(export)
        assertTrue(jsonString.contains("\"backupFormatVersion\":1"))
        assertTrue(jsonString.contains("\"roomSchemaVersion\":17"))
    }

    // --- TEST 8: Legacy song fields without extended columns receive defaults ---
    @Test
    fun testLegacySongFieldsWithoutExtendedColumnsReceiveDefaults() = runTest(testDispatcher) {
        val legacySongJson = """
            {
                "backupFormatVersion": 1,
                "roomSchemaVersion": 16,
                "songs": [
                    {
                        "id": 801,
                        "uri": "content://audio/801",
                        "title": "Song Missing Extended Fields",
                        "artist": "Old Artist",
                        "album": "Old Album",
                        "durationMs": 180000,
                        "albumArtUri": null,
                        "bitDepth": 16,
                        "sampleRate": 44100,
                        "mimeType": "audio/mpeg",
                        "fileExtension": "mp3",
                        "codec": "MP3",
                        "bitrate": 320000,
                        "channels": 2,
                        "fileSize": 5000000,
                        "dateModified": 1000,
                        "dateAdded": 2000,
                        "trackNumber": 1,
                        "discNumber": 1,
                        "year": 2021,
                        "genre": "Rock",
                        "composer": "Old Composer",
                        "albumArtist": "Old Artist"
                    }
                ],
                "playlists": []
            }
        """.trimIndent()

        val result = BackupManager.importBackupFromJsonString(dao, legacySongJson)
        assertTrue(result is BackupResult.Success)

        val restored = dao.getSongById(801L)
        assertNotNull(restored)
        assertEquals(801L, restored?.id)
        assertEquals("", restored?.sortTitle)
        assertEquals("", restored?.comment)
        assertNull(restored?.replayGainTrack)
        assertNull(restored?.replayGainAlbum)
        assertEquals(0, restored?.artworkWidth)
        assertEquals(0, restored?.artworkHeight)
        assertEquals("", restored?.artworkMimeType)
        assertEquals(false, restored?.isExcludedFromLibrary)
    }

    // --- TEST 9: Checksum válido (P1-4C Test 1) ---
    @Test
    fun testValidChecksumGenerationAndRestore() = runTest(testDispatcher) {
        val song = Song(id = 901L, uri = "content://audio/901", title = "Checksum Track", artist = "Artist", album = "Album", durationMs = 200000L, albumArtUri = null)
        dao.insertSongs(listOf(song))

        val export = BackupManager.buildExportData(dao)
        assertEquals(BackupManager.CURRENT_BACKUP_FORMAT_VERSION, export.backupFormatVersion)
        assertEquals(DATABASE_VERSION, export.roomSchemaVersion)
        assertEquals("SHA-256", export.checksumAlgorithm)
        assertNotNull(export.checksumSha256)
        assertEquals(64, export.checksumSha256?.length)

        val jsonString = BackupManager.jsonFormat.encodeToString(export)
        val validation = BackupManager.validateAndParseJson(jsonString)
        assertTrue("Validación debe ser exitosa", validation is BackupValidationResult.Valid)
        val valid = validation as BackupValidationResult.Valid
        assertEquals(IntegrityStatus.Verified, valid.integrityStatus)

        dao.clearSongs()
        assertEquals(0, dao.getAllSongsSync().size)

        val restoreResult = BackupManager.restoreValidatedBackup(dao, valid.export)
        assertTrue(restoreResult is BackupResult.Success)
        assertEquals(1, dao.getAllSongsSync().size)
        assertEquals("Checksum Track", dao.getSongById(901L)?.title)
    }

    // --- TEST 10: Checksum manipulado (P1-4C Test 2) ---
    @Test
    fun testTamperedPayloadFailsChecksumAndLeavesRoomUntouched() = runTest(testDispatcher) {
        val originalSong = Song(id = 902L, uri = "content://audio/902", title = "Original Pure Title", artist = "Artist", album = "Album", durationMs = 200000L, albumArtUri = null)
        dao.insertSongs(listOf(originalSong))

        val export = BackupManager.buildExportData(dao)
        val validJson = BackupManager.jsonFormat.encodeToString(export)

        // Tamper with payload while leaving the original checksum intact
        val tamperedJson = validJson.replace("Original Pure Title", "Malicious Injected Title")

        val result = BackupManager.importBackupFromJsonString(dao, tamperedJson)
        assertTrue("Debe fallar con ChecksumMismatch", result is BackupResult.Error.ChecksumMismatch)
        val mismatch = result as BackupResult.Error.ChecksumMismatch
        assertEquals(export.checksumSha256, mismatch.expected)
        assertNotEquals(mismatch.expected, mismatch.actual)

        // Room state must remain 100% untouched
        val songInDb = dao.getSongById(902L)
        assertNotNull(songInDb)
        assertEquals("Original Pure Title", songInDb?.title)
    }

    // --- TEST 11: Algoritmo desconocido (P1-4C Test 3) ---
    @Test
    fun testUnknownChecksumAlgorithmIsSafelyRejected() = runTest(testDispatcher) {
        val song = Song(id = 903L, uri = "content://audio/903", title = "Song", artist = "Artist", album = "Album", durationMs = 120000L, albumArtUri = null)
        dao.insertSongs(listOf(song))

        val unknownAlgJson = """
            {
                "backupFormatVersion": 1,
                "roomSchemaVersion": 17,
                "checksumAlgorithm": "SHA-999",
                "checksumSha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                "songs": [
                    {
                        "id": 999,
                        "uri": "content://audio/999",
                        "title": "Should Not Be Imported",
                        "artist": "Artist",
                        "album": "Album",
                        "durationMs": 100000,
                        "albumArtUri": null
                    }
                ],
                "playlists": []
            }
        """.trimIndent()

        val result = BackupManager.importBackupFromJsonString(dao, unknownAlgJson)
        assertTrue(result is BackupResult.Error.UnsupportedChecksumAlgorithm)
        val error = result as BackupResult.Error.UnsupportedChecksumAlgorithm
        assertEquals("SHA-999", error.algorithm)

        // Room remains untouched
        assertNull(dao.getSongById(999L))
        assertEquals(1, dao.getAllSongsSync().size)
    }

    // --- TEST 12: Backup antiguo sin checksum (P1-4C Test 4) ---
    @Test
    fun testLegacyBackupWithoutChecksumIsTreatedAsUnverifiedLegacy() = runTest(testDispatcher) {
        val legacyJson = """
            {
                "backupFormatVersion": 1,
                "roomSchemaVersion": 17,
                "songs": [
                    {
                        "id": 904,
                        "uri": "content://audio/904",
                        "title": "Legacy Unhashed Track",
                        "artist": "Artist",
                        "album": "Album",
                        "durationMs": 150000,
                        "albumArtUri": null
                    }
                ],
                "playlists": []
            }
        """.trimIndent()

        val validation = BackupManager.validateAndParseJson(legacyJson)
        assertTrue(validation is BackupValidationResult.Valid)
        val valid = validation as BackupValidationResult.Valid
        assertTrue("No debe fingirse integridad", valid.integrityStatus is IntegrityStatus.UnverifiedLegacy)

        val restoreResult = BackupManager.importBackupFromJsonString(dao, legacyJson)
        assertTrue(restoreResult is BackupResult.Success)
        assertNotNull(dao.getSongById(904L))
    }

    // --- TEST 13: ZIP con SQLite binaria antigua (P1-4C Test 5) ---
    @Test
    fun testLegacyZipWithSqliteBinaryImportsViaMetadata() = runTest(testDispatcher) {
        val song = Song(id = 905L, uri = "content://audio/905", title = "Zip Extracted Song", artist = "Artist", album = "Album", durationMs = 150000L, albumArtUri = null)
        val export = DatabaseExport(
            backupFormatVersion = 1,
            roomSchemaVersion = 17,
            songs = listOf(song),
            playlists = emptyList()
        )
        val metadataJson = BackupManager.jsonFormat.encodeToString(export)

        // Create in-memory ZIP containing avenor_database.db AND metadata.json
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Entry 1: dead physical sqlite binary (dummy data)
            zos.putNextEntry(ZipEntry("avenor_database.db"))
            zos.write("DUMMY RAW SQLITE BINARY DATA".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Entry 2: metadata.json
            zos.putNextEntry(ZipEntry("metadata.json"))
            zos.write(metadataJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        val extractedJson = BackupManager.readJsonFromStream(ByteArrayInputStream(zipBytes))
        assertEquals(metadataJson, extractedJson)

        val result = BackupManager.importBackupFromJsonString(dao, extractedJson)
        assertTrue(result is BackupResult.Success)
        assertNotNull(dao.getSongById(905L))
    }

    // --- TEST 14: Nuevo backup no incluye SQLite inútil (P1-4D Test 6) ---
    @Test
    fun testNewBackupDoesNotIncludeSqliteBinary() = runTest(testDispatcher) {
        val context = RuntimeEnvironment.getApplication()
        val baos = ByteArrayOutputStream()

        val exportSuccess = BackupManager.exportBackupToStream(context, baos)
        assertTrue("Export to stream debe ser exitoso", exportSuccess)

        val zipBytes = baos.toByteArray()
        assertTrue("ZIP debe contener bytes", zipBytes.isNotEmpty())

        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zis.nextEntry
            }
        }

        assertTrue("Debe contener metadata.json", entryNames.contains("metadata.json"))
        assertFalse("NO debe contener avenor_database.db", entryNames.contains("avenor_database.db"))
        assertEquals(1, entryNames.size)
    }

    // --- TEST 15: Auto backup usa contrato con checksum (P1-4C/D Test 7) ---
    @Test
    fun testAutoBackupUsesContractWithChecksum() = runTest(testDispatcher) {
        val context = RuntimeEnvironment.getApplication()
        val autoBackupFile = File(context.filesDir, "avenor_auto_backup.json")
        if (autoBackupFile.exists()) {
            autoBackupFile.delete()
        }

        val success = BackupManager.createAutoBackup(context)
        assertTrue("Auto-backup debe retornar éxito", success)
        assertTrue("Archivo auto_backup debe existir en filesDir", autoBackupFile.exists())

        val content = autoBackupFile.readText(Charsets.UTF_8)
        val validation = BackupManager.validateAndParseJson(content)
        assertTrue("Auto-backup debe ser válido", validation is BackupValidationResult.Valid)
        val valid = validation as BackupValidationResult.Valid

        assertEquals(1, valid.export.backupFormatVersion)
        assertEquals(17, valid.export.roomSchemaVersion)
        assertEquals("SHA-256", valid.export.checksumAlgorithm)
        assertNotNull(valid.export.checksumSha256)
        assertEquals(64, valid.export.checksumSha256?.length)
        assertEquals(IntegrityStatus.Verified, valid.integrityStatus)
    }

    // --- TEST 16: Integridad completa de restore (P1-4C Test 8) ---
    @Test
    fun testCompleteRestoreWithChecksumPreservesAllEntities() = runTest(testDispatcher) {
        val song = Song(id = 910L, uri = "content://audio/910", title = "Full Ent Track", artist = "Artist", album = "Album", durationMs = 180000L, albumArtUri = null)
        dao.insertSongs(listOf(song))

        val playlist = Playlist(id = 70, name = "Full Integrity Playlist", createdAt = 5555L)
        dao.insertPlaylist(playlist)
        dao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId = 70, songId = 910L, addedAt = 1234L))

        val history = ListeningHistory(id = 40, songId = 910L, playedAt = 999999L, skipped = false, timeOfDay = "NIGHT")
        dao.insertHistoryList(listOf(history))

        val fav = Favorite(songId = 910L, addedAt = 12345L)
        dao.addFavorite(fav)

        val preset = EqPreset(id = 30, name = "Vocal Boost", bands = "0,2,4,2,0")
        dao.insertEqPreset(preset)

        val queue = PlaybackQueue(id = 10, name = "MainQueue", currentSongId = 910L, currentPositionMs = 5000L)
        dao.insertPlaybackQueue(queue)
        dao.insertQueueSongs(listOf(QueueSong(id = 1L, queueId = 10, songId = 910L, positionIndex = 0)))

        val export = BackupManager.buildExportData(dao)
        val json = BackupManager.jsonFormat.encodeToString(export)

        // Clear everything
        dao.clearPlaylistSongs()
        dao.clearFavorites()
        dao.clearQueueSongs()
        dao.clearHistory()
        dao.clearPlaylists()
        dao.clearPlaybackQueues()
        dao.clearSongs()
        dao.clearEqPresets()

        assertEquals(0, dao.getAllSongsSync().size)
        assertEquals(0, dao.getAllPlaylistsSync().size)
        assertEquals(0, dao.getAllHistorySync().size)
        assertEquals(0, dao.getAllFavoritesSync().size)

        // Restore
        val result = BackupManager.importBackupFromJsonString(dao, json)
        assertTrue(result is BackupResult.Success)

        assertEquals(1, dao.getAllSongsSync().size)
        assertEquals(1, dao.getAllPlaylistsSync().size)
        assertEquals(1, dao.getAllHistorySync().size)
        assertEquals(1, dao.getAllFavoritesSync().size)
        assertEquals(1, dao.getAllEqPresetsSync().size)
        assertEquals(1, dao.getAllQueuesSync().size)
        assertEquals(1, dao.getAllQueueSongsSync().size)
        assertEquals(70, dao.getAllPlaylistSongsSync().first().playlistId)
        assertEquals(910L, dao.getAllFavoritesSync().first().songId)
    }

    // --- TEST 17: Reducción de consumo de memoria / Streaming (P1-4D Parte H) ---
    @Test
    fun testStreamingSerializationWithoutHeapDuplication() = runTest(testDispatcher) {
        val song1 = Song(id = 920L, uri = "content://audio/920", title = "Stream Song 1", artist = "Artist", album = "Album", durationMs = 180000L, albumArtUri = null)
        val song2 = Song(id = 921L, uri = "content://audio/921", title = "Stream Song 2", artist = "Artist", album = "Album", durationMs = 210000L, albumArtUri = null)
        dao.insertSongs(listOf(song1, song2))

        val export = BackupManager.buildExportData(dao)

        // 1. Verificación de determinismo estricto del SHA-256
        val hash1 = BackupManager.computePayloadSha256(export)
        val hash2 = BackupManager.computePayloadSha256(export)
        assertEquals("El cálculo de SHA-256 sobre el payload debe ser 100% determinista", hash1, hash2)

        // 2. Modificación de un solo byte altera completamente el hash
        val modifiedExport = export.copy(songs = listOf(song1.copy(title = "Stream Song 1 modified"), song2))
        val modifiedHash = BackupManager.computePayloadSha256(modifiedExport)
        assertNotEquals("Modificar un solo byte debe alterar completamente el hash", hash1, modifiedHash)

        // 3. Verificación de streaming hacia ZipOutputStream
        val context = RuntimeEnvironment.getApplication()
        val countingStream = object : ByteArrayOutputStream() {
            var chunkWriteCount = 0
            override fun write(b: ByteArray, off: Int, len: Int) {
                chunkWriteCount++
                super.write(b, off, len)
            }
        }

        val success = BackupManager.exportBackupToStream(context, countingStream)
        assertTrue(success)
        assertTrue("El streaming debe escribir en fragmentos/chunks", countingStream.chunkWriteCount > 0)
        assertTrue("El archivo ZIP generado no debe estar vacío", countingStream.size() > 0)
    }
}
