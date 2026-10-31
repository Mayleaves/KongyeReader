package com.kongye.reader.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Library
import com.kongye.reader.data.Session
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.Card
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.CatPose
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.Segmented
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.bigDuration
import com.kongye.reader.ui.components.drawCatBar
import com.kongye.reader.ui.theme.K
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min

private enum class Period(val label: String) { WEEK("周"), MONTH("月"), YEAR("年"), ALL("总") }

private class Bar(val label: String, val value: Long, val current: Boolean, val future: Boolean, val showLabel: Boolean = true)

private class StatsData(
    val label: String,
    val word: String,
    val title: String,
    val total: Long,
    val days: Int,
    val count: Int,
    val finished: Int,
    val bars: List<Bar>,
    val avg: Long,
    val avgLabel: String,
    val dist: List<Pair<String, Long>>,
)

private fun compute(list: List<Session>, p: Period, anchor: LocalDate): StatsData {
    val today = Stats.today()
    val (from, to) = when (p) {
        Period.WEEK -> Stats.monday(anchor).let { it to it.plusDays(6) }
        Period.MONTH -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
        Period.YEAR -> anchor.withDayOfYear(1) to anchor.withDayOfYear(anchor.lengthOfYear())
        Period.ALL -> (list.minOfOrNull { Stats.date(it.start) } ?: today) to today
    }
    val inRange = list.filter { val d = Stats.date(it.start); d >= from && d <= to }
    val byDay = Stats.byDay(inRange)
    val total = inRange.sumOf { it.millis }
    val bars = when (p) {
        Period.WEEK -> (0..6).map { val d = from.plusDays(it.toLong()); Bar(Fmt.weekday(d), byDay[d] ?: 0, d == today, d > today) }
        Period.MONTH -> (0 until from.lengthOfMonth()).map {
            val d = from.plusDays(it.toLong())
            Bar("${d.dayOfMonth}", byDay[d] ?: 0, d == today, d > today, d.dayOfMonth == 1 || d.dayOfMonth % 5 == 0)
        }
        Period.YEAR -> (1..12).map { m ->
            Bar("${m}月", byDay.filterKeys { it.monthValue == m }.values.sum(), from.year == today.year && m == today.monthValue, YearMonth.of(from.year, m).atDay(1) > today)
        }
        Period.ALL -> (from.year..today.year).map { y -> Bar("$y", byDay.filterKeys { it.year == y }.values.sum(), y == today.year, false) }
    }
    val elapsed = bars.count { !it.future }.coerceAtLeast(1)
    val isNow = when (p) {
        Period.WEEK -> Stats.monday(anchor) == Stats.monday(today)
        Period.MONTH -> anchor.year == today.year && anchor.monthValue == today.monthValue
        Period.YEAR -> anchor.year == today.year
        Period.ALL -> true
    }
    val word = when (p) {
        Period.WEEK -> if (isNow) "本周" else "当周"
        Period.MONTH -> if (isNow) "本月" else "当月"
        Period.YEAR -> if (isNow) "今年" else "当年"
        Period.ALL -> "累计"
    }
    val label = when (p) {
        Period.WEEK -> "${Fmt.md(from)} - ${Fmt.md(to)}"
        Period.MONTH -> "${from.year}年${from.monthValue}月"
        Period.YEAR -> "${from.year}年"
        Period.ALL -> "全部记录"
    }
    val dist = inRange.groupBy { it.bookId }
        .map { (id, ss) -> (Library.get(id)?.title ?: ss.last().title) to ss.sumOf { it.millis } }
        .sortedByDescending { it.second }
    return StatsData(
        label = label,
        word = word,
        title = when (p) { Period.WEEK -> "周概览"; Period.MONTH -> "月概览"; Period.YEAR -> "年概览"; Period.ALL -> "总概览" },
        total = total,
        days = byDay.count { it.value > 0 },
        count = inRange.size,
        finished = inRange.count { it.finished },
        bars = bars,
        avg = total / elapsed,
        avgLabel = when (p) { Period.WEEK, Period.MONTH -> "日均"; Period.YEAR -> "月均"; Period.ALL -> "年均" },
        dist = dist,
    )
}

