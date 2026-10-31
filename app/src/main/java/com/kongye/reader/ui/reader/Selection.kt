package com.kongye.reader.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Dict
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.blockTouches
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import kotlin.math.max
import kotlin.math.min

/** 选中的一段字：某章某页里 [start, end)（章节正文偏移）；originY 是这一页排版原点在屏幕上的位置 */
data class Sel(val cp: ChapterPages, val page: Int, val start: Int, val end: Int, val originY: Float) {
    val ref: PageRef get() = cp.pages[page]
    val layout: TextLayoutResult get() = cp.layouts[ref.chunk]

    fun on(cp: ChapterPages, page: Int) = this.cp === cp && this.page == page

    val layoutStart get() = cp.toLayout(ref.chunk, start)
    val layoutEnd get() = cp.toLayout(ref.chunk, end)
}

/**
 * 选字浮层：两端的拖动把手、选区上方的工具条、默认弹在选区下方的字典卡片。
 * 盖在页面上面，点空白处取消选择
 */
@Composable
fun SelectionLayer(
    sel: Sel,
    originX: Float,
    safeTop: Float,
    safeBottom: Float,
    entries: List<Dict.Entry>?,
    dragging: Boolean,
    onDragging: (Boolean) -> Unit,
    onChange: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onDict: () -> Unit,
    onShare: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val c = K.c
    val density = LocalDensity.current
    val layout = sel.layout
    val ref = sel.ref
    fun screen(x: Float, y: Float) = Offset(originX + x, sel.originY + y - ref.top)
    val sb = layout.getBoundingBox(sel.layoutStart.coerceIn(0, layout.layoutInput.text.length - 1))
    val eb = layout.getBoundingBox((sel.layoutEnd - 1).coerceIn(0, layout.layoutInput.text.length - 1))
    val startTop = screen(sb.left, sb.top)
    val startBottom = screen(sb.left, sb.bottom)
    val endTop = screen(eb.right, eb.top)
    val endBottom = screen(eb.right, eb.bottom)
    val knob = with(density) { 9.dp.toPx() }
    val startKnob = Offset(startBottom.x, startBottom.y + knob)
    val endKnob = Offset(endBottom.x, endBottom.y + knob)
    val bounds = Rect(
        if (startTop.y == endTop.y) startTop.x else originX,
        startTop.y,
        if (startTop.y == endTop.y) endBottom.x else originX + layout.size.width,
        endBottom.y + knob * 2,
    )

    val cur by rememberUpdatedState(sel)
    val knobs by rememberUpdatedState(startKnob to endKnob)
    val anchors by rememberUpdatedState(Offset(startBottom.x, (startTop.y + startBottom.y) / 2) to Offset(endBottom.x, (endTop.y + endBottom.y) / 2))
    val change by rememberUpdatedState(onChange)

    // 屏幕坐标 → 这一页里的光标位置（章节正文偏移，限制在本页内）
    fun caretAt(p: Offset): Int {
        val s = cur
        val r = s.ref
        val local = Offset(p.x - originX, (p.y - s.originY + r.top).coerceIn(r.top + 1f, r.bottom - 1f))
        val o = s.layout.getOffsetForPosition(local)
        return s.cp.toBody(r.chunk, o).coerceIn(r.start, r.end)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val (ks, ke) = knobs
                    val reach = 26.dp.toPx()
                    val ds = (down.position - ks).getDistance()
                    val de = (down.position - ke).getDistance()
                    val which = when {
                        ds < reach && ds <= de -> 0
                        de < reach -> 1
                        else -> -1
                    }
                    if (which < 0) {
                        if (waitForUpOrCancellation() != null) onClear()
                        return@awaitEachGesture
                    }
                    down.consume()
                    val (as_, ae) = anchors
                    val grab = (if (which == 0) as_ else ae) - down.position
                    onDragging(true)
                    drag(down.id) { ch ->
                        ch.consume()
                        val o = caretAt(ch.position + grab)
                        val s = cur
                        if (which == 0) change(min(o, s.end - 1).coerceAtLeast(s.ref.start), s.end)
                        else change(s.start, max(o, s.start + 1))
                    }
                    onDragging(false)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bar = 2.dp.toPx()
            drawLine(c.accent, startTop, startBottom, bar)
            drawLine(c.accent, endTop, endBottom, bar)
            drawCircle(c.accent, knob, startKnob)
            drawCircle(c.accent, knob, endKnob)
        }
        if (!dragging) {
            val side = with(density) { 14.dp.roundToPx() }
            val gap = with(density) { 10.dp.roundToPx() }
            Layout(
                content = {
                    Toolbar(onCopy, onDict, onShare, onSelectAll)
                    if (entries != null) DictCard(entries)
                },
            ) { ms, cs ->
                val w = cs.maxWidth
                val tb = ms[0].measure(Constraints())
                val card = ms.getOrNull(1)?.measure(Constraints(maxWidth = min(w - side * 2, 440.dp.roundToPx()), maxHeight = 270.dp.roundToPx()))
                val top = bounds.top.toInt()
                val bottom = bounds.bottom.toInt()
                val tbAbove = top - gap - tb.height >= safeTop
                val tbY = if (tbAbove) top - gap - tb.height else bottom + gap
                val tbX = (bounds.center.x.toInt() - tb.width / 2).coerceIn(side, max(side, w - side - tb.width))
                var cardY = 0
                if (card != null) {
                    // 字典默认在选区下方；放不下就挪到上方
                    val below = if (tbAbove) bottom + gap else tbY + tb.height + gap
                    cardY = if (below + card.height <= safeBottom) below
                    else ((if (tbAbove) tbY else top) - gap - card.height).coerceAtLeast(safeTop.toInt())
                }
                layout(w, cs.maxHeight) {
                    tb.place(tbX, tbY)
                    card?.place((w - card.width) / 2, cardY)
                }
            }
        }
    }
}

