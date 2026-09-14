package io.github.yisus.avenor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File

object ExtendedMetadataExtractor {
    private const val TAG = "ExtendedMetadataExtractor"

    data class AudioSpecs(
        val bitDepth: Int,
        val sampleRate: Int,
        val mimeType: String,
        val fileExtension: String
    )

    fun extract(context: Context, uri: Uri, filePath: String? = null): AudioSpecs {
        var bitDepth = 16
        var sampleRate = 44100
        var mimeType = "audio/mpeg"
        var ext = "mp3"

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
                
                // Bit Depth inference
                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    val pcm = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    bitDepth = when (pcm) {
                        android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                        android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED,
                        android.media.AudioFormat.ENCODING_PCM_32BIT,
                        android.media.AudioFormat.ENCODING_PCM_FLOAT -> 24 // or 32, label as high res
                        else -> 16
                    }
                } else if (mimeType.contains("flac") || mimeType.contains("alac") || mimeType.contains("opus") || mimeType.contains("ac4") || mimeType.contains("eac3")) {
                     // High res codecs defaults if PCM encoding key is missing
                     bitDepth = 24
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata for $uri", e)
        } finally {
            extractor.release()
        }

        return AudioSpecs(bitDepth, sampleRate, mimeType, ext)
    }
}
