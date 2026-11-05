package com.kongye.reader.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.AchieveCtx
import com.kongye.reader.data.Achievement
import com.kongye.reader.data.Achievements
import com.kongye.reader.data.Library
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.Sheet
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 徽章的成色：按奖励多少分铜、银、金，藏起来的那个是紫色 */
private enum class Tier(val name0: String, val light: Color, val dark: Color) {
    BRONZE("铜", Color(0xFFE2A672), Color(0xFF9A5B2E)),
    SILVER("银", Color(0xFFE6EBF0), Color(0xFF8A96A3)),
    GOLD("金", Color(0xFFFFDF7A), Color(0xFFC08A12)),
    SECRET("秘", Color(0xFFD7C2FF), Color(0xFF6A43C9)),
}

private fun tierOf(a: Achievement) = when {
    a.hidden -> Tier.SECRET
    a.reward >= 200 -> Tier.GOLD
    a.reward >= 60 -> Tier.SILVER
    else -> Tier.BRONZE
}

/** 每个成就徽章中间的图案 */
private val badgeIco = mapOf(
    "first_read" to Ico.BOOK, "read_1h" to Ico.CLOCK, "read_10h" to Ico.CLOCK, "read_50h" to Ico.CLOCK,
    "read_100h" to Ico.CROWN, "day_30m" to Ico.LEAF, "day_2h" to Ico.SUN, "marathon" to Ico.TARGET,
    "night_owl" to Ico.MOON, "early_bird" to Ico.SUN_SMALL,
    "streak_3" to Ico.FLAME, "streak_7" to Ico.FLAME, "streak_30" to Ico.FLAME, "garden_10" to Ico.PAW,
    "shelf_1" to Ico.BOOKMARK, "shelf_10" to Ico.BOOKMARK_ON, "finish_1" to Ico.CHECK, "finish_5" to Ico.STAR,
    "finish_10" to Ico.CROWN,
    "animal_1" to Ico.PAW, "animal_5" to Ico.PAW, "animal_all" to Ico.CROWN, "bg_3" to Ico.SPROUT,
    "acc_10" to Ico.HANGER, "dress_1" to Ico.HANGER, "scene_6" to Ico.GIFT,
    "dict_10" to Ico.SEARCH, "dict_100" to Ico.SEARCH, "edit_1" to Ico.EDIT, "rename" to Ico.KEY,
    "backup_1" to Ico.CLOUD, "egg" to Ico.GIFT,
)

