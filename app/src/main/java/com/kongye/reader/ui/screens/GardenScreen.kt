package com.kongye.reader.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Session
import com.kongye.reader.data.Shop
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.CatPose
import com.kongye.reader.ui.components.Creature
import com.kongye.reader.ui.components.GardenBackdrop
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.Signpost
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
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

/** 一周猫园：参考“一周农场”，这周每读一次书就来一只猫。preview 是商城里点进来试看的背景或挂件 */
@Composable
fun GardenScreen(preview: String? = null) {
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
    val profile by Profiles.state.collectAsState()
    // 猫园里来的动物按装扮里选的顺序轮着来；看板猫还是原来的彩色小猫
    val cast = profile.gardenAnimals.filter { it == Shop.MASCOT || profile.has(it) }.ifEmpty { listOf(Shop.MASCOT) }

    Box(Modifier.fillMaxSize().background(ground)) {
        val tryBg = preview?.let { Shop.background(it) }
        val tryOrn = preview?.let { Shop.ornament(it) }
        val bg = tryBg
            ?: Shop.background(profile.gardenBg)?.takeIf { profile.has(it.id) }
            ?: Shop.background(Shop.DEFAULT_BG)
        val scene = profile.scene.filterValues { profile.has(it) } + listOfNotNull(tryOrn?.let { it.slot to it.id })
        if (bg != null) GardenBackdrop(bg, profile.signText, scene, Modifier.fillMaxSize()) else Scenery(Modifier.fillMaxSize())
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 186.dp, bottom = 92.dp, start = 14.dp, end = 14.dp),
        ) {
            val spots = remember(week, maxWidth, maxHeight) { placeSpots(week, maxWidth.value, maxHeight.value, monday.toEpochDay()) }
            week.forEachIndexed { i, s ->
                GardenAnimal(
                    cast[i % cast.size], s, spots[i], maxWidth.value, maxHeight.value, picked == i,
                    onTap = { picked = if (picked == i) null else i },
                    onLift = { picked = null },
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
        if (tryBg != null) PreviewBar(tryBg.id, tryBg.name, tryBg.price, Modifier.align(Alignment.TopEnd))
        if (tryOrn != null) PreviewBar(tryOrn.id, tryOrn.name, tryOrn.price, Modifier.align(Alignment.TopEnd))
        if (bg == null) {
            Signpost(
                profile.signText,
                Modifier
                    .statusBarsPadding()
                    .padding(start = 72.dp, top = 112.dp)
                    .size(118.dp, 66.dp),
                dark = true,
                pointLeft = false,
            )
        }
        // 小屋上的数字：一共来过多少只猫（试看背景时让位给上面的预览条）
        if (preview == null) Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 48.dp, end = 28.dp)
                .background(c.ink, SmoothShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) { T("$totalCats", 16.sp, c.onInk, FontWeight.Black) }
        // 这一周没有猫：路牌和第一块空地之间放一张小卡片，趴着睡觉的猫 + 两行字（不压在空地和池塘上）
        if (week.isEmpty()) Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 162.dp)
                .background(c.card, SmoothShape(18.dp))
                .padding(start = 10.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatMascot(size = 40.dp, pose = CatPose.LOAF, sleep = true, shadow = false)
            Spacer(Modifier.width(8.dp))
            Column {
                T(if (weeksBack == 0) "猫园还空着" else "那一周没有猫来", 14.sp, c.ink, FontWeight.SemiBold)
                T(if (weeksBack == 0) "读够 3 分钟，就会来一只猫" else "往右翻，回到这一周看看", 12.sp, c.text2)
            }
        }
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

/**
 * 猫园里的一只：点一下看是哪次阅读；长按提起来（变大、离地，地上留一块影子），拖到别处松手放下。
 * 挪过的位置按那次阅读记在 Profile.gardenSpots（占院子宽、高的比例），下次进来还在那儿
 */
@Composable
private fun GardenAnimal(
    who: String,
    s: Session,
    spot: Spot,
    w: Float,
    h: Float,
    selected: Boolean,
    onTap: () -> Unit,
    onLift: () -> Unit,
) {
    val c = K.c
    val profile by Profiles.state.collectAsState()
    val key = s.start.toString()
    fun clamp(x: Float, y: Float) = Offset(x.coerceIn(0f, max(0f, w - spot.size)), y.coerceIn(0f, max(0f, h - spot.size)))
    val saved = profile.gardenSpots[key]
    val home = if (saved != null && saved.size == 2) clamp(saved[0] * w, saved[1] * h) else Offset(spot.x, spot.y)
    val homeNow by rememberUpdatedState(home)
    val onTapNow by rememberUpdatedState(onTap)
    val onLiftNow by rememberUpdatedState(onLift)
    // 提在手里时的位置（dp）；放下后清掉，回到 home（已经存好的新位置）
    var held by remember(key) { mutableStateOf<Offset?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (held != null) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "lift")
    val squeeze by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = 900f), label = "press")
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300 + spot.phase), RepeatMode.Reverse), label = "bob",
    )
    val haptic = LocalHapticFeedback.current
    val gesture = Modifier.pointerInput(key, w, h) {
        awaitEachGesture {
            val down = awaitFirstDown()
            pressed = true
            val longPress = awaitLongPressOrCancellation(down.id)
            pressed = false
            if (longPress == null) {
                // 没按够就松手：当作点一下（滑出去再松手不算）
                if (currentEvent.changes.any { it.id == down.id && it.changedToUp() }) onTapNow()
                return@awaitEachGesture
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onLiftNow()
            var p = homeNow
            held = p
            longPress.consume()
            drag(longPress.id) { change ->
                val d = change.positionChange()
                p = clamp(p.x + d.x / density, p.y + d.y / density)
                held = p
                change.consume()
            }
            Profiles.update { it.copy(gardenSpots = it.gardenSpots + (key to listOf(p.x / w, p.y / h))) }
            held = null
        }
    }
    val pos = held ?: home
    val cat = who == Shop.MASCOT || who == Shop.MINCAT || who == Shop.GOLDCAT
    val replacesGround = !cat && profile.outfits[who]?.get("H")?.let { Shop.accessory(it) } != null
    // 动物的 SVG 四周留白比猫多，画大一点看起来才一样大；脚落在格子里的高度也不一样
    val size = if (cat) spot.size else spot.size * 1.3f
    val feet = if (cat) 0.9f else 0.86f
    Box(Modifier.offset((pos.x - (size - spot.size) / 2).dp, (pos.y - (size - spot.size)).dp).zIndex(if (held != null) 1f else 0f)) {
        Box(Modifier.size(size.dp).then(gesture)) {
            // 提起来以后地上的影子
            if (lift > 0.01f && !replacesGround) Canvas(Modifier.matchParentSize()) {
                val sw = this.size.width * 0.5f * (1f - 0.15f * lift)
                val sh = this.size.width * 0.09f
                drawOval(
                    Color.Black.copy(alpha = (if (c.dark) 0.35f else 0.14f) * lift.coerceIn(0f, 1f)),
                    Offset((this.size.width - sw) / 2f, this.size.height * feet - sh / 2f),
                    Size(sw, sh),
                )
            }
            val body = Modifier.matchParentSize().graphicsLayer {
                val k = squeeze * (1f + 0.12f * lift)
                scaleX = k
                scaleY = k
                transformOrigin = TransformOrigin(0.5f, feet)
                translationY = -bob * 3.dp.toPx() * (1f - lift.coerceIn(0f, 1f)) - lift * 14.dp.toPx()
            }
            if (cat) {
                val color = when {
                    s.finished -> c.accent
                    s.millis >= 90 * 60_000 -> Color(0xFFF2A541)
                    s.millis >= 45 * 60_000 -> Color(0xFF8E8E93)
                    else -> c.ink
                }
                val colored = color != c.ink
                CatMascot(
                    body,
                    size = size.dp,
                    color = color,
                    eye = if (colored) Color.White else c.eye,
                    detail = if (colored) lerp(color, Color.Black, 0.25f) else null,
                    shadow = lift < 0.01f,
                    flip = spot.flip,
                    pose = if (spot.loaf) CatPose.LOAF else CatPose.SIT,
                    sleep = spot.loaf,
                    blink = !spot.loaf,
                    minimal = who == Shop.MINCAT,
                    gold = who == Shop.GOLDCAT,
                )
            } else Creature(who, body, size = size.dp, flip = spot.flip, shadow = lift < 0.01f)
        }
        if (selected) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (if (cat) -46 else -34).dp)
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

/** 试看背景或挂件时右上角的条：名字，换取或者直接用上 */
@Composable
private fun PreviewBar(id: String, name: String, price: Int, modifier: Modifier) {
    val c = K.c
    val profile by Profiles.state.collectAsState()
    val orn = Shop.ornament(id)
    val owned = profile.has(id)
    val using = owned && (if (orn != null) profile.scene[orn.slot] == id else profile.gardenBg == id)
    fun use() {
        if (orn != null) Profiles.hang(orn.slot, id) else Profiles.update { it.copy(gardenBg = id) }
    }
    Row(
        modifier
            .statusBarsPadding()
            .padding(top = 8.dp, end = 12.dp)
            .background(c.card, SmoothShape(20.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("试看 · $name", 13.sp, c.ink, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.widthIn(max = 150.dp))
        Spacer(Modifier.width(10.dp))
        val label = when {
            using -> if (orn != null) "挂着呢" else "正在用"
            owned -> if (orn != null) "挂上" else "用这张"
            else -> "$price 叶换取"
        }
        val enabled = !using && (owned || profile.leaves >= price)
        Box(
            Modifier
                .background(if (enabled) c.ink else c.chip, SmoothShape(16.dp))
                .press(scaleTo = 0.94f) {
                    when {
                        using -> Unit
                        owned -> use().also { Toast.show(if (orn != null) "「$name」挂上了" else "猫园换上了「$name」") }
                        profile.leaves < price -> Toast.show("叶子还差 ${price - profile.leaves} 片")
                        Profiles.buy(id) -> {
                            use()
                            Toast.show(if (orn != null) "换到了「$name」，已经挂上" else "换到了「$name」，猫园已经换上")
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) { T(label, 13.sp, if (enabled) c.onInk else c.text2, FontWeight.SemiBold) }
    }
}

/** 背景：太阳、云、小屋、几块灰色空地（商城背景读不出来时用） */
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
