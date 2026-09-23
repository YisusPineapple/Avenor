package io.github.yisus.avenor.replaygain

/**
 * Encapsulates extracted ReplayGain metadata for an audio track or album.
 *
 * Units & Semantics:
 * - [trackGain]: Perceived track loudness adjustment in decibels (dB) relative to calibration target (89 dB SPL / -18 LUFS).
 * - [albumGain]: Perceived album loudness adjustment in decibels (dB) to preserve intra-album dynamic relationships.
 * - [trackPeak]: Dimensionless sample peak amplitude ratio relative to digital full-scale (1.0 = 0 dBFS).
 * - [albumPeak]: Dimensionless maximum album sample peak amplitude ratio.
 * - null represents missing or invalid metadata. Never conflate null with 0.0 dB (which is explicit unity gain).
 */
data class ReplayGainData(
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null
) {
    val hasGain: Boolean get() = trackGain != null || albumGain != null
    val hasPeak: Boolean get() = trackPeak != null || albumPeak != null
}

/**
 * User-configurable ReplayGain operating modes.
 */
enum class ReplayGainMode {
    /** ReplayGain is disabled. Effective gain is strictly 0.0 dB (unity gain 1.0). */
    OFF,

    /** Prioritizes per-track loudness equalization for consistent volume across diverse tracks. */
    TRACK,

    /** Prioritizes per-album loudness equalization to preserve intentional artistic volume contrasts. */
    ALBUM
}