@Composable
private fun Toolbar(onCopy: () -> Unit, onDict: () -> Unit, onShare: () -> Unit, onSelectAll: () -> Unit) {
    val c = K.c
    val shape = SmoothShape(16.dp)
    Row(
        Modifier
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .background(c.ink, shape)
            .blockTouches()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("复制" to onCopy, "字典" to onDict, "分享" to onShare, "全选" to onSelectAll).forEachIndexed { i, (label, f) ->
            if (i > 0) Box(Modifier.size(1.dp, 14.dp).background(c.onInk.copy(alpha = 0.22f)))
            Box(Modifier.press(scaleTo = 0.9f, onClick = f).padding(horizontal = 15.dp, vertical = 11.dp)) {
                T(label, 14.sp, c.onInk, FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DictCard(entries: List<Dict.Entry>) {
    val c = K.c
    val shape = SmoothShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            // 和纸面拉开：底色略深一点、细描边、更重的阴影
            .shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.45f))
            .background(lerp(c.card, c.ink, if (c.dark) 0.06f else 0.035f), shape)
            .border(1.dp, c.ink.copy(alpha = if (c.dark) 0.16f else 0.1f), shape)
            .blockTouches()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
    ) {
        if (entries.isEmpty()) T("没有查到", 15.sp, c.text2)
        entries.forEachIndexed { i, e ->
            if (i > 0) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Spacer(Modifier.height(12.dp))
            }
            EntryView(e)
        }
    }
}

@Composable
private fun KindTag(text: String, accent: Boolean = false) {
    val c = K.c
    Box(
        Modifier
            .background(if (accent) c.accent.copy(alpha = 0.12f) else c.chip, SmoothShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        T(text, 11.sp, if (accent) c.accent else c.text2, FontWeight.Medium)
    }
}

@Composable
private fun EntryView(e: Dict.Entry) {
    val c = K.c
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val big = when (e.kind) {
                Dict.Kind.CHAR -> 30.sp
                Dict.Kind.POEM -> 18.sp
                else -> if (e.word.length > 6) 18.sp else 22.sp
            }
            T(e.word, big, c.ink, FontWeight.Bold, Modifier.weight(1f, fill = false), maxLines = 2)
            if (e.kind == Dict.Kind.EN && e.phonetic.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                T("/${e.phonetic}/", 14.sp, c.text2, maxLines = 1)
            }
            if (e.kind == Dict.Kind.CHAR && e.pinyin.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                T(e.pinyin, 16.sp, c.text2, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (e.kind != Dict.Kind.CHAR && e.kind != Dict.Kind.EN) {
                Spacer(Modifier.width(8.dp))
                KindTag(if (e.found || e.kind != Dict.Kind.WORD) e.kind.label else "未收录", accent = e.kind == Dict.Kind.IDIOM || e.kind == Dict.Kind.POEM)
            }
        }
        if (e.kind != Dict.Kind.CHAR && e.kind != Dict.Kind.EN && e.pinyin.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            T(e.pinyin, 14.sp, c.text2)
        }
        if (e.source.isNotEmpty() && e.kind == Dict.Kind.EN) {
            Spacer(Modifier.height(4.dp))
            T(e.source, 13.sp, c.text3)
        }
        when (e.kind) {
            Dict.Kind.CHAR -> {
                if (!e.found) Line("词典里没有这个字")
                e.readings.filter { it.meaning.isNotEmpty() }.forEach { r ->
                    Spacer(Modifier.height(8.dp))
                    Row {
                        T(r.pinyin, 14.sp, c.ink, FontWeight.SemiBold, Modifier.width(64.dp))
                        T(r.meaning, 14.sp, c.text2, modifier = Modifier.weight(1f), lineHeight = 21.sp)
                    }
                }
            }
            Dict.Kind.POEM -> {
                Spacer(Modifier.height(8.dp))
                T(e.source, 14.sp, c.ink, FontWeight.Medium)
                if (e.extra.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    T(e.extra, 14.sp, c.text2, lineHeight = 21.sp)
                }
            }
            else -> {
                if (e.meaning.isNotEmpty()) Line(e.meaning)
                if (e.source.isNotEmpty() && e.kind != Dict.Kind.EN) {
                    Spacer(Modifier.height(4.dp))
                    T(e.source, 12.sp, c.text3)
                }
                if (!e.found && e.parts.isEmpty()) Line(if (e.kind == Dict.Kind.EN) "词典里没有这个词" else "没有查到")
                e.parts.forEach { p ->
                    Spacer(Modifier.height(8.dp))
                    Row {
                        T(p.word, 16.sp, c.ink, FontWeight.Bold, Modifier.width(26.dp))
                        val r = p.readings.firstOrNull { it.meaning.isNotEmpty() } ?: p.readings.firstOrNull()
                        T(
                            listOfNotNull(r?.pinyin, r?.meaning?.takeIf { it.isNotEmpty() }).joinToString("  "),
                            14.sp, c.text2, modifier = Modifier.weight(1f), lineHeight = 21.sp, maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(text: String) {
    Spacer(Modifier.height(8.dp))
    T(text, 14.sp, K.c.text2, lineHeight = 21.sp)
}
