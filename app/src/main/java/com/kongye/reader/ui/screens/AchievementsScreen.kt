package com.kongye.reader.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.AchieveCtx
import com.kongye.reader.data.Achievement
import com.kongye.reader.data.Achievements
import com.kongye.reader.data.AchievementOrdering
import com.kongye.reader.data.Library
import com.kongye.reader.data.Profile
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Shop
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.AccessoryArt
import com.kongye.reader.ui.components.BadgeStyle
import com.kongye.reader.ui.components.ConfirmDialog
import com.kongye.reader.ui.components.Creature
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.OrnamentArt
import com.kongye.reader.ui.components.Sheet
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.badgeStyles
import com.kongye.reader.ui.components.drawBadgeIcon
import com.kongye.reader.ui.components.drawAddedBadgeIcon
import com.kongye.reader.ui.components.drawExtraBadgeIcon
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.components.svgPath
import com.kongye.reader.ui.theme.K
import java.time.Instant
import java.time.ZoneId

/*
 * 成就页：照成就设计 HTML（2077 平面机能风）。顶上一张切角统计卡，下面一排配色方案（第一套免费，其余用叶子换），
 * 再按六组列出 49 枚徽章。徽章外框按成就分四种（八角、六角、斜切、星芒），中心图形在 BadgeArt.kt（生成的）。
 */

private const val OCT_OUTER = "28,6 60,6 82,28 82,60 60,82 28,82 6,60 6,28"
private const val OCT_OUTER_RIGHT = "44,6 60,6 82,28 82,60 60,82 44,82"
private const val OCT_INNER = "31,13 57,13 75,31 75,57 57,75 31,75 13,57 13,31"
private const val OCT_INNER_RIGHT = "44,13 57,13 75,31 75,57 57,75 44,75"
private const val OCT_INNER_DIAG = "57,13 75,31 75,57 57,75 31,75 13,57"
private const val HEX_OUTER = "44,5 79,24 79,64 44,83 9,64 9,24"
private const val HEX_OUTER_RIGHT = "44,5 79,24 79,64 44,83"
private const val HEX_INNER = "44,13 72,28 72,60 44,75 16,60 16,28"
private const val HEX_INNER_RIGHT = "44,13 72,28 72,60 44,75"
private const val STAR_OUTER = "44,3 56,13 75,13 75,32 85,44 75,56 75,75 56,75 44,85 32,75 13,75 13,56 3,44 13,32 13,13 32,13"
private const val STAR_OUTER_RIGHT = "44,3 56,13 75,13 75,32 85,44 75,56 75,75 56,75 44,85"

private val polys = HashMap<String, Path>()
private val progressTrack by lazy { svgPath("M 44,6 L 60,6 L 82,28 L 82,60 L 60,82 L 28,82 L 6,60 L 6,28 L 28,6 Z") }
private fun poly(points: String, close: Boolean = true): Path = polys.getOrPut(points + close) {
    val n = points.trim().split(Regex("[ ,]+")).map { it.toFloat() }
    Path().apply {
        moveTo(n[0], n[1])
        for (i in 2 until n.size step 2) lineTo(n[i], n[i + 1])
        if (close) close()
    }
}

/** 外框：殿堂级星芒、重装六角、斜切八角，其余标准八角（和 HTML 一样按成就指定） */
private val starFrame = setOf("c0", "read_100h", "finish_10", "animal_all", "c13", "c17", "egg", "e8", "r13", "r14", "p7", "p8", "b7", "c24", "e11")
private val hexFrame = setOf("streak_3", "day_30m", "c9", "c16", "rename", "p5", "p6", "c22")
private val diagFrame = setOf("night_owl", "shelf_1", "c7", "c7m", "c14", "backup_1", "r11", "b6", "c23", "e10")

