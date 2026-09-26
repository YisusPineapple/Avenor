package io.github.yisus.avenor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import io.github.yisus.avenor.metadata.AudioCodecType
import io.github.yisus.avenor.metadata.AudioContainerType
import io.github.yisus.avenor.metadata.BinaryHeaderMetadataExtractor
import io.github.yisus.avenor.metadata.DefaultMetadataExtractorResolver
import io.github.yisus.avenor.metadata.ExtendedMetadataBlock
import io.github.yisus.avenor.metadata.MetadataExtractorResolver
import io.github.yisus.avenor.metadata.RawMetadataPackage
import io.github.yisus.avenor.metadata.SeekableFileSource
import io.github.yisus.avenor.metadata.SongArtworkDescriptor
import io.github.yisus.avenor.metadata.SongLyricsBlock
import io.github.yisus.avenor.replaygain.ReplayGainData
import io.github.yisus.avenor.replaygain.ReplayGainParser
import java.io.File

/**
 * Productive high-level metadata extraction contract used by [LibraryScanner].
 *
 * Bridges Android [Context]/[Uri] resources with [SeekableFileSource] and [MetadataExtractorResolver].
 */
interface MetadataExtractor {
    fun extract(context: Context, uri: Uri, filePath: String? = null): ExtendedMetadataExtractor.AudioSpecs
}

object ExtendedMetadataExtractor : MetadataExtractor {
    private const val TAG = "ExtendedMetadataExtractor"
    var resolver: MetadataExtractorResolver = DefaultMetadataExtractorResolver

    data class AudioSpecs(
        val bitDepth: Int = 16,
        val sampleRate: Int = 44100,
        val mimeType: String = "audio/mpeg",
        val fileExtension: String = "mp3",
        val codec: String = "MP3",
        val bitrate: Long = 0L,
        val channels: Int = 2,
        val replayGainTrack: Float? = null,
        val replayGainAlbum: Float? = null,
        val extendedMetadata: ExtendedMetadataBlock? = null,
        val lyricsBlock: SongLyricsBlock? = null,
        val artworkDescriptor: SongArtworkDescriptor? = null
    )

    /**
     * Extracts a low-level [RawMetadataPackage] directly from a [SeekableFileSource]
     * using the configured [MetadataExtractorResolver].
     *
     * By default, [includeArtworkBytes] is `false` so batch library scanning inspects only
     * the lightweight picture header (`width`, `height`, `mimeType`, `dataLength`) without
     * loading multi-megabyte image payloads into RAM.
     */
    fun extractFromSource(
        source: SeekableFileSource,
        containerHint: AudioContainerType = AudioContainerType.UNKNOWN,
        includeArtworkBytes: Boolean = false
    ): RawMetadataPackage {
        val containerExtractor = resolver.resolve(source, containerHint)
        return containerExtractor.extract(source, includeArtworkBytes)
    }

    /**
     * Resolves [ReplayGainData] directly from an already-extracted [RawMetadataPackage] without
     * re-opening the file stream or allocating a full ID3 tag buffer.
     */
    fun extractReplayGainFromPackage(pkg: RawMetadataPackage?): ReplayGainData {
        if (pkg == null || pkg.properties.isEmpty()) return ReplayGainData()
        val flatTags = LinkedHashMap<String, String>(pkg.properties.size)
        for ((key, values) in pkg.properties) {
            val firstVal = values.firstOrNull { it.isNotBlank() }
            if (firstVal != null) {
                flatTags[key] = firstVal
            }
        }
        return ReplayGainParser.parseTags(flatTags)
    }

