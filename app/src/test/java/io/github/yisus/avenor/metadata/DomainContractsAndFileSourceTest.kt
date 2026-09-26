package io.github.yisus.avenor.metadata

import androidx.media3.common.C
import io.github.yisus.avenor.ExtendedMetadataExtractor
import io.github.yisus.avenor.replaygain.ReplayGainData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DomainContractsAndFileSourceTest {

    @Test
    fun `test MemorySeekableFileSource read, boundary checks, and RandomAccessFileSeekableSource`() {
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

        // Also test physical File-backed RandomAccessFileSeekableSource
        val tempFile = File.createTempFile("avenor_seek_test", ".bin")
        try {
            tempFile.writeBytes(bytes)
            SeekableFileSource.fromFile(tempFile).use { fileSource ->
                assertEquals(8L, fileSource.size)
                fileSource.seek(3L)
                assertEquals(0x40, fileSource.readByte())
                fileSource.duplicate().use { fileDup ->
                    assertEquals(0L, fileDup.tell())
                    assertEquals(0x10, fileDup.readByte())
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test CanonicalSongCore derived artist, explicit rating, and Room Song round-trip projection`() {
        val core = CanonicalSongCore(
            id = 101L,
            uri = "content://media/external/audio/media/101",
            title = "The Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            bitDepth = 24,
            sampleRate = 96000,
            channels = 6,
            codec = "FLAC",
            container = "flac",
            mimeType = "audio/flac",
            explicitRating = ExplicitRating.CLEAN,
            replayGain = ReplayGainData(trackGain = -4.5f, albumGain = -6.0f)
        )

        assertEquals("Queen", core.artist)
        assertEquals("Queen", core.displayArtist)
        assertEquals(ExplicitRating.CLEAN, core.explicitRating)
        assertEquals(ExplicitRating.EXPLICIT, ExplicitRating.fromTag("explicit"))
        assertEquals(ExplicitRating.CLEAN, ExplicitRating.fromTag("clean"))
        assertEquals(ExplicitRating.UNKNOWN, ExplicitRating.fromTag("unknown_or_null"))

        val songEntity = core.toSong(albumArtUri = "content://media/external/audio/albumart/5", composer = "Freddie Mercury")
        assertEquals(101L, songEntity.id)
        assertEquals("Bohemian Rhapsody", songEntity.sortTitle)
        assertEquals(-4.5f, songEntity.replayGainTrack ?: 0f, 1e-4f)
        assertEquals(6, songEntity.channels)

        val lifted = CanonicalSongCore.fromSong(songEntity)
        assertEquals(core.id, lifted.id)
        assertEquals(core.title, lifted.title)
        assertEquals(core.sampleRate, lifted.sampleRate)
        assertEquals(core.bitDepth, lifted.bitDepth)
        assertEquals(core.channels, lifted.channels)
        assertTrue(lifted.hasEmbeddedArtwork)
    }

    @Test
    fun `test ExtendedMetadataBlock multi-value composers and raw tags from RawMetadataPackage`() {
        val rawMap = mapOf(
            "ENCODER" to listOf("LAME 3.100"),
            "PERFORMER" to listOf("John Deacon; Brian May"),
            "COMPOSER" to listOf("Freddie Mercury"),
            "GENRE" to listOf("Rock/Progressive Rock; Opera Rock"),
            "TRACKTOTAL" to listOf("12")
        )
        val pkg = RawMetadataPackage(properties = rawMap, sampleRate = 48000, channelCount = 2, bitDepth = 24)
        val extended = ExtendedMetadataBlock.fromRawPackage(101L, pkg)

        assertEquals(1, extended.composers.size)
        assertEquals("Freddie Mercury", extended.composers[0])
        assertEquals(2, extended.performers.size)
        assertEquals(3, extended.genres.size)
        assertEquals(12, extended.totalTracks)
        assertEquals("LAME 3.100", extended.rawTags["ENCODER"]?.first())
    }

    @Test
    fun `test SongArtworkDescriptor SHA-256 content hash deduplication`() {
        val sampleArtBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val pkg1 = RawMetadataPackage(
            properties = emptyMap(),
            rawArtworkBytes = sampleArtBytes,
            rawArtworkMime = "image/png"
        )
        val pkg2 = RawMetadataPackage(
            properties = emptyMap(),
            rawArtworkBytes = sampleArtBytes.clone(),
            rawArtworkMime = "image/png"
        )

        val desc1 = SongArtworkDescriptor.fromRawPackage(101L, pkg1)
        val desc2 = SongArtworkDescriptor.fromRawPackage(102L, pkg2)
        assertNotNull(desc1)
        assertNotNull(desc2)
        assertEquals(desc1!!.artworkHash, desc2!!.artworkHash)
        assertEquals(64, desc1.artworkHash.length) // SHA-256 hex is 64 chars
        assertEquals(ArtworkSourceType.EMBEDDED, desc1.sourceType)
    }

    @Test
    fun `test SongLyricsBlock synced and unsynced indicators and RawMetadataPackage extraction`() {
        val syncedPkg = RawMetadataPackage(
            properties = mapOf("LYRICS" to listOf("[00:15.20]Is this the real life?"))
        )
        val synced = SongLyricsBlock.fromRawPackage(101L, syncedPkg)
        assertTrue(synced.hasSynced)
        assertTrue(synced.hasAnyLyrics)
        assertEquals(LyricsSourceType.EMBEDDED_SYNC, synced.sourceType)

        val empty = SongLyricsBlock(songId = 102L)
        assertFalse(empty.hasAnyLyrics)
    }

    @Test
    fun `test AudioContainerType, AudioCodecType AAC MP4 equivalents, and DefaultDecoderResolver`() {
        assertEquals(AudioContainerType.MP3, AudioContainerType.fromExtension("mp3"))
        assertEquals(AudioContainerType.FLAC, AudioContainerType.fromExtension(".flac"))
        assertEquals(AudioContainerType.MATROSKA, AudioContainerType.fromExtension("mka"))
        assertEquals(AudioContainerType.AIFF, AudioContainerType.fromExtension("aif"))
        assertEquals(AudioContainerType.WAVPACK, AudioContainerType.fromExtension(".wv"))

        // AAC & MP4 MIME/Codec variants (Audit Error 6 verification)
        assertEquals(AudioCodecType.AAC, AudioCodecType.fromMimeOrCodec("audio/mp4a-latm"))
        assertEquals(AudioCodecType.AAC, AudioCodecType.fromMimeOrCodec("mp4a.40.2"))
        assertEquals(AudioCodecType.AAC, AudioCodecType.fromMimeOrCodec("mp4a.40.5"))
        assertEquals(AudioCodecType.AAC, AudioCodecType.fromMimeOrCodec("mp4a.40.29"))
        assertEquals(AudioCodecType.AAC, AudioCodecType.fromMimeOrCodec("audio/mp4"))
        assertEquals(AudioCodecType.AAC, AudioCodecType.fromMimeOrCodec("audio/x-m4a"))
        assertEquals(AudioCodecType.AAC, AudioCodecType.fromMimeOrCodec("audio/aacp"))
        assertEquals("AAC", ExtendedMetadataExtractor.resolveCodec("audio/mp4", "m4a"))

        assertEquals(AudioCodecType.ALAC, AudioCodecType.fromMimeOrCodec("audio/alac"))
        assertEquals(AudioCodecType.OPUS, AudioCodecType.fromMimeOrCodec("audio/opus"))
        assertEquals(AudioCodecType.AC3, AudioCodecType.fromMimeOrCodec("audio/ac3"))
        assertEquals(AudioCodecType.EAC3, AudioCodecType.fromMimeOrCodec("audio/eac3"))
        assertEquals(AudioCodecType.PCM_FLOAT, AudioCodecType.fromMimeOrCodec("pcm_float"))

        // Verify DefaultDecoderResolver
        val aacRes = DefaultDecoderResolver.resolve(AudioContainerType.M4A, AudioCodecType.AAC, 48000, 2, 16)
        assertTrue(aacRes is DecoderResolution.PlatformMediaCodec)
        val wavpackRes = DefaultDecoderResolver.resolve(AudioContainerType.WAVPACK, AudioCodecType.WAVPACK, 96000, 2, 24)
        assertTrue(wavpackRes is DecoderResolution.SoftwareDecoder)
        val pcmRes = DefaultDecoderResolver.resolve(AudioContainerType.WAV, AudioCodecType.PCM_S16LE, 44100, 2, 16)
        assertEquals(DecoderResolution.PassthroughPcm(C.ENCODING_PCM_16BIT), pcmRes)
    }

    @Test
    fun `test DefaultMetadataExtractorResolver and BinaryHeaderMetadataExtractor on synthetic WAV stream`() {
        // Construct a valid 44-byte WAV header: 96kHz, 6 channels (5.1), 24-bit PCM
        val wavHeader = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        wavHeader.put("RIFF".toByteArray(Charsets.US_ASCII))
        wavHeader.putInt(36 + 96000 * 6 * 3)
        wavHeader.put("WAVE".toByteArray(Charsets.US_ASCII))
        wavHeader.put("fmt ".toByteArray(Charsets.US_ASCII))
        wavHeader.putInt(16) // fmt chunk size
        wavHeader.putShort(1) // PCM format
        wavHeader.putShort(6) // 6 channels (5.1)
        wavHeader.putInt(96000) // 96 kHz
        wavHeader.putInt(96000 * 6 * 3) // byteRate
        wavHeader.putShort((6 * 3).toShort()) // blockAlign
        wavHeader.putShort(24) // 24-bit
        wavHeader.put("data".toByteArray(Charsets.US_ASCII))
        wavHeader.putInt(96000 * 6 * 3) // 1 second of data

        val source = MemorySeekableFileSource(wavHeader.array())
        val sniffed = AudioContainerType.sniffFromSource(source)
        assertEquals(AudioContainerType.WAV, sniffed)

        val pkg = ExtendedMetadataExtractor.extractFromSource(source, sniffed)
        assertEquals(96000, pkg.sampleRate)
        assertEquals(6, pkg.channelCount)
        assertEquals(24, pkg.bitDepth)
        assertEquals(1000L, pkg.durationMs)
    }
}