private fun DrawScope.drawFrame(id: String, p: BadgeStyle) {
    when (id) {
        in starFrame -> {
            drawPath(poly(STAR_OUTER), p.pri)
            drawPath(poly(STAR_OUTER_RIGHT), p.acc)
            drawPath(poly(OCT_INNER), p.plate)
            drawPath(poly(OCT_INNER_RIGHT), p.sub)
            drawRect(p.paper, Offset(41f, 10f), Size(6f, 3f))
            drawRect(p.paper, Offset(41f, 75f), Size(6f, 3f))
        }
        in hexFrame -> {
            drawPath(poly(HEX_OUTER), p.sec)
            drawPath(poly(HEX_OUTER_RIGHT), p.pri)
            drawPath(poly(HEX_INNER), p.plate)
            drawPath(poly(HEX_INNER_RIGHT), p.sub)
            drawRect(Color.White, Offset(7f, 41f), Size(5f, 6f))
            drawRect(Color.White, Offset(76f, 41f), Size(5f, 6f))
        }
        in diagFrame -> {
            drawPath(poly(OCT_OUTER), p.sec)
            drawPath(poly("28,6 60,6 82,28 82,44 6,44 6,28"), p.pri)
            drawPath(poly(OCT_INNER), p.plate)
            drawPath(poly(OCT_INNER_DIAG), p.sub)
            drawLine(p.acc, Offset(19f, 44f), Offset(23f, 44f), 1.6f)
            drawLine(p.acc, Offset(65f, 44f), Offset(69f, 44f), 1.6f)
        }
        else -> {
            drawPath(poly(OCT_OUTER), p.pri)
            drawPath(poly(OCT_OUTER_RIGHT), p.sec)
            drawRect(Color.White, Offset(40f, 5f), Size(8f, 4f))
            drawRect(Color.White, Offset(40f, 79f), Size(8f, 4f))
            drawPath(poly(OCT_INNER), p.plate)
            drawPath(poly(OCT_INNER_RIGHT), p.sub)
            drawRect(p.acc, Offset(41f, 16f), Size(6f, 2f))
            drawRect(p.pri, Offset(41f, 70f), Size(6f, 2f))
        }
    }
}

/** 没点亮的“工程蓝图芯片”的灰色，深色模式下压暗 */
private class ChipGray(val outer: Color, val outerLine: Color, val tick: Color, val inner: Color, val innerLine: Color, val grid: Color, val reticle: Color, val idle: Color, val g1: Color, val g2: Color, val g3: Color)

private val chipLight = ChipGray(
    Color(0xFFF3F4F7), Color(0xFFE2E5EC), Color(0xFFCFD4DF), Color(0xFFFAFBFD), Color(0xFFE6E8EF), Color(0xFFEFF1F6),
    Color(0xFFC8CEDB), Color(0xFFD0D5E0), Color(0xFF949BA8), Color(0xFFB4BAC6), Color(0xFFC5CAD4),
)
private val chipDark = ChipGray(
    Color(0xFF222226), Color(0xFF34343A), Color(0xFF45454D), Color(0xFF1A1A1D), Color(0xFF2E2E34), Color(0xFF26262B),
    Color(0xFF4A4A52), Color(0xFF3E3E46), Color(0xFF7A7A85), Color(0xFF5E5E68), Color(0xFF55555E),
)

