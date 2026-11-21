package com.kongye.reader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal val scrollContentWidth = 640.dp

/** Keep the layout scale stable when the keyboard opens or system bars become visible. */
@Composable
internal fun adaptiveDensity(): Density {
    val system = LocalDensity.current
    val bounds = LocalWindowInfo.current.containerSize
    val shortEdge = min(bounds.width, bounds.height)
    if (shortEdge <= 0) return system

    val density = (shortEdge / 432f).coerceIn(system.density * 0.84f, system.density)
    return if (density == system.density) system else Density(density, system.fontScale)
}

/** The same window bounds expressed in the app's layout units. */
internal val windowSize: DpSize
    @Composable @ReadOnlyComposable
    get() = with(LocalDensity.current) {
        val bounds = LocalWindowInfo.current.containerSize
        DpSize(bounds.width.toDp(), bounds.height.toDp())
    }
