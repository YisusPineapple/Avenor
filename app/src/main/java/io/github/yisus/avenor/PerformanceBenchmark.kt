package io.github.yisus.avenor

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

object PerformanceBenchmark {
    private const val TAG = "PerformanceBenchmark"

    /**
     * Evaluates the device's CPU and RAM to determine the optimal performance tier.
     * Returns "ECO", "BALANCED", or "VIVID".
     */
    fun evaluateDeviceTier(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val numCores = Runtime.getRuntime().availableProcessors()
        val isEmulatorOrPC = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu") || Build.MANUFACTURER.contains("Genymotion")

        Log.i(TAG, "Hardware Benchmark - RAM: ${String.format("%.2f", totalRamGb)} GB, Cores: $numCores, PC/Emulator: $isEmulatorOrPC")

        return when {
            // Force VIVID on PC/Emulators to ensure highest visual fidelity for testing/desktop usage
            isEmulatorOrPC || (totalRamGb >= 3.0 && numCores >= 4) -> {
                Log.i(TAG, "Applied Tier: VIVID (High-end hardware or PC detected)")
                "VIVID"
            }
            // Mid-range devices
            totalRamGb >= 2.0 && numCores >= 2 -> {
                Log.i(TAG, "Applied Tier: BALANCED (Mid-range hardware detected)")
                "BALANCED"
            }
            // Low-end devices
            else -> {
                Log.i(TAG, "Applied Tier: ECO (Low-end hardware detected)")
                "ECO"
            }
        }
    }
}
