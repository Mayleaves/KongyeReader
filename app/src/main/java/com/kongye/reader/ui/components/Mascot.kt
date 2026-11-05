package com.kongye.reader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    val body by lazy { svgPath("M62 178C55 146 66 116 100 112C134 116 145 146 138 178Z") }
    val tail by lazy { svgPath("M134 170C168 176 178 146 164 128") }
    val legs by lazy { svgPath("M89 150L89 177M111 150L111 177") }
    val ears by lazy { svgPath("M71 30L76 40M129 30L124 40") }
    val mouth by lazy { svgPath("M94 103Q97 107 100 103Q103 107 106 103") }
    val closedEyes by lazy { svgPath("M73 86Q82 92 91 86M109 86Q118 92 127 86") }
    val leaf by lazy { svgPath("M100 40C96 28 104 18 118 18C118 31 111 40 100 40Z") }
    val leafVein by lazy { svgPath("M100 40L108 30") }
    val loafBody by lazy { svgPath("M30 178C26 146 56 128 104 128C150 128 176 146 172 178Z") }
    val loafTail by lazy { svgPath("M164 176C154 190 116 190 98 182") }
    val hangBody by lazy { svgPath("M70 160C64 128 74 104 100 100C126 104 136 128 130 160C124 172 76 172 70 160Z") }
    val hangTail by lazy { svgPath("M104 158C98 172 106 182 100 194") }
    val hangLegs by lazy { svgPath("M82 160L80 178M118 160L120 178") }
    val hangArms by lazy { svgPath("M82 32L76 78M118 30L124 78") }
}

enum class CatPose { SIT, LOAF }

private fun DrawScope.drawHead(ink: Color, eye: Color, detail: Color, accent: Color?, blink: Float, look: Float, sleep: Boolean) {
    drawPath(CatArt.head, ink)
    drawPath(CatArt.ears, detail, style = Stroke(3f, cap = StrokeCap.Round))
    if (sleep) {
        drawPath(CatArt.closedEyes, eye, style = Stroke(3.4f, cap = StrokeCap.Round))
    } else {
        scale(1f, blink.coerceAtLeast(0.08f), pivot = Offset(100f, 84f)) {
            drawCircle(eye, 12f, Offset(82f, 84f))
            drawCircle(eye, 12f, Offset(118f, 84f))
            drawCircle(ink, 5.6f, Offset(84f + look * 2f, 83f))
            drawCircle(ink, 5.6f, Offset(120f + look * 2f, 83f))
        }
    }
    drawPath(CatArt.mouth, detail, style = Stroke(2.6f, cap = StrokeCap.Round))
    if (accent != null) {
        drawPath(CatArt.leaf, accent)
        drawPath(CatArt.leafVein, Color.White.copy(alpha = 0.85f), style = Stroke(1.6f, cap = StrokeCap.Round))
    }
}

/** 在 200×200 坐标系里画一只猫（调用方负责缩放） */
fun DrawScope.drawCat(
    ink: Color,
    eye: Color,
    detail: Color,
    shadow: Color?,
    accent: Color?,
    blink: Float = 1f,
    look: Float = 1f,
    sleep: Boolean = false,
    pose: CatPose = CatPose.SIT,
) {
    when (pose) {
        CatPose.SIT -> {
            shadow?.let { drawOval(it, Offset(52f, 174f), Size(100f, 12f)) }
            drawPath(CatArt.tail, ink, style = Stroke(10f, cap = StrokeCap.Round))
            drawPath(CatArt.body, ink)
            drawPath(CatArt.legs, detail, style = Stroke(3f, cap = StrokeCap.Round))
            drawHead(ink, eye, detail, accent, blink, look, sleep)
        }
        CatPose.LOAF -> {
            shadow?.let { drawOval(it, Offset(30f, 172f), Size(144f, 14f)) }
            drawPath(CatArt.loafBody, ink)
            drawPath(CatArt.loafTail, ink, style = Stroke(10f, cap = StrokeCap.Round))
            withTransform({
                translate(-14f, 30f)
                scale(0.78f, 0.78f, pivot = Offset(100f, 124f))
            }) { drawHead(ink, eye, detail, accent, blink, look, sleep) }
        }
    }
}

@Composable
private fun blinkValue(): Float {
    val t = rememberInfiniteTransition(label = "blink")
    val v by t.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4600
                1f at 0
                1f at 4300
                0.1f at 4400
                1f at 4560
            },
        ),
        label = "blink",
    )
    return v
}

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
) {
    val c = K.c
    val b = if (blink) blinkValue() else 1f
    val d = detail ?: lerp(color, eye, 0.3f)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 200f
        withTransform({
            if (flip) scale(-1f, 1f, pivot = center)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawCat(color, eye, d, if (shadow) c.shadow else null, if (accent) c.accent else null, b, look, sleep, pose)
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
