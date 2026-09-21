package io.github.yisus.avenor

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

class AudioRepository(private val context: Context, dao: MusicDao? = null) {
    private val musicDao: MusicDao = dao ?: AppDatabase.getDatabase(context).musicDao()
    val scanner = LibraryScanner(context, musicDao)
    val scanProgress: StateFlow<ScanProgress> = scanner.progress

    suspend fun scanLibrary(): ScanProgress = scanner.scan()

    /**
     * Legacy adapter delegating to the central [LibraryScanner] to eliminate
     * duplicate MediaStore queries, redundant projections, and duplicate Song model construction.
     */
    suspend fun getLocalAudioFiles(): List<Song> = scanner.queryLocalAudioFiles()

    fun getExcludedFolders(): Set<String> = scanner.getExcludedFolders()

    fun addExcludedFolder(path: String) = scanner.addExcludedFolder(path)

    fun removeExcludedFolder(path: String) = scanner.removeExcludedFolder(path)

    fun setExcludedFolders(folders: Set<String>) = scanner.setExcludedFolders(folders)
}

