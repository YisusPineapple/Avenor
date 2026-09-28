package io.github.yisus.avenor

import io.github.yisus.avenor.app.ui.viewmodels.PlayerViewModel
import io.github.yisus.avenor.playback.PlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for task 0.3.
 *
 * Before 0.3, the "Saved Queues" tab rendered a phantom card named
 * "ACTIVE_QUEUE" because PlayerViewModel.allQueues exposed the internal
 * PlaybackCoordinator.ACTIVE_QUEUE_ID record alongside user-saved queues.
 */
class QueueActiveFilterTest {

    @Test
    fun testFilterSavedQueues_excludesActiveQueueAndKeepsUserQueues() {
        val active = PlaybackQueue(
            id = PlaybackCoordinator.ACTIVE_QUEUE_ID,
            name = "ACTIVE_QUEUE"
        )
        val userQueue1 = PlaybackQueue(id = 2, name = "Roadtrip 2025")
        val userQueue2 = PlaybackQueue(id = 3, name = "Focus Session")

        val result = PlayerViewModel.filterSavedQueues(
            listOf(active, userQueue1, userQueue2)
        )

        assertEquals("Active queue must be excluded", 2, result.size)
        assertFalse(
            "No ACTIVE_QUEUE record may survive the filter",
            result.any { it.id == PlaybackCoordinator.ACTIVE_QUEUE_ID }
        )
        assertTrue("User queue 1 must be preserved", result.contains(userQueue1))
        assertTrue("User queue 2 must be preserved", result.contains(userQueue2))
    }
}
