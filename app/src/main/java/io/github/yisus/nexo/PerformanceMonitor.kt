package io.github.yisus.nexo

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile

class PerformanceMonitor(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    private val _isHighLoad = MutableStateFlow(false)
    val isHighLoad: StateFlow<Boolean> = _isHighLoad.asStateFlow()

    private val _memoryUsagePercent = MutableStateFlow(0f)
    val memoryUsagePercent: StateFlow<Float> = _memoryUsagePercent.asStateFlow()

    suspend fun startMonitoring() {
        while (true) {
            checkPerformance()
            delay(5000) // Poll every 5 seconds
        }
    }

    private suspend fun checkPerformance() {
        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val totalMem = memoryInfo.totalMem
            val availMem = memoryInfo.availMem
            val usedMem = totalMem - availMem
            val memPercent = (usedMem.toFloat() / totalMem.toFloat()) * 100f
            
            _memoryUsagePercent.value = memPercent

            // CPU Usage check via /proc/stat
            var cpuUsage = 0f
            try {
                val reader = RandomAccessFile("/proc/stat", "r")
                val load = reader.readLine()
                val toks = load.split(" +".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val idle1 = toks[4].toLong()
                val cpu1 = toks[1].toLong() + toks[2].toLong() + toks[3].toLong() + toks[5].toLong() + toks[6].toLong() + toks[7].toLong() + toks[8].toLong()
                
                delay(360)
                
                reader.seek(0)
                val load2 = reader.readLine()
                val toks2 = load2.split(" +".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val idle2 = toks2[4].toLong()
                val cpu2 = toks2[1].toLong() + toks2[2].toLong() + toks2[3].toLong() + toks2[5].toLong() + toks2[6].toLong() + toks2[7].toLong() + toks2[8].toLong()
                
                val cpuDiff = (cpu2 - cpu1).toFloat()
                val idleDiff = (idle2 - idle1).toFloat()
                cpuUsage = if ((cpuDiff + idleDiff) > 0) cpuDiff / (cpuDiff + idleDiff) * 100f else 0f
                reader.close()
            } catch (e: Exception) {
                // Ignore, as /proc/stat is restricted on newer Android versions
                cpuUsage = 0f
            }

            // High load is triggered if RAM > 85% or CPU > 90% (if readable), or system explicitly says lowMemory
            val highLoad = memoryInfo.lowMemory || memPercent > 85f || cpuUsage > 90f
            _isHighLoad.value = highLoad

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
