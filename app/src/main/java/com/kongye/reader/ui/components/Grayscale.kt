package com.kongye.reader.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/** 没解锁的东西：去色、变淡（商城、装扮、成就徽章） */
fun Modifier.grayscale(on: Boolean, alpha: Float = 0.45f): Modifier = if (!on) this else drawWithCache {
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        this.alpha = alpha
    }
    onDrawWithContent {
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), paint) }
        drawContent()
        drawIntoCanvas { it.restore() }
    }
}
