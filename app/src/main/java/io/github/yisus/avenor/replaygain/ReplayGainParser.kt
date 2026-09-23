package io.github.yisus.avenor.replaygain

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Robust extractor and parser for ReplayGain metadata across ID3, Vorbis Comments, and container tags.
 *
 * Supported formats & keys:
 * - ID3v2: TXXX:REPLAYGAIN_TRACK_GAIN, TXXX:REPLAYGAIN_ALBUM_GAIN, TXXX:REPLAYGAIN_TRACK_PEAK, TXXX:REPLAYGAIN_ALBUM_PEAK
 * - Vorbis Comments (FLAC, Ogg Vorbis, Opus): REPLAYGAIN_TRACK_GAIN, REPLAYGAIN_ALBUM_GAIN, REPLAYGAIN_TRACK_PEAK, REPLAYGAIN_ALBUM_PEAK, R128_TRACK_GAIN, R128_ALBUM_GAIN
 * - MP4/M4A: Freeform atoms (----:com.apple.iTunes:replaygain_track_gain, replaygain_album_gain)
 */
object ReplayGainParser {

    private const val TAG = "ReplayGainParser"
    private const val MAX_ID3_TAG_SIZE = 512 * 1024 // 512 KB sanity ceiling

    // Standard valid range for ReplayGain in dB
    private const val MIN_VALID_GAIN_DB = -50.0f
    private const val MAX_VALID_GAIN_DB = 50.0f

    /**
     * Parses a raw gain string (e.g., "-6.84 dB", "+2.00 dB", "-6.84", "-8,20") into a Float in decibels.
     *
     * Returns null if the value is missing, blank, non-numeric, or outside valid physical bounds [-50, +50] dB.
     */
    fun parseGainString(raw: String?): Float? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Strip optional trailing "dB" or "db" case-insensitively
        val cleanString = if (trimmed.endsWith("dB", ignoreCase = true)) {
            trimmed.substring(0, trimmed.length - 2).trim()
        } else {
            trimmed
        }

        // Normalize decimal commas to standard dots (e.g. "-6,84" -> "-6.84")
        val normalized = cleanString.replace(',', '.')

        val parsed = normalized.toFloatOrNull() ?: return null
        if (!parsed.isFinite()) return null
        if (parsed < MIN_VALID_GAIN_DB || parsed > MAX_VALID_GAIN_DB) return null

