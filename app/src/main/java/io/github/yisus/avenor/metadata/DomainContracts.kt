package io.github.yisus.avenor.metadata

import io.github.yisus.avenor.LibraryScanner
import io.github.yisus.avenor.Song
import io.github.yisus.avenor.replaygain.ReplayGainData
import java.security.MessageDigest

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
 * Scope Note (P2-7.1B.1):
 * This is the canonical in-memory domain contract bridging metadata extraction and Room v17 [Song].
 * Room database remains at schema v17; [toSong] and [fromSong] provide lossless projection
 * to and from the persisted [Song] entity.
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

    /**
     * Projects this canonical domain model into the Room v17 [Song] entity.
     */
    fun toSong(
        albumArtUri: String? = null,
        composer: String = "",
        comment: String = "",
        artworkWidth: Int = 0,
        artworkHeight: Int = 0,
        artworkMimeType: String = ""
    ): Song = Song(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        albumArtUri = albumArtUri,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        mimeType = mimeType,
        fileExtension = container,
        codec = codec,
        bitrate = bitrate,
        channels = channels,
        fileSize = fileSize,
        dateModified = dateModified,
        dateAdded = dateAdded,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        composer = composer,
        albumArtist = albumArtist.ifBlank { artist },
        sortTitle = sortTitle.ifBlank { LibraryScanner.generateSortTitle(title) },
        comment = comment,
        replayGainTrack = replayGain.trackGain,
        replayGainAlbum = replayGain.albumGain,
        artworkWidth = artworkWidth,
        artworkHeight = artworkHeight,
        artworkMimeType = artworkMimeType,
        isExcludedFromLibrary = isExcludedFromLibrary
    )

    companion object {
        /**
         * Lifts a persisted Room v17 [Song] entity into a [CanonicalSongCore] domain instance.
         */
        fun fromSong(song: Song): CanonicalSongCore = CanonicalSongCore(
            id = song.id,
            uri = song.uri,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumArtist = song.albumArtist,
            sortTitle = song.sortTitle.ifBlank { LibraryScanner.generateSortTitle(song.title) },
            sortArtist = LibraryScanner.generateSortTitle(song.artist),
            sortAlbum = LibraryScanner.generateSortTitle(song.album),
            durationMs = song.durationMs,
            trackNumber = song.trackNumber,
            discNumber = song.discNumber,
            year = song.year,
            genre = song.genre,
            bitrate = song.bitrate,
            sampleRate = song.sampleRate,
            bitDepth = song.bitDepth,
            channels = song.channels,
            codec = song.codec,
            container = song.fileExtension,
            mimeType = song.mimeType,
            fileSize = song.fileSize,
            dateAdded = song.dateAdded,
            dateModified = song.dateModified,
            hasEmbeddedArtwork = !song.albumArtUri.isNullOrBlank() || song.artworkWidth > 0,
            isExcludedFromLibrary = song.isExcludedFromLibrary,
            replayGain = ReplayGainData(
                trackGain = song.replayGainTrack,
                albumGain = song.replayGainAlbum
            )
        )
    }
}

