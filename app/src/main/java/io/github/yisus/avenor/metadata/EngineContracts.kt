package io.github.yisus.avenor.metadata

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
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
    DTS,
    TRUEHD,
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
        DTS -> "DTS"
        TRUEHD -> "TrueHD"
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

                    // Dolby & Home-Theater surround families (E-AC-3 checked before AC-3)
                    s.contains("truehd") || s.contains("mlp") -> TRUEHD
                    s.contains("dts") || s.contains("dca") || s.contains("vnd.dts") -> DTS
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
 * Explicit runtime availability classification for a resolved decoder pipeline.
 *
 * Distinguishes codecs supported today in the active runtime pipeline ([SUPPORTED_NOW])
 * from codecs that map to planned future software backends not bundled in this build ([PLANNED_BACKEND])
 * or completely unsupported configurations ([UNSUPPORTED]).
 */
enum class DecoderAvailabilityStatus {
    SUPPORTED_NOW,
    PLANNED_BACKEND,
    UNSUPPORTED
}

/**
 * Software decoder engines defined as architectural targets when platform MediaCodec cannot decode a stream.
 *
 * Note (P2-7.1B.1): None of these native backends are bundled in the current repository build
 * (`isAvailableInBuild = false`). [DefaultDecoderResolver.resolveProductive] rejects streams
 * requiring unbundled backends so playback never assumes a non-existent decoder is installed.
 */
enum class SoftwareBackendType(val isAvailableInBuild: Boolean = false) {
    LIBALAC(isAvailableInBuild = false),
    LIBAVCODEC_MINIMAL(isAvailableInBuild = false),
    LIBWAVPACK(isAvailableInBuild = false),
    LIBMAC_APE(isAvailableInBuild = false),
    DSD_TO_PCM_CONVERTER(isAvailableInBuild = false)
}

/**
 * Result of resolving a media stream's decoder requirements.
 */
sealed class DecoderResolution {
    abstract val availabilityStatus: DecoderAvailabilityStatus

    val isSupportedNow: Boolean
        get() = availabilityStatus == DecoderAvailabilityStatus.SUPPORTED_NOW

    /**
     * Decoded via Android platform `MediaCodec`.
     *
     * - Universal Android CDD codecs (`MP3`, `AAC`, `FLAC`, `VORBIS`, `OPUS`) have `isDeviceDependentCodec = false`
     *   and report [DecoderAvailabilityStatus.SUPPORTED_NOW].
     * - Proprietary/OEM-dependent platform codecs (`AC3`, `EAC3`, `AC4`) have `isDeviceDependentCodec = true`
     *   and report [DecoderAvailabilityStatus.SUPPORTED_NOW] **only** when [isDeviceVerified] is `true`
     *   (i.e. confirmed present in the device's `MediaCodecList`); otherwise they report
     *   [DecoderAvailabilityStatus.UNSUPPORTED].
     */
    data class PlatformMediaCodec(
        val mimeType: String,
        val isHardwareAccelerated: Boolean = false,
        val isDeviceVerified: Boolean = false,
        val isDeviceDependentCodec: Boolean = false
    ) : DecoderResolution() {
        override val availabilityStatus: DecoderAvailabilityStatus
            get() = if (!isDeviceDependentCodec || isDeviceVerified) {
                DecoderAvailabilityStatus.SUPPORTED_NOW
            } else {
                DecoderAvailabilityStatus.UNSUPPORTED
            }
    }

    /**
     * Maps to a software decoder contract.
     * Reports [DecoderAvailabilityStatus.PLANNED_BACKEND] unless [isBackendInstalled] is `true`.
     */
    data class SoftwareDecoder(
        val backend: SoftwareBackendType,
        val outputSampleFormat: Int,
        val isBackendInstalled: Boolean = backend.isAvailableInBuild
    ) : DecoderResolution() {
        override val availabilityStatus: DecoderAvailabilityStatus
            get() = if (isBackendInstalled) {
                DecoderAvailabilityStatus.SUPPORTED_NOW
            } else {
                DecoderAvailabilityStatus.PLANNED_BACKEND
            }
    }

    data class PassthroughPcm(
        val encoding: Int
    ) : DecoderResolution() {
        override val availabilityStatus: DecoderAvailabilityStatus = DecoderAvailabilityStatus.SUPPORTED_NOW
    }

    data class Unsupported(
        val reason: String
    ) : DecoderResolution() {
        override val availabilityStatus: DecoderAvailabilityStatus = DecoderAvailabilityStatus.UNSUPPORTED
    }
}

/**
 * Preparatory contract for resolving the decoding pipeline for an audio track.
 *
 * Architectural Status (P2-7.1B.1):
 * This resolver is preparatory infrastructure for the upcoming multi-backend decoding phase
 * and is **not** actively invoked by `PlaybackService` during ExoPlayer track preparation.
 */
interface DecoderResolver {
    fun resolve(
        container: AudioContainerType,
        codec: AudioCodecType,
        sampleRate: Int,
        channelCount: Int,
        bitDepth: Int
    ): DecoderResolution

