package io.github.yisus.avenor

import androidx.room.Room
import io.github.yisus.avenor.playback.PlaybackCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackCoordinatorTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MusicDao
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val sampleSongs = listOf(
        Song(id = 1L, uri = "content://audio/1", title = "Song One", artist = "Artist A", album = "Album A", durationMs = 180000L, albumArtUri = null),
        Song(id = 2L, uri = "content://audio/2", title = "Song Two", artist = "Artist B", album = "Album B", durationMs = 210000L, albumArtUri = null),
        Song(id = 3L, uri = "content://audio/3", title = "Song Three", artist = "Artist C", album = "Album C", durationMs = 240000L, albumArtUri = null)
    )

    @Before
    fun setup() = runTest(testDispatcher) {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.musicDao()

        // Insert sample songs in database
        dao.insertSongs(sampleSongs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSaveAndRestoreFullQueue() = runTest(testDispatcher) {
        val coordinator = PlaybackCoordinator(dao, testScope)

        // Save queue with song at index 1 and position 45,000ms
        coordinator.saveFullQueue(
            songs = sampleSongs,
            currentIndex = 1,
            positionMs = 45000L,
            isPlaying = true,
            shuffleMode = true,
            repeatMode = 2
        )

        val currentState = coordinator.playbackState.value
        assertEquals(3, currentState.queue.size)
        assertEquals(1, currentState.currentIndex)
        assertEquals(sampleSongs[1], currentState.currentSong)
        assertEquals(45000L, currentState.currentPositionMs)
        assertTrue(currentState.shuffleMode)
        assertEquals(2, currentState.repeatMode)

        // Verify direct database persistence
        val dbQueue = dao.getPlaybackQueueSync(PlaybackCoordinator.ACTIVE_QUEUE_ID)
        assertNotNull(dbQueue)
        assertEquals(1, dbQueue!!.currentIndex)
        assertEquals(45000L, dbQueue.currentPositionMs)
        assertTrue(dbQueue.shuffleMode)
        assertEquals(2, dbQueue.repeatMode)

        val dbSongs = dao.getSongsForQueueSync(PlaybackCoordinator.ACTIVE_QUEUE_ID)
        assertEquals(3, dbSongs.size)
        assertEquals(sampleSongs[0].id, dbSongs[0].id)
        assertEquals(sampleSongs[1].id, dbSongs[1].id)
        assertEquals(sampleSongs[2].id, dbSongs[2].id)
    }

    @Test
    fun testProcessDeathSimulation_restoresExactState() = runTest(testDispatcher) {
        // Process 1: Coordinator writes state
        val process1Coordinator = PlaybackCoordinator(dao, testScope)
        process1Coordinator.saveFullQueue(
            songs = sampleSongs,
            currentIndex = 2,
            positionMs = 120000L,
            isPlaying = true,
            shuffleMode = false,
            repeatMode = 1
        )

        // Simulate Process Death: process1Coordinator is discarded.
        // Process 2: App restarts, new coordinator initializes with existing database
        val process2Coordinator = PlaybackCoordinator(dao, testScope)
        val restored = process2Coordinator.restorePersistedState()

        assertEquals(3, restored.queue.size)
        assertEquals(2, restored.currentIndex)
        assertEquals(sampleSongs[2], restored.currentSong)
        assertEquals(120000L, restored.currentPositionMs)
        assertEquals(false, restored.isPlaying) // Must be paused upon restore
        assertEquals(false, restored.shuffleMode)
        assertEquals(1, restored.repeatMode)
    }

    @Test
    fun testUpdatePosition_persistsToDatabase() = runTest(testDispatcher) {
        val coordinator = PlaybackCoordinator(dao, testScope)
        coordinator.saveFullQueue(
            songs = sampleSongs,
            currentIndex = 0,
            positionMs = 0L
        )

        // Force position update
        coordinator.updatePosition(30000L, force = true)

        assertEquals(30000L, coordinator.playbackState.value.currentPositionMs)
        val dbQueue = dao.getPlaybackQueueSync(PlaybackCoordinator.ACTIVE_QUEUE_ID)
        assertEquals(30000L, dbQueue?.currentPositionMs)
    }

    @Test
    fun testRemoveFromQueue_updatesStateAndDatabase() = runTest(testDispatcher) {
        val coordinator = PlaybackCoordinator(dao, testScope)
        coordinator.saveFullQueue(
            songs = sampleSongs,
            currentIndex = 2,
            positionMs = 10000L
        )

        // Remove song at index 0 (which shifts currentIndex from 2 to 1)
        coordinator.removeFromQueue(0)

        val state = coordinator.playbackState.value
        assertEquals(2, state.queue.size)
        assertEquals(1, state.currentIndex)
        assertEquals(sampleSongs[2], state.currentSong)

        // Verify in DB
        val dbSongs = dao.getSongsForQueueSync(PlaybackCoordinator.ACTIVE_QUEUE_ID)
        assertEquals(2, dbSongs.size)
        assertEquals(sampleSongs[1].id, dbSongs[0].id)
        assertEquals(sampleSongs[2].id, dbSongs[1].id)
    }

    @Test
    fun testReorderQueue_updatesOrderAndDatabase() = runTest(testDispatcher) {
        val coordinator = PlaybackCoordinator(dao, testScope)
        coordinator.saveFullQueue(
            songs = sampleSongs,
            currentIndex = 0,
            positionMs = 10000L
        )

        // Move item at index 0 to index 2
        coordinator.reorderQueue(0, 2)

        val state = coordinator.playbackState.value
        assertEquals(sampleSongs[1].id, state.queue[0].id)
        assertEquals(sampleSongs[2].id, state.queue[1].id)
        assertEquals(sampleSongs[0].id, state.queue[2].id)
        assertEquals(2, state.currentIndex)
        assertEquals(sampleSongs[0], state.currentSong)

        // Verify in DB
        val dbSongs = dao.getSongsForQueueSync(PlaybackCoordinator.ACTIVE_QUEUE_ID)
        assertEquals(3, dbSongs.size)
        assertEquals(sampleSongs[1].id, dbSongs[0].id)
        assertEquals(sampleSongs[2].id, dbSongs[1].id)
        assertEquals(sampleSongs[0].id, dbSongs[2].id)
    }
}