/**
 * Secondary metadata block consulted on-demand for track info, tag editing,
 * classical music credits and audiophile cataloging.
 *
 * Scope Note (P2-7.1B.1):
 * Domain contract and in-memory tag representation extracted from [RawMetadataPackage]
 * or projected from [Song].
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
) {
    companion object {
        private const val MAX_TAG_VALUE_LENGTH = 2048

        fun fromRawPackage(songId: Long, pkg: RawMetadataPackage): ExtendedMetadataBlock {
            val props = pkg.properties
            fun firstVal(vararg keys: String): String? {
                for (k in keys) {
                    val v = props[k]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    if (v != null) return v.take(MAX_TAG_VALUE_LENGTH)
                }
                return null
            }
            fun listVal(vararg keys: String): List<String> {
                val result = mutableListOf<String>()
                for (k in keys) {
                    props[k]?.forEach { item ->
                        item.split(';', '/').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                            val bounded = it.take(MAX_TAG_VALUE_LENGTH)
                            if (bounded !in result) result.add(bounded)
                        }
                    }
                }
                return result
            }
            val normalizedRaw = props.mapValues { (_, values) ->
                values.map { it.take(MAX_TAG_VALUE_LENGTH) }
            }
            return ExtendedMetadataBlock(
                songId = songId,
                composers = listVal("COMPOSER", "TCOM", "©wrt"),
                lyricists = listVal("LYRICIST", "TEXT"),
                conductors = listVal("CONDUCTOR", "TPE3"),
                performers = listVal("PERFORMER", "ARTIST", "TPE1"),
                remixers = listVal("REMIXER", "TPE4"),
                totalTracks = firstVal("TRACKTOTAL", "TOTALTRACKS")?.toIntOrNull(),
                totalDiscs = firstVal("DISCTOTAL", "TOTALDISCS")?.toIntOrNull(),
                releaseDate = firstVal("DATE", "TDAT", "TDRC", "YEAR"),
                originalDate = firstVal("ORIGINALDATE", "TDOR"),
                genres = listVal("GENRE", "TCON"),
                mood = firstVal("MOOD", "TMOO"),
                isrc = firstVal("ISRC", "TSRC"),
                barcode = firstVal("BARCODE", "UPC"),
                catalogNumber = firstVal("CATALOGNUMBER"),
                copyright = firstVal("COPYRIGHT", "TCOP"),
                label = firstVal("LABEL", "ORGANIZATION", "TPUB"),
                publisher = firstVal("PUBLISHER", "TPUB"),
                comment = firstVal("COMMENT", "COMM"),
                musicBrainzTrackId = firstVal("MUSICBRAINZ_TRACKID"),
                musicBrainzReleaseId = firstVal("MUSICBRAINZ_ALBUMID"),
                musicBrainzArtistId = firstVal("MUSICBRAINZ_ARTISTID"),
                musicBrainzReleaseGroupId = firstVal("MUSICBRAINZ_RELEASEGROUPID"),
                rawTags = normalizedRaw
            )
        }
    }
}

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
 *
 * Scope Note (P2-7.1B.1):
 * Domain contract constructed from [RawMetadataPackage] or external LRC discovery.
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

    companion object {
        fun fromRawPackage(songId: Long, pkg: RawMetadataPackage): SongLyricsBlock {
            val text = pkg.rawLyricsText?.takeIf { it.isNotBlank() }
                ?: pkg.properties["LYRICS"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: pkg.properties["USLT"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: pkg.properties["SYLT"]?.firstOrNull()?.takeIf { it.isNotBlank() }
            if (text == null) return SongLyricsBlock(songId = songId)

            val looksSynced = pkg.isLyricsSynced || Regex("""\[\d{2}:\d{2}([.:]\d{2,3})?]""").containsMatchIn(text)
            return if (looksSynced) {
                SongLyricsBlock(
                    songId = songId,
                    hasSynced = true,
                    syncedLyricsLrc = text,
                    sourceType = LyricsSourceType.EMBEDDED_SYNC
                )
            } else {
                SongLyricsBlock(
                    songId = songId,
                    hasSynced = false,
                    unsyncedLyrics = text,
                    sourceType = LyricsSourceType.EMBEDDED_UNSYNC
                )
            }
        }
    }
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
 * Content-addressed artwork descriptor contract.
 *
 * Scope Note (P2-7.1B.1):
 * Provides deterministic SHA-256 content hashing ([computeContentHash]) and metadata representation
 * for embedded or MediaStore artwork without requiring a new Room table in schema v17.
 * [legacyUri] stores legacy MediaStore `content://` URIs for compatibility with Room v17 [Song.albumArtUri].
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
) {
    companion object {
        /**
         * Computes a lowercase hex SHA-256 hash for binary artwork bytes or URI fallback keys.
         */
        fun computeContentHash(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            return sb.toString()
        }

        fun fromRawPackage(songId: Long, pkg: RawMetadataPackage, fallbackLegacyUri: String? = null): SongArtworkDescriptor? {
            val bytes = pkg.rawArtworkBytes
            if (bytes != null && bytes.isNotEmpty()) {
                val hash = computeContentHash(bytes)
                return SongArtworkDescriptor(
                    songId = songId,
                    artworkHash = hash,
                    durableRelativePath = "media_art/$hash.bin",
                    legacyUri = fallbackLegacyUri,
                    mimeType = pkg.rawArtworkMime ?: "image/jpeg",
                    artworkType = pkg.rawArtworkType,
                    sourceType = ArtworkSourceType.EMBEDDED
                )
            }
            if (!fallbackLegacyUri.isNullOrBlank()) {
                return SongArtworkDescriptor(
                    songId = songId,
                    artworkHash = computeContentHash(fallbackLegacyUri.toByteArray(Charsets.UTF_8)),
                    legacyUri = fallbackLegacyUri,
                    sourceType = ArtworkSourceType.MEDIA_STORE_LEGACY
                )
            }
            return null
        }
    }
}
