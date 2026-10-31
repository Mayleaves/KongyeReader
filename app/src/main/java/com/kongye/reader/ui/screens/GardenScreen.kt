package com.kongye.reader.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Session
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.CatPose
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.Signpost
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private class Spot(val x: Float, val y: Float, val size: Float, val flip: Boolean, val loaf: Boolean, val phase: Int)

/** 每次阅读一只猫，随机散在院子里，不重叠 */
private fun placeSpots(sessions: List<Session>, w: Float, h: Float, seed: Long): List<Spot> {
    val n = sessions.size
    if (n == 0) return emptyList()
    val rnd = Random(seed)
    val cols = if (n > 12) 4 else 3
    val rows = max(4, ceil(n / cols.toFloat()).toInt())
    val cw = w / cols
    val ch = h / rows
    val cells = (0 until rows * cols).shuffled(rnd).take(n)
    return sessions.mapIndexed { i, s ->
        val cell = cells[i]
        val factor = when {
            s.millis < 20 * 60_000 -> 0.72f
            s.millis < 60 * 60_000 -> 0.86f
            else -> 1f
        }
        val size = min(min(cw, ch) * 0.86f, 92f) * factor
        val x = (cell % cols) * cw + rnd.nextFloat() * (cw - size).coerceAtLeast(0f)
        val y = (cell / cols) * ch + rnd.nextFloat() * (ch - size).coerceAtLeast(0f)
        Spot(x, y, size, rnd.nextBoolean(), rnd.nextInt(4) == 0, rnd.nextInt(800))
    }
}

/** 一周猫园：参考“一周农场”，这周每读一次书就来一只猫 */
@Composable
fun GardenScreen() {
    val c = K.c
    val sessions by Stats.sessions.collectAsState()
    var weeksBack by remember { mutableIntStateOf(0) }
    val today = Stats.today()
    val monday = Stats.monday(today).minusWeeks(weeksBack.toLong())
    val week = remember(sessions, weeksBack) {
        sessions.filter { Stats.isCat(it) && Stats.date(it.start).let { d -> d >= monday && d <= monday.plusDays(6) } }
    }
    val totalCats = remember(sessions) { sessions.count { Stats.isCat(it) } }
    var picked by remember(weeksBack) { mutableStateOf<Int?>(null) }
    val ground = if (c.dark) Color(0xFF141415) else Color(0xFFF6F6F6)

    Box(Modifier.fillMaxSize().background(ground)) {
        Scenery(Modifier.fillMaxSize())
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 186.dp, bottom = 92.dp, start = 14.dp, end = 14.dp),
        ) {
            val spots = remember(week, maxWidth, maxHeight) { placeSpots(week, maxWidth.value, maxHeight.value, monday.toEpochDay()) }
            week.forEachIndexed { i, s ->
                GardenCat(s, spots[i], picked == i) { picked = if (picked == i) null else i }
            }
            if (week.isEmpty()) {
                T(
                    if (weeksBack == 0) "这周的猫园还空着\n读够 3 分钟，就会来一只猫" else "那一周没有猫来过",
                    15.sp, c.text2, modifier = Modifier.align(Alignment.Center), align = TextAlign.Center, lineHeight = 24.sp,
                )
            }
        }
        IconBtn(
            Ico.BACK,
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .background(c.card, CircleShape),
        ) { Nav.pop() }
        Signpost(
            "一周猫园",
            Modifier
                .statusBarsPadding()
                .padding(start = 72.dp, top = 112.dp)
                .size(118.dp, 66.dp),
            dark = true,
            pointLeft = false,
        )
        // 小屋上的数字：一共来过多少只猫
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 48.dp, end = 28.dp)
                .background(c.ink, SmoothShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) { T("$totalCats", 16.sp, c.onInk, FontWeight.Black) }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 22.dp)
                .background(c.card, SmoothShape(18.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Ico.CHEVRON_LEFT, size = 20.dp) { weeksBack++ }
            val label = when (weeksBack) {
                0 -> "本周 · ${week.size} 只"
                1 -> "上周 · ${week.size} 只"
                else -> "${Fmt.md(monday)}起 · ${week.size} 只"
            }
            T(label, 14.sp, c.ink, FontWeight.SemiBold, Modifier.widthIn(min = 120.dp), align = TextAlign.Center)
            IconBtn(Ico.CHEVRON_RIGHT, color = if (weeksBack > 0) c.ink else c.text3, size = 20.dp) { if (weeksBack > 0) weeksBack-- }
        }
    }
}