    override fun extract(context: Context, uri: Uri, filePath: String?): AudioSpecs {
        var bitDepth = 16
        var sampleRate = 44100
        var mimeType = "audio/mpeg"
        var ext = "mp3"
        var bitrate = 0L
        var channels = 2

        if (filePath != null) {
            val file = File(filePath)
            if (file.extension.isNotBlank()) {
                ext = file.extension.lowercase()
            }
        }

        // 1. Sniff & extract lightweight binary header package via SeekableFileSource (includeArtworkBytes = false)
        var binaryPkg: RawMetadataPackage? = null
        var sniffedContainer: AudioContainerType = AudioContainerType.fromExtension(ext)
        try {
            SeekableFileSource.fromContextUri(context, uri, filePath)?.use { source ->
                sniffedContainer = AudioContainerType.sniffFromSource(source, sniffedContainer)
                if (sniffedContainer != AudioContainerType.UNKNOWN && ext == "mp3" && sniffedContainer.standardExtension != "raw") {
                    ext = sniffedContainer.standardExtension
                }
                binaryPkg = extractFromSource(source, sniffedContainer, includeArtworkBytes = false)
            }
        } catch (e: Exception) {
            Log.d(TAG, "SeekableFileSource binary header pass skipped for $uri: ${e.message}")
        }

        var extendedBlock: ExtendedMetadataBlock? = null
        var lyricsBlock: SongLyricsBlock? = null
        var artworkDescriptor: SongArtworkDescriptor? = null

        if (binaryPkg != null) {
            val pkg = binaryPkg!!
            if (pkg.sampleRate > 0) sampleRate = pkg.sampleRate
            if (pkg.channelCount > 0) channels = pkg.channelCount
            if (pkg.bitDepth > 0) bitDepth = pkg.bitDepth
            if (pkg.bitrate > 0L) bitrate = pkg.bitrate

            extendedBlock = ExtendedMetadataBlock.fromRawPackage(0L, pkg)
            lyricsBlock = SongLyricsBlock.fromRawPackage(0L, pkg)
            artworkDescriptor = SongArtworkDescriptor.fromRawPackage(0L, pkg)
        }

        // 2. Platform MediaExtractor pass for container/track format verification
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            if (extractor.trackCount > 0) {
                val format = extractor.getTrackFormat(0)

                if (format.containsKey(MediaFormat.KEY_MIME)) {
                    mimeType = format.getString(MediaFormat.KEY_MIME) ?: mimeType
                }

                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }

                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }

                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                }

                // Bit Depth inference
                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    val pcm = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    bitDepth = when (pcm) {
                        android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                        android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED,
                        android.media.AudioFormat.ENCODING_PCM_32BIT,
                        android.media.AudioFormat.ENCODING_PCM_FLOAT -> 24
                        else -> bitDepth
                    }
                } else if (binaryPkg == null || binaryPkg!!.bitDepth <= 0) {
                    if (mimeType.contains("flac") || mimeType.contains("alac") || mimeType.contains("opus") || mimeType.contains("ac4") || mimeType.contains("eac3")) {
                        bitDepth = 24
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata for $uri", e)
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
                // ignore
            }
        }

        val codec = resolveCodec(mimeType, ext)
        val fromBinaryPackage = extractReplayGainFromPackage(binaryPkg)
        val coveredByBinaryExtractor = binaryPkg != null && BinaryHeaderMetadataExtractor.canHandle(sniffedContainer)
        val replayGainData = if (coveredByBinaryExtractor || fromBinaryPackage.hasGain || fromBinaryPackage.hasPeak) {
            fromBinaryPackage
        } else {
            try {
                ReplayGainParser.extractFromUri(context, uri, filePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing ReplayGain for $uri: ${e.message}")
                ReplayGainData()
            }
        }

        return AudioSpecs(
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            mimeType = mimeType,
            fileExtension = ext,
            codec = codec,
            bitrate = bitrate,
            channels = channels,
            replayGainTrack = replayGainData.trackGain,
            replayGainAlbum = replayGainData.albumGain,
            extendedMetadata = extendedBlock,
            lyricsBlock = lyricsBlock,
            artworkDescriptor = artworkDescriptor
        )
    }

    /**
     * Resolves the canonical user-facing codec label by delegating to [AudioCodecType]
     * and [AudioContainerType].
     */
    fun resolveCodec(mimeType: String, ext: String): String {
        val containerHint = AudioContainerType.fromExtension(ext)
        val codecType = AudioCodecType.fromMimeOrCodec(mimeType, containerHint)
        return codecType.toDisplayLabel(ext)
    }
}
