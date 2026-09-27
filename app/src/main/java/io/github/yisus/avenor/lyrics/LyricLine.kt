package io.github.yisus.avenor.lyrics

data class LyricSyllable(
    val timeMs: Long,
    val text: String
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val syllables: List<LyricSyllable> = emptyList()
)