@Composable
fun StatsScreen() {
    val c = K.c
    val sessions by Stats.sessions.collectAsState()
    var period by rememberSaveable { mutableIntStateOf(0) }
    val today = Stats.today()
    var anchor by remember { mutableStateOf(today) }
    val p = Period.entries[period]
    val data = remember(sessions, period, anchor) { compute(sessions, p, anchor) }
    val streak = remember(sessions) { Stats.streak(sessions) }

    fun shifted(dir: Long): LocalDate = when (p) {
        Period.WEEK -> anchor.plusWeeks(dir)
        Period.MONTH -> anchor.plusMonths(dir)
        Period.YEAR -> anchor.plusYears(dir)
        Period.ALL -> anchor
    }
    val canForward = p != Period.ALL && when (p) {
        Period.WEEK -> Stats.monday(shifted(1)) <= today
        Period.MONTH -> shifted(1).withDayOfMonth(1) <= today
        else -> shifted(1).withDayOfYear(1) <= today
    }

    Column(Modifier.fillMaxSize().background(c.bg2)) {
        TopBar("统计", onBack = { Nav.pop() }) {
            IconBtn(Ico.TIMELINE) { Nav.push(Screen.Timeline) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Segmented(Period.entries.map { it.label }, period, Modifier.fillMaxWidth()) {
                period = it
                anchor = today
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ico.CHEVRON_LEFT, color = if (p == Period.ALL) c.text3 else c.text2, size = 20.dp) { if (p != Period.ALL) anchor = shifted(-1) }
                Spacer(Modifier.weight(1f))
                T(data.label, 14.sp, c.text2, FontWeight.Medium)
                if (p != Period.ALL && anchor != today) {
                    IconBtn(Ico.TARGET, color = c.text2, size = 18.dp) { anchor = today }
                }
                Spacer(Modifier.weight(1f))
                IconBtn(Ico.CHEVRON_RIGHT, color = if (canForward) c.text2 else c.text3, size = 20.dp) { if (canForward) anchor = shifted(1) }
            }
            Overview(data, streak)
            Spacer(Modifier.height(12.dp))
            Card {
                T("趋势", 17.sp, c.ink, FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                T("${data.avgLabel} ${Fmt.hm(data.avg)}", 12.sp, c.text2)
                Spacer(Modifier.height(8.dp))
                TrendChart(data.bars, data.avg, Modifier.fillMaxWidth().height(210.dp))
            }
            Spacer(Modifier.height(12.dp))
            Distribution(data)
            Spacer(Modifier.height(12.dp))
            HeatmapCard(sessions)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Overview(data: StatsData, streak: Int) {
    val c = K.c
    Card {
        T(data.title, 17.sp, c.ink, FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Row {
            Metric("${data.word}阅读时长", bigDuration(data.total, 40.sp, 17.sp), 40)
            Metric("${data.word}阅读天数", AnnotatedString("${data.days}"), 40)
        }
        Spacer(Modifier.height(18.dp))
        Row {
            Metric("阅读次数", AnnotatedString("${data.count}"), 26)
            Metric("平均每次时长", bigDuration(if (data.count > 0) data.total / data.count else 0, 26.sp, 14.sp), 26)
        }
        Spacer(Modifier.height(18.dp))
        Row {
            Metric("读完的书", AnnotatedString("${data.finished}"), 26)
            Metric(
                "连续阅读",
                buildAnnotatedString {
                    append("$streak")
                    withStyle(SpanStyle(fontSize = 14.sp)) { append("天") }
                },
                26,
            )
        }
    }
}

@Composable
private fun RowScope.Metric(label: String, value: AnnotatedString, size: Int) {
    val c = K.c
    Column(Modifier.weight(1f)) {
        T(label, 13.sp, c.text2)
        Spacer(Modifier.height(4.dp))
        T(value, size.sp, c.ink, FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun TrendChart(bars: List<Bar>, avg: Long, modifier: Modifier) {
    val c = K.c
    val measurer = rememberTextMeasurer()
    var sel by remember(bars) {
        val cur = bars.indexOfFirst { it.current && it.value > 0 }
        mutableIntStateOf(if (cur >= 0) cur else bars.indices.maxByOrNull { bars[it].value }?.takeIf { bars[it].value > 0 } ?: -1)
    }
    val maxBar = bars.maxOfOrNull { it.value } ?: 0
    Canvas(
        modifier.pointerInput(bars) {
            detectTapGestures { o ->
                val i = (o.x / (size.width.toFloat() / bars.size)).toInt().coerceIn(bars.indices)
                sel = if (bars[i].value > 0) i else -1
            }
        },
    ) {
        val n = bars.size
        val labelH = 24.dp.toPx()
        val topPad = 58.dp.toPx()
        val base = size.height - labelH
        val slot = size.width / n
        val heads = n <= 12
        val w = if (heads) min(slot * 0.62f, 30.dp.toPx()) else slot * 0.56f
        val maxV = max(maxBar, avg).coerceAtLeast(1L).toFloat()
        val avail = base - topPad
        val tops = FloatArray(n)
        bars.forEachIndexed { i, b ->
            val x = i * slot + (slot - w) / 2
            val gray = b.value == 0L || b.value < avg * 0.5f
            val col = if (gray) c.track else c.ink
            if (heads) {
                val h = max(w * 0.9f, b.value / maxV * avail)
                tops[i] = base - h
                if (!b.future) drawCatBar(x, base - h, base, w, col, if (gray) c.card else c.eye, gray, if (b.value == maxBar && b.value > 0) c.accent else null)
            } else {
                val h = max(3.dp.toPx(), b.value / maxV * avail)
                tops[i] = base - h
                if (!b.future) drawRoundRect(if (b.current) c.accent else col, Offset(x, base - h), Size(w, h), CornerRadius(w / 2))
            }
        }
        drawLine(c.ink, Offset(0f, base), Offset(size.width, base), 1.2.dp.toPx())
        if (avg > 0) {
            val y = base - avg / maxV * avail
            drawLine(c.accent, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        bars.forEachIndexed { i, b ->
            if (!b.showLabel) return@forEachIndexed
            val lay = measurer.measure(b.label, TextStyle(fontSize = if (n > 7) 10.sp else 12.sp, color = if (b.current) c.ink else c.text2, fontWeight = if (b.current) FontWeight.Bold else FontWeight.Medium))
            drawText(lay, topLeft = Offset(i * slot + slot / 2 - lay.size.width / 2, base + 7.dp.toPx()))
        }
        if (sel in bars.indices && bars[sel].value > 0) {
            val lay = measurer.measure(Fmt.hm(bars[sel].value), TextStyle(fontSize = 11.sp, color = c.onInk, fontWeight = FontWeight.Bold))
            val pw = lay.size.width + 14.dp.toPx()
            val ph = lay.size.height + 8.dp.toPx()
            val cx = (sel * slot + slot / 2).coerceIn(pw / 2, size.width - pw / 2)
            val bottom = tops[sel] - (if (heads) w * 0.95f else 0f) - 6.dp.toPx()
            drawRoundRect(c.ink, Offset(cx - pw / 2, bottom - ph), Size(pw, ph), CornerRadius(8.dp.toPx()))
            drawText(lay, topLeft = Offset(cx - lay.size.width / 2, bottom - ph + 4.dp.toPx()))
        }
    }
}

@Composable
private fun Distribution(data: StatsData) {
    val c = K.c
    val shades = if (c.dark) listOf(Color(0xFFF2F2F2), Color(0xFFB0B0B4), Color(0xFF8E8E93), Color(0xFF5C5C60), Color(0xFF3A3A3C))
    else listOf(Color(0xFF1F1F1F), Color(0xFF5C5C5C), Color(0xFF8E8E93), Color(0xFFC4C4C8), Color(0xFFE2E2E5))
    val parts = remember(data) {
        if (data.dist.size <= 5) data.dist
        else data.dist.take(4) + ("其他" to data.dist.drop(4).sumOf { it.second })
    }
    val measurer = rememberTextMeasurer()
    Card {
        T("分布", 17.sp, c.ink, FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (parts.isEmpty()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CatMascot(size = 96.dp, pose = CatPose.LOAF, sleep = true)
                Spacer(Modifier.height(6.dp))
                T("这段时间还没有阅读记录", 13.sp, c.text2)
            }
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(132.dp)) {
                val sw = 22.dp.toPx()
                val total = data.total.coerceAtLeast(1L).toFloat()
                var start = -90f
                val gap = if (parts.size > 1) 2f else 0f
                parts.forEachIndexed { i, (_, v) ->
                    val sweep = 360f * v / total
                    drawArc(
                        shades[i % shades.size], start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                        Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw),
                    )
                    start += sweep
                }
                val big = measurer.measure(bigDuration(data.total, 20.sp, 11.sp), TextStyle(color = c.ink, fontWeight = FontWeight.Black))
                val small = measurer.measure("合计", TextStyle(fontSize = 11.sp, color = c.text2))
                val th = big.size.height + small.size.height
                drawText(big, topLeft = Offset(size.width / 2 - big.size.width / 2, size.height / 2 - th / 2))
                drawText(small, topLeft = Offset(size.width / 2 - small.size.width / 2, size.height / 2 - th / 2 + big.size.height))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                parts.forEachIndexed { i, (name, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(shades[i % shades.size], SmoothShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            T(Fmt.hm(v), 14.sp, c.ink, FontWeight.Bold)
                            T(name, 12.sp, c.text2, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(sessions: List<Session>) {
    val c = K.c
    val byDay = remember(sessions) { Stats.byDay(sessions) }
    val today = Stats.today()
    val thisYear = remember(byDay) { byDay.count { it.key.year == today.year && it.value > 0 } }
    val levels = if (c.dark) listOf(Color(0xFF2A2A2C), Color(0xFF3A3A3C), Color(0xFF5C5C60), Color(0xFF8E8E93), Color(0xFFF2F2F2))
    else listOf(Color(0xFFEFEFF1), Color(0xFFD3D3D6), Color(0xFFA3A3A8), Color(0xFF6B6B70), Color(0xFF1F1F1F))
    fun level(ms: Long) = when {
        ms <= 0 -> 0
        ms < 15 * 60_000 -> 1
        ms < 30 * 60_000 -> 2
        ms < 60 * 60_000 -> 3
        else -> 4
    }
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            T("热力图", 17.sp, c.ink, FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            T("$thisYear / ${today.dayOfYear}", 12.sp, c.text2)
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(118.dp)) {
            val gap = 4.dp.toPx()
            val cell = (size.height - gap * 6) / 7
            val cols = ((size.width + gap) / (cell + gap)).toInt().coerceAtLeast(1)
            val left = (size.width - (cols * cell + (cols - 1) * gap)) / 2
            val start = Stats.monday(today).minusWeeks((cols - 1).toLong())
            for (col in 0 until cols) for (row in 0 until 7) {
                val d = start.plusDays((col * 7 + row).toLong())
                if (d > today) continue
                val color = if (d == today) c.accent else levels[level(byDay[d] ?: 0)]
                drawRoundRect(color, Offset(left + col * (cell + gap), row * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.28f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("少", 11.sp, c.text2)
            Spacer(Modifier.width(6.dp))
            levels.forEach {
                Box(Modifier.padding(horizontal = 2.dp).size(10.dp).background(it, SmoothShape(3.dp)))
            }
            Spacer(Modifier.width(6.dp))
            T("多", 11.sp, c.text2)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).background(c.accent, SmoothShape(3.dp)))
            Spacer(Modifier.width(4.dp))
            T("今天", 11.sp, c.text2)
        }
    }
}
