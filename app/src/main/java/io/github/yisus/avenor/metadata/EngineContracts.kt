package io.github.yisus.avenor.metadata

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standardized audio container formats.
 */
enum class AudioContainerType(val standardExtension: String) {
    MP3("mp3"),
    AAC("aac"),
    M4A("m4a"),
    MP4("mp4"),
    OGG("ogg"),
    FLAC("flac"),
    WAV("wav"),
    AIFF("aiff"),
    MATROSKA("mkv"),
    AC3("ac3"),
    EAC3("eac3"),
    WAVPACK("wv"),
    APE("ape"),
    DSD_DSF("dsf"),
    DSD_DFF("dff"),
    WMA("wma"),
    TTA("tta"),
    UNKNOWN("raw");

    companion object {
        fun fromExtension(ext: String?): AudioContainerType {
            if (ext == null) return UNKNOWN
            val clean = ext.trim().lowercase().removePrefix(".")
            return values().firstOrNull { it.standardExtension == clean } ?: when (clean) {
                "mka", "webm" -> MATROSKA
                "aif", "aifc" -> AIFF
                "ec3" -> EAC3
                "oga", "opus", "spx" -> OGG
                "m4b", "m4p", "m4r", "3gp", "3gpp" -> M4A
                "wave" -> WAV
                "mp2", "mp1", "mpga" -> MP3
                else -> UNKNOWN
            }
        }

        /**
         * Sniffs container format from the leading magic bytes of a [SeekableFileSource]
         * without mutating the caller's read cursor (uses [SeekableFileSource.duplicate]).
         */
        fun sniffFromSource(source: SeekableFileSource, fallbackHint: AudioContainerType = UNKNOWN): AudioContainerType {
            if (source.size < 4L) return fallbackHint
            val clone = source.duplicate()
            return try {
                clone.seek(0L)
                val header = ByteArray(minOf(36, clone.size.toInt()))
                val read = clone.read(header, 0, header.size)
                if (read < 4) return fallbackHint

                when {
                    // "fLaC"
                    header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
                        header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte() -> FLAC

                    // "OggS"
                    header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
                        header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte() -> OGG

                    // "RIFF" .... "WAVE"
                    header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                        header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                        read >= 12 &&
                        header[8] == 'W'.code.toByte() && header[9] == 'A'.code.toByte() &&
                        header[10] == 'V'.code.toByte() && header[11] == 'E'.code.toByte() -> WAV

                    // "FORM" .... "AIFF" or "AIFC"
                    header[0] == 'F'.code.toByte() && header[1] == 'O'.code.toByte() &&
                        header[2] == 'R'.code.toByte() && header[3] == 'M'.code.toByte() &&
                        read >= 12 &&
                        header[8] == 'A'.code.toByte() && header[9] == 'I'.code.toByte() &&
                        header[10] == 'F'.code.toByte() -> AIFF

                    // ISO BMFF / MP4 / M4A: bytes 4..7 == "ftyp"
                    read >= 12 &&
                        header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                        header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte() -> {
                        val brand = String(header, 8, 4, Charsets.US_ASCII)
                        if (brand.startsWith("M4", ignoreCase = true)) M4A else MP4
                    }

                    // "wvpk" (WavPack)
                    header[0] == 'w'.code.toByte() && header[1] == 'v'.code.toByte() &&
                        header[2] == 'p'.code.toByte() && header[3] == 'k'.code.toByte() -> WAVPACK

                    // "MAC " (Monkey's Audio)
                    header[0] == 'M'.code.toByte() && header[1] == 'A'.code.toByte() &&
                        header[2] == 'C'.code.toByte() && header[3] == ' '.code.toByte() -> APE

                    // "DSD " (DSF)
                    header[0] == 'D'.code.toByte() && header[1] == 'S'.code.toByte() &&
                        header[2] == 'D'.code.toByte() && header[3] == ' '.code.toByte() -> DSD_DSF

                    // "FRM8" (DSDIFF)
                    header[0] == 'F'.code.toByte() && header[1] == 'R'.code.toByte() &&
                        header[2] == 'M'.code.toByte() && header[3] == '8'.code.toByte() -> DSD_DFF

                    // "TTA1"
                    header[0] == 'T'.code.toByte() && header[1] == 'T'.code.toByte() &&
                        header[2] == 'A'.code.toByte() && header[3] == '1'.code.toByte() -> TTA

                    // EBML / Matroska: 0x1A 0x45 0xDF 0xA3
                    (header[0].toInt() and 0xFF) == 0x1A && (header[1].toInt() and 0xFF) == 0x45 &&
                        (header[2].toInt() and 0xFF) == 0xDF && (header[3].toInt() and 0xFF) == 0xA3 -> MATROSKA

                    // ID3v2 ("ID3") or MPEG frame sync (0xFF 0xFB / 0xFF 0xF3 / 0xFF 0xF2)
                    (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) -> {
                        if (fallbackHint != UNKNOWN) fallbackHint else MP3
                    }

                    (header[0].toInt() and 0xFF) == 0xFF && ((header[1].toInt() and 0xE0) == 0xE0) -> {
                        val layerBits = (header[1].toInt() shr 1) and 0x03
                        if (layerBits == 0) AAC else MP3
                    }

                    // AC-3 syncword 0x0B 0x77
                    (header[0].toInt() and 0xFF) == 0x0B && (header[1].toInt() and 0xFF) == 0x77 -> AC3

                    else -> fallbackHint
                }
            } finally {
                clone.close()
            }
        }
    }
}

