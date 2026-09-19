package io.github.yisus.avenor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import io.github.yisus.avenor.ui.components.DesktopInputWrapper
import io.github.yisus.avenor.ui.navigation.AvenorAppRoot
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    lateinit var performanceMonitor: PerformanceMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        performanceMonitor = PerformanceMonitor(this)
        lifecycleScope.launch {
            performanceMonitor.startMonitoring()
        }

        // Strictly enforce ultra-lightweight memory profile using Coil ImageLoader (<120MB target)
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10) // Restrict memory cache strictly to 10%
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("avenor_image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()

        // Schedule weekly backup
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeeklyBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
        
        val trashRequest = PeriodicWorkRequestBuilder<SmartTrashWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyTrashPurge",
            ExistingPeriodicWorkPolicy.KEEP,
            trashRequest
        )

        setContent {
            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                DesktopInputWrapper(viewModel = viewModel()) {
                    AvenorAppRoot()
                }
            }
        }
    }
}
