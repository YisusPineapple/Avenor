package io.github.yisus.nexo

import android.util.Log

object EqPresetValidator {
    private const val TAG = "EqPresetValidator"
    
    // Fallback 'Flat' preset data
    private const val FLAT_BANDS = "0.0,0.0,0.0,0.0,0.0" // Assuming 5 bands for standard Android Equalizer
    
    val predefinedPresets = listOf(
        EqPreset(name = "Flat", bands = FLAT_BANDS),
        EqPreset(name = "Classical", bands = "5.0,3.0,-2.0,4.0,5.0"),
        EqPreset(name = "Jazz", bands = "4.0,2.0,-2.0,2.0,5.0"),
        EqPreset(name = "Live", bands = "-2.0,0.0,2.0,3.0,3.0"),
        EqPreset(name = "Club", bands = "6.0,4.0,2.0,-1.0,-2.0"),
        EqPreset(name = "Bass Extreme", bands = "10.0,8.0,0.0,-2.0,-4.0"),
        EqPreset(name = "Rock", bands = "5.0,3.0,-1.0,3.0,5.0"),
        EqPreset(name = "Pop", bands = "-1.0,2.0,5.0,1.0,-2.0"),
        EqPreset(name = "Vocal Booster", bands = "-2.0,0.0,4.0,2.0,-1.0"),
        EqPreset(name = "Electronic", bands = "4.0,2.0,-1.0,2.0,5.0"),
        EqPreset(name = "Dance", bands = "6.0,0.0,2.0,4.0,1.0"),
        EqPreset(name = "Acoustic", bands = "2.0,1.0,0.0,2.0,3.0"),
        EqPreset(name = "R&B", bands = "3.0,5.0,-1.0,2.0,3.0"),
        EqPreset(name = "Latin", bands = "4.0,2.0,0.0,2.0,4.0"),
        EqPreset(name = "Treble Boost", bands = "0.0,0.0,0.0,5.0,8.0"),
        EqPreset(name = "Lo-Fi", bands = "2.0,-2.0,-4.0,-2.0,1.0")
    )

    fun validateAndParse(preset: EqPreset, expectedBandCount: Int = 5): List<Float> {
        return try {
            val parsed = preset.bands.split(",").map { it.toFloat() }
            if (parsed.size != expectedBandCount) {
                Log.w(TAG, "Corrupted EQ preset '${preset.name}': expected $expectedBandCount bands, found ${parsed.size}. Resetting to Flat.")
                throw IllegalArgumentException("Band count mismatch")
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse EQ preset '${preset.name}'. Data: ${preset.bands}. Resetting to Flat.", e)
            List(expectedBandCount) { 0.0f }
        }
    }
}
