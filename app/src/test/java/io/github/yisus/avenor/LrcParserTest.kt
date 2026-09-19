package io.github.yisus.avenor

import io.github.yisus.avenor.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testEmptyOrNullReturnsEmptyList() {
        assertTrue(LrcParser.parse(null).isEmpty())
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("   \n\n  ").isEmpty())
    }

    @Test
    fun testStandardLrcParsing() {
        val lrc = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:05.50]First line of lyrics
            [00:10.00]Second line of lyrics
            [01:00.250]Third line after one minute
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(3, parsed.size)
        assertEquals(5500L, parsed[0].timeMs)
        assertEquals("First line of lyrics", parsed[0].text)

        assertEquals(10000L, parsed[1].timeMs)
        assertEquals("Second line of lyrics", parsed[1].text)

        assertEquals(60250L, parsed[2].timeMs)
        assertEquals("Third line after one minute", parsed[2].text)
    }

    @Test
    fun testMultipleTimestampsPerLine() {
        val lrc = """
            [00:10.00][00:20.00]Repeated chorus line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(2, parsed.size)
        assertEquals(10000L, parsed[0].timeMs)
        assertEquals("Repeated chorus line", parsed[0].text)
        assertEquals(20000L, parsed[1].timeMs)
        assertEquals("Repeated chorus line", parsed[1].text)
    }

    @Test
    fun testGlobalOffsetTag() {
        val lrc = """
            [offset:+500]
            [00:02.00]Offset line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(1, parsed.size)
        assertEquals(2500L, parsed[0].timeMs)
        assertEquals("Offset line", parsed[0].text)
    }

    @Test
    fun testNonLrcTextIgnoredGracefully() {
        val lrc = """
            This is not LRC format
            Random line without timestamps
            [invalid] also not valid
            [00:03.00]Valid line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(1, parsed.size)
        assertEquals(3000L, parsed[0].timeMs)
        assertEquals("Valid line", parsed[0].text)
    }
}
