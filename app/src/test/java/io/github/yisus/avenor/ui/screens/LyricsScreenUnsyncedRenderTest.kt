package io.github.yisus.avenor.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsScreenUnsyncedRenderTest {

    @Test
    fun testShouldRenderUnsynced_emptySyncedAndNonEmptyUnsynced_returnsTrue() {
        assertTrue(shouldRenderUnsynced(syncedCount = 0, unsynced = "line 1\nline 2"))
    }

    @Test
    fun testShouldRenderUnsynced_emptySyncedAndNullUnsynced_returnsFalse() {
        assertFalse(shouldRenderUnsynced(syncedCount = 0, unsynced = null))
    }

    @Test
    fun testShouldRenderUnsynced_emptySyncedAndBlankUnsynced_returnsFalse() {
        assertFalse(shouldRenderUnsynced(syncedCount = 0, unsynced = "   \n  "))
    }

    @Test
    fun testShouldRenderUnsynced_syncedPresent_returnsFalseEvenWithUnsynced() {
        assertFalse(shouldRenderUnsynced(syncedCount = 5, unsynced = "fallback text"))
    }
}