        return parsed
    }

    /**
     * Parses a raw peak ratio string (e.g., "0.985432", "1.000000", "0,98") into a dimensionless Float ratio.
     *
     * Returns null if missing, non-numeric, or outside reasonable physical bounds (0.0 .. 5.0].
     */
    fun parsePeakString(raw: String?): Float? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val cleanString = if (trimmed.endsWith("FS", ignoreCase = true)) {
            trimmed.substring(0, trimmed.length - 2).trim()
        } else {
            trimmed
        }

        val normalized = cleanString.replace(',', '.')
        val parsed = normalized.toFloatOrNull() ?: return null
        if (!parsed.isFinite()) return null
        if (parsed <= 0.0f || parsed > 5.0f) return null

        return parsed
    }

    /**
     * Extracts ReplayGainData from a case-insensitive dictionary of tag keys and values.
     */
    fun parseTags(tags: Map<String, String>): ReplayGainData {
        var trackGain: Float? = null
        var albumGain: Float? = null
        var trackPeak: Float? = null
        var albumPeak: Float? = null

        for ((key, value) in tags) {
            val upperKey = key.trim().uppercase()
            when {
                // Track Gain
                upperKey == "REPLAYGAIN_TRACK_GAIN" ||
                upperKey == "TXXX:REPLAYGAIN_TRACK_GAIN" ||
                upperKey.endsWith(":REPLAYGAIN_TRACK_GAIN") ||
                upperKey == "R128_TRACK_GAIN" -> {
                    if (trackGain == null) trackGain = parseGainString(value)
                }

                // Album Gain
                upperKey == "REPLAYGAIN_ALBUM_GAIN" ||
                upperKey == "TXXX:REPLAYGAIN_ALBUM_GAIN" ||
                upperKey.endsWith(":REPLAYGAIN_ALBUM_GAIN") ||
                upperKey == "R128_ALBUM_GAIN" -> {
                    if (albumGain == null) albumGain = parseGainString(value)
                }

                // Track Peak
                upperKey == "REPLAYGAIN_TRACK_PEAK" ||
                upperKey == "TXXX:REPLAYGAIN_TRACK_PEAK" ||
                upperKey.endsWith(":REPLAYGAIN_TRACK_PEAK") -> {
                    if (trackPeak == null) trackPeak = parsePeakString(value)
                }

                // Album Peak
                upperKey == "REPLAYGAIN_ALBUM_PEAK" ||
                upperKey == "TXXX:REPLAYGAIN_ALBUM_PEAK" ||
                upperKey.endsWith(":REPLAYGAIN_ALBUM_PEAK") -> {
                    if (albumPeak == null) albumPeak = parsePeakString(value)
                }
            }
        }

        return ReplayGainData(
            trackGain = trackGain,
            albumGain = albumGain,
            trackPeak = trackPeak,
            albumPeak = albumPeak
        )
    }

    /**
     * Extracts ReplayGain metadata directly from an audio file.
     */
    fun extractFromFile(file: File): ReplayGainData {
        if (!file.exists() || !file.canRead() || file.length() < 10) {
            return ReplayGainData()
        }
        return try {
            FileInputStream(file).use { fis ->
                extractFromStream(BufferedInputStream(fis, 16384), file.extension)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ReplayGain from file ${file.path}", e)
            ReplayGainData()
        }
    }

    /**
     * Extracts ReplayGain metadata from an Android content Uri or file path.
     */
    fun extractFromUri(context: Context, uri: Uri, filePath: String? = null): ReplayGainData {
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                val fromFile = extractFromFile(file)
                if (fromFile.hasGain || fromFile.hasPeak) {
                    return fromFile
                }
            }
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractFromStream(BufferedInputStream(stream, 16384), uri.lastPathSegment)
            } ?: ReplayGainData()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ReplayGain from uri $uri", e)
            ReplayGainData()
        }
    }

    /**
     * Core stream-based extractor that detects header signatures (ID3v2, FLAC, Ogg, MP4)
     * and parses tag payloads without reading the whole audio stream.
     */
    fun extractFromStream(inputStream: InputStream, extensionOrMime: String? = null): ReplayGainData {
        val bis = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream, 16384)
        bis.mark(16)
        val header = ByteArray(16)
        val bytesRead = bis.read(header, 0, 16)
        bis.reset()
        if (bytesRead < 4) return ReplayGainData()

        return when {
            // ID3v2 tag (MP3, WAV, AIFF, or prepended ID3)
            header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte() -> {
                extractFromId3Stream(bis)
            }

            // FLAC native stream ("fLaC")
            header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() && header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte() -> {
                extractFromFlacStream(bis)
            }

            // Ogg container ("OggS")
            header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() && header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte() -> {
                extractFromOggStream(bis)
            }

            // ISO / MP4 file structure (e.g. "....ftyp")
            bytesRead >= 8 && header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() && header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte() -> {
                extractFromMp4Stream(bis)
            }

            else -> {
                // If extension hints at ID3 (mp3), try seeking ID3v2
                if (extensionOrMime?.contains("mp3", ignoreCase = true) == true) {
                    extractFromId3Stream(bis)
                } else {
                    ReplayGainData()
                }
            }
        }
    }

    private fun extractFromId3Stream(stream: InputStream): ReplayGainData {
        val header = ByteArray(10)
        var totalRead = 0
        while (totalRead < 10) {
            val r = stream.read(header, totalRead, 10 - totalRead)
            if (r == -1) return ReplayGainData()
            totalRead += r
        }

        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return ReplayGainData()
        }

        val tagSize = (header[6].toInt() and 0x7F shl 21) or
                (header[7].toInt() and 0x7F shl 14) or
                (header[8].toInt() and 0x7F shl 7) or
                (header[9].toInt() and 0x7F)

        if (tagSize <= 0 || tagSize > MAX_ID3_TAG_SIZE) {
            return ReplayGainData()
        }

        val fullTagBuffer = ByteArray(10 + tagSize)
        System.arraycopy(header, 0, fullTagBuffer, 0, 10)

        var payloadRead = 0
        while (payloadRead < tagSize) {
            val r = stream.read(fullTagBuffer, 10 + payloadRead, tagSize - payloadRead)
            if (r == -1) break
            payloadRead += r
        }

        val totalValid = 10 + payloadRead
        val decoder = Id3Decoder()
        val metadata = try {
            decoder.decode(fullTagBuffer, totalValid)
        } catch (e: Exception) {
            Log.e(TAG, "ID3 decoding failed: ${e.message}")
            null
        } ?: return ReplayGainData()

        val tagMap = mutableMapOf<String, String>()
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is TextInformationFrame -> {
                    val desc = entry.description ?: entry.id
                    val textVal = entry.value ?: entry.values.firstOrNull()
                    if (desc != null && textVal != null) {
                        tagMap[desc] = textVal
                    }
                }
                is CommentFrame -> {
                    if (entry.description != null && entry.text != null) {
                        tagMap[entry.description] = entry.text
                    }
                }
                is InternalFrame -> {
                    if (entry.description != null && entry.text != null) {
                        tagMap[entry.description] = entry.text
                    }
                }
            }
        }

        return parseTags(tagMap)
    }

    private fun extractFromFlacStream(stream: InputStream): ReplayGainData {
        // Skip "fLaC" 4 bytes
        val magic = ByteArray(4)
        if (stream.read(magic) != 4) return ReplayGainData()

        var isLast = false
        while (!isLast) {
            val blockHeader = ByteArray(4)
            if (stream.read(blockHeader) != 4) break
            isLast = (blockHeader[0].toInt() and 0x80) != 0
            val blockType = blockHeader[0].toInt() and 0x7F
            val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)

            if (length < 0 || length > 2 * 1024 * 1024) break

            if (blockType == 4) { // VORBIS_COMMENT block
                val payload = ByteArray(length)
                var read = 0
                while (read < length) {
                    val r = stream.read(payload, read, length - read)
                    if (r == -1) break
                    read += r
                }
                return parseVorbisCommentBlock(payload, read)
            } else {
                // Skip other blocks
                var skipped = 0L
                while (skipped < length) {
                    val s = stream.skip(length.toLong() - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }
        }

        return ReplayGainData()
    }

    private fun extractFromOggStream(stream: InputStream): ReplayGainData {
        // Simple OGG scanner to find vorbis or opus comment packet
        val buffer = ByteArray(65536)
        val read = stream.read(buffer)
        if (read < 64) return ReplayGainData()

        // Search for "vorbis" (0x03, 'v', 'o', 'r', 'b', 'i', 's') or "OpusTags"
        for (i in 0 until read - 12) {
            if (buffer[i] == 0x03.toByte() &&
                buffer[i + 1] == 'v'.code.toByte() && buffer[i + 2] == 'o'.code.toByte() &&
                buffer[i + 3] == 'r'.code.toByte() && buffer[i + 4] == 'b'.code.toByte() &&
                buffer[i + 5] == 'i'.code.toByte() && buffer[i + 6] == 's'.code.toByte()
            ) {
                val offset = i + 7
                return parseVorbisCommentBlock(buffer.copyOfRange(offset, read), read - offset)
            }

            if (buffer[i] == 'O'.code.toByte() && buffer[i + 1] == 'p'.code.toByte() &&
                buffer[i + 2] == 'u'.code.toByte() && buffer[i + 3] == 's'.code.toByte() &&
                buffer[i + 4] == 'T'.code.toByte() && buffer[i + 5] == 'a'.code.toByte() &&
                buffer[i + 6] == 'g'.code.toByte() && buffer[i + 7] == 's'.code.toByte()
            ) {
                val offset = i + 8
                return parseVorbisCommentBlock(buffer.copyOfRange(offset, read), read - offset)
            }
        }

        return ReplayGainData()
    }

    private fun extractFromMp4Stream(stream: InputStream): ReplayGainData {
        // Read initial header chunks to scan for metadata tags
        val buffer = ByteArray(65536)
        val read = stream.read(buffer)
        if (read < 32) return ReplayGainData()

        val text = String(buffer, 0, read, Charsets.ISO_8859_1)
        val tagMap = mutableMapOf<String, String>()

        // Search for iTunes custom tags or replaygain strings
        findMp4GainString(text, "replaygain_track_gain")?.let { tagMap["REPLAYGAIN_TRACK_GAIN"] = it }
        findMp4GainString(text, "replaygain_album_gain")?.let { tagMap["REPLAYGAIN_ALBUM_GAIN"] = it }
        findMp4GainString(text, "replaygain_track_peak")?.let { tagMap["REPLAYGAIN_TRACK_PEAK"] = it }
        findMp4GainString(text, "replaygain_album_peak")?.let { tagMap["REPLAYGAIN_ALBUM_PEAK"] = it }

        return parseTags(tagMap)
    }

    private fun findMp4GainString(content: String, tagKey: String): String? {
        val idx = content.indexOf(tagKey, ignoreCase = true)
        if (idx == -1) return null
        val sub = content.substring(idx + tagKey.length)
        val match = Regex("""([+-]?\d+[.,]\d+)(\s*dB)?""", RegexOption.IGNORE_CASE).find(sub)
        return match?.value
    }

    private fun parseVorbisCommentBlock(data: ByteArray, length: Int): ReplayGainData {
        if (length < 8) return ReplayGainData()
        val bb = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)

        val vendorLen = bb.int
        if (vendorLen < 0 || vendorLen > length - 8) return ReplayGainData()
        bb.position(bb.position() + vendorLen)

        if (bb.remaining() < 4) return ReplayGainData()
        val commentCount = bb.int
        if (commentCount <= 0 || commentCount > 1000) return ReplayGainData()

        val tagMap = mutableMapOf<String, String>()
        for (i in 0 until commentCount) {
            if (bb.remaining() < 4) break
            val commentLen = bb.int
            if (commentLen <= 0 || commentLen > bb.remaining()) break
            val commentBytes = ByteArray(commentLen)
            bb.get(commentBytes)
            val comment = String(commentBytes, Charsets.UTF_8)
            val eqIdx = comment.indexOf('=')
            if (eqIdx != -1) {
                val key = comment.substring(0, eqIdx).trim()
                val value = comment.substring(eqIdx + 1).trim()
                tagMap[key] = value
            }
        }

        return parseTags(tagMap)
    }
}
