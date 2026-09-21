package io.github.yisus.avenor

import android.content.Context
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Normalizes directory and file paths for deterministic, platform-independent comparisons.
 */
object PathNormalizer {

    /**
     * Splits a path into clean, trimmed, lowercase segments.
     * Backslashes are replaced by forward slashes, and empty segments are discarded.
     * E.g. "/storage/emulated/0/Music/WhatsApp/" -> ["storage", "emulated", "0", "music", "whatsapp"]
     */
    fun toSegments(path: String): List<String> {
        if (path.isBlank()) return emptyList()
        return path.replace('\\', '/')
            .split('/')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Normalizes a path to canonical forward slashes without redundant or trailing slashes.
     * Preserves leading slash for absolute paths.
     */
    fun normalize(path: String): String {
        val trimmed = path.trim().replace('\\', '/')
        if (trimmed.isEmpty()) return ""
        val isAbsolute = trimmed.startsWith('/')
        val segments = toSegments(trimmed)
        if (segments.isEmpty()) return if (isAbsolute) "/" else ""
        val joined = segments.joinToString("/")
        return if (isAbsolute) "/$joined" else joined
    }

    /**
     * Extracts directory segments from a path.
     * If the path points to a file (last segment contains a dot and doesn't start with dot),
     * the file segment is dropped.
     */
    fun getDirectorySegments(path: String): List<String> {
        val segments = toSegments(path)
        if (segments.isEmpty()) return emptyList()
        val lastSegment = segments.last()
        val isFile = lastSegment.contains('.') && !lastSegment.startsWith('.')
        return if (isFile) segments.dropLast(1) else segments
    }
}

/**
 * Matches directory segments against exclusion rules using segment-based comparison.
 * Avoids naive substring matching (e.g. "Music/Podcasts" will not match "Music/PodcastsOld").
 */
object ExclusionMatcher {

    /**
     * Returns true if [dirSegments] matches [excludedRule].
     *
     * 1. Absolute rule (starts with '/'):
     *    [dirSegments] must have [excludedSegments] as its exact leading prefix.
     * 2. Relative rule (e.g. "Music/WhatsApp" or "Podcasts"):
     *    [excludedSegments] must appear as a contiguous sublist anywhere within [dirSegments].
     */
    fun matches(dirSegments: List<String>, excludedRule: String): Boolean {
        if (dirSegments.isEmpty() || excludedRule.isBlank()) return false
        val isAbsolute = excludedRule.trim().startsWith('/')
        val excludedSegments = PathNormalizer.toSegments(excludedRule)
        if (excludedSegments.isEmpty()) return false

        if (isAbsolute) {
            if (dirSegments.size < excludedSegments.size) return false
            return dirSegments.take(excludedSegments.size) == excludedSegments
        } else {
            if (dirSegments.size < excludedSegments.size) return false
            val maxStart = dirSegments.size - excludedSegments.size
            for (i in 0..maxStart) {
                if (dirSegments.subList(i, i + excludedSegments.size) == excludedSegments) {
                    return true
                }
            }
            return false
        }
    }
}

/**
 * Abstraction for detecting .nomedia markers.
 */
interface NoMediaDetector {
    fun hasNoMedia(filePath: String): Boolean
    fun clearCache()
}

/**
 * Default implementation of [NoMediaDetector] utilizing directory-level memoization.
 * Never executes global filesystem walks and checks each directory at most once during a scan.
 */
class DefaultNoMediaDetector(
    private val fileExistsCheck: (File) -> Boolean = { it.exists() && it.isFile }
) : NoMediaDetector {

    private val directoryCache = ConcurrentHashMap<String, Boolean>()

    override fun clearCache() {
        directoryCache.clear()
    }

    override fun hasNoMedia(filePath: String): Boolean {
        if (filePath.isBlank()) return false
        val file = File(filePath)
        val parentDir = file.parentFile ?: return false
        return checkDirectory(parentDir)
    }

    private fun checkDirectory(dir: File): Boolean {
        val canonical = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }

        // O(1) cache hit
        directoryCache[canonical]?.let { return it }

        // Root boundaries where upward traversal halts
        if (dir.parentFile == null || canonical == "/" || canonical == "/storage" || canonical == "/storage/emulated") {
            directoryCache[canonical] = false
            return false
        }

        // Direct .nomedia check
        val directNoMedia = try {
            val noMediaFile = File(dir, ".nomedia")
            fileExistsCheck(noMediaFile)
        } catch (_: Exception) {
            false
        }

        if (directNoMedia) {
            directoryCache[canonical] = true
            return true
        }

        // Check parent directory recursively with memoization
        val parentHasNoMedia = dir.parentFile?.let { checkDirectory(it) } ?: false
        directoryCache[canonical] = parentHasNoMedia
        return parentHasNoMedia
    }
}

