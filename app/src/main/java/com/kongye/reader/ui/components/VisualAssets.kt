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
import com.kongye.reader.data.Shop
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

/**
 * The supplied 240×260 homepage drawing, with its original animated parts.
 * minimal：换成墨团（200x220），同样三种状态、眼睛跟手指，身上戴着装扮里给它的饰品。
 * leaf：当天读满半小时才在头顶长出红叶子。
 */
@Composable
fun DashboardCat(
    modifier: Modifier = Modifier, size: Dp = 210.dp, petSignal: Int = 0,
    leaf: Boolean = false, gaze: Offset = Offset.Zero,
    sleeping: Boolean = false,
    minimal: Boolean = false,
    /** 鎏金看板猫：和看板猫同一套骨架、同样的动画，自带金色装饰 */
    gold: Boolean = false,
    /** 鎏金墨团：和墨团同一套骨架、同样的动画，自带金色装饰 */
    goldMin: Boolean = false,
) {
    val dark = K.c.dark
    val minCat = if (goldMin) Shop.animal(Shop.GOLDMIN) else if (minimal) Shop.animal(Shop.MINCAT) else if (gold) Shop.animal(Shop.GOLDCAT) else Shop.wearer(Shop.MASCOT)
    val pieces = if (minCat != null) outfitPieces(minCat) else emptyList()
    val showGround = pieces.none { it.slot == "H" }
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
        if (minimal || goldMin) {
            val (k, ox, oy) = minimalFrame()
            if (showGround) withTransform({ translate(ox, oy); scale(k, k, pivot = Offset.Zero) }) { drawCatGroundShadow(if (goldMin) Shop.GOLDMIN else Shop.MINCAT, dark, sleeping) }
            if (minCat != null) drawOutfit(minCat, pieces, k, ox, oy, behind = true)
            withTransform({ translate(ox, oy); scale(k, k, pivot = Offset.Zero) }) {
                if (goldMin) when {
                    sleeping -> drawGoldMinSleep(dark, time, leaf, shadow = false)
                    happy -> drawGoldMinHappy(dark, time, leaf, shadow = false)
                    else -> drawGoldMinFocus(dark, time, leaf, gaze, shadow = false)
                } else when {
                    sleeping -> drawMinimalSleep(dark, time, leaf, shadow = false)
                    happy -> drawMinimalHappy(dark, time, leaf, shadow = false)
                    else -> drawMinimalFocus(dark, time, leaf, gaze, shadow = false)
                }
            }
            if (minCat != null) drawOutfit(minCat, pieces, k, ox, oy, behind = false)
            return@Canvas
        }
        // Crop only the workbench's empty outer margin; keep the existing home layout.
        val s = this.size.minDimension / 210f
        if (gold) {
            val ox = (this@Canvas.size.width - 240f * s) / 2f
            val oy = -35f * s
            if (showGround) withTransform({ translate(ox, oy); scale(s, s, pivot = Offset.Zero) }) { drawCatGroundShadow(Shop.GOLDCAT, dark, sleeping) }
            if (minCat != null) drawOutfit(minCat, pieces, s, ox, oy, behind = true)
            withTransform({ translate(ox, oy); scale(s, s, pivot = Offset.Zero) }) {
                when {
                    sleeping -> drawGoldSleep(dark, time, leaf, shadow = false)
                    happy -> drawGoldHappy(dark, time, leaf, shadow = false)
                    else -> drawGoldFocus(dark, time, leaf, gaze, shadow = false)
                }
            }
            if (minCat != null) drawOutfit(minCat, pieces, s, ox, oy, behind = false)
            return@Canvas
        }
        val ox = (this@Canvas.size.width - 240f * s) / 2f
        val oy = -35f * s
        if (showGround) withTransform({ translate(ox, oy); scale(s, s, pivot = Offset.Zero) }) { drawCatGroundShadow(Shop.MASCOT, dark, sleeping) }
        if (minCat != null) drawOutfit(minCat, pieces, s, ox, oy, behind = true)
        withTransform({
            translate(ox, oy)
            scale(s, s, pivot = Offset.Zero)
        }) {
            when {
                sleeping -> drawImportedSleep(dark, time, leaf, shadow = false)
                happy -> drawImportedHappy(dark, time, leaf, shadow = false)
                // Use the latest pointer position directly; no tween trails the finger.
                else -> drawImportedFocus(dark, time, leaf, gaze, shadow = false)
            }
        }
        if (minCat != null) drawOutfit(minCat, pieces, s, ox, oy, behind = false)
    }
}