/** 一枚徽章（88x88 画稿）：点亮是阵营配色的平面贴花；没点亮是灰色蓝图芯片，外圈折线是进度 */
@Composable
internal fun Badge(a: Achievement, on: Boolean, ratio: Float, style: BadgeStyle, size: Dp) {
    val gray = if (K.c.dark) chipDark else chipLight
    Canvas(Modifier.size(size)) {
        scale(this.size.minDimension / 88f, pivot = Offset.Zero) {
            if (on) {
                drawFrame(a.id, style)
                if (!drawExtraBadgeIcon(a.icon, style.paper, style.pri, style.sec, style.plate, style.acc) &&
                    !drawAddedBadgeIcon(a.icon, style.paper, style.pri, style.sec, style.plate, blueprint=false))
                    drawBadgeIcon(a.icon, style.paper, style.pri, style.sec, style.plate, blueprint = false)
                return@scale
            }
            drawPath(poly(OCT_OUTER), gray.outer)
            drawPath(poly(OCT_OUTER), gray.outerLine, style = Stroke(2.2f))
            for ((x1, y1, x2, y2) in listOf(listOf(28f, 6f, 31f, 11f), listOf(60f, 6f, 57f, 11f), listOf(60f, 82f, 57f, 77f), listOf(28f, 82f, 31f, 77f)))
                drawLine(gray.tick, Offset(x1, y1), Offset(x2, y2), 1.5f)
            val progress = ratio > 0f
            if (progress) {
                // 从顶部中间顺时针绕八角一圈的进度轨
                val track = progressTrack
                val part = Path()
                PathMeasure().apply { setPath(track, false); getSegment(0f, length * ratio.coerceIn(0.06f, 1f), part, true) }
                drawPath(part, style.lockProg, style = Stroke(3.4f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
                drawRect(style.lockProg, Offset(41.5f, 3.5f), Size(5f, 5f))
            } else {
                drawRect(gray.idle, Offset(42f, 4.5f), Size(4f, 3f))
            }
            drawPath(poly(OCT_INNER), gray.inner)
            drawPath(poly(OCT_INNER), gray.innerLine, style = Stroke(1.4f))
            drawLine(gray.grid, Offset(21f, 65f), Offset(65f, 21f), 1.5f)
            drawLine(gray.grid, Offset(15f, 44f), Offset(73f, 44f), 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
            for (pts in listOf("27,22 32,17 37,17", "61,22 56,17 51,17", "27,66 32,71 37,71", "61,66 56,71 51,71"))
                drawPath(poly(pts, close = false), gray.reticle, style = Stroke(1.5f))
            // 中心图形：灰色蓝图，58% 透明
            drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, 88f, 88f), Paint().apply { alpha = 0.58f })
            val g3 = if (progress) style.lockProg else gray.g3
            if (!drawExtraBadgeIcon(a.icon, gray.g1, gray.g2, g3, gray.inner, gray.g2) &&
                !drawAddedBadgeIcon(a.icon, gray.g1, gray.g2, g3, gray.inner, blueprint=true))
                drawBadgeIcon(a.icon, gray.g1, gray.g2, g3, gray.inner, blueprint = true)
            drawContext.canvas.restore()
            drawRect(if (progress) style.lockProg else gray.idle, Offset(39f, 69f), Size(10f, 2.2f), alpha = 0.65f)
        }
    }
}

@Composable
fun AchievementsScreen() {
    val c = K.c
    val profile by Profiles.state.collectAsState()
    val sessions by Stats.sessions.collectAsState()
    val books by Library.books.collectAsState()
    val ctx = remember(sessions, books, profile) { AchieveCtx(sessions, books, profile) }
    val using = badgeStyles.firstOrNull { it.id == profile.badgeStyle && profile.has("style_${it.id}") } ?: badgeStyles.first()
    var picked by remember { mutableStateOf<Achievement?>(null) }
    var buying by remember { mutableStateOf<BadgeStyle?>(null) }
    // 试看：没换到的配色点一下，整页先换成它看看；底下一条可以换取或者退出试看
    var preview by remember { mutableStateOf<BadgeStyle?>(null) }
    val style = preview ?: using
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val done = Achievements.all.count { it.id in profile.achievements }
    val earned = Achievements.all.filter { it.id in profile.achievements }.sumOf { it.reward }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("成就", onBack = { Nav.pop() })
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navPad + if (preview != null) 96.dp else 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Summary(style, done, Achievements.all.size, earned) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StyleBar(style, profile) { s ->
                        when {
                            profile.has("style_${s.id}") -> {
                                preview = null
                                Profiles.update { it.copy(badgeStyle = s.id) }
                            }
                            preview?.id == s.id -> buying = s
                            else -> preview = s
                        }
                    }
                }
                for (group in Achievements.groups) {
                    val list = AchievementOrdering.category(group.id)
                    item(key = "g-${group.id}", span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                            T(group.name, 18.sp, c.ink, FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            T("${list.count { it.id in profile.achievements }} / ${list.size}", 13.sp, Color(0xFFA0A4B0), FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            T(group.subtitle, 11.sp, c.text3, maxLines = 1)
                        }
                    }
                    items(list, key = { it.id }) { a -> Cell(a, a.id in profile.achievements, a.id in profile.unreadAchievements, ctx, style) { picked = a } }
                }
            }
        }
        preview?.let { pv ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp)
                    .background(c.ink, SmoothShape(22.dp))
                    .padding(start = 16.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("试看 · ${pv.name}", 14.sp, c.onInk, FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .press(scaleTo = 0.94f) { buying = pv }
                        .background(c.onInk, SmoothShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { T("${Shop.STYLE_PRICE} 叶换取", 13.sp, c.ink, FontWeight.SemiBold) }
                IconBtn(Ico.CLOSE, color = c.onInk, size = 18.dp) { preview = null }
            }
        }
        Detail(picked, ctx, profile, style) { picked = null }
        val b = buying
        ConfirmDialog(
            visible = b != null,
            title = "换「${b?.name ?: ""}」配色？",
            message = if (profile.leaves >= Shop.STYLE_PRICE) "用 ${Shop.STYLE_PRICE} 片叶子换，换到后成就页就用这套颜色"
            else "要 ${Shop.STYLE_PRICE} 片叶子，还差 ${Shop.STYLE_PRICE - profile.leaves} 片",
            confirm = if (profile.leaves >= Shop.STYLE_PRICE) "换" else "知道了",
            ico = Ico.LEAF,
            onConfirm = {
                if (b != null && Profiles.buy("style_${b.id}")) {
                    Profiles.update { it.copy(badgeStyle = b.id) }
                    preview = null
                    Toast.show("换上了「${b.name}」")
                }
                buying = null
            },
            onDismiss = { buying = null },
        )
    }
}

