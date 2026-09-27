package io.github.yisus.avenor.lyrics

/**
 * Robust LRC Parser supporting standard LRC, Enhanced LRC (word/syllable timestamps),
 * multi-timestamp lines, millisecond/centisecond precision, and offset tags.
 */
object LrcParser {
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)
    val SYLLABLE_REGEX = Regex("""<(\d{2}):(\d{2})\.(\d{2,3})>([^<]*)""")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        var globalOffsetMs = 0L
        val parsedLines = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            // Check for offset tag e.g. [offset:+500]
            val offsetMatch = OFFSET_REGEX.find(trimmed)
            if (offsetMatch != null) {
                globalOffsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }

            // Extract all timestamps from the line
            val timestampMatches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (timestampMatches.isNotEmpty()) {
                // The lyric text is everything after the last timestamp
                val lastMatch = timestampMatches.last()
                val rawText = trimmed.substring(lastMatch.range.last + 1).trim()

                val syllableMatches = if (rawText.contains('<') && rawText.contains('>')) {
                    SYLLABLE_REGEX.findAll(rawText).toList()
                } else {
                    emptyList()
                }

                val syllables: List<LyricSyllable>
                val cleanText: String

                if (syllableMatches.isNotEmpty()) {
                    val parsedSyllables = ArrayList<LyricSyllable>(syllableMatches.size)
                    val textBuilder = StringBuilder(rawText.length)

                    for (syllableMatch in syllableMatches) {
                        val sMinutes = syllableMatch.groupValues[1].toLongOrNull() ?: 0L
                        val sSeconds = syllableMatch.groupValues[2].toLongOrNull() ?: 0L
                        val sFraction = syllableMatch.groupValues[3]
                        val sMillis = parseFractionToMillis(sFraction)
                        val sText = syllableMatch.groupValues[4]

                        val sTimeMs = maxOf(0L, (sMinutes * 60_000L) + (sSeconds * 1_000L) + sMillis + globalOffsetMs)
                        if (sText.isNotEmpty()) {
                            parsedSyllables.add(LyricSyllable(timeMs = sTimeMs, text = sText))
                            textBuilder.append(sText)
                        }
                    }

                    syllables = parsedSyllables
                    cleanText = textBuilder.toString().trim()
                } else {
                    syllables = emptyList()
                    cleanText = rawText
                }

                for (match in timestampMatches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues.getOrNull(3).orEmpty()
                    val millis = parseFractionToMillis(fractionStr)

                    val timeMs = (minutes * 60_000L) + (seconds * 1_000L) + millis + globalOffsetMs
                    parsedLines.add(
                        LyricLine(
                            timeMs = maxOf(0L, timeMs),
                            text = cleanText,
                            syllables = syllables
                        )
                    )
                }
            }
        }

        return parsedLines.sortedBy { it.timeMs }
    }

    private fun parseFractionToMillis(fractionStr: String): Long {
        return when (fractionStr.length) {
            1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
            3 -> fractionStr.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
}
