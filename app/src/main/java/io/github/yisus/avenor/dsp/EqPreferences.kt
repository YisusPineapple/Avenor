package io.github.yisus.avenor.dsp

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistence for active Equalizer settings.
 *
 * Saves and restores:
 * - EQ Enabled status
 * - Current 5-band gains in dB
 * - Current Preset name
 *
 * Storage: SharedPreferences ("avenor_eq_preferences")
 * Operates purely outside the audio hot path with non-blocking apply().
 */
class EqPreferences(context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_EQ_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
    }

    fun getBands(): FloatArray {
        val str = prefs.getString(KEY_EQ_BANDS, null) ?: return FloatArray(EqBandSettings.BAND_COUNT) { 0.0f }
        return EqPresetDefinitions.parseBands(str, EqBandSettings.BAND_COUNT)
    }

    fun setBands(bands: FloatArray) {
        val clamped = FloatArray(EqBandSettings.BAND_COUNT) { i ->
            if (i < bands.size) bands[i].coerceIn(-12.0f, 12.0f) else 0.0f
        }
        val str = clamped.joinToString(",")
        prefs.edit().putString(KEY_EQ_BANDS, str).apply()
    }

    fun getCurrentPresetName(): String = prefs.getString(KEY_CURRENT_PRESET_NAME, "Flat") ?: "Flat"

    fun setCurrentPresetName(name: String) {
        prefs.edit().putString(KEY_CURRENT_PRESET_NAME, name).apply()
    }

    companion object {
        const val PREFS_NAME = "avenor_eq_preferences"
        const val KEY_EQ_ENABLED = "KEY_EQ_ENABLED"
        const val KEY_EQ_BANDS = "KEY_EQ_BANDS"
        const val KEY_CURRENT_PRESET_NAME = "KEY_CURRENT_PRESET_NAME"
    }
}