@Composable
private fun GardenCat(s: Session, spot: Spot, selected: Boolean, onClick: () -> Unit) {
    val c = K.c
    val color = when {
        s.finished -> c.accent
        s.millis >= 90 * 60_000 -> Color(0xFFF2A541)
        s.millis >= 45 * 60_000 -> Color(0xFF8E8E93)
        else -> c.ink
    }
    val colored = color != c.ink
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300 + spot.phase), RepeatMode.Reverse), label = "bob",
    )
    Box(Modifier.offset(spot.x.dp, spot.y.dp)) {
        CatMascot(
            Modifier
                .graphicsLayer { translationY = -bob * 3.dp.toPx() }
                .press(scaleTo = 0.9f, onClick = onClick),
            size = spot.size.dp,
            color = color,
            eye = if (colored) Color.White else c.eye,
            detail = if (colored) lerp(color, Color.Black, 0.25f) else null,
            flip = spot.flip,
            pose = if (spot.loaf) CatPose.LOAF else CatPose.SIT,
            sleep = spot.loaf,
            blink = !spot.loaf,
        )
        if (selected) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-46).dp)
                    .wrapContentSize(unbounded = true)
                    .widthIn(max = 180.dp)
                    .background(c.ink, SmoothShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(s.title, 12.sp, c.onInk, FontWeight.Bold, maxLines = 1)
                T("${Fmt.weekday(Stats.date(s.start))} · ${Fmt.cn(s.millis)}", 11.sp, c.onInk.copy(alpha = 0.75f))
            }
        }
    }
}

/** 背景：太阳、云、小屋、几块灰色空地 */
@Composable
private fun Scenery(modifier: Modifier) {
    val c = K.c
    val cloud = if (c.dark) Color(0xFF1E1E20) else Color.White
    val patch = if (c.dark) Color(0xFF1C1C1E) else Color(0xFFEAEAEA)
    val patchIn = if (c.dark) Color(0xFF232325) else Color(0xFFF1F1F1)
    val house = if (c.dark) Color(0xFF3A3A3C) else Color(0xFFDADADA)
    val roof = if (c.dark) Color(0xFF6A6A6C) else Color(0xFF3A3A3A)
    Canvas(modifier.statusBarsPadding()) {
        val w = size.width
        val h = size.height
        val dp = density
        // 太阳
        val sun = Offset(w * 0.2f, 96 * dp)
        drawCircle(
            Brush.verticalGradient(listOf(Color(0xFFF0806A), Color(0xFFF7C9BD).copy(alpha = 0.2f)), startY = sun.y - 30 * dp, endY = sun.y + 30 * dp),
            30 * dp, sun,
        )
        // 云
        fun cloudAt(x: Float, y: Float, s: Float) {
            drawRoundRect(cloud, Offset(x, y), Size(90 * s * dp, 10 * s * dp), androidx.compose.ui.geometry.CornerRadius(5 * s * dp))
            drawRoundRect(cloud, Offset(x + 24 * s * dp, y - 6 * s * dp), Size(50 * s * dp, 10 * s * dp), androidx.compose.ui.geometry.CornerRadius(5 * s * dp))
        }
        cloudAt(w * 0.08f, 120 * dp, 1.1f)
        cloudAt(w * 0.42f, 74 * dp, 0.9f)
        cloudAt(w * 0.72f, 140 * dp, 0.8f)
        // 小屋
        val hx = w * 0.64f
        val hy = 96 * dp
        drawRoundRect(house, Offset(hx, hy), Size(90 * dp, 52 * dp), androidx.compose.ui.geometry.CornerRadius(10 * dp))
        val roofPath = Path().apply {
            moveTo(hx - 12 * dp, hy + 10 * dp)
            cubicTo(hx + 10 * dp, hy - 22 * dp, hx + 70 * dp, hy - 26 * dp, hx + 102 * dp, hy + 8 * dp)
            lineTo(hx + 96 * dp, hy + 14 * dp)
            cubicTo(hx + 70 * dp, hy - 8 * dp, hx + 20 * dp, hy - 6 * dp, hx - 6 * dp, hy + 16 * dp)
            close()
        }
        drawPath(roofPath, roof)
        drawRoundRect(roof, Offset(hx + 36 * dp, hy + 22 * dp), Size(20 * dp, 30 * dp), androidx.compose.ui.geometry.CornerRadius(10 * dp))
        val smoke = Path().apply {
            moveTo(hx + 72 * dp, hy - 14 * dp)
            cubicTo(hx + 82 * dp, hy - 30 * dp, hx + 66 * dp, hy - 40 * dp, hx + 80 * dp, hy - 56 * dp)
        }
        drawPath(smoke, cloud.copy(alpha = 0.9f), style = Stroke(5 * dp, cap = StrokeCap.Round))
        // 空地
        fun patchAt(cx: Float, cy: Float, rw: Float, rh: Float) {
            drawOval(patch, Offset(cx - rw / 2, cy - rh / 2), Size(rw, rh))
            drawOval(patchIn, Offset(cx - rw * 0.3f, cy - rh * 0.25f), Size(rw * 0.45f, rh * 0.4f))
        }
        patchAt(w * 0.36f, h * 0.42f, w * 0.5f, 46 * dp)
        patchAt(w * 0.7f, h * 0.64f, w * 0.46f, 40 * dp)
        patchAt(w * 0.32f, h * 0.86f, w * 0.6f, 52 * dp)
    }
}
