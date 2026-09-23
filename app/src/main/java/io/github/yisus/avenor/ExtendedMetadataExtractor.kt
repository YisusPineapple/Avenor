package io.github.yisus.avenor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import io.github.yisus.avenor.replaygain.ReplayGainParser
import java.io.File

interface MetadataExtractor {
    fun extract(context: Context, uri: Uri, filePath: String? = null): ExtendedMetadataExtractor.AudioSpecs
}

object ExtendedMetadataExtractor : MetadataExtractor {
    private const val TAG = "ExtendedMetadataExtractor"

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

    override fun extract(context: Context, uri: Uri, filePath: String?): AudioSpecs {
        var bitDepth = 16
        var sampleRate = 44100
        var mimeType = "audio/mpeg"
        var ext = "mp3"
        var bitrate = 0L
        var channels = 2

        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                ext = file.extension.lowercase()
            }
        }

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
                        else -> 16
                    }
                } else if (mimeType.contains("flac") || mimeType.contains("alac") || mimeType.contains("opus") || mimeType.contains("ac4") || mimeType.contains("eac3")) {
                    bitDepth = 24
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata for $uri", e)
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
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

    fun resolveCodec(mimeType: String, ext: String): String {
        val lowerMime = mimeType.lowercase()
        val lowerExt = ext.lowercase()
        return when {
            lowerMime.contains("flac") || lowerExt == "flac" -> "FLAC"
            lowerMime.contains("alac") || lowerExt == "alac" -> "ALAC"
            lowerMime.contains("opus") || lowerExt == "opus" -> "OPUS"
            lowerMime.contains("vorbis") || lowerExt == "ogg" -> "OGG"
            lowerMime.contains("mp4a") || lowerMime.contains("aac") || lowerExt in listOf("m4a", "aac", "mp4") -> "AAC"
            lowerMime.contains("mpeg") || lowerMime.contains("mp3") || lowerExt == "mp3" -> "MP3"
            lowerMime.contains("wav") || lowerExt == "wav" -> "WAV"
            lowerMime.contains("eac3") || lowerMime.contains("ac3") -> "Dolby Digital"
            else -> ext.uppercase().ifEmpty { "AUDIO" }
        }
    }
}
