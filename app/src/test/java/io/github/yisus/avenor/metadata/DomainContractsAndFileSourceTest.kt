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

        // Verify DefaultDecoderResolver availability status (SUPPORTED_NOW vs PLANNED_BACKEND vs UNSUPPORTED)
        val aacRes = DefaultDecoderResolver.resolve(AudioContainerType.M4A, AudioCodecType.AAC, 48000, 2, 16)
        assertTrue(aacRes is DecoderResolution.PlatformMediaCodec)
        assertEquals(DecoderAvailabilityStatus.SUPPORTED_NOW, aacRes.availabilityStatus)
        assertTrue(aacRes.isSupportedNow)
        // On JVM unit test without device MediaCodecList, isDeviceVerified is false and isHardwareAccelerated is not hardcoded to true
        assertFalse((aacRes as DecoderResolution.PlatformMediaCodec).isDeviceVerified)

        val wavpackRes = DefaultDecoderResolver.resolve(AudioContainerType.WAVPACK, AudioCodecType.WAVPACK, 96000, 2, 24)
        assertTrue(wavpackRes is DecoderResolution.SoftwareDecoder)
        assertFalse((wavpackRes as DecoderResolution.SoftwareDecoder).isBackendInstalled)
        assertEquals(DecoderAvailabilityStatus.PLANNED_BACKEND, wavpackRes.availabilityStatus)
        assertFalse(wavpackRes.isSupportedNow)

        // Productive resolution rejects PLANNED_BACKEND codecs whose native libraries are not bundled
        val wavpackProductive = DefaultDecoderResolver.resolveProductive(
            AudioContainerType.WAVPACK,
            AudioCodecType.WAVPACK,
            96000,
            2,
            24
        )
        assertTrue(wavpackProductive is DecoderResolution.Unsupported)
        assertEquals(DecoderAvailabilityStatus.UNSUPPORTED, wavpackProductive.availabilityStatus)

        val pcmRes = DefaultDecoderResolver.resolve(AudioContainerType.WAV, AudioCodecType.PCM_S16LE, 44100, 2, 16)
        assertEquals(DecoderResolution.PassthroughPcm(C.ENCODING_PCM_16BIT), pcmRes)
        assertEquals(DecoderAvailabilityStatus.SUPPORTED_NOW, pcmRes.availabilityStatus)

        // AIFF is classified as PLANNED_BACKEND in resolve() and Unsupported in resolveProductive()
        // because Media3 1.2.1 DefaultExtractorsFactory does not bundle an AIFF extractor
        val aiffRes = DefaultDecoderResolver.resolve(AudioContainerType.AIFF, AudioCodecType.PCM_S16LE, 48000, 2, 24)
        assertTrue(aiffRes is DecoderResolution.SoftwareDecoder)
        assertEquals(DecoderAvailabilityStatus.PLANNED_BACKEND, aiffRes.availabilityStatus)
        assertFalse(aiffRes.isSupportedNow)
        val aiffProductive = DefaultDecoderResolver.resolveProductive(AudioContainerType.AIFF, AudioCodecType.PCM_S16LE, 48000, 2, 24)
        assertTrue(aiffProductive is DecoderResolution.Unsupported)

        // Device-dependent OEM codecs (AC3, EAC3, AC4) are NOT reported as SUPPORTED_NOW when MediaCodecList does not verify them
        val ac3Res = DefaultDecoderResolver.resolve(AudioContainerType.AC3, AudioCodecType.AC3, 48000, 6, 16)
        assertTrue(ac3Res is DecoderResolution.PlatformMediaCodec)
        assertTrue((ac3Res as DecoderResolution.PlatformMediaCodec).isDeviceDependentCodec)
        assertFalse(ac3Res.isDeviceVerified)
        assertEquals(DecoderAvailabilityStatus.UNSUPPORTED, ac3Res.availabilityStatus)
        assertFalse(ac3Res.isSupportedNow)
        val ac3Productive = DefaultDecoderResolver.resolveProductive(AudioContainerType.AC3, AudioCodecType.AC3, 48000, 6, 16)
        assertTrue(ac3Productive is DecoderResolution.Unsupported)

        // DTS and TrueHD are recognized and classified as PLANNED_BACKEND -> Unsupported in resolveProductive()
        assertEquals(AudioCodecType.DTS, AudioCodecType.fromMimeOrCodec("audio/vnd.dts"))
        assertEquals(AudioCodecType.TRUEHD, AudioCodecType.fromMimeOrCodec("audio/truehd"))
        val dtsRes = DefaultDecoderResolver.resolve(AudioContainerType.MATROSKA, AudioCodecType.DTS, 48000, 6, 24)
        assertEquals(DecoderAvailabilityStatus.PLANNED_BACKEND, dtsRes.availabilityStatus)
        assertTrue(
            DefaultDecoderResolver.resolveProductive(AudioContainerType.MATROSKA, AudioCodecType.DTS, 48000, 6, 24) is DecoderResolution.Unsupported
        )
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
        assertEquals(BinaryHeaderMetadataExtractor, DefaultMetadataExtractorResolver.resolve(source, sniffed))

        val pkg = ExtendedMetadataExtractor.extractFromSource(source, sniffed)
        assertEquals(96000, pkg.sampleRate)
        assertEquals(6, pkg.channelCount)
        assertEquals(24, pkg.bitDepth)
        assertEquals(1000L, pkg.durationMs)
    }

    @Test
    fun `test BinaryHeaderMetadataExtractor lightweight FLAC picture header scan avoids allocating payload bytes`() {
        // Build synthetic FLAC with STREAMINFO (block 0) + PICTURE (block 6, isLast = true)
        val mimeBytes = "image/jpeg".toByteArray(Charsets.US_ASCII)
        val dummyImagePayload = ByteArray(1024) { 0x5A.toByte() }
        val picBlockBodyLen = 4 + 4 + mimeBytes.size + 4 + 20 + dummyImagePayload.size
        val flacBuf = ByteBuffer.allocate(4 + (4 + 34) + (4 + picBlockBodyLen)).order(ByteOrder.BIG_ENDIAN)
        flacBuf.put("fLaC".toByteArray(Charsets.US_ASCII))
        // Block 0: STREAMINFO, length 34, not last
        flacBuf.put(0.toByte())
        flacBuf.put(0.toByte()).put(0.toByte()).put(34.toByte())
        val si = ByteArray(34)
        // 48000 Hz (0x0BB80), 2 channels (1), 24-bit (23 = 0x17), 48000 total samples
        si[10] = 0x0B.toByte()
        si[11] = 0xB8.toByte()
        si[12] = ((0x00 shl 4) or (1 shl 1) or 1).toByte() // sr low=0, ch-1=1 (2ch), bps-1 high bit=1
        si[13] = (0x07 shl 4).toByte() // bps-1 low 4 bits=7 -> (16+7)+1 = 24-bit
        si[16] = 0xBB.toByte()
        si[17] = 0x80.toByte()
        flacBuf.put(si)

        // Block 6: PICTURE, isLast = true (0x80 or 6 = 0x86)
        flacBuf.put((0x80 or 6).toByte())
        flacBuf.put(((picBlockBodyLen ushr 16) and 0xFF).toByte())
        flacBuf.put(((picBlockBodyLen ushr 8) and 0xFF).toByte())
        flacBuf.put((picBlockBodyLen and 0xFF).toByte())
        flacBuf.putInt(3) // FRONT_COVER = 1 in our enum or code 3 -> LEAFLET, let's use 1 (FRONT_COVER)
        flacBuf.position(flacBuf.position() - 4)
        flacBuf.putInt(1) // ArtworkPictureType.FRONT_COVER
        flacBuf.putInt(mimeBytes.size)
        flacBuf.put(mimeBytes)
        flacBuf.putInt(0) // descLen = 0
        flacBuf.putInt(1400) // width = 1400
        flacBuf.putInt(1400) // height = 1400
        flacBuf.putInt(24) // colorDepth
        flacBuf.putInt(0) // indexedColors
        flacBuf.putInt(dummyImagePayload.size)
        flacBuf.put(dummyImagePayload)

        val source = MemorySeekableFileSource(flacBuf.array())
        // 1. Default lightweight scan (includeArtworkBytes = false) MUST NOT load rawArtworkBytes into RAM
        val lightweightPkg = ExtendedMetadataExtractor.extractFromSource(source, AudioContainerType.FLAC)
        assertEquals(48000, lightweightPkg.sampleRate)
        assertEquals(2, lightweightPkg.channelCount)
        assertEquals(24, lightweightPkg.bitDepth)
        assertTrue(lightweightPkg.hasEmbeddedArtwork)
        assertEquals(null, lightweightPkg.rawArtworkBytes)
        assertEquals("image/jpeg", lightweightPkg.rawArtworkMime)
        assertEquals(1400, lightweightPkg.rawArtworkWidth)
        assertEquals(1400, lightweightPkg.rawArtworkHeight)
        assertEquals(1024, lightweightPkg.rawArtworkDataLength)

        val artDesc = SongArtworkDescriptor.fromRawPackage(55L, lightweightPkg)
        assertNotNull(artDesc)
        assertEquals(1400, artDesc!!.width)
        assertEquals(1400, artDesc.height)
        assertEquals("image/jpeg", artDesc.mimeType)

        // 2. On-demand extraction (includeArtworkBytes = true) loads rawArtworkBytes when explicitly requested
        val fullPkg = ExtendedMetadataExtractor.extractFromSource(
            source,
            AudioContainerType.FLAC,
            includeArtworkBytes = true
        )
        assertNotNull(fullPkg.rawArtworkBytes)
        assertEquals(1024, fullPkg.rawArtworkBytes!!.size)
    }

    @Test
    fun `test BinaryHeaderMetadataExtractor AIFF OGG and ID3v2 real parsing and FallbackContainerMetadataExtractor routing`() {
        // 1. Synthetic AIFF (48kHz, 2ch, 24-bit, 48000 frames)
        val aiffBuf = ByteBuffer.allocate(54).order(ByteOrder.BIG_ENDIAN)
        aiffBuf.put("FORM".toByteArray(Charsets.US_ASCII))
        aiffBuf.putInt(46)
        aiffBuf.put("AIFF".toByteArray(Charsets.US_ASCII))
        aiffBuf.put("COMM".toByteArray(Charsets.US_ASCII))
        aiffBuf.putInt(18)
        aiffBuf.putShort(2) // 2 channels
        aiffBuf.putInt(48000) // 48000 sample frames (1 sec)
        aiffBuf.putShort(24) // 24-bit
        // 80-bit IEEE 754 extended for 48000.0: exp = 16383 + 15 = 16398 (0x400E), mantissa = 0xBB80000000000000
        aiffBuf.putShort(0x400E.toShort())
        aiffBuf.putLong(0xBB80000000000000uL.toLong())
        aiffBuf.put("NAME".toByteArray(Charsets.US_ASCII))
        aiffBuf.putInt(8)
        aiffBuf.put("AiffSong".toByteArray(Charsets.US_ASCII))

        val aiffSource = MemorySeekableFileSource(aiffBuf.array())
        val aiffPkg = BinaryHeaderMetadataExtractor.extract(aiffSource)
        assertEquals(48000, aiffPkg.sampleRate)
        assertEquals(2, aiffPkg.channelCount)
        assertEquals(24, aiffPkg.bitDepth)
        assertEquals(1000L, aiffPkg.durationMs)
        assertEquals("AiffSong", aiffPkg.properties["TITLE"]?.firstOrNull())

        // 2. Synthetic OGG Vorbis (Page 1: \x01vorbis id header 48kHz 6ch; Page 2: \x03vorbis comment header)
        val vorbisId = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
        vorbisId.put(1.toByte())
        vorbisId.put("vorbis".toByteArray(Charsets.US_ASCII))
        vorbisId.putInt(0) // version
        vorbisId.put(6.toByte()) // 6 channels (5.1)
        vorbisId.putInt(48000) // 48000 Hz
        vorbisId.putInt(0) // bitrate max
        vorbisId.putInt(320000) // bitrate nominal
        vorbisId.putInt(0) // bitrate min
        vorbisId.put(0.toByte()).put(0.toByte())

        val commentStr = "COMPOSER=Wendy Carlos"
        val commentBytes = commentStr.toByteArray(Charsets.UTF_8)
        val vorbisComment = ByteBuffer.allocate(7 + 4 + 4 + 4 + commentBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        vorbisComment.put(3.toByte())
        vorbisComment.put("vorbis".toByteArray(Charsets.US_ASCII))
        vorbisComment.putInt(0) // vendorLen = 0
        vorbisComment.putInt(1) // 1 comment
        vorbisComment.putInt(commentBytes.size)
        vorbisComment.put(commentBytes)

        val oggBuf = ByteBuffer.allocate(28 + vorbisId.capacity() + 28 + vorbisComment.capacity()).order(ByteOrder.LITTLE_ENDIAN)
        // Page 1
        oggBuf.put("OggS".toByteArray(Charsets.US_ASCII))
        oggBuf.put(ByteArray(22))
        oggBuf.put(1.toByte()) // 1 segment
        oggBuf.put(vorbisId.capacity().toByte())
        oggBuf.put(vorbisId.array())
        // Page 2
        oggBuf.put("OggS".toByteArray(Charsets.US_ASCII))
        oggBuf.put(ByteArray(22))
        oggBuf.put(1.toByte()) // 1 segment
        oggBuf.put(vorbisComment.capacity().toByte())
        oggBuf.put(vorbisComment.array())

        val oggSource = MemorySeekableFileSource(oggBuf.array())
        val oggPkg = BinaryHeaderMetadataExtractor.extract(oggSource)
        assertEquals(48000, oggPkg.sampleRate)
        assertEquals(6, oggPkg.channelCount)
        assertEquals(320000L, oggPkg.bitrate)
        assertEquals("Wendy Carlos", oggPkg.properties["COMPOSER"]?.firstOrNull())

        // 3. Synthetic ID3v2.3 + MP3 frame sync
        val titleText = "\u0003Synthetic Title".toByteArray(Charsets.UTF_8) // encoding 3 = UTF-8
        val id3TagSize = 10 + titleText.size
        val mp3Buf = ByteBuffer.allocate(10 + id3TagSize + 4).order(ByteOrder.BIG_ENDIAN)
        mp3Buf.put("ID3".toByteArray(Charsets.US_ASCII))
        mp3Buf.put(3.toByte()).put(0.toByte()).put(0.toByte()) // v2.3
        mp3Buf.put(0.toByte()).put(0.toByte()).put(0.toByte()).put(id3TagSize.toByte())
        mp3Buf.put("TIT2".toByteArray(Charsets.US_ASCII))
        mp3Buf.putInt(titleText.size)
        mp3Buf.putShort(0)
        mp3Buf.put(titleText)
        // MPEG-1 Layer III 320kbps 44.1kHz Stereo frame header: 0xFF 0xFB 0xE0 0x00
        mp3Buf.put(0xFF.toByte()).put(0xFB.toByte()).put(0xE0.toByte()).put(0x00.toByte())

        val mp3Source = MemorySeekableFileSource(mp3Buf.array())
        val mp3Pkg = BinaryHeaderMetadataExtractor.extract(mp3Source)
        assertEquals("Synthetic Title", mp3Pkg.properties["TITLE"]?.firstOrNull())
        assertEquals(44100, mp3Pkg.sampleRate)
        assertEquals(2, mp3Pkg.channelCount)
        assertEquals(320000L, mp3Pkg.bitrate)

        // 4. Verify DefaultMetadataExtractorResolver routes WavPack ("wvpk") to FallbackContainerMetadataExtractor
        val wvSource = MemorySeekableFileSource("wvpk\u0000\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII))
        assertEquals(
            FallbackContainerMetadataExtractor,
            DefaultMetadataExtractorResolver.resolve(wvSource, AudioContainerType.WAVPACK)
        )
    }

    @Test
    fun `test single-pass ReplayGain extraction from BinaryHeaderMetadataExtractor survives ID3v2 APIC larger than 512 KiB without loading image bytes`() {
        // Build an ID3v2.3 MP3 where APIC is 600 KiB (> ReplayGainParser's 512 KiB limit),
        // followed by TXXX:REPLAYGAIN_TRACK_GAIN (-7.25 dB) and TXXX:REPLAYGAIN_ALBUM_GAIN (-5.50 dB)
        val largeImageLen = 600 * 1024 // 600 KiB
        val apicHeaderPrefix = byteArrayOf(
            0, // ISO-8859-1 description encoding
            'i'.code.toByte(), 'm'.code.toByte(), 'a'.code.toByte(), 'g'.code.toByte(), 'e'.code.toByte(),
            '/'.code.toByte(), 'j'.code.toByte(), 'p'.code.toByte(), 'e'.code.toByte(), 'g'.code.toByte(),
            0, // null terminator for MIME
            3, // Front cover
            0  // empty description null terminator
        )
        val apicFrameSize = apicHeaderPrefix.size + largeImageLen

        val txxxTrackPayload = "\u0003REPLAYGAIN_TRACK_GAIN\u0000-7.25 dB".toByteArray(Charsets.UTF_8)
        val txxxAlbumPayload = "\u0003REPLAYGAIN_ALBUM_GAIN\u0000-5.50 dB".toByteArray(Charsets.UTF_8)

        val totalId3PayloadSize = (10 + apicFrameSize) + (10 + txxxTrackPayload.size) + (10 + txxxAlbumPayload.size)
        val mp3Bytes = ByteArray(10 + totalId3PayloadSize + 4)
        val bb = ByteBuffer.wrap(mp3Bytes).order(ByteOrder.BIG_ENDIAN)
        bb.put("ID3".toByteArray(Charsets.US_ASCII))
        bb.put(3.toByte()).put(0.toByte()).put(0.toByte()) // ID3v2.3
        // 28-bit syncsafe tag size
        bb.put(((totalId3PayloadSize ushr 21) and 0x7F).toByte())
        bb.put(((totalId3PayloadSize ushr 14) and 0x7F).toByte())
        bb.put(((totalId3PayloadSize ushr 7) and 0x7F).toByte())
        bb.put((totalId3PayloadSize and 0x7F).toByte())

        // Frame 1: APIC (600 KiB image)
        bb.put("APIC".toByteArray(Charsets.US_ASCII))
        bb.putInt(apicFrameSize)
        bb.putShort(0)
        bb.put(apicHeaderPrefix)
        bb.position(bb.position() + largeImageLen) // skip 600 KiB image payload (zero-filled)

        // Frame 2: TXXX REPLAYGAIN_TRACK_GAIN
        bb.put("TXXX".toByteArray(Charsets.US_ASCII))
        bb.putInt(txxxTrackPayload.size)
        bb.putShort(0)
        bb.put(txxxTrackPayload)

        // Frame 3: TXXX REPLAYGAIN_ALBUM_GAIN
        bb.put("TXXX".toByteArray(Charsets.US_ASCII))
        bb.putInt(txxxAlbumPayload.size)
        bb.putShort(0)
        bb.put(txxxAlbumPayload)

        // Trailing MPEG-1 Layer III frame header
        bb.put(0xFF.toByte()).put(0xFB.toByte()).put(0xE0.toByte()).put(0x00.toByte())

        var totalBytesReadFromSource = 0L
        fun wrapTracking(delegate: SeekableFileSource): SeekableFileSource =
            object : SeekableFileSource by delegate {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val r = delegate.read(buffer, offset, length)
                    if (r > 0) totalBytesReadFromSource += r
                    return r
                }
                override fun duplicate(): SeekableFileSource = wrapTracking(delegate.duplicate())
            }
        val trackingSource = wrapTracking(MemorySeekableFileSource(mp3Bytes))

        // Extract with default includeArtworkBytes = false
        val pkg = ExtendedMetadataExtractor.extractFromSource(trackingSource, AudioContainerType.MP3)
        // Verify that the 600 KiB image payload was NOT read or allocated
        assertEquals(null, pkg.rawArtworkBytes)
        assertEquals(largeImageLen, pkg.rawArtworkDataLength)
        assertTrue(
            "Expected source reads ($totalBytesReadFromSource bytes) to be < 2 KiB by seeking over 600 KiB APIC",
            totalBytesReadFromSource < 2048L
        )

        // Verify ReplayGain is resolved directly from the extracted package without a second pass
        val rg = ExtendedMetadataExtractor.extractReplayGainFromPackage(pkg)
        assertEquals(-7.25f, rg.trackGain!!, 0.001f)
        assertEquals(-5.50f, rg.albumGain!!, 0.001f)
    }
}
