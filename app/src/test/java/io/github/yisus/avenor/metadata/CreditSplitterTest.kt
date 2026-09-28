package io.github.yisus.avenor.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CreditSplitter] covering whitelist preservation,
 * non-delimiter symbols (e.g. slash in AC/DC), standard multi-artist
 * delimiters, and boundary inputs.
 */
class CreditSplitterTest {

    @Test
    fun testSplitArtists_simonAndGarfunkel_remainsSingleArtist() {
        val result = CreditSplitter.splitArtists("Simon & Garfunkel")
        assertEquals(listOf("Simon & Garfunkel"), result)
    }

    @Test
    fun testSplitArtists_simonAndGarfunkelCaseInsensitive_preservesOriginalCasing() {
        val result = CreditSplitter.splitArtists("  SIMON & GARFUNKEL  ")
        assertEquals(listOf("SIMON & GARFUNKEL"), result)
    }

    @Test
    fun testSplitArtists_acDcWithSlash_remainsSingleArtist() {
        val result = CreditSplitter.splitArtists("AC/DC")
        assertEquals(listOf("AC/DC"), result)
    }

    @Test
    fun testSplitArtists_featDelimiter_splitsIntoTwoArtists() {
        val result = CreditSplitter.splitArtists("Artist A feat. Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun testSplitArtists_ampersandNonWhitelisted_splitsIntoTwoArtists() {
        val result = CreditSplitter.splitArtists("Artist A & Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun testSplitArtists_commaDelimiter_splitsAllArtists() {
        val result = CreditSplitter.splitArtists("Artist A, Artist B, Artist C")
        assertEquals(listOf("Artist A", "Artist B", "Artist C"), result)
    }

    @Test
    fun testSplitArtists_mixedDelimiters_splitsAllArtists() {
        val result = CreditSplitter.splitArtists("Artist A feat. Artist B & Artist C")
        assertEquals(listOf("Artist A", "Artist B", "Artist C"), result)
    }

    @Test
    fun testSplitArtists_blankInput_returnsEmptyList() {
        val result = CreditSplitter.splitArtists("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testSplitArtists_singleRegularArtist_returnsSingleElementList() {
        val result = CreditSplitter.splitArtists("Pink Floyd")
        assertEquals(listOf("Pink Floyd"), result)
    }
}