/** 成就：像游戏里的徽章墙，达成的徽章亮起来，没达成的灰着并显示进度 */
@Composable
fun AchievementsScreen() {
    val c = K.c
    val profile by Profiles.state.collectAsState()
    val sessions by Stats.sessions.collectAsState()
    val books by Library.books.collectAsState()
    val ctx = remember(profile, sessions, books) { AchieveCtx(sessions, books, profile) }
    val done = Achievements.all.count { it.id in profile.achievements }
    val earned = Achievements.all.filter { it.id in profile.achievements }.sumOf { it.reward }
    var open by remember { mutableStateOf<Achievement?>(null) }
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("成就", onBack = { Nav.pop() })
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navPad + 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Summary(done, Achievements.all.size, earned) }
                for (group in Achievements.all.map { it.group }.distinct()) {
                    val list = Achievements.all.filter { it.group == group }
                    item(key = "g-$group", span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
                            T(group, 17.sp, c.ink, FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            T("${list.count { it.id in profile.achievements }} / ${list.size}", 12.sp, c.text3)
                        }
                    }
                    items(list, key = { it.id }) { a ->
                        val at = profile.achievements[a.id]
                        val (cur, goal) = a.progress(ctx)
                        Column(
                            Modifier.press(scaleTo = 0.92f) { open = a },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Badge(a, at != null, (cur.toFloat() / goal).coerceIn(0f, 1f), 66.dp)
                            Spacer(Modifier.height(6.dp))
                            val secret = a.hidden && at == null
                            T(
                                if (secret) "？？？" else a.title, 12.sp, if (at != null) c.ink else c.text3,
                                if (at != null) FontWeight.Bold else FontWeight.Normal, maxLines = 1, align = TextAlign.Center,
                            )
                            if (at == null && !secret && goal > 1) {
                                T("${cur.coerceAtMost(goal)}/$goal", 10.sp, c.text3, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        DetailSheet(open, ctx, profile.achievements) { open = null }
    }
}

@Composable
private fun Summary(done: Int, total: Int, earned: Int) {
    val c = K.c
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.ink, SmoothShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T("成就徽章", 13.sp, c.onInk.copy(alpha = 0.7f))
            Row(verticalAlignment = Alignment.Bottom) {
                T("$done", 34.sp, c.onInk, FontWeight.Black)
                T(" / $total", 16.sp, c.onInk.copy(alpha = 0.6f), FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(c.onInk.copy(alpha = 0.18f), CircleShape)) {
                Box(
                    Modifier
                        .fillMaxWidth(done.toFloat() / total.coerceAtLeast(1))
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(Tier.GOLD.light, Tier.GOLD.dark)), CircleShape),
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Ico.LEAF, size = 22.dp, color = Tier.GOLD.light)
            T("+$earned", 15.sp, c.onInk, FontWeight.Bold)
            T("成就奖励", 10.sp, c.onInk.copy(alpha = 0.6f))
        }
    }
}

/**
 * 一枚徽章：达成后是带花边的金属奖章，外圈有光；没达成是灰色圆片，外圈一圈进度。
 */
@Composable
private fun Badge(a: Achievement, on: Boolean, progress: Float, size: Dp) {
    val c = K.c
    val tier = tierOf(a)
    val secret = a.hidden && !on
    val ico = if (secret) Ico.LOCK else badgeIco[a.id] ?: Ico.TROPHY
    // 亮着的徽章：光晕慢慢呼吸
    val glow = if (on) {
        val t by rememberInfiniteTransition(label = "glow").animateFloat(
            0.55f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "glow",
        )
        t
    } else 0f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val o = center
            if (on) {
                // 光晕
                drawCircle(Brush.radialGradient(listOf(tier.light.copy(alpha = 0.55f * glow), Color.Transparent), o, r), r, o)
                // 花边外圈（16 个圆齿）
                val path = Path()
                val n = 16
                for (i in 0..n * 4) {
                    val ang = 2 * PI * i / (n * 4)
                    val rr = r * (0.86f + 0.05f * cos(n * ang).toFloat())
                    val p = Offset(o.x + rr * cos(ang).toFloat(), o.y + rr * sin(ang).toFloat())
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, Brush.linearGradient(listOf(tier.light, tier.dark, tier.light), Offset(0f, 0f), Offset(this.size.width, this.size.height)))
                // 内圈：深色底
                drawCircle(Brush.linearGradient(listOf(Color(0xFF2C2C30), Color(0xFF111113)), Offset(0f, 0f), Offset(0f, this.size.height)), r * 0.66f, o)
                drawCircle(tier.light.copy(alpha = 0.8f), r * 0.66f, o, style = Stroke(r * 0.04f))
                // 高光
                rotate(-35f, o) {
                    drawArc(Color.White.copy(alpha = 0.35f), 200f, 70f, false, Offset(o.x - r * 0.76f, o.y - r * 0.76f), Size(r * 1.52f, r * 1.52f), style = Stroke(r * 0.06f, cap = StrokeCap.Round))
                }
            } else {
                drawCircle(c.chip, r * 0.86f, o)
                drawCircle(c.bg, r * 0.68f, o)
                // 进度弧
                drawCircle(c.text3.copy(alpha = 0.25f), r * 0.77f, o, style = Stroke(r * 0.1f))
                if (progress > 0f && !secret) {
                    drawArc(
                        c.text2, -90f, 360f * progress, false,
                        Offset(o.x - r * 0.77f, o.y - r * 0.77f), Size(r * 1.54f, r * 1.54f),
                        style = Stroke(r * 0.1f, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Icon(ico, size = size * 0.36f, color = if (on) tier.light else c.text3)
    }
}

@Composable
private fun DetailSheet(a: Achievement?, ctx: AchieveCtx, done: Map<String, Long>, onDismiss: () -> Unit) {
    val c = K.c
    val last = remember { arrayOfNulls<Achievement>(1) }
    a?.let { last[0] = it }
    val fmt = remember { SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINA) }
    Sheet(visible = a != null, onDismiss = onDismiss) {
        val item = last[0] ?: return@Sheet
        val at = done[item.id]
        val secret = item.hidden && at == null
        val (cur, goal) = item.progress(ctx)
        val tier = tierOf(item)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge(item, at != null, (cur.toFloat() / goal).coerceIn(0f, 1f), 128.dp)
            Spacer(Modifier.height(10.dp))
            T(if (secret) "？？？" else item.title, 22.sp, c.ink, FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            T("${item.group} · ${tier.name0}徽章", 12.sp, c.text3)
            Spacer(Modifier.height(10.dp))
            T(if (secret) "藏起来的成就，去“关于”里找找看" else item.desc, 14.sp, c.text2, align = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            when {
                at != null -> T("${fmt.format(Date(at))} 点亮", 13.sp, c.ink, FontWeight.SemiBold)
                !secret && goal > 1 -> {
                    Box(Modifier.width(220.dp).height(6.dp).background(c.chip, CircleShape)) {
                        Box(Modifier.fillMaxWidth((cur.toFloat() / goal).coerceIn(0f, 1f)).fillMaxHeight().background(c.ink, CircleShape))
                    }
                    Spacer(Modifier.height(6.dp))
                    T("${cur.coerceAtMost(goal)} / $goal", 12.sp, c.text2)
                }
                else -> T("还没点亮", 13.sp, c.text3)
            }
            if (item.reward > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.LEAF, size = 14.dp, color = c.accent)
                    Spacer(Modifier.width(4.dp))
                    T(if (at != null) "已得 ${item.reward} 片叶子" else "点亮奖励 ${item.reward} 片叶子", 13.sp, c.ink)
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("关闭") { onDismiss() }
        }
    }
}
