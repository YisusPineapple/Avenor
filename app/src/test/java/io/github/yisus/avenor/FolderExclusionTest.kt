package io.github.yisus.avenor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FolderExclusionTest {

    // 1. Normalización de rutas
    @Test
    fun testPathNormalization_handlesSlashesAndCasing() {
        val raw1 = "\\storage\\emulated\\0\\Music\\WhatsApp\\"
        val normalized1 = PathNormalizer.normalize(raw1)
        assertEquals("/storage/emulated/0/music/whatsapp", normalized1)

        val raw2 = "Music///Podcasts//Old//"
        val normalized2 = PathNormalizer.normalize(raw2)
        assertEquals("music/podcasts/old", normalized2)

        val segments = PathNormalizer.toSegments("  /SDCard/AUDIO/Tracks/Song.MP3  ")
        assertEquals(listOf("sdcard", "audio", "tracks", "song.mp3"), segments)

        val dirSegments = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/Album/Song.flac")
        assertEquals(listOf("storage", "emulated", "0", "music", "album"), dirSegments)

        val pureDirSegments = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/Album")
        assertEquals(listOf("storage", "emulated", "0", "music", "album"), pureDirSegments)
    }

    // 2. Coincidencia exacta de carpeta
    @Test
    fun testExclusionMatcher_exactFolderMatch() {
        val dirSegments = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/WhatsApp")
        
        // Exact relative rule matching folder in path
        assertTrue(ExclusionMatcher.matches(dirSegments, "Music/WhatsApp"))
        assertTrue(ExclusionMatcher.matches(dirSegments, "WhatsApp"))

        // Exact absolute rule
        assertTrue(ExclusionMatcher.matches(dirSegments, "/storage/emulated/0/Music/WhatsApp"))

        // Different root absolute rule should not match
        assertFalse(ExclusionMatcher.matches(dirSegments, "/other_storage/Music/WhatsApp"))
    }

    // 3. Exclusión de descendientes
    @Test
    fun testExclusionMatcher_descendantsExcluded() {
        val rule = "Music/WhatsApp"

        // Direct child folder
        val childSegments = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/WhatsApp/Audio/song.opus")
        assertTrue(ExclusionMatcher.matches(childSegments, rule))

        // Deep descendant folder
        val deepSegments = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/WhatsApp/Audio/Sent/Artist/voice.mp3")
        assertTrue(ExclusionMatcher.matches(deepSegments, rule))

        // Absolute rule descendant matching
        val absRule = "/storage/emulated/0/Podcasts"
        val podcastEpisode = PathNormalizer.getDirectorySegments("/storage/emulated/0/Podcasts/Tech/Episode1.mp3")
        assertTrue(ExclusionMatcher.matches(podcastEpisode, absRule))
    }

    // 4. No coincidencia de nombres similares (evitar falso positivo por substring)
    @Test
    fun testExclusionMatcher_noSubstringFalsePositives() {
        val rule = "Music/Podcasts"

        // PodcastsOld must NOT match Podcasts
        val similarSegments1 = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/PodcastsOld/ep1.mp3")
        assertFalse(ExclusionMatcher.matches(similarSegments1, rule))

        // MyPodcasts must NOT match Podcasts
        val similarSegments2 = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/MyPodcasts/ep1.mp3")
        assertFalse(ExclusionMatcher.matches(similarSegments2, rule))

        // AudioBooks must NOT match Audio
        val audioRule = "Audio"
        val audioBooksSegments = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/AudioBooks/chapter1.mp3")
        assertFalse(ExclusionMatcher.matches(audioBooksSegments, audioRule))

        // But actual Audio folder matches
        val actualAudioSegments = PathNormalizer.getDirectorySegments("/storage/emulated/0/Music/Audio/chapter1.mp3")
        assertTrue(ExclusionMatcher.matches(actualAudioSegments, audioRule))
    }

    // 5. Múltiples carpetas excluidas
    @Test
    fun testExclusionPolicy_multipleExcludedFolders() {
        val storage = InMemoryFolderExclusionStorage()
        val policy = FolderExclusionPolicy(storage)

        policy.setExcludedFolders(
            setOf(
                "Music/WhatsApp",
                "Download/VoiceNotes",
                "Ringtones"
            )
        )

        // Matches first rule
        assertTrue(policy.isExcluded("/storage/emulated/0/Music/WhatsApp/note.opus"))
        // Matches second rule
        assertTrue(policy.isExcluded("/storage/emulated/0/Download/VoiceNotes/memo.m4a"))
        // Matches third rule
        assertTrue(policy.isExcluded("/system/media/audio/Ringtones/ring.mp3"))

        // Unrelated legitimate folders must NOT be excluded
        assertFalse(policy.isExcluded("/storage/emulated/0/Music/Rock/Queen/Bohemian.flac"))
        assertFalse(policy.isExcluded("/storage/emulated/0/Music/Downloads/Song.mp3"))
        assertFalse(policy.isExcluded("/storage/emulated/0/Music/Ring/Song.mp3"))
    }

    // 6. .nomedia con abstracción/mock independiente del filesystem físico
    @Test
    fun testNoMediaDetection_withMockAndAncestorChecking() {
        val noMediaDirs = mutableSetOf(
            "/storage/emulated/0/Telegram",
            "/storage/emulated/0/Private/Notes"
        )

        var fileExistsInvocationCount = 0

        val detector = DefaultNoMediaDetector(
            fileExistsCheck = { file ->
                fileExistsInvocationCount++
                // Normalize parent path for comparison
                val dirPath = file.parentFile?.absolutePath?.replace('\\', '/') ?: ""
                noMediaDirs.contains(dirPath)
            }
        )

        // 1. Direct directory with .nomedia
        assertTrue(detector.hasNoMedia("/storage/emulated/0/Telegram/audio.mp3"))

        // 2. Child directory inheriting .nomedia from ancestor
        assertTrue(detector.hasNoMedia("/storage/emulated/0/Telegram/Audio/voice.ogg"))
        assertTrue(detector.hasNoMedia("/storage/emulated/0/Telegram/Audio/Sent/voice2.ogg"))

        // 3. Clean directory without .nomedia
        assertFalse(detector.hasNoMedia("/storage/emulated/0/Music/Rock/song.flac"))

        // 4. Verify directory memoization: repeat calls in the same directory do not re-check filesystem
        val countBefore = fileExistsInvocationCount
        assertTrue(detector.hasNoMedia("/storage/emulated/0/Telegram/another_audio.mp3"))
        assertTrue(detector.hasNoMedia("/storage/emulated/0/Telegram/audio3.mp3"))
        assertFalse(detector.hasNoMedia("/storage/emulated/0/Music/Rock/song2.flac"))
        // All directory queries should hit the in-memory cache
        assertEquals("Cache should prevent repeated checks for the same directories", countBefore, fileExistsInvocationCount)

        // 5. Verify cache clear
        detector.clearCache()
        assertTrue(detector.hasNoMedia("/storage/emulated/0/Telegram/audio.mp3"))
        assertTrue(fileExistsInvocationCount > countBefore)
    }

    // 7. Canción excluida no entra en el conjunto procesable
    @Test
    fun testExcludedSongDoesNotEnterProcessingOrRoom() {
        val storage = InMemoryFolderExclusionStorage(setOf("Music/Exempt"))
        val noMediaDirs = setOf("/storage/emulated/0/Hidden")
        val noMediaDetector = DefaultNoMediaDetector { file ->
            noMediaDirs.contains(file.parentFile?.absolutePath?.replace('\\', '/'))
        }
        val policy = FolderExclusionPolicy(storage, noMediaDetector)

        // Excluded by folder policy
        val song1Path = "/storage/emulated/0/Music/Exempt/track1.mp3"
        assertTrue(policy.isExcluded(song1Path))

        // Excluded by relative path
        val song2Path = "/storage/emulated/0/Unknown/track2.mp3"
        val song2RelPath = "Music/Exempt/track2.mp3"
        assertTrue(policy.isExcluded(song2Path, song2RelPath))

        // Excluded by .nomedia
        val song3Path = "/storage/emulated/0/Hidden/track3.mp3"
        assertTrue(policy.isExcluded(song3Path))

        // Valid audio file
        val validSongPath = "/storage/emulated/0/Music/Valid/track4.mp3"
        assertFalse(policy.isExcluded(validSongPath, "Music/Valid/"))
    }

    // 8. Persistencia de exclusiones con SharedPreferences
    @Test
    fun testPersistence_survivesThroughSharedPreferences() {
        val context = RuntimeEnvironment.getApplication()
        val storage1 = SharedPreferencesFolderExclusionStorage(context)

        // Initial state is empty
        storage1.setExcludedFolders(emptySet())
        assertTrue(storage1.getExcludedFolders().isEmpty())

        // Add excluded folders
        storage1.addExcludedFolder("Music/WhatsApp")
        storage1.addExcludedFolder("/storage/emulated/0/Podcasts")

        val expected = setOf("Music/WhatsApp", "/storage/emulated/0/Podcasts")
        assertEquals(expected, storage1.getExcludedFolders())

        // Recreate storage instance simulating app/process restart
        val storage2 = SharedPreferencesFolderExclusionStorage(context)
        assertEquals("Excluded folders must survive process restart", expected, storage2.getExcludedFolders())

        // Remove one folder
        storage2.removeExcludedFolder("Music/WhatsApp")
        assertEquals(setOf("/storage/emulated/0/Podcasts"), storage2.getExcludedFolders())

        // Verify with policy wrapper
        val policy = FolderExclusionPolicy(storage2)
        assertEquals(setOf("/storage/emulated/0/Podcasts"), policy.getExcludedFolders())
        policy.addExcludedFolder("NewFolder/Audio")
        assertEquals(setOf("/storage/emulated/0/Podcasts", "newfolder/audio"), policy.getExcludedFolders())
        storage1.setExcludedFolders(emptySet())
    }
}