/**
 * Standardized audio compression and PCM encoding types.
 */
enum class AudioCodecType {
    MP3,
    AAC,
    ALAC,
    FLAC,
    VORBIS,
    OPUS,
    PCM_S16LE,
    PCM_FLOAT,
    AC3,
    EAC3,
    AC4,
    WAVPACK,
    APE,
    WMA,
    TTA,
    DSD,
    UNKNOWN;

    /**
     * Canonical user-facing codec label consistent with Avenor UI & Room `Song.codec`.
     */
    fun toDisplayLabel(fallbackExtension: String = ""): String = when (this) {
        FLAC -> "FLAC"
        ALAC -> "ALAC"
        OPUS -> "OPUS"
        VORBIS -> "OGG"
        AAC -> "AAC"
        MP3 -> "MP3"
        PCM_S16LE, PCM_FLOAT -> "WAV"
        AC3, EAC3, AC4 -> "Dolby Digital"
        WAVPACK -> "WV"
        APE -> "APE"
        WMA -> "WMA"
        TTA -> "TTA"
        DSD -> "DSD"
        UNKNOWN -> fallbackExtension.uppercase().ifEmpty { "AUDIO" }
    }

    companion object {
        /**
         * Robustly classifies a MIME type, RFC 6381 codec string, or container/extension hint
         * into a canonical [AudioCodecType].
         */
        fun fromMimeOrCodec(
            mimeOrCodec: String?,
            containerHint: AudioContainerType = AudioContainerType.UNKNOWN
        ): AudioCodecType {
            if (!mimeOrCodec.isNullOrBlank()) {
                val s = mimeOrCodec.trim().lowercase()
                val resolved = when {
                    // Lossless Apple ALAC must precede generic mp4/m4a matching
                    s.contains("alac") -> ALAC

                    // MP3 / MPEG-1/2 Audio Layer III (including RFC 6381 mp4a.6b / mp4a.69)
                    s.contains("mp3") || s.contains("mpeg") || s == "mp4a.6b" || s == "mp4a.69" -> MP3

                    // AAC & MP4 Audio family (AAC-LC, HE-AAC, HE-AACv2, xHE-AAC, M4A, 3GPP)
                    s.contains("aac") ||
                        s.contains("mp4a") ||
                        s == "audio/mp4" ||
                        s == "audio/x-m4a" ||
                        s == "audio/m4a" ||
                        s == "audio/3gpp" ||
                        s == "audio/3gpp2" -> AAC

                    // FLAC
                    s.contains("flac") -> FLAC

                    // Opus & Vorbis
                    s.contains("opus") -> OPUS
                    s.contains("vorbis") || s == "audio/ogg" || s == "application/ogg" -> VORBIS

                    // Dolby family (E-AC-3 checked before AC-3 so "eac3" doesn't match "ac3" first)
                    s.contains("eac3") || s.contains("e-ac-3") || s.contains("ec-3") -> EAC3
                    s.contains("ac4") || s.contains("ac-4") -> AC4
                    s.contains("ac3") || s.contains("ac-3") -> AC3

                    // Audiophile / Hybrid / Legacy lossless & lossy codecs
                    s.contains("wavpack") || s == "audio/x-wv" || s == "wv" -> WAVPACK
                    s.contains("ape") || s.contains("monkeys") || s == "audio/x-monkeys-audio" -> APE
                    s.contains("wma") || s.contains("x-ms-wma") -> WMA
                    s.contains("tta") || s.contains("true-audio") -> TTA
                    s.contains("dsd") || s.contains("dsf") || s.contains("dff") -> DSD

                    // PCM Float & Integer
                    s.contains("float") || s.contains("f32") || s.contains("f64") -> PCM_FLOAT
                    s.contains("pcm") || s.contains("wav") || s.contains("wave") ||
                        s.contains("aiff") || s == "audio/raw" -> PCM_S16LE

                    else -> UNKNOWN
                }
                if (resolved != UNKNOWN) return resolved
            }

            return when (containerHint) {
                AudioContainerType.MP3 -> MP3
                AudioContainerType.AAC, AudioContainerType.M4A, AudioContainerType.MP4 -> AAC
                AudioContainerType.FLAC -> FLAC
                AudioContainerType.OGG -> VORBIS
                AudioContainerType.WAV, AudioContainerType.AIFF -> PCM_S16LE
                AudioContainerType.AC3 -> AC3
                AudioContainerType.EAC3 -> EAC3
                AudioContainerType.WAVPACK -> WAVPACK
                AudioContainerType.APE -> APE
                AudioContainerType.DSD_DSF, AudioContainerType.DSD_DFF -> DSD
                AudioContainerType.WMA -> WMA
                AudioContainerType.TTA -> TTA
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Software decoder engines available as fallback when platform MediaCodec cannot decode a stream.
 */
enum class SoftwareBackendType {
    LIBALAC,
    LIBAVCODEC_MINIMAL,
    LIBWAVPACK,
    LIBMAC_APE,
    DSD_TO_PCM_CONVERTER
}

/**
 * Result of resolving a media stream's decoder requirements.
 */
sealed class DecoderResolution {
    data class PlatformMediaCodec(
        val mimeType: String,
        val isHardwareAccelerated: Boolean
    ) : DecoderResolution()

    data class SoftwareDecoder(
        val backend: SoftwareBackendType,
        val outputSampleFormat: Int
    ) : DecoderResolution()

    data class PassthroughPcm(
        val encoding: Int
    ) : DecoderResolution()

    data class Unsupported(
        val reason: String
    ) : DecoderResolution()
}

/**
 * Contract for resolving the decoding pipeline for an audio track before playback.
 */
interface DecoderResolver {
    fun resolve(
        container: AudioContainerType,
        codec: AudioCodecType,
        sampleRate: Int,
        channelCount: Int,
        bitDepth: Int
    ): DecoderResolution
}

/**
 * Productive implementation of [DecoderResolver] that maps container/codec/stream parameters
 * to Android platform MediaCodec, direct PCM passthrough, or fallback software backends.
 */
object DefaultDecoderResolver : DecoderResolver {
    override fun resolve(
        container: AudioContainerType,
        codec: AudioCodecType,
        sampleRate: Int,
        channelCount: Int,
        bitDepth: Int
    ): DecoderResolution {
        if (sampleRate <= 0 || sampleRate > 768_000) {
            return DecoderResolution.Unsupported("Invalid sample rate: $sampleRate Hz")
        }
        if (channelCount <= 0 || channelCount > 8) {
            return DecoderResolution.Unsupported("Unsupported channel count: $channelCount")
        }

        val outPcmFormat = if (bitDepth > 16) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT

        return when (codec) {
            AudioCodecType.PCM_S16LE -> DecoderResolution.PassthroughPcm(C.ENCODING_PCM_16BIT)
            AudioCodecType.PCM_FLOAT -> DecoderResolution.PassthroughPcm(C.ENCODING_PCM_FLOAT)
            AudioCodecType.MP3 -> DecoderResolution.PlatformMediaCodec("audio/mpeg", isHardwareAccelerated = true)
            AudioCodecType.AAC -> DecoderResolution.PlatformMediaCodec("audio/mp4a-latm", isHardwareAccelerated = true)
            AudioCodecType.FLAC -> DecoderResolution.PlatformMediaCodec("audio/flac", isHardwareAccelerated = false)
            AudioCodecType.VORBIS -> DecoderResolution.PlatformMediaCodec("audio/vorbis", isHardwareAccelerated = false)
            AudioCodecType.OPUS -> DecoderResolution.PlatformMediaCodec("audio/opus", isHardwareAccelerated = false)
            AudioCodecType.AC3 -> DecoderResolution.PlatformMediaCodec("audio/ac3", isHardwareAccelerated = true)
            AudioCodecType.EAC3 -> DecoderResolution.PlatformMediaCodec("audio/eac3", isHardwareAccelerated = true)
            AudioCodecType.AC4 -> DecoderResolution.PlatformMediaCodec("audio/ac4", isHardwareAccelerated = true)
            AudioCodecType.ALAC -> DecoderResolution.SoftwareDecoder(SoftwareBackendType.LIBALAC, outPcmFormat)
            AudioCodecType.WAVPACK -> DecoderResolution.SoftwareDecoder(SoftwareBackendType.LIBWAVPACK, outPcmFormat)
            AudioCodecType.APE -> DecoderResolution.SoftwareDecoder(SoftwareBackendType.LIBMAC_APE, outPcmFormat)
            AudioCodecType.WMA, AudioCodecType.TTA -> DecoderResolution.SoftwareDecoder(
                SoftwareBackendType.LIBAVCODEC_MINIMAL,
                outPcmFormat
            )
            AudioCodecType.DSD -> DecoderResolution.SoftwareDecoder(
                SoftwareBackendType.DSD_TO_PCM_CONVERTER,
                C.ENCODING_PCM_FLOAT
            )
            AudioCodecType.UNKNOWN -> DecoderResolution.Unsupported("Unrecognized codec for container $container")
        }
    }
}

/**
 * Raw unparsed metadata package extracted from an audio file.
 */
data class RawMetadataPackage(
    val properties: Map<String, List<String>>,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val bitDepth: Int = 0,
    val bitrate: Long = 0L,
    val durationMs: Long = 0L,
    val rawArtworkBytes: ByteArray? = null,
    val rawArtworkMime: String? = null,
    val rawArtworkType: ArtworkPictureType = ArtworkPictureType.FRONT_COVER,
    val rawLyricsText: String? = null,
    val isLyricsSynced: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawMetadataPackage

        if (properties != other.properties) return false
        if (sampleRate != other.sampleRate) return false
        if (channelCount != other.channelCount) return false
        if (bitDepth != other.bitDepth) return false
        if (bitrate != other.bitrate) return false
        if (durationMs != other.durationMs) return false
        if (rawArtworkBytes != null) {
            if (other.rawArtworkBytes == null) return false
            if (!rawArtworkBytes.contentEquals(other.rawArtworkBytes)) return false
        } else if (other.rawArtworkBytes != null) return false
        if (rawArtworkMime != other.rawArtworkMime) return false
        if (rawArtworkType != other.rawArtworkType) return false
        if (rawLyricsText != other.rawLyricsText) return false
        if (isLyricsSynced != other.isLyricsSynced) return false

        return true
    }

    override fun hashCode(): Int {
        var result = properties.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channelCount
        result = 31 * result + bitDepth
        result = 31 * result + bitrate.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + (rawArtworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + (rawArtworkMime?.hashCode() ?: 0)
        result = 31 * result + rawArtworkType.hashCode()
        result = 31 * result + (rawLyricsText?.hashCode() ?: 0)
        result = 31 * result + isLyricsSynced.hashCode()
        return result
    }
}

/**
 * Contract for low-level binary container metadata extractors operating on a [SeekableFileSource].
 *
 * Named [ContainerMetadataExtractor] to avoid ambiguity with the high-level Android
 * [io.github.yisus.avenor.MetadataExtractor] interface used by [io.github.yisus.avenor.LibraryScanner].
 */
interface ContainerMetadataExtractor {
    fun canHandle(container: AudioContainerType): Boolean
    fun extract(source: SeekableFileSource): RawMetadataPackage
}

/**
 * Resolver that selects the most specialized [ContainerMetadataExtractor] for a given file source.
 */
interface MetadataExtractorResolver {
    fun resolve(source: SeekableFileSource, containerHint: AudioContainerType): ContainerMetadataExtractor
}

/**
 * Pure Kotlin binary header & tag extractor operating directly on [SeekableFileSource].
 *
 * Parses stream properties (sampleRate, channelCount, bitDepth, durationMs) and embedded tags
 * for FLAC (STREAMINFO + Vorbis comments + PICTURE), WAV (RIFF fmt), AIFF (COMM), OGG (Vorbis/Opus),
 * and ID3v2 headers without external native dependencies.
 */
object BinaryHeaderMetadataExtractor : ContainerMetadataExtractor {

    override fun canHandle(container: AudioContainerType): Boolean = container != AudioContainerType.UNKNOWN

    override fun extract(source: SeekableFileSource): RawMetadataPackage {
        val detectedContainer = AudioContainerType.sniffFromSource(source, AudioContainerType.UNKNOWN)
        val clone = source.duplicate()
        return try {
            clone.seek(0L)
            when (detectedContainer) {
                AudioContainerType.FLAC -> parseFlac(clone)
                AudioContainerType.WAV -> parseWav(clone)
                else -> RawMetadataPackage(
                    properties = mapOf("CONTAINER" to listOf(detectedContainer.standardExtension))
                )
            }
        } catch (_: Exception) {
            RawMetadataPackage(properties = emptyMap())
        } finally {
            clone.close()
        }
    }

    private fun parseFlac(source: SeekableFileSource): RawMetadataPackage {
        if (source.size < 42L) return RawMetadataPackage(emptyMap())
        source.seek(4L) // Skip "fLaC"
        var sampleRate = 44100
        var channels = 2
        var bitDepth = 16
        var durationMs = 0L
        val props = mutableMapOf<String, MutableList<String>>()
        var artworkBytes: ByteArray? = null
        var artworkMime: String? = null
        var artworkType: ArtworkPictureType = ArtworkPictureType.FRONT_COVER

        var isLast = false
        val headerBuf = ByteArray(4)
        while (!isLast && source.tell() + 4 <= source.size) {
            if (source.read(headerBuf, 0, 4) != 4) break
            val b0 = headerBuf[0].toInt() and 0xFF
            isLast = (b0 and 0x80) != 0
            val blockType = b0 and 0x7F
            val blockLength = ((headerBuf[1].toInt() and 0xFF) shl 16) or
                ((headerBuf[2].toInt() and 0xFF) shl 8) or
                (headerBuf[3].toInt() and 0xFF)
            val nextBlockPos = source.tell() + blockLength
            if (nextBlockPos > source.size || blockLength < 0) break

            when (blockType) {
                0 -> { // STREAMINFO (34 bytes)
                    if (blockLength >= 18) {
                        val si = ByteArray(18)
                        if (source.read(si, 0, 18) == 18) {
                            // Bytes 10..17 contain sampleRate (20 bits), channels-1 (3 bits), bps-1 (5 bits), totalSamples (36 bits)
                            val b10 = si[10].toInt() and 0xFF
                            val b11 = si[11].toInt() and 0xFF
                            val b12 = si[12].toInt() and 0xFF
                            val b13 = si[13].toInt() and 0xFF
                            val sr = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
                            if (sr > 0) sampleRate = sr
                            channels = ((b12 ushr 1) and 0x07) + 1
                            bitDepth = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1
                            val totalSamples = ((b13.toLong() and 0x0FL) shl 32) or
                                ((si[14].toLong() and 0xFFL) shl 24) or
                                ((si[15].toLong() and 0xFFL) shl 16) or
                                ((si[16].toLong() and 0xFFL) shl 8) or
                                (si[17].toLong() and 0xFFL)
                            if (sampleRate > 0 && totalSamples > 0) {
                                durationMs = (totalSamples * 1000L) / sampleRate
                            }
                        }
                    }
                }
                4 -> { // VORBIS_COMMENT
                    if (blockLength in 8..(512 * 1024)) {
                        val vc = ByteArray(blockLength)
                        if (source.read(vc, 0, blockLength) == blockLength) {
                            val bb = ByteBuffer.wrap(vc).order(ByteOrder.LITTLE_ENDIAN)
                            val vendorLen = bb.int
                            if (vendorLen >= 0 && bb.remaining() >= vendorLen + 4) {
                                bb.position(bb.position() + vendorLen)
                                val count = bb.int
                                for (i in 0 until minOf(count, 512)) {
                                    if (bb.remaining() < 4) break
                                    val cLen = bb.int
                                    if (cLen < 0 || bb.remaining() < cLen) break
                                    val str = String(vc, bb.position(), cLen, Charsets.UTF_8)
                                    bb.position(bb.position() + cLen)
                                    val eqIdx = str.indexOf('=')
                                    if (eqIdx > 0) {
                                        val key = str.substring(0, eqIdx).trim().uppercase()
                                        val value = str.substring(eqIdx + 1)
                                        props.getOrPut(key) { mutableListOf() }.add(value)
                                    }
                                }
                            }
                        }
                    }
                }
                6 -> { // PICTURE
                    if (artworkBytes == null && blockLength in 32..(8 * 1024 * 1024)) {
                        val pic = ByteArray(blockLength)
                        if (source.read(pic, 0, blockLength) == blockLength) {
                            val bb = ByteBuffer.wrap(pic).order(ByteOrder.BIG_ENDIAN)
                            val picType = bb.int
                            val mimeLen = bb.int
                            if (mimeLen in 0..128 && bb.remaining() >= mimeLen + 4) {
                                val mime = String(pic, bb.position(), mimeLen, Charsets.US_ASCII)
                                bb.position(bb.position() + mimeLen)
                                val descLen = bb.int
                                if (descLen >= 0 && bb.remaining() >= descLen + 20) {
                                    bb.position(bb.position() + descLen + 16) // skip width, height, depth, colors
                                    val dataLen = bb.int
                                    if (dataLen > 0 && bb.remaining() >= dataLen) {
                                        val img = ByteArray(dataLen)
                                        bb.get(img)
                                        artworkBytes = img
                                        artworkMime = mime.ifBlank { "image/jpeg" }
                                        artworkType = ArtworkPictureType.fromCode(picType)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            source.seek(nextBlockPos)
        }

        val lyricsText = props["LYRICS"]?.firstOrNull() ?: props["UNSYNCEDLYRICS"]?.firstOrNull()
        val isSynced = lyricsText != null && Regex("""\[\d{2}:\d{2}([.:]\d{2,3})?]""").containsMatchIn(lyricsText)
        val bitrate = if (durationMs > 0) (source.size * 8000L) / durationMs else 0L

        return RawMetadataPackage(
            properties = props,
            sampleRate = sampleRate,
            channelCount = channels,
            bitDepth = bitDepth,
            bitrate = bitrate,
            durationMs = durationMs,
            rawArtworkBytes = artworkBytes,
            rawArtworkMime = artworkMime,
            rawArtworkType = artworkType,
            rawLyricsText = lyricsText,
            isLyricsSynced = isSynced
        )
    }

    private fun parseWav(source: SeekableFileSource): RawMetadataPackage {
        if (source.size < 44L) return RawMetadataPackage(emptyMap())
        source.seek(12L)
        val chunkHeader = ByteArray(8)
        var sampleRate = 44100
        var channels = 2
        var bitDepth = 16
        var byteRate = 0L
        var dataSize = 0L

        while (source.tell() + 8 <= source.size) {
            if (source.read(chunkHeader, 0, 8) != 8) break
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val chunkDataStart = source.tell()
            val nextPos = chunkDataStart + chunkSize + (chunkSize and 1L)
            if (chunkId == "fmt " && chunkSize >= 16L && chunkDataStart + 16L <= source.size) {
                val fmt = ByteArray(16)
                if (source.read(fmt, 0, 16) == 16) {
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    bb.short // audioFormat
                    channels = (bb.short.toInt() and 0xFFFF).coerceIn(1, 8)
                    sampleRate = bb.int.coerceAtLeast(8000)
                    byteRate = bb.int.toLong() and 0xFFFFFFFFL
                    bb.short // blockAlign
                    bitDepth = (bb.short.toInt() and 0xFFFF).coerceIn(8, 64)
                }
            } else if (chunkId == "data") {
                dataSize = chunkSize
            }
            if (nextPos > source.size || nextPos <= chunkDataStart) break
            source.seek(nextPos)
        }

        val durationMs = if (byteRate > 0 && dataSize > 0) (dataSize * 1000L) / byteRate else 0L
        val bitrate = byteRate * 8L

        return RawMetadataPackage(
            properties = mapOf("CONTAINER" to listOf("wav")),
            sampleRate = sampleRate,
            channelCount = channels,
            bitDepth = bitDepth,
            bitrate = bitrate,
            durationMs = durationMs
        )
    }
}

/**
 * Productive [MetadataExtractorResolver] that inspects the [SeekableFileSource] magic bytes
 * and returns the appropriate [ContainerMetadataExtractor].
 */
object DefaultMetadataExtractorResolver : MetadataExtractorResolver {
    override fun resolve(source: SeekableFileSource, containerHint: AudioContainerType): ContainerMetadataExtractor {
        val detected = AudioContainerType.sniffFromSource(source, containerHint)
        return if (BinaryHeaderMetadataExtractor.canHandle(detected)) {
            BinaryHeaderMetadataExtractor
        } else {
            BinaryHeaderMetadataExtractor
        }
    }
}