/** Increase travel with finger distance, approaching the eye-white limit smoothly. */
internal fun catGazeAt(pointer: Offset?, eyeCenter: Offset, radius: Float): Offset {
    if (pointer == null || radius <= 0f) return Offset.Zero
    val delta = pointer - eyeCenter
    return delta / (radius + delta.getDistance())
}

/** Full-screen cold-start animation. SVG coordinates/timing come from the workbench. minimal：桌面图标选了极简款时用墨团 */
@Composable
fun ReadingSplash(onFinished: () -> Unit, onSkip: () -> Unit, minimal: Boolean = false) {
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
        }) { if (minimal) drawMinimalSplash(dark, time) else drawImportedSplash(dark, time) }
    }
}

private val settle = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val bouncy = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** 墨团和经典猫同名的动画组，在墨团 200x220 坐标里的转动中心 */
private val minimalPivot = mapOf(
    "splash-ear-l" to Offset(74f, 70f), "splash-ear-r" to Offset(126f, 70f), "splash-eyes" to Offset(100f, 98f),
    "head-sprout" to Offset(100f, 45f), "head-sprout-float" to Offset(100f, 45f), "svg-cheek-pulse" to Offset(100f, 106f),
    "svg-zzz-1" to Offset(148f, 68f), "svg-zzz-2" to Offset(160f, 49f),
    "svg-heart-1" to Offset(34f, 40f), "svg-heart-2" to Offset(43f, 61f), "svg-heart-3" to Offset(45f, 123f),
    "svg-heart-4" to Offset(158f, 58f), "svg-heart-5" to Offset(160f, 81f),
)

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
internal fun DrawScope.assetGroup(group: String, time: Float, block: DrawScope.() -> Unit) {
    var alpha = 1f
    var tx = 0f; var ty = 0f; var sx = 1f; var sy = 1f; var angle = 0f
    var pivot = Offset.Zero
    // 墨团的组名带 m- 前缀：动画和经典猫一样，只是转动中心在 200x220 的坐标里
    val name = group.removePrefix("m-")
    when (name) {
        "min-head" -> ty = -2.8f * wave(time, 3.6f)
        "min-head-loaf-wrap" -> ty = -2f * wave(time, 3.8f)
        "min-body" -> { sy += 0.018f * wave(time, 3.6f); pivot = Offset(100f, 195f) }
        "min-body-loaf" -> { sy += 0.022f * wave(time, 3.8f); pivot = Offset(120f, 189f) }
        "min-tail" -> { angle = 9f * wave(time, 3.1f); pivot = Offset(126f, 188f) }
        "min-tail-sleep" -> { angle = 2f * wave(time, 4.5f); pivot = Offset(124f, 186f) }
        "min-leaf" -> { angle = -4f + 10f * wave(time, 3.4f); pivot = Offset(100f, 45f) }
        "min-eye-blink" -> {
            sy = keys(time, 5.4f, 0f to 1f, 0.45f to 1f, 0.47f to 0.06f, 0.49f to 1f,
                0.94f to 1f, 0.96f to 0.06f, 0.98f to 1f, 1f to 1f)
            pivot = Offset(100f, 98f)
        }
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
    if (group.startsWith("m-")) minimalPivot[name]?.let { pivot = it }
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
