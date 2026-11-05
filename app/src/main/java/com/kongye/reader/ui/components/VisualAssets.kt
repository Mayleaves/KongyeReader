package com.kongye.reader.ui.components

import android.graphics.Typeface
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/** Seconds from the first displayed frame. The frame clock pauses with the window. */
@Composable
internal fun assetTime(stopAt: Float = Float.POSITIVE_INFINITY): Float {
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (elapsed < stopAt) {
            withFrameNanos { elapsed = ((it - start) / 1_000_000_000f).coerceAtMost(stopAt) }
        }
    }
    return elapsed
}

/** The supplied 240×260 homepage drawing, with its original animated parts. */
@Composable
fun DashboardCat(
    modifier: Modifier = Modifier, size: Dp = 210.dp, petSignal: Int = 0,
    leaf: Boolean = false, gaze: Offset = Offset.Zero,
    sleeping: Boolean = false,
) {
    val dark = K.c.dark
    val time = assetTime()
    var happy by remember { mutableStateOf(false) }
    LaunchedEffect(petSignal) {
        if (petSignal > 0) {
            happy = true
            delay(3200)
            happy = false
        }
    }
    LaunchedEffect(sleeping) { if (sleeping) happy = false }
    Canvas(modifier.size(size).semantics {
        contentDescription = if (sleeping) "小憩黑猫，轻触唤醒" else "空叶黑猫，轻触摸摸头"
    }) {
        // Crop only the workbench's empty outer margin; keep the existing home layout.
        val s = this.size.minDimension / 210f
        withTransform({
            translate((this@Canvas.size.width - 240f * s) / 2f, -35f * s)
            scale(s, s, pivot = Offset.Zero)
        }) {
            when {
                sleeping -> drawImportedSleep(dark, time)
                happy -> drawImportedHappy(dark, time)
                // Use the latest pointer position directly; no tween trails the finger.
                else -> drawImportedFocus(dark, time, leaf, gaze)
            }
        }
    }
}

/** Increase travel with finger distance, approaching the eye-white limit smoothly. */
internal fun catGazeAt(pointer: Offset?, eyeCenter: Offset, radius: Float): Offset {
    if (pointer == null || radius <= 0f) return Offset.Zero
    val delta = pointer - eyeCenter
    return delta / (radius + delta.getDistance())
}

