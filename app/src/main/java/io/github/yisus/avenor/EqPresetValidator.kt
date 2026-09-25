package io.github.yisus.avenor

import android.util.Log
import io.github.yisus.avenor.dsp.EqPresetDefinitions

object EqPresetValidator {
    private const val TAG = "EqPresetValidator"

    val predefinedPresets: List<EqPreset>
        get() = EqPresetDefinitions.DEFAULT_PRESETS

    fun validateAndParse(preset: EqPreset, expectedBandCount: Int = 5): List<Float> {
        return try {
            val parsed = preset.bands.split(",").map { it.trim().toFloat() }
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