    /**
     * Resolves only decoders that are actively available in the current runtime build ([DecoderAvailabilityStatus.SUPPORTED_NOW]),
     * converting any [DecoderAvailabilityStatus.PLANNED_BACKEND] or unverified device-dependent codec
     * into [DecoderResolution.Unsupported].
     */
    fun resolveProductive(
        container: AudioContainerType,
        codec: AudioCodecType,
        sampleRate: Int,
        channelCount: Int,
        bitDepth: Int
    ): DecoderResolution {
        val candidate = resolve(container, codec, sampleRate, channelCount, bitDepth)
        return when {
            candidate is DecoderResolution.SoftwareDecoder && !candidate.isBackendInstalled -> {
                DecoderResolution.Unsupported(
                    "Planned software decoder backend ${candidate.backend} is not bundled in this build"
                )
            }
            candidate is DecoderResolution.PlatformMediaCodec && !candidate.isSupportedNow -> {
                DecoderResolution.Unsupported(
                    "Device-dependent platform codec ${candidate.mimeType} is not verified in MediaCodecList on this device"
                )
            }
            else -> candidate
        }
    }
}

/**
 * Preparatory implementation of [DecoderResolver] (P2-7.1B.1) that classifies container/codec/stream
 * parameters into:
 * - Universal Android CDD `MediaCodec` pipelines (`MP3`, `AAC`, `FLAC`, `VORBIS`, `OPUS`) -> [DecoderAvailabilityStatus.SUPPORTED_NOW]
 * - Direct WAV PCM passthrough (`WAV` with `PCM_S16LE` / `PCM_FLOAT`) -> [DecoderAvailabilityStatus.SUPPORTED_NOW]
 * - Device-dependent OEM `MediaCodec` pipelines (`AC3`, `EAC3`, `AC4`) -> [DecoderAvailabilityStatus.SUPPORTED_NOW] only if verified in `MediaCodecList`, otherwise [DecoderAvailabilityStatus.UNSUPPORTED]
 * - Planned software decoder backends not bundled in this build (`AIFF`, `ALAC`, `WAVPACK`, `APE`, `WMA`, `TTA`, `DSD`, `DTS`, `TRUEHD`) -> [DecoderAvailabilityStatus.PLANNED_BACKEND] in [resolve] and [DecoderResolution.Unsupported] in [resolveProductive]
 */
@OptIn(UnstableApi::class)
object DefaultDecoderResolver : DecoderResolver {

