package io.github.yisus.avenor.lyrics

/**
 * Robust LRC Parser supporting standard LRC, multi-timestamp lines,
 * millisecond/centisecond precision, and offset tags.
 */
object LrcParser {
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

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
                val text = trimmed.substring(lastMatch.range.last + 1).trim()

                for (match in timestampMatches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues.getOrNull(3).orEmpty()

                    val millis = when (fractionStr.length) {
                        1 -> fractionStr.toLong() * 100
                        2 -> fractionStr.toLong() * 10
                        3 -> fractionStr.toLong()
                        else -> 0L
                    }

                    val timeMs = (minutes * 60_000L) + (seconds * 1_000L) + millis + globalOffsetMs
                    parsedLines.add(LyricLine(timeMs = maxOf(0L, timeMs), text = text))
                }
            }
        }

        return parsedLines.sortedBy { it.timeMs }
    }
}
