package io.github.yisus.avenor.metadata

/**
 * Utility for splitting raw multi-artist credit strings into individual artist names
 * for UI presentation without mutating the underlying persisted database metadata.
 */
object CreditSplitter {

    /**
     * Compiled once to avoid repeated allocations during UI recompositions or list scrolling.
     * Matches common music industry delimiters:
     * - Word delimiters (require surrounding whitespace): feat., ft., featuring, x, vs., with
     * - Symbol delimiters (optional surrounding whitespace): &, ,
     */
    private val ARTIST_DELIMITER_REGEX = Regex(
        pattern = """\s+(?:feat\.?|ft\.?|featuring|x|vs\.?|with)\s+|\s*(?:&|,)\s*""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Splits a raw artist string (e.g. "Artist A feat. Artist B & Artist C")
     * into a clean list of individual artist names.
     */
    fun splitArtists(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return emptyList()
        return ARTIST_DELIMITER_REGEX
            .split(rawArtist)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
