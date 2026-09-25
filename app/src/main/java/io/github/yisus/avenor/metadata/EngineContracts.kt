package io.github.yisus.avenor.metadata

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
                "mka" -> MATROSKA
                "aif" -> AIFF
                "ec3" -> EAC3
                "oga" -> OGG
                "opus" -> OGG
                else -> UNKNOWN
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

    companion object {
        fun fromMimeOrCodec(mimeOrCodec: String?): AudioCodecType {
            if (mimeOrCodec == null) return UNKNOWN
            val s = mimeOrCodec.trim().lowercase()
            return when {
                s.contains("mp3") || s.contains("mpeg") -> MP3
                s.contains("alac") -> ALAC
                s.contains("aac") -> AAC
                s.contains("flac") -> FLAC
                s.contains("vorbis") -> VORBIS
                s.contains("opus") -> OPUS
                s.contains("ac3") || s.contains("ac-3") -> AC3
                s.contains("eac3") || s.contains("e-ac-3") -> EAC3
                s.contains("ac4") || s.contains("ac-4") -> AC4
                s.contains("wavpack") -> WAVPACK
                s.contains("ape") || s.contains("monkeys") -> APE
                s.contains("wma") -> WMA
                s.contains("tta") -> TTA
                s.contains("dsd") || s.contains("dsf") || s.contains("dff") -> DSD
                s.contains("float") -> PCM_FLOAT
                s.contains("pcm") || s.contains("wav") -> PCM_S16LE
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
 * Contract for format-specific metadata extractors.
 */
interface MetadataExtractor {
    fun canHandle(container: AudioContainerType): Boolean
    fun extract(source: SeekableFileSource): RawMetadataPackage
}

/**
 * Resolver that selects the most specialized metadata extractor for a given file source.
 */
interface MetadataExtractorResolver {
    fun resolve(source: SeekableFileSource, containerHint: AudioContainerType): MetadataExtractor
}