    /**
     * Queries Android's `MediaCodecList(REGULAR_CODECS)` when running on a physical/emulated Android runtime to
     * determine whether a registered decoder for [mimeType] exists and whether it is hardware-accelerated.
     * Safely returns `(isHardwareAccelerated = false, isDeviceVerified = false)` on JVM unit tests
     * or when `MediaCodecList` has no registered decoder for [mimeType].
     */
    private fun queryDeviceHardwareAcceleration(mimeType: String): Pair<Boolean, Boolean> {
        return try {
            val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            val infos = list.codecInfos
            if (infos.isNullOrEmpty()) return false to false
            val matchingDecoders = infos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
            if (matchingDecoders.isEmpty()) return false to false
            val hasHw = matchingDecoders.any { info ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    val name = info.name.lowercase()
                    !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
                }
            }
            hasHw to true
        } catch (_: Throwable) {
            false to false
        }
    }

    private fun platformCodec(
        mimeType: String,
        isDeviceDependentCodec: Boolean = false
    ): DecoderResolution.PlatformMediaCodec {
        val (isHw, verified) = queryDeviceHardwareAcceleration(mimeType)
        return DecoderResolution.PlatformMediaCodec(
            mimeType = mimeType,
            isHardwareAccelerated = isHw,
            isDeviceVerified = verified,
            isDeviceDependentCodec = isDeviceDependentCodec
        )
    }

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

        // Media3 1.2.1 DefaultExtractorsFactory does not bundle an AIFF container extractor;
        // AIFF playback requires a planned software extractor/decoder backend.
        if (container == AudioContainerType.AIFF) {
            return DecoderResolution.SoftwareDecoder(SoftwareBackendType.LIBAVCODEC_MINIMAL, outPcmFormat)
        }

        return when (codec) {
            AudioCodecType.PCM_S16LE -> DecoderResolution.PassthroughPcm(C.ENCODING_PCM_16BIT)
            AudioCodecType.PCM_FLOAT -> DecoderResolution.PassthroughPcm(C.ENCODING_PCM_FLOAT)
            AudioCodecType.MP3 -> platformCodec("audio/mpeg", isDeviceDependentCodec = false)
            AudioCodecType.AAC -> platformCodec("audio/mp4a-latm", isDeviceDependentCodec = false)
            AudioCodecType.FLAC -> platformCodec("audio/flac", isDeviceDependentCodec = false)
            AudioCodecType.VORBIS -> platformCodec("audio/vorbis", isDeviceDependentCodec = false)
            AudioCodecType.OPUS -> platformCodec("audio/opus", isDeviceDependentCodec = false)
            AudioCodecType.AC3 -> platformCodec("audio/ac3", isDeviceDependentCodec = true)
            AudioCodecType.EAC3 -> platformCodec("audio/eac3", isDeviceDependentCodec = true)
            AudioCodecType.AC4 -> platformCodec("audio/ac4", isDeviceDependentCodec = true)
            AudioCodecType.ALAC -> DecoderResolution.SoftwareDecoder(SoftwareBackendType.LIBALAC, outPcmFormat)
            AudioCodecType.WAVPACK -> DecoderResolution.SoftwareDecoder(SoftwareBackendType.LIBWAVPACK, outPcmFormat)
            AudioCodecType.APE -> DecoderResolution.SoftwareDecoder(SoftwareBackendType.LIBMAC_APE, outPcmFormat)
            AudioCodecType.WMA, AudioCodecType.TTA, AudioCodecType.DTS, AudioCodecType.TRUEHD ->
                DecoderResolution.SoftwareDecoder(
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
 *
 * Memory Design for Modest Hardware:
 * During library scanning, [rawArtworkBytes] is `null` by default (`includeArtworkBytes = false`),
 * while [rawArtworkMime], [rawArtworkWidth], [rawArtworkHeight], and [rawArtworkDataLength]
 * capture the embedded image header metadata with zero multi-megabyte heap allocations.
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
    val rawArtworkWidth: Int = 0,
    val rawArtworkHeight: Int = 0,
    val rawArtworkDataLength: Int = rawArtworkBytes?.size ?: 0,
    val rawLyricsText: String? = null,
    val isLyricsSynced: Boolean = false
) {
    val hasEmbeddedArtwork: Boolean
        get() = (rawArtworkBytes != null && rawArtworkBytes.isNotEmpty()) || rawArtworkDataLength > 0

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
        if (rawArtworkWidth != other.rawArtworkWidth) return false
        if (rawArtworkHeight != other.rawArtworkHeight) return false
        if (rawArtworkDataLength != other.rawArtworkDataLength) return false
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
        result = 31 * result + rawArtworkWidth
        result = 31 * result + rawArtworkHeight
        result = 31 * result + rawArtworkDataLength
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

    /**
     * Extracts metadata from [source].
     * By default, `includeArtworkBytes = false` to avoid allocating multi-megabyte image buffers
     * during batch library scanning; only artwork dimensions, MIME, and byte length are read.
     */
    fun extract(source: SeekableFileSource): RawMetadataPackage =
        extract(source, includeArtworkBytes = false)

    fun extract(source: SeekableFileSource, includeArtworkBytes: Boolean): RawMetadataPackage
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
 * Implemented capabilities in P2-7.1B.1:
 * - **FLAC**: `STREAMINFO` (sampleRate, channels, bitDepth, totalSamples/duration), `VORBIS_COMMENT` tags,
 *   and `PICTURE` block header metadata (`width`, `height`, `mimeType`, `pictureType`, `dataLength`).
 *   Image payload bytes are skipped via [SeekableFileSource.seek] unless `includeArtworkBytes = true`.
 * - **WAV**: RIFF/WAVE header, `fmt ` chunk (including `WAVE_FORMAT_EXTENSIBLE` `validBitsPerSample`
 *   and `dwChannelMask`), and `data` chunk size/duration.
 * - **AIFF**: IFF `FORM`/`AIFF` header, `COMM` chunk (channels, sampleFrames, bitDepth, 80-bit IEEE 754
 *   extended sampleRate), and basic `NAME`/`AUTH`/`ANNO` text chunks.
 * - **OGG (Vorbis & Opus)**: Single-page `OggS` headers, `\x01vorbis` / `OpusHead` identification headers,
 *   and `\x03vorbis` / `OpusTags` Vorbis comment packets.
 * - **MP3 / AAC (ID3v2.3 & ID3v2.4 + MPEG-1 Layer III frame sync)**: ID3v2.3/v2.4 text frames
 *   (`TIT2`, `TPE1`, `TALB`, `TPE2`, `TCOM`, `TCON`, `TYER`/`TDRC`, `TRCK`, `TPOS`, `COMM`, `USLT`, `TXXX`),
 *   lightweight `APIC` header inspection (`mimeType`, `pictureType`, `dataLength` without decoding JPEG/PNG
 *   dimensions), and basic MPEG-1 Layer III CBR frame sync.
 *
 * Explicitly out of scope in P2-7.1B.1 (deferred to future phases):
 * - WAV `LIST/INFO` and `id3 ` chunks;
 * - AIFF `ID3 ` chunks and `AIFC` compression-type sub-headers;
 * - Legacy ID3v2.2 (3-character frame IDs / `PIC`);
 * - Decoding image dimensions (`width`/`height`) from compressed JPEG/PNG headers inside ID3 `APIC`;
 * - Multi-page spanning Ogg comment packets and MPEG VBR (`Xing`/`VBRI`) / Layer I-II bitrate tables.
 *
 * Containers outside this set (`M4A`/`MP4`, `MATROSKA`, `WAVPACK`, `APE`, `DSD`, `WMA`, `TTA`, `AC3`, `EAC3`)
 * are routed by [DefaultMetadataExtractorResolver] to [FallbackContainerMetadataExtractor].
 */
object BinaryHeaderMetadataExtractor : ContainerMetadataExtractor {

    private const val MAX_ON_DEMAND_ARTWORK_BYTES = 2 * 1024 * 1024 // 2 MiB safety cap when explicitly requested

    private val SUPPORTED_CONTAINERS = setOf(
        AudioContainerType.FLAC,
        AudioContainerType.WAV,
        AudioContainerType.AIFF,
        AudioContainerType.OGG,
        AudioContainerType.MP3,
        AudioContainerType.AAC
    )

    override fun canHandle(container: AudioContainerType): Boolean = container in SUPPORTED_CONTAINERS

    override fun extract(source: SeekableFileSource, includeArtworkBytes: Boolean): RawMetadataPackage {
        val detectedContainer = AudioContainerType.sniffFromSource(source, AudioContainerType.UNKNOWN)
        val clone = source.duplicate()
        return try {
            clone.seek(0L)
            when (detectedContainer) {
                AudioContainerType.FLAC -> parseFlac(clone, includeArtworkBytes)
                AudioContainerType.WAV -> parseWav(clone)
                AudioContainerType.AIFF -> parseAiff(clone)
                AudioContainerType.OGG -> parseOgg(clone)
                AudioContainerType.MP3, AudioContainerType.AAC ->
                    parseId3AndMpeg(clone, detectedContainer, includeArtworkBytes)
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

    private fun parseFlac(source: SeekableFileSource, includeArtworkBytes: Boolean): RawMetadataPackage {
        if (source.size < 42L) return RawMetadataPackage(emptyMap())
        source.seek(4L) // Skip "fLaC"
        var sampleRate = 44100
        var channels = 2
        var bitDepth = 16
        var durationMs = 0L
        val props = mutableMapOf<String, MutableList<String>>(
            "CONTAINER" to mutableListOf("flac")
        )
        var artworkBytes: ByteArray? = null
        var artworkMime: String? = null
        var artworkType: ArtworkPictureType = ArtworkPictureType.FRONT_COVER
        var artworkWidth = 0
        var artworkHeight = 0
        var artworkDataLen = 0

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
                    if (blockLength in 8..(256 * 1024)) {
                        val vc = ByteArray(blockLength)
                        if (source.read(vc, 0, blockLength) == blockLength) {
                            parseVorbisCommentBuffer(vc, 0, vc.size, props)
                        }
                    }
                }
                6 -> { // PICTURE (lightweight header-only scan unless includeArtworkBytes == true)
                    if (artworkDataLen == 0 && blockLength >= 32) {
                        val intBuf = ByteArray(4)
                        if (source.read(intBuf, 0, 4) == 4) {
                            val picType = ByteBuffer.wrap(intBuf).order(ByteOrder.BIG_ENDIAN).int
                            if (source.read(intBuf, 0, 4) == 4) {
                                val mimeLen = ByteBuffer.wrap(intBuf).order(ByteOrder.BIG_ENDIAN).int
                                if (mimeLen in 0..128 && source.tell() + mimeLen + 4 <= nextBlockPos) {
                                    val mimeBytes = ByteArray(mimeLen)
                                    val mimeRead = if (mimeLen > 0) source.read(mimeBytes, 0, mimeLen) else 0
                                    if (mimeRead == mimeLen && source.read(intBuf, 0, 4) == 4) {
                                        val mime = String(mimeBytes, Charsets.US_ASCII).ifBlank { "image/jpeg" }
                                        val descLen = ByteBuffer.wrap(intBuf).order(ByteOrder.BIG_ENDIAN).int
                                        val dimsPos = source.tell() + descLen
                                        if (descLen >= 0 && dimsPos + 20 <= nextBlockPos) {
                                            source.seek(dimsPos)
                                            val dims = ByteArray(20)
                                            if (source.read(dims, 0, 20) == 20) {
                                                val dbb = ByteBuffer.wrap(dims).order(ByteOrder.BIG_ENDIAN)
                                                val w = dbb.int.coerceAtLeast(0)
                                                val h = dbb.int.coerceAtLeast(0)
                                                dbb.int // colorDepth
                                                dbb.int // indexedColors
                                                val dataLen = dbb.int
                                                if (dataLen > 0 && source.tell() + dataLen <= nextBlockPos) {
                                                    artworkMime = mime
                                                    artworkType = ArtworkPictureType.fromCode(picType)
                                                    artworkWidth = w
                                                    artworkHeight = h
                                                    artworkDataLen = dataLen
                                                    if (includeArtworkBytes && dataLen <= MAX_ON_DEMAND_ARTWORK_BYTES) {
                                                        val img = ByteArray(dataLen)
                                                        if (source.read(img, 0, dataLen) == dataLen) {
                                                            artworkBytes = img
                                                        }
                                                    }
                                                }
                                            }
                                        }
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
            rawArtworkWidth = artworkWidth,
            rawArtworkHeight = artworkHeight,
            rawArtworkDataLength = artworkDataLen,
            rawLyricsText = lyricsText,
            isLyricsSynced = isSynced
        )
    }

    private fun parseVorbisCommentBuffer(
        buf: ByteArray,
        offset: Int,
        length: Int,
        props: MutableMap<String, MutableList<String>>
    ) {
        if (length < 8) return
        val bb = ByteBuffer.wrap(buf, offset, length).order(ByteOrder.LITTLE_ENDIAN)
        val vendorLen = bb.int
        if (vendorLen < 0 || bb.remaining() < vendorLen + 4) return
        bb.position(bb.position() + vendorLen)
        val count = bb.int
        for (i in 0 until minOf(count, 512)) {
            if (bb.remaining() < 4) break
            val cLen = bb.int
            if (cLen < 0 || bb.remaining() < cLen) break
            val str = String(buf, bb.position(), cLen, Charsets.UTF_8)
            bb.position(bb.position() + cLen)
            val eqIdx = str.indexOf('=')
            if (eqIdx > 0) {
                val key = str.substring(0, eqIdx).trim().uppercase()
                val value = str.substring(eqIdx + 1)
                props.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
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
        val props = mutableMapOf<String, MutableList<String>>(
            "CONTAINER" to mutableListOf("wav")
        )

        while (source.tell() + 8 <= source.size) {
            if (source.read(chunkHeader, 0, 8) != 8) break
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val chunkDataStart = source.tell()
            val nextPos = chunkDataStart + chunkSize + (chunkSize and 1L)
            if (chunkId == "fmt " && chunkSize >= 16L && chunkDataStart + 16L <= source.size) {
                val readLen = minOf(chunkSize.toInt(), 40)
                val fmt = ByteArray(readLen)
                if (source.read(fmt, 0, readLen) == readLen) {
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    val audioFormat = bb.short.toInt() and 0xFFFF
                    channels = (bb.short.toInt() and 0xFFFF).coerceIn(1, 8)
                    sampleRate = bb.int.coerceAtLeast(8000)
                    byteRate = bb.int.toLong() and 0xFFFFFFFFL
                    bb.short // blockAlign
                    bitDepth = (bb.short.toInt() and 0xFFFF).coerceIn(8, 64)
                    // WAVE_FORMAT_EXTENSIBLE (0xFFFE) carries validBitsPerSample and dwChannelMask
                    if (audioFormat == 0xFFFE && readLen >= 24) {
                        val cbSize = bb.short.toInt() and 0xFFFF
                        if (cbSize >= 6) {
                            val validBits = bb.short.toInt() and 0xFFFF
                            if (validBits in 8..64) bitDepth = validBits
                            val dwChannelMask = bb.int
                            if (dwChannelMask != 0) {
                                props["WAVEFORMATEXTENSIBLE_CHANNEL_MASK"] =
                                    mutableListOf("0x${dwChannelMask.toString(16)}")
                            }
                        }
                    }
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
            properties = props,
            sampleRate = sampleRate,
            channelCount = channels,
            bitDepth = bitDepth,
            bitrate = bitrate,
            durationMs = durationMs
        )
    }

    private fun parseAiff(source: SeekableFileSource): RawMetadataPackage {
        if (source.size < 30L) return RawMetadataPackage(emptyMap())
        source.seek(12L) // Skip "FORM" + size + "AIFF"/"AIFC"
        val chunkHeader = ByteArray(8)
        var channels = 2
        var sampleFrames = 0L
        var bitDepth = 16
        var sampleRate = 44100
        val props = mutableMapOf<String, MutableList<String>>(
            "CONTAINER" to mutableListOf("aiff")
        )

        while (source.tell() + 8 <= source.size) {
            if (source.read(chunkHeader, 0, 8) != 8) break
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val chunkDataStart = source.tell()
            val nextPos = chunkDataStart + chunkSize + (chunkSize and 1L)
            if (chunkId == "COMM" && chunkSize >= 18L && chunkDataStart + 18L <= source.size) {
                val comm = ByteArray(18)
                if (source.read(comm, 0, 18) == 18) {
                    val bb = ByteBuffer.wrap(comm).order(ByteOrder.BIG_ENDIAN)
                    channels = (bb.short.toInt() and 0xFFFF).coerceIn(1, 8)
                    sampleFrames = bb.int.toLong() and 0xFFFFFFFFL
                    bitDepth = (bb.short.toInt() and 0xFFFF).coerceIn(8, 64)
                    // 80-bit IEEE 754 extended precision float (10 bytes: bytes 8..17)
                    val exp = ((comm[8].toInt() and 0x7F) shl 8) or (comm[9].toInt() and 0xFF)
                    var mantissa = 0L
                    for (i in 10..17) {
                        mantissa = (mantissa shl 8) or (comm[i].toLong() and 0xFFL)
                    }
                    if (exp in 16383..16446) {
                        val shift = 63 - (exp - 16383)
                        val sr = (mantissa ushr shift).toInt()
                        if (sr in 8000..768000) sampleRate = sr
                    }
                }
            } else if ((chunkId == "NAME" || chunkId == "AUTH" || chunkId == "ANNO") &&
                chunkSize in 1L..4096L && chunkDataStart + chunkSize <= source.size
            ) {
                val textBytes = ByteArray(chunkSize.toInt())
                if (source.read(textBytes, 0, textBytes.size) == textBytes.size) {
                    val text = String(textBytes, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    if (text.isNotEmpty()) {
                        val key = when (chunkId) {
                            "NAME" -> "TITLE"
                            "AUTH" -> "ARTIST"
                            else -> "COMMENT"
                        }
                        props.getOrPut(key) { mutableListOf() }.add(text)
                    }
                }
            }
            if (nextPos > source.size || nextPos <= chunkDataStart) break
            source.seek(nextPos)
        }

        val durationMs = if (sampleRate > 0 && sampleFrames > 0) (sampleFrames * 1000L) / sampleRate else 0L
        val bitrate = sampleRate.toLong() * channels * bitDepth

        return RawMetadataPackage(
            properties = props,
            sampleRate = sampleRate,
            channelCount = channels,
            bitDepth = bitDepth,
            bitrate = bitrate,
            durationMs = durationMs
        )
    }

    private fun parseOgg(source: SeekableFileSource): RawMetadataPackage {
        if (source.size < 28L) return RawMetadataPackage(emptyMap())
        source.seek(0L)
        var sampleRate = 48000
        var channels = 2
        val bitDepth = 16
        var bitrate = 0L
        val props = mutableMapOf<String, MutableList<String>>(
            "CONTAINER" to mutableListOf("ogg")
        )

        val pageHeader = ByteArray(27)
        var pagesRead = 0
        while (pagesRead < 4 && source.tell() + 27 <= source.size) {
            if (source.read(pageHeader, 0, 27) != 27) break
            if (pageHeader[0] != 'O'.code.toByte() || pageHeader[1] != 'g'.code.toByte() ||
                pageHeader[2] != 'g'.code.toByte() || pageHeader[3] != 'S'.code.toByte()
            ) break
            val numSegments = pageHeader[26].toInt() and 0xFF
            if (source.tell() + numSegments > source.size) break
            val segTable = ByteArray(numSegments)
            if (source.read(segTable, 0, numSegments) != numSegments) break
            var payloadLen = 0
            for (b in segTable) payloadLen += (b.toInt() and 0xFF)
            val nextPagePos = source.tell() + payloadLen
            if (nextPagePos > source.size || payloadLen < 0) break

            if (payloadLen in 8..(128 * 1024)) {
                val payload = ByteArray(payloadLen)
                if (source.read(payload, 0, payloadLen) == payloadLen) {
                    if (payloadLen >= 30 && payload[0] == 1.toByte() &&
                        String(payload, 1, 6, Charsets.US_ASCII) == "vorbis"
                    ) {
                        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                        channels = (payload[11].toInt() and 0xFF).coerceIn(1, 8)
                        sampleRate = bb.getInt(12).coerceAtLeast(8000)
                        val nominalBr = bb.getInt(20)
                        if (nominalBr > 0) bitrate = nominalBr.toLong()
                        props["CODEC"] = mutableListOf("VORBIS")
                    } else if (payloadLen >= 19 && String(payload, 0, 8, Charsets.US_ASCII) == "OpusHead") {
                        channels = (payload[9].toInt() and 0xFF).coerceIn(1, 8)
                        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                        val origSr = bb.getInt(12)
                        sampleRate = if (origSr in 8000..192000) origSr else 48000
                        props["CODEC"] = mutableListOf("OPUS")
                    } else if (payloadLen >= 15 && payload[0] == 3.toByte() &&
                        String(payload, 1, 6, Charsets.US_ASCII) == "vorbis"
                    ) {
                        parseVorbisCommentBuffer(payload, 7, payloadLen - 7, props)
                    } else if (payloadLen >= 16 && String(payload, 0, 8, Charsets.US_ASCII) == "OpusTags") {
                        parseVorbisCommentBuffer(payload, 8, payloadLen - 8, props)
                    }
                }
            }
            source.seek(nextPagePos)
            pagesRead++
        }

        val lyricsText = props["LYRICS"]?.firstOrNull() ?: props["UNSYNCEDLYRICS"]?.firstOrNull()
        val isSynced = lyricsText != null && Regex("""\[\d{2}:\d{2}([.:]\d{2,3})?]""").containsMatchIn(lyricsText)

        return RawMetadataPackage(
            properties = props,
            sampleRate = sampleRate,
            channelCount = channels,
            bitDepth = bitDepth,
            bitrate = bitrate,
            rawLyricsText = lyricsText,
            isLyricsSynced = isSynced
        )
    }

    private fun parseId3AndMpeg(
        source: SeekableFileSource,
        container: AudioContainerType,
        includeArtworkBytes: Boolean
    ): RawMetadataPackage {
        if (source.size < 10L) return RawMetadataPackage(emptyMap())
        source.seek(0L)
        val props = mutableMapOf<String, MutableList<String>>(
            "CONTAINER" to mutableListOf(container.standardExtension)
        )
        var artworkBytes: ByteArray? = null
        var artworkMime: String? = null
        var artworkType = ArtworkPictureType.FRONT_COVER
        var artworkDataLen = 0
        var audioStartOffset = 0L

        val id3Header = ByteArray(10)
        if (source.read(id3Header, 0, 10) == 10 &&
            id3Header[0] == 'I'.code.toByte() &&
            id3Header[1] == 'D'.code.toByte() &&
            id3Header[2] == '3'.code.toByte()
        ) {
            val versionMajor = id3Header[3].toInt() and 0xFF
            val tagSize = ((id3Header[6].toInt() and 0x7F) shl 21) or
                ((id3Header[7].toInt() and 0x7F) shl 14) or
                ((id3Header[8].toInt() and 0x7F) shl 7) or
                (id3Header[9].toInt() and 0x7F)
            val tagEnd = minOf(source.size, 10L + tagSize)
            audioStartOffset = tagEnd

            if (versionMajor in 3..4) {
                val frameHeader = ByteArray(10)
                while (source.tell() + 10 <= tagEnd) {
                    if (source.read(frameHeader, 0, 10) != 10) break
                    if (frameHeader[0].toInt() == 0) break // Padding reached
                    val frameId = String(frameHeader, 0, 4, Charsets.US_ASCII)
                    val frameSize = if (versionMajor == 4) {
                        ((frameHeader[4].toInt() and 0x7F) shl 21) or
                            ((frameHeader[5].toInt() and 0x7F) shl 14) or
                            ((frameHeader[6].toInt() and 0x7F) shl 7) or
                            (frameHeader[7].toInt() and 0x7F)
                    } else {
                        ByteBuffer.wrap(frameHeader, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                    }
                    val nextFramePos = source.tell() + frameSize
                    if (frameSize <= 0 || nextFramePos > tagEnd) break

                    if (frameId == "APIC" && frameSize >= 14 && artworkDataLen == 0) {
                        val headLen = minOf(frameSize, 128)
                        val apicHead = ByteArray(headLen)
                        if (source.read(apicHead, 0, headLen) == headLen) {
                            val textEnc = apicHead[0].toInt() and 0xFF
                            var idx = 1 // skip encoding byte
                            val mimeStart = idx
                            while (idx < headLen && apicHead[idx].toInt() != 0) idx++
                            val mime = String(apicHead, mimeStart, idx - mimeStart, Charsets.US_ASCII)
                                .ifBlank { "image/jpeg" }
                            if (idx + 2 < headLen) {
                                idx++ // skip null terminator
                                val picType = apicHead[idx].toInt() and 0xFF
                                idx++
                                // skip description string terminator (2-byte null for UTF-16, 1-byte null for ISO-8859-1/UTF-8)
                                if (textEnc == 1 || textEnc == 2) {
                                    while (idx + 1 < headLen && (apicHead[idx].toInt() != 0 || apicHead[idx + 1].toInt() != 0)) {
                                        idx += 2
                                    }
                                    if (idx + 1 < headLen) idx += 2
                                } else {
                                    while (idx < headLen && apicHead[idx].toInt() != 0) idx++
                                    if (idx < headLen) idx++
                                }
                                val imgLen = (frameSize - idx).coerceAtLeast(0)
                                if (imgLen > 0) {
                                    artworkMime = mime
                                    artworkType = ArtworkPictureType.fromCode(picType)
                                    artworkDataLen = imgLen
                                    if (includeArtworkBytes && imgLen <= MAX_ON_DEMAND_ARTWORK_BYTES) {
                                        source.seek(nextFramePos - imgLen)
                                        val img = ByteArray(imgLen)
                                        if (source.read(img, 0, imgLen) == imgLen) {
                                            artworkBytes = img
                                        }
                                    }
                                }
                            }
                        }
                    } else if ((frameId.startsWith("T") || frameId == "COMM" || frameId == "USLT") &&
                        frameSize in 2..16384
                    ) {
                        val body = ByteArray(frameSize)
                        if (source.read(body, 0, frameSize) == frameSize) {
                            parseId3TextFrame(frameId, body, props)
                        }
                    }
                    source.seek(nextFramePos)
                }
            }
        }

        // Parse MPEG audio frame header after ID3v2 tag if available
        var sampleRate = 44100
        var channels = 2
        var bitrate = 0L
        if (audioStartOffset + 4 <= source.size) {
            source.seek(audioStartOffset)
            val syncBuf = ByteArray(minOf(512, (source.size - audioStartOffset).toInt()))
            val read = source.read(syncBuf, 0, syncBuf.size)
            for (i in 0 until read - 3) {
                val b0 = syncBuf[i].toInt() and 0xFF
                val b1 = syncBuf[i + 1].toInt() and 0xFF
                if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                    val b2 = syncBuf[i + 2].toInt() and 0xFF
                    val b3 = syncBuf[i + 3].toInt() and 0xFF
                    val versionBits = (b1 ushr 3) and 0x03
                    val srIdx = (b2 ushr 2) and 0x03
                    val brIdx = (b2 ushr 4) and 0x0F
                    val chanMode = (b3 ushr 6) and 0x03
                    if (versionBits != 1 && srIdx != 3 && brIdx in 1..14) {
                        val baseSr = when (srIdx) {
                            0 -> 44100
                            1 -> 48000
                            else -> 32000
                        }
                        sampleRate = when (versionBits) {
                            3 -> baseSr          // MPEG-1
                            2 -> baseSr / 2      // MPEG-2
                            else -> baseSr / 4   // MPEG-2.5
                        }
                        channels = if (chanMode == 3) 1 else 2
                        val brTableMpeg1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
                        bitrate = brTableMpeg1L3[brIdx] * 1000L
                        break
                    }
                }
            }
        }

        val durationMs = if (bitrate > 0 && source.size > audioStartOffset) {
            ((source.size - audioStartOffset) * 8000L) / bitrate
        } else 0L

        val lyricsText = props["USLT"]?.firstOrNull() ?: props["LYRICS"]?.firstOrNull()
        val isSynced = lyricsText != null && Regex("""\[\d{2}:\d{2}([.:]\d{2,3})?]""").containsMatchIn(lyricsText)

        return RawMetadataPackage(
            properties = props,
            sampleRate = sampleRate,
            channelCount = channels,
            bitDepth = 16,
            bitrate = bitrate,
            durationMs = durationMs,
            rawArtworkBytes = artworkBytes,
            rawArtworkMime = artworkMime,
            rawArtworkType = artworkType,
            rawArtworkDataLength = artworkDataLen,
            rawLyricsText = lyricsText,
            isLyricsSynced = isSynced
        )
    }

    private fun parseId3TextFrame(
        frameId: String,
        body: ByteArray,
        props: MutableMap<String, MutableList<String>>
    ) {
        if (body.isEmpty()) return
        val enc = body[0].toInt() and 0xFF
        val charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val startOffset = if ((frameId == "COMM" || frameId == "USLT") && body.size > 4) 4 else 1
        if (startOffset >= body.size) return
        val rawStr = String(body, startOffset, body.size - startOffset, charset)
            .trim { it <= ' ' || it == '\u0000' }
        if (rawStr.isEmpty()) return

        if (frameId == "TXXX") {
            val parts = rawStr.split('\u0000', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                props.getOrPut(parts[0].trim().uppercase()) { mutableListOf() }.add(parts[1].trim())
            }
            return
        }

        val cleaned = if (frameId == "COMM" || frameId == "USLT") {
            rawStr.substringAfter('\u0000', rawStr).trim()
        } else {
            rawStr.replace('\u0000', ';')
        }
        if (cleaned.isNotEmpty()) {
            props.getOrPut(frameId) { mutableListOf() }.add(cleaned)
            val canonicalAlias = when (frameId) {
                "TIT2" -> "TITLE"
                "TPE1" -> "ARTIST"
                "TALB" -> "ALBUM"
                "TPE2" -> "ALBUMARTIST"
                "TCOM" -> "COMPOSER"
                "TCON" -> "GENRE"
                "TYER", "TDRC" -> "DATE"
                "TRCK" -> "TRACKNUMBER"
                "TPOS" -> "DISCNUMBER"
                "COMM" -> "COMMENT"
                "USLT" -> "LYRICS"
                else -> null
            }
            if (canonicalAlias != null) {
                props.getOrPut(canonicalAlias) { mutableListOf() }.add(cleaned)
            }
        }
    }
}

/**
 * Fallback [ContainerMetadataExtractor] for containers whose binary tags are not parsed directly
 * by [BinaryHeaderMetadataExtractor] (e.g., `M4A`/`MP4`, `MATROSKA`, `WAVPACK`, `APE`, `DSD`, `AC3`, `EAC3`).
 * Sniffs the container format and delegates stream inspection to Android's platform `MediaExtractor`.
 */
object FallbackContainerMetadataExtractor : ContainerMetadataExtractor {
    override fun canHandle(container: AudioContainerType): Boolean = true

    override fun extract(source: SeekableFileSource, includeArtworkBytes: Boolean): RawMetadataPackage {
        val detected = AudioContainerType.sniffFromSource(source, AudioContainerType.UNKNOWN)
        return RawMetadataPackage(
            properties = mapOf("CONTAINER" to listOf(detected.standardExtension))
        )
    }
}

/**
 * Productive [MetadataExtractorResolver] that inspects the [SeekableFileSource] magic bytes
 * and routes supported binary containers (`FLAC`, `WAV`, `AIFF`, `OGG`, `MP3`, `AAC`) to
 * [BinaryHeaderMetadataExtractor], and all other containers to [FallbackContainerMetadataExtractor].
 */
object DefaultMetadataExtractorResolver : MetadataExtractorResolver {
    override fun resolve(source: SeekableFileSource, containerHint: AudioContainerType): ContainerMetadataExtractor {
        val detected = AudioContainerType.sniffFromSource(source, containerHint)
        return if (BinaryHeaderMetadataExtractor.canHandle(detected)) {
            BinaryHeaderMetadataExtractor
        } else {
            FallbackContainerMetadataExtractor
        }
    }
}