/** Full-screen cold-start animation. SVG coordinates/timing come from the workbench. */
@Composable
fun ReadingSplash(onFinished: () -> Unit, onSkip: () -> Unit) {
    val dark = K.c.dark
    val bg = if (dark) Color(0xFF121214) else Color.White
    val time = assetTime(2.65f)
    LaunchedEffect(time >= 2.65f) { if (time >= 2.65f) onFinished() }
    Canvas(Modifier.fillMaxSize().background(bg)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = "跳过开屏动画",
            onClick = onSkip,
        )
        .semantics { contentDescription = "空叶阅读启动动画，轻触跳过" },
    ) {
        val s = min(size.width / 375f, size.height / 812f)
        withTransform({
            translate((size.width - 375f * s) / 2f, (size.height - 812f * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
        }) { drawImportedSplash(dark, time) }
    }
}

private val settle = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val bouncy = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

internal fun splashProgress(time: Float, delay: Float, duration: Float): Float =
    ((time - delay) / duration).coerceIn(0f, 1f)

private fun wave(time: Float, period: Float): Float =
    ((1.0 - cos(2.0 * PI * time / period)) / 2.0).toFloat()

private fun keys(time: Float, period: Float, vararg points: Pair<Float, Float>): Float {
    val phase = ((time % period) / period).coerceAtLeast(0f)
    for (i in 1 until points.size) {
        val (a, av) = points[i - 1]
        val (b, bv) = points[i]
        if (phase <= b) return av + (bv - av) * ((phase - a) / (b - a)).coerceIn(0f, 1f)
    }
    return points.last().second
}

/** Apply the HTML's CSS group animations in native canvas coordinates. */
internal fun DrawScope.assetGroup(name: String, time: Float, block: DrawScope.() -> Unit) {
    var alpha = 1f
    var tx = 0f; var ty = 0f; var sx = 1f; var sy = 1f; var angle = 0f
    var pivot = Offset.Zero
    when (name) {
        "svg-cat-breathe" -> {
            val p = wave(time, 3.8f); sy += 0.018f * p; ty = -1.8f * p; pivot = Offset(120f, 232f)
        }
        "svg-loaf-breathe" -> {
            val p = wave(time, 4f); sy += 0.022f * p; ty = -1.2f * p; pivot = Offset(132f, 231f)
        }
        "svg-cat-tail", "svg-cat-tail-sleep" -> {
            val sleep = name.endsWith("sleep")
            angle = wave(time, if (sleep) 4.5f else 3.1f) * if (sleep) 2.2f else 11f
            pivot = if (sleep) Offset(150f, 220f) else Offset(154f, 218f)
        }
        "svg-ear-l", "splash-ear-l" -> {
            angle = keys((time - if (name.startsWith("splash")) 2f else 0f).coerceAtLeast(0f), 6.5f,
                0f to 0f, 0.84f to 0f, 0.88f to -4.5f, 0.93f to 1.5f, 1f to 0f)
            pivot = Offset(96f, 104f)
        }
        "svg-ear-r", "splash-ear-r" -> {
            angle = keys((time - if (name.startsWith("splash")) 2f else 0f).coerceAtLeast(0f), 7.8f,
                0f to 0f, 0.60f to 0f, 0.64f to 4.5f, 0.68f to 0f, 1f to 0f)
            pivot = Offset(144f, 104f)
        }
        "svg-head-leaf", "head-sprout-float" -> {
            val p = wave((time - if (name == "head-sprout-float") 2f else 0f).coerceAtLeast(0f), 3.4f)
            angle = -5f + 12f * p; ty = -2.5f * p; pivot = Offset(120f, 76f)
        }
        "svg-eye-blink" -> {
            sy = keys(time, 5.4f, 0f to 1f, 0.45f to 1f, 0.47f to 0.06f, 0.49f to 1f,
                0.94f to 1f, 0.96f to 0.06f, 0.98f to 1f, 1f to 1f)
            pivot = Offset(120f, 125f)
        }
        "splash-eyes" -> {
            sy = keys((time - 1.85f).coerceAtLeast(0f), 2.8f,
                0f to 1f, 0.16f to 1f, 0.20f to 0.08f, 0.24f to 1f, 1f to 1f)
            pivot = Offset(120f, 126f)
        }
        "svg-cheek-pulse" -> {
            val p = wave(time, 1.8f); sx = 1f + 0.12f * p; sy = sx; alpha = 0.72f + 0.23f * p; pivot = Offset(120f, 134f)
        }
        "svg-zzz-1", "svg-zzz-2" -> {
            val second = name == "svg-zzz-2"
            val p = (time % 2.8f) / 2.8f
            alpha = if (second) keys(time, 2.8f, 0f to 0f, 0.22f to 0f, 0.60f to 0.9f, 1f to 0f)
                else keys(time, 2.8f, 0f to 0f, 0.35f to 0.92f, 1f to 0f)
            tx = (if (second) 22f else 14f) * p
            ty = 4f - (if (second) 38f else 26f) * p
            sx = (if (second) 0.55f else 0.65f) + (if (second) 0.65f else 0.45f) * p
            sy = sx
            pivot = if (second) Offset(176f, 75f) else Offset(163f, 96f)
        }
        "hero-right-leaf" -> {
            val p = settle.transform(splashProgress(time, 0f, 1.25f))
            // CSS's intermediate pose at 42%, followed by settling into the right page.
            val q = if (p < 0.42f) p / 0.42f else (p - 0.42f) / 0.58f
            if (p < 0.42f) { tx = -18f + 4f * q; ty = -72f + 8f * q; angle = -54f + 6f * q; sx = 1.06f }
            else { tx = -14f * (1f-q); ty = -64f * (1f-q); angle = -48f * (1f-q); sx = 1.06f - 0.06f*q }
            sy = sx; pivot = Offset(187.5f, 386f)
        }
        "left-solid-leaf" -> {
            val p = splashProgress(time, 0.98f, 0.65f); val q = bouncy.transform(p)
            alpha = p; sx = 0.25f + 0.75f*q; sy = sx; angle = 32f*(1f-q); pivot = Offset(187.5f, 386f)
        }
        "book-base-mask" -> alpha = splashProgress(time, 1.05f, 0.3f)
        "splash-cat" -> {
            val p = settle.transform(splashProgress(time, 1.08f, 0.8f))
            alpha = p; ty = 38f*(1f-p); sx = 0.9f+0.1f*p; sy = sx; pivot = Offset(187.5f, 355f)
        }
        "cat-paws" -> {
            val p = splashProgress(time, 1.32f, 0.5f); val q = bouncy.transform(p)
            alpha = p; ty = -8f*(1f-q); sx = 0.7f+0.3f*q; sy = sx; pivot = Offset(187.5f, 355f)
        }
        "head-sprout" -> {
            val p = splashProgress(time, 1.45f, 0.55f); val q = bouncy.transform(p)
            alpha = p; sx = q; sy = q; angle = -25f*(1f-q); pivot = Offset(120f, 79f)
        }
        "brand-title", "brand-sub" -> {
            val title = name == "brand-title"
            val p = settle.transform(splashProgress(time, if (title) 1.4f else 1.58f, 0.75f))
            alpha = p; ty = (if (title) 12f else 10f)*(1f-p)
        }
        else -> if (name.startsWith("svg-heart-")) {
            val index = name.last().digitToInt() - 1
            val delays = floatArrayOf(0f, 0.5f, 1f, 0.3f, 0.8f)
            val p = wave((time-delays[index]).coerceAtLeast(0f), 2.4f)
            pivot = arrayOf(Offset(52f,62f), Offset(61f,86f), Offset(63f,152f), Offset(177f,83f), Offset(179f,107f))[index]
            ty = 3f-9f*p; sx = 0.86f+0.22f*p; sy = sx; alpha = 0.12f+0.88f*p
        }
    }
    if (alpha <= 0f) return
    val layered = alpha < 1f
    if (layered) drawContext.canvas.saveLayer(Rect(-1000f, -1000f, 2000f, 2000f), Paint().apply { this.alpha = alpha.coerceIn(0f,1f) })
    try {
        withTransform({ translate(tx, ty); rotate(angle, pivot); scale(sx, sy, pivot) }, block)
    } finally {
        if (layered) drawContext.canvas.restore()
    }
}

internal fun DrawScope.assetStroke(path: Path, color: Color, width: Float, cap: StrokeCap, join: StrokeJoin, alpha: Float, reveal: Float) {
    if (reveal <= 0f) return
    val visible = if (reveal >= 1f) path else Path().also {
        val measure = PathMeasure().apply { setPath(path, false) }
        measure.getSegment(0f, measure.length * reveal, it, true)
    }
    drawPath(visible, color, alpha = alpha, style = Stroke(width, cap = cap, join = join))
}

private val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

internal fun DrawScope.assetText(text: String, x: Float, y: Float, size: Float, color: Color, bold: Boolean, centered: Boolean, spacing: Float) {
    labelPaint.apply {
        this.color = color.toArgb(); textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    val width = labelPaint.measureText(text) + spacing*(text.length-1)
    var left = x - if (centered) width/2 else 0f
    for (char in text) {
        val value = char.toString()
        drawContext.canvas.nativeCanvas.drawText(value, left, y, labelPaint)
        left += labelPaint.measureText(value) + spacing
    }
}

/** The visible ear tips of all three SVGs are at this Y, below their viewBox top. */
private const val BAR_EAR_TIP = 14.58456f

/** Stretch the body while preserving the face; gray ear tips meet the average line. */
fun DrawScope.drawReadingCatBar(
    x: Float, top: Float, base: Float, width: Float, sleeping: Boolean, leaf: Boolean,
    grayEarY: Float? = null,
) {
    // If the average is close to zero, shrink the whole gray cat enough to fit its
    // face below the line instead of clipping it or letting the ears cross it.
    val s = if (sleeping && grayEarY != null) {
        min(width / 112.89f, (base - grayEarY).coerceAtLeast(0f) / (94f - BAR_EAR_TIP))
    } else width / 112.89f
    if (s <= 0f) return
    val height = if (sleeping && grayEarY != null) (base - grayEarY) / s + BAR_EAR_TIP
        else ((base-top)/s).coerceAtLeast(94f)
    val body = svgPath("M4,$height L13.35,18.47 c.64-5,4.46-5,7.65-1 l15.3,20 h35.69 l15.3-20 c3.19-4,7.01-4,7.65,1 L108.9,$height H4Z")
    withTransform({ translate(x + (width - 112.89f*s) / 2f, base-height*s); scale(s,s,pivot=Offset.Zero) }) {
        when {
            sleeping -> drawImportedBarGray(body)
            leaf -> drawImportedBarLeaf(body)
            else -> drawImportedBarBlack(body)
        }
    }
}
