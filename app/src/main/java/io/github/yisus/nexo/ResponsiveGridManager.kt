package io.github.yisus.nexo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSizeClass {
    COMPACT, MEDIUM, EXPANDED
}

object ResponsiveGridManager {

    @Composable
    fun getWindowSizeClass(screenWidthDp: Dp): WindowSizeClass {
        return when {
            screenWidthDp < 600.dp -> WindowSizeClass.COMPACT
            screenWidthDp < 840.dp -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }
    }

    fun getGridCells(windowSizeClass: WindowSizeClass): Int {
        return when (windowSizeClass) {
            WindowSizeClass.COMPACT -> 2
            WindowSizeClass.MEDIUM -> 3
            WindowSizeClass.EXPANDED -> 4
        }
    }

    fun getPadding(windowSizeClass: WindowSizeClass): PaddingValues {
        return when (windowSizeClass) {
            WindowSizeClass.COMPACT -> PaddingValues(8.dp)
            WindowSizeClass.MEDIUM -> PaddingValues(16.dp)
            WindowSizeClass.EXPANDED -> PaddingValues(24.dp)
        }
    }
}
