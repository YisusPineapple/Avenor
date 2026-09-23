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
import io.github.yisus.avenor.replaygain.ReplayGainData
import io.github.yisus.avenor.replaygain.ReplayGainMode
import io.github.yisus.avenor.replaygain.ReplayGainParser
import io.github.yisus.avenor.replaygain.ReplayGainPolicy
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReplayGainFoundationTest {

    // -------------------------------------------------------------
    // BLOQUE 1: PARSING DE STRINGS Y TAGS (Tests 1 - 8)
    // -------------------------------------------------------------

    @Test
    fun test1_trackGain_withDb() {
        val result = ReplayGainParser.parseGainString("-6.84 dB")
        assertNotNull("Should parse valid negative gain with dB suffix", result)
        assertEquals(-6.84f, result!!, 0.001f)
    }

    @Test
    fun test2_albumGain_withDb() {
        val result = ReplayGainParser.parseGainString("-8.20 dB")
        assertNotNull("Should parse valid album gain with dB suffix", result)
        assertEquals(-8.20f, result!!, 0.001f)
    }

    @Test
    fun test3_gain_withPlusSign() {
        val withDb = ReplayGainParser.parseGainString("+2.00 dB")
        assertNotNull("Should parse positive gain with plus sign", withDb)
        assertEquals(2.00f, withDb!!, 0.001f)

        val withoutDb = ReplayGainParser.parseGainString("+1.50")
        assertNotNull("Should parse positive gain without dB suffix", withoutDb)
        assertEquals(1.50f, withoutDb!!, 0.001f)
    }

    @Test
    fun test4_gain_withoutDb() {
        val result = ReplayGainParser.parseGainString("-6.84")
        assertNotNull("Should parse valid gain without dB suffix", result)
        assertEquals(-6.84f, result!!, 0.001f)
    }

    @Test
    fun test5_gain_caseInsensitiveAndWhitespace() {
        val lowerDb = ReplayGainParser.parseGainString("  -6.84 db  ")
        assertNotNull(lowerDb)
        assertEquals(-6.84f, lowerDb!!, 0.001f)

        val upperDb = ReplayGainParser.parseGainString(" -6.84 DB ")
        assertNotNull(upperDb)
        assertEquals(-6.84f, upperDb!!, 0.001f)

        val mixedDb = ReplayGainParser.parseGainString("+3.14 Db")
        assertNotNull(mixedDb)
        assertEquals(3.14f, mixedDb!!, 0.001f)

        // Decimal comma support
        val commaResult = ReplayGainParser.parseGainString("-8,20 dB")
        assertNotNull(commaResult)
        assertEquals(-8.20f, commaResult!!, 0.001f)
    }

    @Test
    fun test6_corruptValues_returnNull() {
        assertNull(ReplayGainParser.parseGainString("corrupted"))
        assertNull(ReplayGainParser.parseGainString("-- dB"))
        assertNull(ReplayGainParser.parseGainString("NaN"))
        assertNull(ReplayGainParser.parseGainString("Infinity"))
        assertNull(ReplayGainParser.parseGainString("-Infinity"))
        assertNull(ReplayGainParser.parseGainString("invalid 12.3 dB"))
        // Physical sanity bound checks (outside [-50, +50] dB)
        assertNull(ReplayGainParser.parseGainString("-999.0 dB"))
        assertNull(ReplayGainParser.parseGainString("+150.0 dB"))
    }

    @Test
    fun test7_missingOrBlankValues_returnNull() {
        assertNull(ReplayGainParser.parseGainString(null))
        assertNull(ReplayGainParser.parseGainString(""))
        assertNull(ReplayGainParser.parseGainString("   "))
    }

    @Test
    fun test8_trackAndAlbumSimultaneous_parsedCorrectly() {
        val tags = mapOf(
            "REPLAYGAIN_TRACK_GAIN" to "-6.84 dB",
            "REPLAYGAIN_ALBUM_GAIN" to "-8.20 dB",
            "REPLAYGAIN_TRACK_PEAK" to "0.985432",
            "REPLAYGAIN_ALBUM_PEAK" to "0.999900"
        )
        val data = ReplayGainParser.parseTags(tags)
        assertNotNull(data.trackGain)
        assertEquals(-6.84f, data.trackGain!!, 0.001f)
        assertNotNull(data.albumGain)
        assertEquals(-8.20f, data.albumGain!!, 0.001f)
        assertNotNull(data.trackPeak)
        assertEquals(0.985432f, data.trackPeak!!, 0.0001f)
        assertNotNull(data.albumPeak)
        assertEquals(0.999900f, data.albumPeak!!, 0.0001f)
    }

    // -------------------------------------------------------------
    // BLOQUE 2: LINEAR CONVERSION Y MATEMÁTICA PURA (Tests 9 - 11)
    // -------------------------------------------------------------

    @Test
    fun test9_linearGain_zeroDb_isOne() {
        val linear = ReplayGainPolicy.dbToLinearGain(0.0f)
        assertEquals("0 dB must produce exactly 1.0 linear gain", 1.0f, linear, 0.0001f)
        val backToDb = ReplayGainPolicy.linearToDb(linear)
        assertEquals(0.0f, backToDb, 0.001f)
    }

    @Test
    fun test10_linearGain_minusSixDb_isHalf() {
        // -6.0206 dB corresponds to a factor of ~0.5 (half amplitude)
        val linear = ReplayGainPolicy.dbToLinearGain(-6.0206f)
        assertEquals(0.5f, linear, 0.005f)
    }

    @Test
    fun test11_linearGain_plusSixDb_isDouble() {
        // +6.0206 dB corresponds to a factor of ~2.0 (double amplitude)
        val linear = ReplayGainPolicy.dbToLinearGain(6.0206f)
        assertEquals(2.0f, linear, 0.005f)
    }

    // -------------------------------------------------------------
    // BLOQUE 3: POLÍTICAS DE GANANCIA Y PREAMP (Tests 12 - 16)
    // -------------------------------------------------------------

    @Test
    fun test12_policyOff_alwaysZeroDb() {
        val effective = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = ReplayGainMode.OFF,
            trackGain = -6.84f,
            albumGain = -8.20f,
            preampDb = 3.0f
        )
        assertEquals("In OFF mode, effective gain must strictly be 0.0 dB regardless of tags or preamp", 0.0f, effective, 0.0001f)
    }

    @Test
    fun test13_policyTrack_usesTrackGain() {
        val effective = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = ReplayGainMode.TRACK,
            trackGain = -6.84f,
            albumGain = -8.20f,
            preampDb = 0.0f
        )
        assertEquals(-6.84f, effective, 0.001f)
    }

    @Test
    fun test14_policyAlbum_usesAlbumGain() {
        val effective = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = ReplayGainMode.ALBUM,
            trackGain = -6.84f,
            albumGain = -8.20f,
            preampDb = 0.0f
        )
        assertEquals(-8.20f, effective, 0.001f)
    }

    @Test
    fun test15_policyFallback_whenSelectedMetadataMissing() {
        // ALBUM mode with missing albumGain falls back to trackGain
        val albumFallback = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = ReplayGainMode.ALBUM,
            trackGain = -5.50f,
            albumGain = null,
            preampDb = 0.0f
        )
        assertEquals("ALBUM mode must fallback to trackGain if albumGain is null", -5.50f, albumFallback, 0.001f)

        // TRACK mode with missing trackGain falls back to albumGain
        val trackFallback = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = ReplayGainMode.TRACK,
            trackGain = null,
            albumGain = -7.10f,
            preampDb = 0.0f
        )
        assertEquals("TRACK mode must fallback to albumGain if trackGain is null", -7.10f, trackFallback, 0.001f)

        // Both missing falls back to 0.0 dB
        val bothMissing = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = ReplayGainMode.TRACK,
            trackGain = null,
            albumGain = null,
            preampDb = 0.0f
        )
        assertEquals("When both tags missing, gain must be 0.0 dB", 0.0f, bothMissing, 0.0001f)
    }

    @Test
    fun test16_policyPreamp_modifiesEffectiveGainProperly() {
        val withPreamp = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = ReplayGainMode.TRACK,
            trackGain = -6.0f,
            albumGain = null,
            preampDb = 2.0f
        )
        // -6.0 + 2.0 = -4.0 dB
        assertEquals(-4.0f, withPreamp, 0.001f)

        // Preamp clamping check (exceeding MAX_PREAMP_DB 15 dB)
        val clampedPreamp = ReplayGainPolicy.calculateEffectiveGainDb(
            mode = ReplayGainMode.TRACK,
            trackGain = 0.0f,
            albumGain = null,
            preampDb = 50.0f
        )
        assertEquals("Preamp must be clamped to max 15.0 dB", 15.0f, clampedPreamp, 0.001f)
    }

    // -------------------------------------------------------------
    // BLOQUE 4: PEAK Y CLIPPING HEADROOM (Tests Adicionales)
    // -------------------------------------------------------------

    @Test
    fun test_peakParsingAndClippingDetection() {
        val peak = ReplayGainParser.parsePeakString("0.891234")
        assertNotNull(peak)
        assertEquals(0.891234f, peak!!, 0.0001f)

        // If peak is 0.891234 and gain is +0.5 dB, linear gain = 1.059, resulting peak = 0.944 <= 1.0 (no clip)
        assertFalse(ReplayGainPolicy.isClippingLikely(0.5f, peak))

        // If gain is +6.0 dB (linear factor 2.0), resulting peak = 1.78 > 1.0 (clipping likely)
        assertTrue(ReplayGainPolicy.isClippingLikely(6.0f, peak))

        // Max safe gain without clipping
        val maxGain = ReplayGainPolicy.calculateMaxGainWithoutClippingDb(0.5f) // 1.0 / 0.5 = 2.0 -> ~6.02 dB
        assertNotNull(maxGain)
        assertEquals(6.0206f, maxGain!!, 0.01f)
    }

    // -------------------------------------------------------------
    // BLOQUE 5: INTEGRACIÓN CON SCANNER (Tests 17 - 20)
    // -------------------------------------------------------------

    class FakeReplayGainMediaProvider : ContentProvider() {
        companion object {
            val records = mutableListOf<ContentValues>()
            fun reset() {
                records.clear()
            }
        }

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? = null

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

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val cols = projection ?: arrayOf(
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
                MediaStore.Audio.Media.IS_MUSIC
            )
            val matrixCursor = MatrixCursor(cols)
            for (item in records) {
                if (selection != null && selectionArgs != null && selection.contains("IN")) {
                    val idStr = item.getAsLong(MediaStore.Audio.Media._ID).toString()
                    if (!selectionArgs.contains(idStr)) continue
                }
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

    class StubReplayGainMetadataExtractor(
        private val gainsById: Map<Long, Pair<Float?, Float?>> = emptyMap()
    ) : MetadataExtractor {
        val extractCallCount = AtomicInteger(0)
        val extractedIds = mutableListOf<Long>()

        override fun extract(context: Context, uri: Uri, filePath: String?): ExtendedMetadataExtractor.AudioSpecs {
            val id = ContentUris.parseId(uri)
            extractCallCount.incrementAndGet()
            extractedIds.add(id)
            val gains = gainsById[id]

            return ExtendedMetadataExtractor.AudioSpecs(
                bitDepth = 16,
                sampleRate = 44100,
                mimeType = "audio/mpeg",
                fileExtension = "mp3",
                codec = "MP3",
                bitrate = 320000L,
                channels = 2,
                replayGainTrack = gains?.first,
                replayGainAlbum = gains?.second
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
        FakeReplayGainMediaProvider.reset()
        Robolectric.setupContentProvider(FakeReplayGainMediaProvider::class.java, "media")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun insertMediaStoreSong(
        id: Long,
        title: String,
        path: String,
        dateModified: Long = 1000L,
        size: Long = 5000000L
    ) {
        val cv = ContentValues().apply {
            put(MediaStore.Audio.Media._ID, id)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, "Artist $id")
            put(MediaStore.Audio.Media.ALBUM, "Album $id")
            put(MediaStore.Audio.Media.ALBUM_ID, 100L + id)
            put(MediaStore.Audio.Media.DURATION, 200000L)
            put(MediaStore.Audio.Media.DATA, path)
            put(MediaStore.Audio.Media.SIZE, size)
            put(MediaStore.Audio.Media.DATE_MODIFIED, dateModified)
            put(MediaStore.Audio.Media.DATE_ADDED, 900L)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.TRACK, 1)
            put(MediaStore.Audio.Media.YEAR, 2024)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }
        context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv)
    }

    @Test
    fun test17_newSong_extractsAndPersistsReplayGain() = runTest(testDispatcher) {
        insertMediaStoreSong(id = 1001L, title = "New Song With RG", path = "/storage/emulated/0/Music/new_rg.mp3")

        val extractor = StubReplayGainMetadataExtractor(
            gainsById = mapOf(1001L to Pair(-7.45f, -6.20f))
        )
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()
        assertEquals(1, progress.newCount)
        assertEquals(1, extractor.extractCallCount.get())

        val persisted = dao.getSongById(1001L)
        assertNotNull("Song must be saved in Room", persisted)
        assertNotNull("Track gain must be persisted", persisted!!.replayGainTrack)
        assertEquals(-7.45f, persisted.replayGainTrack!!, 0.001f)
        assertNotNull("Album gain must be persisted", persisted.replayGainAlbum)
        assertEquals(-6.20f, persisted.replayGainAlbum!!, 0.001f)
    }

    @Test
    fun test18_modifiedSong_updatesReplayGain() = runTest(testDispatcher) {
        // Pre-insert an existing song with old ReplayGain values
        val existingSong = Song(
            id = 2001L,
            uri = "content://media/external/audio/media/2001",
            title = "Existing Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            albumArtUri = null,
            fileSize = 4000000L,
            dateModified = 1000L,
            replayGainTrack = -3.0f,
            replayGainAlbum = -3.0f
        )
        dao.insertSongs(listOf(existingSong))

        // Insert modified version in MediaStore (updated dateModified and size)
        insertMediaStoreSong(
            id = 2001L,
            title = "Existing Song Re-encoded",
            path = "/storage/emulated/0/Music/modified_rg.mp3",
            dateModified = 2000L, // Newer timestamp triggers MODIFIED
            size = 4500000L
        )

        val extractor = StubReplayGainMetadataExtractor(
            gainsById = mapOf(2001L to Pair(-8.50f, -7.80f))
        )
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()
        assertEquals(0, progress.newCount)
        assertEquals(1, progress.modifiedCount)
        assertEquals(1, extractor.extractCallCount.get())

        val updated = dao.getSongById(2001L)
        assertNotNull(updated)
        assertEquals("ReplayGain track gain must be updated", -8.50f, updated!!.replayGainTrack!!, 0.001f)
        assertEquals("ReplayGain album gain must be updated", -7.80f, updated.replayGainAlbum!!, 0.001f)
    }

    @Test
    fun test19_unchangedSong_doesNotInvokeParser() = runTest(testDispatcher) {
        // Existing song in Room matching MediaStore exactly
        val existingSong = Song(
            id = 3001L,
            uri = "content://media/external/audio/media/3001",
            title = "Unchanged Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            albumArtUri = null,
            fileSize = 4000000L,
            dateModified = 1000L,
            replayGainTrack = -4.50f,
            replayGainAlbum = -4.50f
        )
        dao.insertSongs(listOf(existingSong))

        // Same timestamp & size in MediaStore
        insertMediaStoreSong(
            id = 3001L,
            title = "Unchanged Song",
            path = "/storage/emulated/0/Music/unchanged.mp3",
            dateModified = 1000L,
            size = 4000000L
        )

        val extractor = StubReplayGainMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)

        val progress = scanner.scan()
        assertEquals(0, progress.newCount)
        assertEquals(0, progress.modifiedCount)
        assertEquals("Extractor/Parser MUST NOT be called for UNCHANGED songs", 0, extractor.extractCallCount.get())

        val preserved = dao.getSongById(3001L)
        assertNotNull(preserved)
        assertEquals(-4.50f, preserved!!.replayGainTrack!!, 0.001f)
    }

    @Test
    fun test20_excludedSong_doesNotInvokeParser() = runTest(testDispatcher) {
        // Song in excluded folder
        insertMediaStoreSong(
            id = 4001L,
            title = "Excluded Audio",
            path = "/storage/emulated/0/Music/Podcasts/episode.mp3"
        )

        val extractor = StubReplayGainMetadataExtractor()
        val scanner = LibraryScanner(context, dao, metadataExtractor = extractor)
        scanner.addExcludedFolder("/storage/emulated/0/Music/Podcasts")

        val progress = scanner.scan()
        assertEquals(0, progress.newCount)
        assertEquals("Extractor/Parser MUST NOT be called for EXCLUDED songs", 0, extractor.extractCallCount.get())
        assertNull("Excluded song must not be stored in active library", dao.getSongById(4001L))
    }

    // -------------------------------------------------------------
    // BLOQUE 6: STREAM BINARIO ID3v2 Y FLAC VORBIS COMMENT
    // -------------------------------------------------------------

    @Test
    fun test_flacVorbisComment_binaryExtraction() {
        val baos = ByteArrayOutputStream()
        // 1. "fLaC" magic
        baos.write("fLaC".toByteArray(Charsets.US_ASCII))

        // 2. Vorbis Comment metadata block header (type 4, isLast = true)
        // Build payload first
        val commentBaos = ByteArrayOutputStream()
        val commentBb = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)

        val vendor = "reference libFLAC 1.3.2"
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        commentBb.putInt(vendorBytes.size)
        commentBb.put(vendorBytes)

        val userComments = listOf(
            "REPLAYGAIN_TRACK_GAIN=-7.12 dB",
            "REPLAYGAIN_ALBUM_GAIN=-8.50 dB",
            "REPLAYGAIN_TRACK_PEAK=0.978123"
        )
        commentBb.putInt(userComments.size)
        for (c in userComments) {
            val cBytes = c.toByteArray(Charsets.UTF_8)
            commentBb.putInt(cBytes.size)
            commentBb.put(cBytes)
        }

        val payload = commentBb.array().copyOf(commentBb.position())
        val payloadLen = payload.size

        // Block header: 1 byte (0x80 | 4 = 0x84), 3 bytes length
        baos.write(0x84)
        baos.write((payloadLen shr 16) and 0xFF)
        baos.write((payloadLen shr 8) and 0xFF)
        baos.write(payloadLen and 0xFF)
        baos.write(payload)

        val flacBytes = baos.toByteArray()
        val stream = ByteArrayInputStream(flacBytes)

        val data = ReplayGainParser.extractFromStream(stream, "flac")
        assertNotNull("FLAC Vorbis comment track gain must be extracted", data.trackGain)
        assertEquals(-7.12f, data.trackGain!!, 0.001f)
        assertNotNull("FLAC Vorbis comment album gain must be extracted", data.albumGain)
        assertEquals(-8.50f, data.albumGain!!, 0.001f)
        assertNotNull("FLAC Vorbis comment track peak must be extracted", data.trackPeak)
        assertEquals(0.978123f, data.trackPeak!!, 0.0001f)
    }
}
