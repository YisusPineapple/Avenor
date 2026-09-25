package io.github.yisus.avenor.metadata

import io.github.yisus.avenor.replaygain.ReplayGainData

/**
 * Three-state classification for explicit content rating.
 */
enum class ExplicitRating(val code: Int) {
    UNKNOWN(0),
    CLEAN(1),
    EXPLICIT(2);

    companion object {
        fun fromCode(code: Int): ExplicitRating = values().firstOrNull { it.code == code } ?: UNKNOWN

        fun fromTag(value: String?): ExplicitRating {
            if (value == null) return UNKNOWN
            return when (value.trim().lowercase()) {
                "explicit", "1", "yes", "x" -> EXPLICIT
                "clean", "2", "no", "c" -> CLEAN
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Core metadata model for UI lists, queue playback, sorting and navigation.
 *
 * NOTE: Preserves [artist] as the primary canonical property to ensure seamless
 * compatibility with existing DAO queries, while exposing [displayArtist] as a derived property.
 */
data class CanonicalSongCore(
    val id: Long = 0L,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val sortTitle: String = "",
    val sortArtist: String = "",
    val sortAlbum: String = "",
    val durationMs: Long = 0L,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val bitrate: Long = 0L,
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val channels: Int = 2,
    val codec: String = "MP3",
    val container: String = "mp3",
    val mimeType: String = "audio/mpeg",
    val fileSize: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0L,
    val isFavorite: Boolean = false,
    val rating: Int = 0,
    val explicitRating: ExplicitRating = ExplicitRating.UNKNOWN,
    val hasEmbeddedArtwork: Boolean = false,
    val hasLyrics: Boolean = false,
    val isExcludedFromLibrary: Boolean = false,
    val replayGain: ReplayGainData = ReplayGainData()
) {
    /**
     * Derived display artist string. Equals [artist].
     */
    val displayArtist: String get() = artist
}

/**
 * Secondary metadata block consulted on-demand for track info, tag editing,
 * classical music credits and audiophile cataloging.
 */
data class ExtendedMetadataBlock(
    val songId: Long = 0L,
    val composers: List<String> = emptyList(),
    val lyricists: List<String> = emptyList(),
    val conductors: List<String> = emptyList(),
    val performers: List<String> = emptyList(),
    val remixers: List<String> = emptyList(),
    val totalTracks: Int? = null,
    val totalDiscs: Int? = null,
    val releaseDate: String? = null,   // ISO-8601 (YYYY-MM-DD or YYYY)
    val originalDate: String? = null,  // ISO-8601 (YYYY-MM-DD or YYYY)
    val genres: List<String> = emptyList(),
    val mood: String? = null,
    val isrc: String? = null,
    val barcode: String? = null,
    val catalogNumber: String? = null,
    val copyright: String? = null,
    val label: String? = null,
    val publisher: String? = null,
    val comment: String? = null,
    val musicBrainzTrackId: String? = null,
    val musicBrainzReleaseId: String? = null,
    val musicBrainzArtistId: String? = null,
    val musicBrainzReleaseGroupId: String? = null,
    /**
     * Preserves raw, non-canonical, or proprietary tags with multi-value support in memory.
     * Persistence layer applies 2048 character limits during storage normalization.
     */
    val rawTags: Map<String, List<String>> = emptyMap()
)

/**
 * Lyrics source categorization.
 */
enum class LyricsSourceType(val code: Int) {
    NONE(0),
    EXTERNAL_LRC(1),
    EMBEDDED_SYNC(2),
    EMBEDDED_UNSYNC(3);

    companion object {
        fun fromCode(code: Int): LyricsSourceType = values().firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * Decoupled lyrics block representing synchronized LRC content or multi-line plain text.
 */
data class SongLyricsBlock(
    val songId: Long = 0L,
    val hasSynced: Boolean = false,
    val syncedLyricsLrc: String? = null,
    val unsyncedLyrics: String? = null,
    val sourceType: LyricsSourceType = LyricsSourceType.NONE,
    val offsetMs: Int = 0,
    val externalLrcUri: String? = null,
    val externalLrcLastModified: Long = 0L
) {
    val hasAnyLyrics: Boolean get() = !syncedLyricsLrc.isNullOrBlank() || !unsyncedLyrics.isNullOrBlank()
}

/**
 * Picture role type according to ID3v2 APIC / FLAC Picture specifications.
 */
enum class ArtworkPictureType(val code: Int) {
    OTHER(0),
    FRONT_COVER(1),
    BACK_COVER(2),
    LEAFLET(3),
    MEDIA_LABEL(4),
    LEAD_ARTIST(5),
    ARTIST(6),
    CONDUCTOR(7),
    BAND(8),
    COMPOSER(9);

    companion object {
        fun fromCode(code: Int): ArtworkPictureType = values().firstOrNull { it.code == code } ?: FRONT_COVER
    }
}

/**
 * Source origin of an album artwork asset.
 */
enum class ArtworkSourceType(val code: Int) {
    EMBEDDED(1),
    EXTERNAL_FOLDER(2),
    MEDIA_STORE_LEGACY(3);

    companion object {
        fun fromCode(code: Int): ArtworkSourceType = values().firstOrNull { it.code == code } ?: MEDIA_STORE_LEGACY
    }
}

/**
 * Content-addressed artwork descriptor.
 *
 * Guarantees deduplication across identical tracks by indexing [artworkHash].
 * [legacyUri] stores legacy MediaStore content:// URIs for fallback.
 */
data class SongArtworkDescriptor(
    val songId: Long = 0L,
    val artworkHash: String,
    val durableRelativePath: String? = null,
    val legacyUri: String? = null,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val artworkType: ArtworkPictureType = ArtworkPictureType.FRONT_COVER,
    val sourceType: ArtworkSourceType = ArtworkSourceType.EMBEDDED,
    val isRegenerable: Boolean = true
)
