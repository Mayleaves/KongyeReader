package com.kongye.reader.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Library
import com.kongye.reader.data.Session
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.BookCover
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.CatPose
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import java.time.LocalDate
import kotlin.math.abs

/** 时间轴：一周日期条 + 当天每次阅读，参考小鸡专注的时间轴页 */
@Composable
fun TimelineScreen() {
    val c = K.c
    val sessions by Stats.sessions.collectAsState()
    val today = Stats.today()
    var day by remember { mutableStateOf(today) }
    val list = remember(sessions, day) { sessions.filter { Stats.date(it.start) == day }.sortedBy { it.start } }
    val byDay = remember(sessions) { Stats.byDay(sessions) }
    val total = list.sumOf { it.millis }
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun shiftWeek(w: Long) {
        val d = day.plusWeeks(w)
        day = if (d > today) today else d
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("时间轴", onBack = { Nav.pop() })
        WeekStrip(Stats.monday(day), day, today, byDay, onPick = { day = it }, onShift = ::shiftWeek)
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            T("${Fmt.md(day)} ${Fmt.weekday(day)}", 22.sp, c.ink, FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (list.isNotEmpty()) T("${list.size}次 · ${Fmt.cn(total)}", 14.sp, c.ink, FontWeight.SemiBold)
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CatMascot(size = 130.dp, pose = CatPose.LOAF, sleep = true)
                Spacer(Modifier.height(10.dp))
                T(if (day == today) "今天还没有翻书" else "这天没有阅读记录", 15.sp, c.text2)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = navPad + 24.dp)) {
                itemsIndexed(list) { i, s -> TimelineItem(s, i == list.lastIndex) }
            }
        }
    }
}

@Composable
private fun WeekStrip(monday: LocalDate, day: LocalDate, today: LocalDate, byDay: Map<LocalDate, Long>, onPick: (LocalDate) -> Unit, onShift: (Long) -> Unit) {
    val c = K.c
    val swipe = with(LocalDensity.current) { 60.dp.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .pointerInput(monday) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = { if (abs(acc) > swipe) onShift(if (acc < 0) 1 else -1) },
                ) { ch, dx ->
                    ch.consume()
                    acc += dx
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Ico.CHEVRON_LEFT, color = c.text2, size = 18.dp) { onShift(-1) }
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val sel = d == day
            val future = d > today
            val bg = when {
                sel -> c.accent
                d == today -> c.chip
                else -> Color.Transparent
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .press(enabled = !future) { onPick(d) }
                    .background(bg, SmoothShape(16.dp))
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(Fmt.weekday(d), 12.sp, if (sel) Color.White else c.text2)
                Spacer(Modifier.height(3.dp))
                T("${d.dayOfMonth}", 20.sp, if (sel) Color.White else if (future) c.text3 else c.ink, FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.size(4.dp).background(if ((byDay[d] ?: 0) > 0 && !sel) c.ink else Color.Transparent, CircleShape))
            }
        }
        val canNext = Stats.monday(today) > monday
        IconBtn(Ico.CHEVRON_RIGHT, color = if (canNext) c.text2 else c.text3, size = 18.dp) { if (canNext) onShift(1) }
    }
}

@Composable
private fun TimelineItem(s: Session, isLast: Boolean) {
    val c = K.c
    val book = remember(s.bookId) { Library.get(s.bookId) }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(20.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = 12.dp.toPx()
                if (!isLast) {
                    drawLine(
                        c.text3, Offset(cx, cy + 9.dp.toPx()), Offset(cx, size.height), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    )
                }
                drawCircle(if (s.finished) c.accent else c.ink, 6.dp.toPx(), Offset(cx, cy), style = Stroke(2.2.dp.toPx()))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${Fmt.time(s.start)}～${Fmt.time(s.end)}", 17.sp, c.ink, FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                T(Fmt.cn(s.millis), 13.sp, c.text2)
            }
            Spacer(Modifier.height(10.dp))
            val shape = SmoothShape(18.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .press(enabled = book != null, scaleTo = 0.98f) { book?.let { Nav.openBook(it.id) } }
                    .background(if (s.finished) c.accent.copy(alpha = 0.08f) else c.chip, shape)
                    .then(if (s.finished) Modifier.border(1.dp, c.accent.copy(alpha = 0.35f), shape) else Modifier)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (book != null) BookCover(book, Modifier.width(38.dp), 7.sp)
                else CatMascot(size = 40.dp, shadow = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(s.title, 16.sp, c.ink, FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    val where = when {
                        s.fromChapter.isEmpty() -> ""
                        s.fromChapter == s.toChapter || s.toChapter.isEmpty() -> "读到「${s.fromChapter}」"
                        else -> "「${s.fromChapter}」→「${s.toChapter}」"
                    }
                    val desc = listOfNotNull(where.ifEmpty { null }, if (s.pages > 0) "翻了 ${s.pages} 页" else null, if (s.finished) "读完了全书" else null)
                        .joinToString(" · ")
                    T(desc.ifEmpty { "随便翻了翻" }, 13.sp, c.text2, maxLines = 2, lineHeight = 18.sp)
                }
            }
        }
    }
}
