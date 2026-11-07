package com.kongye.reader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kongye.reader.ui.theme.K

/** 吉祥物黑猫的画稿，坐标系 200×200 */
object CatArt {
    val head by lazy { svgPath("M56 88C54 70 59 56 65 46L68 24C69 19 74 18 77 21L93 41C97 40 103 40 107 41L123 21C126 18 131 19 132 24L135 46C141 56 146 70 144 88C142 110 124 124 100 124C76 124 58 110 56 88Z") }
    val ears by lazy { svgPath("M71 30L76 40M129 30L124 40") }
    val hangBody by lazy { svgPath("M70 160C64 128 74 104 100 100C126 104 136 128 130 160C124 172 76 172 70 160Z") }
    val hangTail by lazy { svgPath("M104 158C98 172 106 182 100 194") }
    val hangLegs by lazy { svgPath("M82 160L80 178M118 160L120 178") }
    val hangArms by lazy { svgPath("M82 32L76 78M118 30L124 78") }
}

enum class CatPose { SIT, LOAF }

@Composable
fun CatMascot(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    color: Color = K.c.ink,
    eye: Color = K.c.eye,
    detail: Color? = null,
    accent: Boolean = false,
    shadow: Boolean = true,
    blink: Boolean = false,
    sleep: Boolean = false,
    pose: CatPose = CatPose.SIT,
    flip: Boolean = false,
    look: Float = 1f,
    /** 墨团（200x220 的画稿），取景和经典猫一样，同样大小一样大 */
    minimal: Boolean = false,
    /** 鎏金看板猫（猫园里也会趴着、睡觉；颜色是自己的） */
    gold: Boolean = false,
    /** 鎏金墨团：墨团的取景和姿势，颜色是自己的 */
    goldMin: Boolean = false,
) {
    val c = K.c
    val time = if (blink || pose == CatPose.LOAF) assetTime() else 0f
    val bodyColor = color.takeIf { it != c.ink }
    Canvas(modifier.size(size)) {
        if (gold) {
            if (pose == CatPose.SIT) {
                val s = this.size.minDimension / 210f
                withTransform({
                    if (flip) scale(-1f, 1f, pivot = center)
                    translate((this@Canvas.size.width - 240f * s) / 2f, -35f * s)
                    scale(s, s, pivot = Offset.Zero)
                }) { if (sleep) drawGoldSleep(c.dark, time, accent, shadow) else drawGoldFocus(c.dark, time, accent, shadow = shadow) }
            } else {
                val s = this.size.minDimension / 200f
                withTransform({
                    if (flip) scale(-1f, 1f, pivot = center)
                    scale(s, s, pivot = Offset.Zero)
                    translate(-32f, -55f)
                }) { drawGoldLoaf(c.dark, time, shadow) }
            }
        } else if (minimal || goldMin) {
            val (k, ox, oy) = if (pose == CatPose.SIT) minimalFrame() else {
                // 趴着：和经典趴姿一样的取景（200 宽的格子，脚踩同一条线）
                val s = this.size.minDimension / 200f
                Triple(s, -18f * s, -15f * s)
            }
            withTransform({
                if (flip) scale(-1f, 1f, pivot = center)
                translate(ox, oy)
                scale(k, k, pivot = Offset.Zero)
            }) {
                if (goldMin) when {
                    pose == CatPose.LOAF -> drawGoldMinLoaf(c.dark, time, shadow)
                    sleep -> drawGoldMinSleep(c.dark, time, accent, shadow)
                    else -> drawGoldMinFocus(c.dark, time, accent, shadow = shadow)
                } else when {
                    pose == CatPose.LOAF -> drawMinimalLoaf(c.dark, time, bodyColor, if (bodyColor != null) eye else null, shadow)
                    sleep -> drawMinimalSleep(c.dark, time, accent, bodyColor, shadow)
                    else -> drawMinimalFocus(c.dark, time, accent, bodyColor = bodyColor, shadow = shadow)
                }
            }
        } else if (pose == CatPose.SIT) {
            val s = this.size.minDimension / 210f
            withTransform({
                if (flip) scale(-1f, 1f, pivot = center)
                translate((this@Canvas.size.width - 240f * s) / 2f, -35f * s)
                scale(s, s, pivot = Offset.Zero)
            }) {
                if (sleep) drawImportedSleep(c.dark, time, accent, bodyColor, detail, shadow)
                else drawImportedFocus(c.dark, time, accent, Offset.Zero, bodyColor, detail, shadow)
            }
        } else {
            val s = this.size.minDimension / 200f
            withTransform({
                if (flip) scale(-1f, 1f, pivot = center)
                scale(s, s, pivot = Offset.Zero)
                // Fit the supplied 240×260 drawing to the existing 200×200 slot,
                // keeping the continuous curled tail completely inside the canvas.
                translate(-32f, -55f)
            }) {
                drawImportedLoaf(c.dark, time, bodyColor, if (bodyColor != null) eye else null, detail, shadow)
            }
        }
    }
}

/** 抓着一根绳子吊着的猫，用在“完蛋，打不开”那类页面 */
@Composable
fun HangingCat(modifier: Modifier = Modifier, width: Dp = 190.dp) {
    val c = K.c
    val swing by rememberInfiniteTransition(label = "swing").animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swing",
    )
    val detail = lerp(c.ink, c.eye, 0.3f)
    Canvas(modifier.size(width, width * 1.25f)) {
        val s = size.width / 200f
        scale(s, pivot = Offset.Zero) {
            drawLine(c.ink, Offset(-600f, 30f), Offset(800f, 20f), strokeWidth = 2f)
            rotate(swing, pivot = Offset(100f, 27f)) {
                drawPath(CatArt.hangArms, c.ink, style = Stroke(12f, cap = StrokeCap.Round))
                drawOval(c.ink, Offset(73f, 21f), Size(18f, 14f))
                drawOval(c.ink, Offset(109f, 19f), Size(18f, 14f))
                translate(0f, 40f) {
                    drawPath(CatArt.hangTail, c.ink, style = Stroke(8f, cap = StrokeCap.Round))
                    drawPath(CatArt.hangBody, c.ink)
                    drawPath(CatArt.hangLegs, c.ink, style = Stroke(9f, cap = StrokeCap.Round))
                    translate(0f, -6f) {
                        drawPath(CatArt.head, c.ink)
                        drawPath(CatArt.ears, detail, style = Stroke(3f, cap = StrokeCap.Round))
                        drawCircle(c.eye, 12f, Offset(82f, 84f))
                        drawCircle(c.eye, 12f, Offset(118f, 84f))
                        drawCircle(c.ink, 5.6f, Offset(82f, 79f))
                        drawCircle(c.ink, 5.6f, Offset(118f, 79f))
                        drawOval(detail, Offset(95f, 102f), Size(10f, 8f))
                    }
                }
            }
        }
    }
}
