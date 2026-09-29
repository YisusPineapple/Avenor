package io.github.yisus.avenor.lyrics

import android.content.Context
import android.net.Uri
import io.github.yisus.avenor.ExtendedMetadataExtractor
import io.github.yisus.avenor.Song
import io.github.yisus.avenor.metadata.SongLyricsBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LyricsRepository(private val context: Context) {

    suspend fun getLyricsForSong(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        // 1. Check for sibling .lrc file in filesystem if file path is available
        val siblingLyrics = findSiblingLrcFile(song)
        if (!siblingLyrics.isNullOrBlank()) {
            return@withContext LrcParser.parse(siblingLyrics)
        }

        // 2. Check embedded lyrics via MediaMetadataRetriever
        val embeddedLyrics = extractEmbeddedLyrics(song)
        if (!embeddedLyrics.isNullOrBlank()) {
            return@withContext LrcParser.parse(embeddedLyrics)
        }

        emptyList()
    }

    suspend fun getUnsyncedLyricsForSong(song: Song): String? = withContext(Dispatchers.IO) {
        readEmbeddedLyricsBlock(song)?.unsyncedLyrics
    }

    private fun findSiblingLrcFile(song: Song): String? {
        try {
            val uri = Uri.parse(song.uri)
            val path = when (uri.scheme) {
                "file" -> uri.path
                null -> song.uri
                else -> null
            }

            if (path != null) {
                val audioFile = File(path)
                if (audioFile.exists()) {
                    // Try exact name match with .lrc extension
                    val baseName = audioFile.nameWithoutExtension
                    val lrcFile = File(audioFile.parentFile, "$baseName.lrc")
                    if (lrcFile.exists() && lrcFile.canRead()) {
                        return lrcFile.readText(Charsets.UTF_8)
                    }

                    // Also try case-insensitive check
                    val parent = audioFile.parentFile
                    if (parent != null && parent.isDirectory) {
                        val matching = parent.listFiles { file ->
                            file.isFile && file.name.equals("$baseName.lrc", ignoreCase = true)
                        }?.firstOrNull()
                        if (matching != null && matching.canRead()) {
                            return matching.readText(Charsets.UTF_8)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handle filesystem read errors
        }
        return null
    }

    private fun readEmbeddedLyricsBlock(song: Song): SongLyricsBlock? {
        return try {
            val uri = Uri.parse(song.uri)
            val specs = ExtendedMetadataExtractor.extract(
                context = context,
                uri = uri,
                filePath = null
            )
            specs.lyricsBlock
        } catch (e: Exception) {
            null
        }
    }

    private fun extractEmbeddedLyrics(song: Song): String? {
        val block = readEmbeddedLyricsBlock(song) ?: return null
        return block.syncedLyricsLrc ?: block.unsyncedLyrics
    }
}