private val cutCard = GenericShape { size, _ ->
    val k = 14f * 3f
    moveTo(0f, 0f)
    lineTo(size.width - k, 0f)
    lineTo(size.width, k)
    lineTo(size.width, size.height)
    lineTo(k, size.height)
    lineTo(0f, size.height - k)
    close()
}

/** 顶部切角统计卡：阵营代号、点亮数、成就奖励的叶子、分段能量槽 */
@Composable
private fun Summary(p: BadgeStyle, done: Int, total: Int, earned: Int) {
    val pct = if (total == 0) 0f else done * 100f / total
    Box(Modifier.fillMaxWidth().clip(cutCard).background(p.card)) {
        Box(Modifier.width(112.dp).height(3.dp).background(p.pri))
        Canvas(Modifier.align(Alignment.TopEnd).size(24.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); close() }, p.pri)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 40.dp).width(64.dp).height(3.dp).background(p.sec))
        // 水印：八角和菱形
        Canvas(Modifier.align(Alignment.TopEnd).padding(end = 64.dp, top = 8.dp).size(96.dp)) {
            val s = size.width / 100f
            scale(s, pivot = Offset.Zero) {
                drawPath(poly("30,8 70,8 92,30 92,70 70,92 30,92 8,70 8,30"), p.pri, alpha = 0.1f, style = Stroke(4f))
                drawPath(poly("50,18 82,50 50,82 18,50"), p.sec, alpha = 0.1f, style = Stroke(3f))
            }
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(p.pri))
                Spacer(Modifier.width(6.dp))
                T(p.code, 10.sp, p.pri, FontWeight.SemiBold, maxLines = 1, spacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    T("SYNC ${"%.1f".format(pct)}%", 10.sp, p.acc, FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    T("成就徽章", 12.sp, Color(0xFFA0A4B0), FontWeight.Medium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        T("$done", 34.sp, Color.White, FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        T("/ $total", 15.sp, Color(0xFF7E828E), FontWeight.SemiBold, Modifier.padding(bottom = 6.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Canvas(Modifier.size(28.dp)) {
                        scale(size.width / 28f, pivot = Offset.Zero) {
                            drawPath(poly("23,4 12,7 5,16 5,23 13,22 22,13"), p.pri)
                            drawPath(poly("23,4 13,22 5,23"), p.sec)
                            drawLine(p.card, Offset(5f, 23f), Offset(19f, 9f), 1.6f)
                            drawRect(p.acc, Offset(20f, 3f), Size(3f, 3f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    T("+$earned", 19.sp, p.acc, FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    T("成就奖励", 11.sp, Color(0xFF8E929F))
                }
            }
            Spacer(Modifier.height(16.dp))
            // 能量槽：黑底，按点亮比例填阵营主色，七道刻度
            Box(Modifier.fillMaxWidth().height(10.dp).background(Color.Black.copy(alpha = 0.45f)).padding(2.dp)) {
                Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).background(p.pri))
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { Box(Modifier.width(1.5.dp).fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) }
                }
            }
        }
    }
}

/** 配色方案：一排可以横着滑的小牌子，没换到的带锁 */
@Composable
private fun StyleBar(current: BadgeStyle, profile: Profile, onPick: (BadgeStyle) -> Unit) {
    val c = K.c
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("徽章配色", 11.sp, c.text3, FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            T("带锁的点一下先试看", 11.sp, c.text3)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in badgeStyles) {
                val on = s.id == current.id
                val has = profile.has("style_${s.id}")
                Row(
                    Modifier
                        .press(scaleTo = 0.95f) { onPick(s) }
                        .background(if (on) Color(0xFF16161A) else c.chip, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close() }, s.pri)
                        drawPath(Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, s.sec)
                    }
                    Spacer(Modifier.width(6.dp))
                    T(s.name, 11.sp, if (on) Color.White else c.text2, FontWeight.Bold, maxLines = 1)
                    if (!has) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Ico.LOCK, size = 11.dp, color = c.text3)
                    }
                }
            }
        }
    }
}

private fun ratioOf(a: Achievement, ctx: AchieveCtx): Pair<Float, Pair<Long, Long>> {
    val (now, goal) = a.progress(ctx)
    return (if (goal <= 0) 0f else (now.toFloat() / goal).coerceIn(0f, 1f)) to (minOf(now, goal) to goal)
}

