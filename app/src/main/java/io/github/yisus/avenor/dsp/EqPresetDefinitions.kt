package io.github.yisus.avenor.dsp

import io.github.yisus.avenor.EqPreset

/**
 * Single source of truth for all Equalizer Preset definitions in Avenor.
 *
 * All presets define standard 5-band gains in decibels (-12 dB to +12 dB)
 * for bands: 60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz.
 */
object EqPresetDefinitions {
    const val FLAT_BANDS = "0.0,0.0,0.0,0.0,0.0"

    val DEFAULT_PRESETS: List<EqPreset> = listOf(
        EqPreset(id = -1, name = "Flat", bands = FLAT_BANDS),
        EqPreset(id = -2, name = "Classical", bands = "5.0,3.0,-2.0,4.0,5.0"),
        EqPreset(id = -3, name = "Jazz", bands = "4.0,2.0,-2.0,2.0,5.0"),
        EqPreset(id = -4, name = "Live", bands = "-2.0,0.0,2.0,3.0,3.0"),
        EqPreset(id = -5, name = "Club", bands = "6.0,4.0,2.0,-1.0,-2.0"),
        EqPreset(id = -6, name = "Bass Extreme", bands = "10.0,8.0,0.0,-2.0,-4.0"),
        EqPreset(id = -7, name = "Rock", bands = "5.0,3.0,-1.0,3.0,5.0"),
        EqPreset(id = -8, name = "Pop", bands = "-1.0,2.0,5.0,1.0,-2.0"),
        EqPreset(id = -9, name = "Vocal Booster", bands = "-2.0,0.0,4.0,2.0,-1.0"),
        EqPreset(id = -10, name = "Electronic", bands = "4.0,2.0,-1.0,2.0,5.0"),
        EqPreset(id = -11, name = "Dance", bands = "6.0,0.0,2.0,4.0,1.0"),
        EqPreset(id = -12, name = "Acoustic", bands = "2.0,1.0,0.0,2.0,3.0"),
        EqPreset(id = -13, name = "R&B", bands = "3.0,5.0,-1.0,2.0,3.0"),
        EqPreset(id = -14, name = "Latin", bands = "4.0,2.0,0.0,2.0,4.0"),
        EqPreset(id = -15, name = "Treble Boost", bands = "0.0,0.0,0.0,5.0,8.0"),
        EqPreset(id = -16, name = "Lo-Fi", bands = "2.0,-2.0,-4.0,-2.0,1.0")
    )

    fun getByName(name: String): EqPreset? {
        return DEFAULT_PRESETS.find { it.name.equals(name, ignoreCase = true) }
    }

    fun parseBands(bandsStr: String, expectedCount: Int = 5): FloatArray {
        return try {
            val parts = bandsStr.split(",").map { it.trim().toFloat() }
            if (parts.size == expectedCount) {
                FloatArray(expectedCount) { parts[it].coerceIn(-12.0f, 12.0f) }
            } else {
                FloatArray(expectedCount) { 0.0f }
            }
        } catch (e: Exception) {
            FloatArray(expectedCount) { 0.0f }
        }
    }
}