/**
 * Persistent storage abstraction for user-configured excluded folders.
 */
interface FolderExclusionStorage {
    fun getExcludedFolders(): Set<String>
    fun addExcludedFolder(folderPath: String)
    fun removeExcludedFolder(folderPath: String)
    fun setExcludedFolders(folders: Set<String>)
}

/**
 * SharedPreferences-based persistence for excluded folders.
 * Survives application close, process death, and device reboots.
 */
class SharedPreferencesFolderExclusionStorage(
    private val context: Context
) : FolderExclusionStorage {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getExcludedFolders(): Set<String> {
        return prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet())?.toSet() ?: emptySet()
    }

    override fun addExcludedFolder(folderPath: String) {
        val current = getExcludedFolders().toMutableSet()
        current.add(folderPath)
        prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, current).apply()
    }

    override fun removeExcludedFolder(folderPath: String) {
        val current = getExcludedFolders().toMutableSet()
        current.remove(folderPath)
        prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, current).apply()
    }

    override fun setExcludedFolders(folders: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, folders).apply()
    }

    companion object {
        private const val PREFS_NAME = "avenor_folder_exclusions"
        private const val KEY_EXCLUDED_FOLDERS = "excluded_folders_set"
    }
}

/**
 * In-memory implementation for isolated testing without Android Context dependency.
 */
class InMemoryFolderExclusionStorage(
    initial: Set<String> = emptySet()
) : FolderExclusionStorage {
    private val set = Collections.synchronizedSet(initial.toMutableSet())

    override fun getExcludedFolders(): Set<String> = set.toSet()

    override fun addExcludedFolder(folderPath: String) {
        set.add(folderPath)
    }

    override fun removeExcludedFolder(folderPath: String) {
        set.remove(folderPath)
    }

    override fun setExcludedFolders(folders: Set<String>) {
        set.clear()
        set.addAll(folders)
    }
}

/**
 * Core exclusion policy combining folder path segment matching and .nomedia detection.
 */
class FolderExclusionPolicy(
    private val storage: FolderExclusionStorage,
    private val noMediaDetector: NoMediaDetector = DefaultNoMediaDetector()
) {

    /**
     * Determines whether an audio item is excluded based on its filesystem path and/or relative path.
     */
    fun isExcluded(filePath: String?, relativePath: String? = null): Boolean {
        val excludedFolders = storage.getExcludedFolders()

        if (excludedFolders.isNotEmpty()) {
            if (!filePath.isNullOrBlank()) {
                val dirSegments = PathNormalizer.getDirectorySegments(filePath)
                for (rule in excludedFolders) {
                    if (ExclusionMatcher.matches(dirSegments, rule)) {
                        return true
                    }
                }
            }
            if (!relativePath.isNullOrBlank()) {
                val relSegments = PathNormalizer.getDirectorySegments(relativePath)
                for (rule in excludedFolders) {
                    if (ExclusionMatcher.matches(relSegments, rule)) {
                        return true
                    }
                }
            }
        }

        if (!filePath.isNullOrBlank() && noMediaDetector.hasNoMedia(filePath)) {
            return true
        }

        return false
    }

    fun getExcludedFolders(): Set<String> = storage.getExcludedFolders()

    fun addExcludedFolder(path: String) {
        val normalized = PathNormalizer.normalize(path)
        if (normalized.isNotBlank()) {
            storage.addExcludedFolder(normalized)
        }
    }

    fun removeExcludedFolder(path: String) {
        val normalized = PathNormalizer.normalize(path)
        storage.removeExcludedFolder(normalized)
        if (normalized != path.trim()) {
            storage.removeExcludedFolder(path.trim())
        }
    }

    fun setExcludedFolders(folders: Set<String>) {
        val normalized = folders.mapNotNull {
            val norm = PathNormalizer.normalize(it)
            if (norm.isNotBlank()) norm else null
        }.toSet()
        storage.setExcludedFolders(normalized)
    }

    fun clearNoMediaCache() {
        noMediaDetector.clearCache()
    }
}