@Composable
private fun Cell(a: Achievement, on: Boolean, fresh: Boolean, ctx: AchieveCtx, style: BadgeStyle, onClick: () -> Unit) {
    val c = K.c
    val (ratio, nums) = remember(a, ctx) { ratioOf(a, ctx) }
    val secret = a.hidden && !on
    Column(Modifier.press(scaleTo = 0.94f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(74.dp)) {
            Badge(a, on, ratio, style, 74.dp)
            if(on && fresh) Box(Modifier.align(Alignment.TopEnd).offset(x=5.dp,y=(-1).dp)
                .semantics { contentDescription="未查看的新成就：${a.title}" }
                .background(Color(0xFFFBBF24),RoundedCornerShape(4.dp)).padding(horizontal=4.dp,vertical=1.dp)) {
                T("NEW",8.sp,Color(0xFF17171B),FontWeight.ExtraBold,maxLines=1)
            }
        }
        Spacer(Modifier.height(6.dp))
        T(if (secret) "？？？" else a.title, 12.sp, if (on) c.ink else Color(0xFF9EA3AE), if (on) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, align = TextAlign.Center)
        val sub = when {
            on -> a.tag
            !secret && nums.second > 1 -> "${nums.first}/${nums.second}"
            else -> ""
        }
        T(sub, 10.sp, if (on) c.text3 else Color(0xFFB0B5C0), FontWeight.Medium, maxLines = 1)
    }
}

/** 点开一枚徽章：大图、条件、点亮日期或进度、奖励；奖励是东西的话给它一张预览卡 */
@Composable
private fun Detail(item: Achievement?, ctx: AchieveCtx, profile: Profile, style: BadgeStyle, onDismiss: () -> Unit) {
    val c = K.c
    LaunchedEffect(item?.id,item?.let { profile.achievements[it.id] }) {
        item?.let { Profiles.markAchievementViewed(it.id) }
    }
    val last = remember { arrayOfNulls<Achievement>(1) }
    item?.let { last[0] = it }
    Sheet(visible = item != null, onDismiss = onDismiss) {
        val a = last[0] ?: return@Sheet
        val at = profile.achievements[a.id]
        val on = at != null
        val secret = a.hidden && !on
        val (ratio, nums) = ratioOf(a, ctx)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge(a, on, ratio, style, 128.dp)
            Spacer(Modifier.height(14.dp))
            T(if (secret) "？？？" else a.title, 22.sp, c.ink, FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val group = Achievements.groups.firstOrNull { it.id == a.group }?.name?.substringBefore(" · ") ?: ""
            T("$group · ${a.tier}徽章", 13.sp, Color(0xFFB4B4B8))
            Spacer(Modifier.height(12.dp))
            T(if (secret) "藏起来的成就，找到了才知道是什么" else a.desc, 15.sp, c.text2, align = TextAlign.Center, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            if (on) {
                val d = Instant.ofEpochMilli(at!!).atZone(ZoneId.systemDefault()).toLocalDate()
                T("${d.year} 年 ${d.monthValue} 月 ${d.dayOfMonth} 日 点亮", 14.sp, c.ink, FontWeight.SemiBold)
            } else if (!secret) {
                Box(Modifier.width(248.dp).height(6.dp).background(c.chip, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(if (ratio > 0f) ratio.coerceAtLeast(0.04f) else 0f).height(6.dp).background(c.ink, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.height(8.dp))
                T("${nums.first} / ${nums.second}", 13.sp, c.text2)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (a.item != null) {
                    Canvas(Modifier.size(16.dp)) {
                        scale(size.width / 20f, pivot = Offset.Zero) {
                            drawPath(poly("10,2 17,6 17,14 10,18 3,14 3,6"), style.plate)
                            drawPath(poly("10,2 17,6 10,10 3,6"), style.pri)
                            drawPath(poly("10,10 17,6 17,14 10,18"), style.sec)
                            drawRect(Color.White, Offset(8.5f, 7.5f), Size(3f, 3f))
                        }
                    }
                } else Icon(Ico.LEAF, size = 16.dp, color = Color(0xFFDF4B3B))
                Spacer(Modifier.width(6.dp))
                val text = when {
                    a.item != null && on -> "已解锁「${a.item}」"
                    a.item != null -> "点亮解锁「${a.item}」"
                    on -> "已得 ${a.reward} 片叶子"
                    else -> "点亮奖励 ${a.reward} 片叶子"
                }
                T(text, 14.sp, c.ink)
            }
            if (a.item != null && on) {
                Spacer(Modifier.height(14.dp))
                InkButton("去装扮里用上", Modifier.fillMaxWidth(), height = 46.dp) {
                    onDismiss()
                    Nav.dress(Shop.rewardOf(a.id)?.let { Nav.dressTabFor(it) } ?: 0)
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("关闭") { onDismiss() }
        }
    }
}
