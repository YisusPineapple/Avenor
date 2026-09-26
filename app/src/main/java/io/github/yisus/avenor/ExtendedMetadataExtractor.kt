package io.github.yisus.avenor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import io.github.yisus.avenor.metadata.AudioCodecType
import io.github.yisus.avenor.metadata.AudioContainerType
import io.github.yisus.avenor.metadata.DefaultMetadataExtractorResolver
import io.github.yisus.avenor.metadata.MetadataExtractorResolver
import io.github.yisus.avenor.metadata.RawMetadataPackage
import io.github.yisus.avenor.metadata.SeekableFileSource
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
        val replayGainAlbum: Float? = null
    )

    /**
     * Extracts a low-level [RawMetadataPackage] directly from a [SeekableFileSource]
     * using the configured [MetadataExtractorResolver].
     */
    fun extractFromSource(
        source: SeekableFileSource,
        containerHint: AudioContainerType = AudioContainerType.UNKNOWN
    ): RawMetadataPackage {
        val containerExtractor = resolver.resolve(source, containerHint)
        return containerExtractor.extract(source)
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

        // 1. Sniff & extract binary header package via SeekableFileSource when accessible
        var binaryPkg: RawMetadataPackage? = null
        var sniffedContainer: AudioContainerType = AudioContainerType.fromExtension(ext)
        try {
            SeekableFileSource.fromContextUri(context, uri, filePath)?.use { source ->
                sniffedContainer = AudioContainerType.sniffFromSource(source, sniffedContainer)
                if (sniffedContainer != AudioContainerType.UNKNOWN && ext == "mp3" && sniffedContainer.standardExtension != "raw") {
                    ext = sniffedContainer.standardExtension
                }
                binaryPkg = extractFromSource(source, sniffedContainer)
            }
        } catch (e: Exception) {
            Log.d(TAG, "SeekableFileSource binary header pass skipped for $uri: ${e.message}")
        }

        if (binaryPkg != null) {
            if (binaryPkg!!.sampleRate > 0) sampleRate = binaryPkg!!.sampleRate
            if (binaryPkg!!.channelCount > 0) channels = binaryPkg!!.channelCount
            if (binaryPkg!!.bitDepth > 0) bitDepth = binaryPkg!!.bitDepth
            if (binaryPkg!!.bitrate > 0L) bitrate = binaryPkg!!.bitrate
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
        val replayGainData = try {
            ReplayGainParser.extractFromUri(context, uri, filePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ReplayGain for $uri: ${e.message}")
            io.github.yisus.avenor.replaygain.ReplayGainData()
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
            replayGainAlbum = replayGainData.albumGain
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
