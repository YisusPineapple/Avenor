package io.github.yisus.avenor.metadata

import io.github.yisus.avenor.replaygain.ReplayGainData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainContractsAndFileSourceTest {

    @Test
    fun `test MemorySeekableFileSource read and boundary checks`() {
        val bytes = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80.toByte())
        val source = MemorySeekableFileSource(bytes)

        assertEquals(8L, source.size)
        assertEquals(0L, source.tell())

        val buffer = ByteArray(4)
        val read1 = source.read(buffer, 0, 4)
        assertEquals(4, read1)
        assertEquals(0x10.toByte(), buffer[0])
        assertEquals(0x40.toByte(), buffer[3])
        assertEquals(4L, source.tell())

        // Seek backward
        source.seek(2L)
        assertEquals(2L, source.tell())
        val single = source.readByte()
        assertEquals(0x30, single)
        assertEquals(3L, source.tell())

        // Duplicate
        val dup = source.duplicate()
        assertEquals(0L, dup.tell())
        assertEquals(8L, dup.size)

        // Read to EOF
        source.seek(7L)
        assertEquals(0x80, source.readByte())
        assertEquals(-1, source.readByte()) // EOF

        source.close()
        dup.close()
    }

    @Test
    fun `test CanonicalSongCore derived artist and explicit rating`() {
        val core = CanonicalSongCore(
            id = 101L,
            uri = "content://media/external/audio/media/101",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            explicitRating = ExplicitRating.CLEAN
        )

        assertEquals("Queen", core.artist)
        assertEquals("Queen", core.displayArtist)
        assertEquals(ExplicitRating.CLEAN, core.explicitRating)
        assertEquals(ExplicitRating.EXPLICIT, ExplicitRating.fromTag("explicit"))
        assertEquals(ExplicitRating.CLEAN, ExplicitRating.fromTag("clean"))
        assertEquals(ExplicitRating.UNKNOWN, ExplicitRating.fromTag("unknown_or_null"))
    }

    @Test
    fun `test ExtendedMetadataBlock multi-value composers and raw tags`() {
        val rawMap = mapOf(
            "ENCODER" to listOf("LAME 3.100"),
            "PERFORMER" to listOf("John Deacon", "Brian May")
        )
        val extended = ExtendedMetadataBlock(
            songId = 101L,
            composers = listOf("Freddie Mercury"),
            genres = listOf("Rock", "Progressive Rock", "Opera Rock"),
            rawTags = rawMap
        )

        assertEquals(1, extended.composers.size)
        assertEquals("Freddie Mercury", extended.composers[0])
        assertEquals(3, extended.genres.size)
        assertEquals("LAME 3.100", extended.rawTags["ENCODER"]?.first())
    }

    @Test
    fun `test SongArtworkDescriptor deduplication attributes`() {
        val descriptor = SongArtworkDescriptor(
            songId = 101L,
            artworkHash = "a1b2c3d4e5f6",
            durableRelativePath = "media_art/a1b2c3d4e5f6.webp",
            mimeType = "image/webp",
            width = 512,
            height = 512,
            artworkType = ArtworkPictureType.FRONT_COVER,
            sourceType = ArtworkSourceType.EMBEDDED
        )

        assertEquals("a1b2c3d4e5f6", descriptor.artworkHash)
        assertEquals(512, descriptor.width)
        assertTrue(descriptor.isRegenerable)
    }

    @Test
    fun `test SongLyricsBlock synced and unsynced indicators`() {
        val synced = SongLyricsBlock(
            songId = 101L,
            hasSynced = true,
            syncedLyricsLrc = "[00:15.20]Is this the real life?",
            sourceType = LyricsSourceType.EXTERNAL_LRC
        )
        assertTrue(synced.hasSynced)
        assertTrue(synced.hasAnyLyrics)

        val empty = SongLyricsBlock(songId = 102L)
        assertFalse(empty.hasAnyLyrics)
    }

    @Test
    fun `test AudioContainerType and AudioCodecType resolution`() {
        assertEquals(AudioContainerType.MP3, AudioContainerType.fromExtension("mp3"))
        assertEquals(AudioContainerType.FLAC, AudioContainerType.fromExtension(".flac"))
        assertEquals(AudioContainerType.MATROSKA, AudioContainerType.fromExtension("mka"))
        assertEquals(AudioContainerType.AIFF, AudioContainerType.fromExtension("aif"))
        assertEquals(AudioContainerType.WAVPACK, AudioContainerType.fromExtension(".wv"))

        assertEquals(AudioCodecType.ALAC, AudioCodecType.fromMimeOrCodec("audio/alac"))
        assertEquals(AudioCodecType.OPUS, AudioCodecType.fromMimeOrCodec("audio/opus"))
        assertEquals(AudioCodecType.AC3, AudioCodecType.fromMimeOrCodec("audio/ac3"))
        assertEquals(AudioCodecType.PCM_FLOAT, AudioCodecType.fromMimeOrCodec("pcm_float"))
    }
}
