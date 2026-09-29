package io.github.yisus.avenor.lyrics

import android.content.Context
import io.github.yisus.avenor.Song
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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class LyricsRepositoryEmbeddedTest {

    private lateinit var context: Context
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    /**
     * Builds a minimal valid MP3 file: ID3v2.3 tag with a USLT frame
     * (synchronized LRC format) followed by a single MPEG-1 Layer III
     * frame header so BinaryHeaderMetadataExtractor sniffs it as MP3.
     */
    private fun buildMp3WithUslt(lyrics: String): File {
        val lyricsPayload = "\u0003${lyrics}".toByteArray(Charsets.UTF_8)
        val usltFrameSize = 3 + lyricsPayload.size // lang(3) + payload
        val id3TagSize = 10 + usltFrameSize

        val bytes = ByteArray(10 + id3TagSize + 4)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // ID3v2.3 header
        bb.put("ID3".toByteArray(Charsets.US_ASCII))
        bb.put(3).put(0).put(0)
        // syncsafe size
        bb.put(((id3TagSize ushr 21) and 0x7F).toByte())
        bb.put(((id3TagSize ushr 14) and 0x7F).toByte())
        bb.put(((id3TagSize ushr 7) and 0x7F).toByte())
        bb.put((id3TagSize and 0x7F).toByte())

        // USLT frame
        bb.put("USLT".toByteArray(Charsets.US_ASCII))
        bb.putInt(usltFrameSize)
        bb.putShort(0)
        bb.put("eng".toByteArray(Charsets.US_ASCII))
        bb.put(lyricsPayload)

        // MPEG-1 Layer III frame header (320kbps 44.1kHz stereo)
        bb.put(0xFF.toByte()).put(0xFB.toByte()).put(0xE0.toByte()).put(0x00.toByte())

        val file = File.createTempFile("lyrics_test_", ".mp3", context.cacheDir)
        file.writeBytes(bytes)
        tempFiles.add(file)
        return file
    }

    private fun songFor(file: File): Song = Song(
        id = 1L,
        uri = "file://${file.absolutePath}",
        title = "Test Song",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        albumArtUri = null
    )

    @Test
    fun testEmbeddedSyncedUslt_isParsedWithoutSiblingLrc() = runTest {
        val lyrics = "[00:05.00]First line\n[00:10.00]Second line"
        val file = buildMp3WithUslt(lyrics)

        // Pre-condition: no .lrc sibling exists
        val lrcSibling = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
        assertTrue("Test setup must not create a .lrc sibling", !lrcSibling.exists())

        val repo = LyricsRepository(context)
        val result = repo.getLyricsForSong(songFor(file))

        assertEquals("Embedded USLT must be parsed into 2 lines", 2, result.size)
        assertEquals(5_000L, result[0].timeMs)
        assertEquals("First line", result[0].text)
        assertEquals(10_000L, result[1].timeMs)
        assertEquals("Second line", result[1].text)
    }

    @Test
    fun testNoEmbeddedLyrics_returnsEmptyList() = runTest {
        // MP3 with no USLT frame
        val bytes = ByteArray(14)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        bb.put("ID3".toByteArray(Charsets.US_ASCII)).put(3).put(0).put(0)
        bb.put(0).put(0).put(0).put(0)
        bb.put(0xFF.toByte()).put(0xFB.toByte()).put(0xE0.toByte()).put(0x00.toByte())

        val file = File.createTempFile("no_lyrics_", ".mp3", context.cacheDir)
        file.writeBytes(bytes)
        tempFiles.add(file)

        val repo = LyricsRepository(context)
        val result = repo.getLyricsForSong(songFor(file))

        assertTrue("Song without lyrics must return empty list", result.isEmpty())
    }

    @Test
    fun testEmbeddedUnsyncedUslt_isReturnedByGetUnsyncedLyricsForSong() = runTest {
        val plainLyrics = "First line without timestamp\nSecond line without timestamp"
        val file = buildMp3WithUslt(plainLyrics)
        val repo = LyricsRepository(context)

        val synced = repo.getLyricsForSong(songFor(file))
        assertTrue("Synced list must be empty for plain-text USLT", synced.isEmpty())

        val unsynced = repo.getUnsyncedLyricsForSong(songFor(file))
        assertNotNull("Unsynced lyrics must be returned", unsynced)
        assertTrue(unsynced!!.contains("First line without timestamp"))
        assertTrue(unsynced.contains("Second line without timestamp"))
    }
}
