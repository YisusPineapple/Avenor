package io.github.yisus.avenor.replaygain

import kotlin.math.log10
import kotlin.math.pow

/**
 * Pure policy and mathematical operations for ReplayGain gain calculation.
 *
 * Invariants:
 * - When [ReplayGainMode.OFF], effective gain in dB is strictly 0.0 dB (linear factor 1.0f).
 * - Preamp values are clamped between [MIN_PREAMP_DB] and [MAX_PREAMP_DB].
 * - Preamp is only applied when ReplayGain mode is active ([ReplayGainMode.TRACK] or [ReplayGainMode.ALBUM]).
 * - Conversion formula: linearGain = 10^(gainDb / 20).
 */
object ReplayGainPolicy {

    const val MIN_PREAMP_DB = -15.0f
    const val MAX_PREAMP_DB = 15.0f
    const val DEFAULT_PREAMP_DB = 0.0f

    /**
     * Calculates the effective gain in decibels (dB) for a given track under the active mode and preamp.
     *
     * Fallback Strategy:
     * - In [ReplayGainMode.TRACK] mode: Prioritizes [trackGain]. If missing, falls back to [albumGain]
     *   as the nearest acoustic approximation, or 0.0 dB if neither exists.
     * - In [ReplayGainMode.ALBUM] mode: Prioritizes [albumGain] to preserve album dynamics. If missing,
     *   falls back to [trackGain] to prevent jarring loudness spikes, or 0.0 dB if neither exists.
     * - In [ReplayGainMode.OFF] mode: Returns strictly 0.0 dB.
     */
    fun calculateEffectiveGainDb(
        mode: ReplayGainMode,
        trackGain: Float?,
        albumGain: Float?,
        preampDb: Float = DEFAULT_PREAMP_DB
    ): Float {
        if (mode == ReplayGainMode.OFF) {
            return 0.0f
        }

        val clampedPreamp = preampDb.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB)

        val baseGain = when (mode) {
            ReplayGainMode.TRACK -> trackGain ?: albumGain ?: 0.0f
            ReplayGainMode.ALBUM -> albumGain ?: trackGain ?: 0.0f
            ReplayGainMode.OFF -> 0.0f
        }

        return baseGain + clampedPreamp
    }

    /**
     * Converts a gain value in decibels (dB) to an amplitude multiplication factor.
     *
     * Formula: linearGain = 10^(gainDb / 20)
     */
    fun dbToLinearGain(gainDb: Float): Float {
        if (gainDb.isNaN()) return 1.0f
        return (10.0.pow(gainDb.toDouble() / 20.0)).toFloat()
    }

    /**
     * Converts an amplitude multiplication factor to decibels (dB).
     *
     * Formula: gainDb = 20 * log10(linearGain)
     */
    fun linearToDb(linearGain: Float): Float {
        if (linearGain <= 0.0f || linearGain.isNaN()) return -100.0f
        return (20.0 * log10(linearGain.toDouble())).toFloat()
    }

    /**
     * Calculates the maximum safe gain (in dB) that can be applied without digital clipping
     * given the peak amplitude ratio.
     *
     * If peak <= 0 or null, returns null (cannot be determined).
     */
    fun calculateMaxGainWithoutClippingDb(peak: Float?): Float? {
        if (peak == null || peak <= 0.0f || peak.isNaN()) return null
        return linearToDb(1.0f / peak)
    }

    /**
     * Returns true if applying [effectiveGainDb] to a track with [peak] amplitude
     * would result in peak levels exceeding digital full-scale (1.0 = 0 dBFS).
     */
    fun isClippingLikely(effectiveGainDb: Float, peak: Float?): Boolean {
        if (peak == null || peak <= 0.0f || peak.isNaN()) return false
        val linearFactor = dbToLinearGain(effectiveGainDb)
        return (peak * linearFactor) > 1.0f
    }
}
