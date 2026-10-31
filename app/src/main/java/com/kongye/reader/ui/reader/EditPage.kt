package com.kongye.reader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.ui.components.ConfirmDialog
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.blockTouches
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 编辑本章。布局参考 Notepad++：顶上是标签页和撤销、重做、查找，左边距里是段号，当前段铺一层浅色，底下是状态栏。
 * 正文的字体、字号、行距、段距、缩进、版心宽度都和阅读页一样，打开时正在读的那一行停在原来的高度，改到哪一眼就能看到。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditPage(
    spec: PageSpec,
    textColor: Color,
    textLeft: Int,
    textTop: Int,
    title: String,
    body: String,
    cursor: Int,
    alignTitle: Boolean,
    saving: Boolean,
    onClose: () -> Unit,
    onSave: (title: String, body: String, cursor: Int) -> Unit,
) {
    val c = K.c
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val titleState = rememberTextFieldState(title)
    val bodyState = rememberTextFieldState(body, TextRange(cursor.coerceIn(0, body.length)))
    val query = rememberTextFieldState()
    val replacement = rememberTextFieldState()
    var confirm by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    val changed by remember { derivedStateOf { titleState.text.toString() != title || bodyState.text.toString() != body } }
    fun close() {
        if (changed) confirm = true else onClose()
    }
    BackHandler {
        if (finding) finding = false else close()
    }

    val bodyStyle = remember(spec, textColor) { bodyTextStyle(spec).copy(color = textColor) }
    val titleStyle = remember(spec, textColor) { titleTextStyle(spec).copy(color = textColor) }
    val textW = with(density) { spec.width.toDp() }
    val leftDp = with(density) { textLeft.toDp() }

    // —— 查找 ——
    val q = query.text.toString()
    val matches by remember { derivedStateOf { allMatches(bodyState.text, query.text.toString()) } }
    val sel = bodyState.selection
    val current = if (q.isNotEmpty() && sel.length == q.length && sel.start in matches) sel.start else -1

    // 两端对齐：逐段按阅读页同样的办法算字间距，按段落内容缓存，打字时只重算正在改的那一段
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val emPx = with(density) { spec.fontSize.sp.toPx() }
    val toSp = spec.density * spec.fontScale
    val spacing = remember(spec, bodyStyle) { HashMap<String, List<Triple<Int, Int, Float>>>() }
    fun spacingOf(para: String): List<Triple<Int, Int, Float>> {
        if (para.length < 2) return emptyList()
        if (spacing.size > 4000) spacing.clear()
        return spacing.getOrPut(para) {
            val cons = Constraints(maxWidth = spec.width)
            val plain = measurer.measure(para, bodyStyle, constraints = cons)
            val lines = justifyLines(plain, para, spec.width, emPx)
            if (lines.isEmpty()) return@getOrPut emptyList()
            val styled = measurer.measure(withSpacing(AnnotatedString(para), lines, spec), bodyStyle, constraints = cons)
            if (sameBreaks(plain, styled)) lines else emptyList()
        }
    }

    // 显示时：每行补字间距、段落之间的换行换成段距占位（都和阅读页同一个办法），查找结果铺底色
    val gap = remember(spec) { gapParagraphStyle(spec) }
    val spaced = spec.paraSpacing > 0.01f
    val hl = c.accent.copy(alpha = 0.18f)
    val hlCur = c.accent.copy(alpha = 0.42f)
    val output = remember(spec, spaced, gap, q, current, hl) {
        OutputTransformation {
            val raw = asCharSequence().toString()
            var start = 0
            while (true) {
                val nl = raw.indexOf('\n', start)
                val end = if (nl < 0) raw.length else nl
                for ((s, e, ls) in spacingOf(raw.substring(start, end))) addStyle(SpanStyle(letterSpacing = (ls / toSp).sp), start + s, start + e)
                if (nl < 0) break
                start = nl + 1
            }
            if (spaced) {
                val cs = asCharSequence()
                val at = ArrayList<Int>()
                for (k in 0 until length) if (cs[k] == '\n') at += k
                for (k in at) {
                    replace(k, k + 1, PARA_GAP.toString())
                    addStyle(gap, k, k + 1)
                    addStyle(SpanStyle(fontSize = 1.sp), k, k + 1)
                }
            }
            if (q.isNotEmpty()) {
                val s = asCharSequence().toString()
                var i = s.indexOf(q)
                var n = 0
                while (i >= 0 && n < 2000) {
                    addStyle(SpanStyle(background = if (i == current) hlCur else hl), i, i + q.length)
                    i = s.indexOf(q, i + q.length)
                    n++
                }
            }
        }
    }

    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var bodyTop by remember { mutableFloatStateOf(-1f) }
    var viewportTop by remember { mutableFloatStateOf(-1f) }
    var viewportH by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    fun reveal(offset: Int) {
        val lay = layout ?: return
        val o = offset.coerceIn(0, lay.layoutInput.text.length)
        val y = bodyTop + lay.getLineTop(lay.getLineForOffset(o))
        scope.launch { scroll.animateScrollTo((y - viewportH / 3).roundToInt().coerceAtLeast(0)) }
    }

    fun select(m: Int) {
        bodyState.edit { selection = TextRange(m, m + q.length) }
        reveal(m)
    }

    fun findNext() {
        if (matches.isEmpty()) return Toast.show("没有找到")
        select(matches.firstOrNull { it >= bodyState.selection.end } ?: matches.first())
    }

    fun findPrev() {
        if (matches.isEmpty()) return Toast.show("没有找到")
        select(matches.lastOrNull { it < bodyState.selection.start } ?: matches.last())
    }

    fun replaceOne() {
        if (q.isEmpty()) return
        val r = replacement.text.toString()
        val s = bodyState.selection
        if (current >= 0) {
            bodyState.edit {
                replace(s.start, s.end, r)
                selection = TextRange(s.start + r.length)
            }
        }
        val next = allMatches(bodyState.text, q)
        if (next.isEmpty()) Toast.show("已全部替换") else select(next.firstOrNull { it >= bodyState.selection.end } ?: next.first())
    }

    fun replaceAll() {
        if (q.isEmpty()) return
        val r = replacement.text.toString()
        val all = allMatches(bodyState.text, q)
        if (all.isEmpty()) return Toast.show("没有找到")
        bodyState.edit { for (i in all.asReversed()) replace(i, i + q.length, r) }
        Toast.show("替换了 ${all.size} 处")
    }

    // 打开时：正在读的那一行放回它在阅读页里的高度，然后弹出键盘
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(layout, bodyTop, viewportTop) }.first { it.first != null && it.second >= 0f && it.third >= 0f }
        if (!alignTitle) {
            val lay = layout!!
            val o = cursor.coerceIn(0, lay.layoutInput.text.length)
            val y = bodyTop + lay.getLineTop(lay.getLineForOffset(o))
            // 工具栏比阅读页的页眉高时，这一行只能贴着工具栏
            scroll.scrollTo((y - (textTop - viewportTop).coerceAtLeast(0f)).roundToInt().coerceAtLeast(0))
        }
        focus.requestFocus()
    }

    // 光标自己画：停在段尾时系统会把它画进段距那条缝里
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(sel, focused) {
        caretOn = true
        while (focused) {
            delay(530)
            caretOn = !caretOn
        }
    }
    val numbers = rememberTextMeasurer(cacheSize = 256)
    val numStyle = TextStyle(fontSize = 10.sp, color = c.text3)
    val numCur = TextStyle(fontSize = 10.sp, color = c.ink, fontWeight = FontWeight.Bold)
    val band = c.ink.copy(alpha = if (c.dark) 0.07f else 0.045f)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .blockTouches()
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // —— 工具栏：关闭 | 标签页 | 撤销 重做 查找 | 保存 ——
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.CLOSE, size = 20.dp) { close() }
            Row(
                Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .background(c.chip, SmoothShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(titleState.text.toString().ifBlank { "无标题" }, 13.sp, c.ink, FontWeight.Medium, Modifier.weight(1f, fill = false), maxLines = 1)
                    if (changed) {
                        Spacer(Modifier.width(7.dp))
                        Box(Modifier.size(7.dp).background(c.accent, CircleShape))
                    }
                }
            }
            val undo = bodyState.undoState
            IconBtn(Ico.UNDO, size = 21.dp, color = if (undo.canUndo) c.ink else c.text3) { if (undo.canUndo) undo.undo() }
            IconBtn(Ico.REDO, size = 21.dp, color = if (undo.canRedo) c.ink else c.text3) { if (undo.canRedo) undo.redo() }
            IconBtn(Ico.SEARCH, size = 21.dp, color = if (finding) c.accent else c.ink) { finding = !finding }
            Spacer(Modifier.width(4.dp))
            InkButton(if (saving) "保存中" else "保存", Modifier.width(66.dp), height = 34.dp) {
                if (!saving) onSave(titleState.text.toString(), bodyState.text.toString(), bodyState.selection.start)
            }
        }
        if (finding) FindBar(query, replacement, matches.size, matches.indexOf(current) + 1, ::findPrev, ::findNext, ::replaceOne, ::replaceAll) { finding = false }

        // —— 正文：和阅读页同一套版式 ——
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    viewportTop = it.positionInRoot().y
                    viewportH = it.size.height.toFloat()
                }
                .verticalScroll(scroll)
                .padding(start = leftDp),
        ) {
            // 标题顶和阅读页第一页标题顶对齐
            val topPad = with(density) { (textTop - max(viewportTop, 0f)).coerceAtLeast(0f).toDp() }
            Spacer(Modifier.height(if (viewportTop < 0f) 0.dp else topPad))
            BasicTextField(
                titleState,
                Modifier.width(textW),
                textStyle = titleStyle,
                inputTransformation = NoNewline,
                cursorBrush = SolidColor(c.accent),
            )
            // 标题和正文之间空一行正文行高，和阅读页一样
            Spacer(Modifier.height(with(density) { (spec.fontSize * spec.lineHeight).sp.toDp() }))
            Box(Modifier.onPlaced { bodyTop = it.positionInParent().y }) {
                BasicTextField(
                    bodyState,
                    Modifier
                        .width(textW)
                        .focusRequester(focus)
                        .drawBehind {
                            val lay = layout ?: return@drawBehind
                            val text = bodyState.text
                            val len = lay.layoutInput.text.length
                            val at = bodyState.selection.start.coerceIn(0, text.length)
                            // 当前段铺浅色，像 Notepad++ 的当前行
                            val ps = if (at == 0) 0 else text.lastIndexOf('\n', at - 1) + 1
                            var pe = text.indexOf('\n', at)
                            if (pe < 0) pe = text.length
                            val l1 = lay.getLineForOffset(ps.coerceAtMost(len))
                            val l2 = lay.getLineForOffset(max(ps, pe - 1).coerceAtMost(len))
                            drawRect(band, Offset(-textLeft.toFloat(), lay.getLineTop(l1)), Size(size.width + textLeft * 2f, lay.getLineBottom(l2) - lay.getLineTop(l1)))
                            // 段号写在左边距里
                            val pad = 5.dp.toPx()
                            var start = 0
                            var n = 1
                            while (true) {
                                val line = lay.getLineForOffset(start.coerceAtMost(len))
                                val r = numbers.measure("$n", if (start == ps) numCur else numStyle)
                                val y = (lay.getLineTop(line) + lay.getLineBottom(line) - r.size.height) / 2
                                drawText(r, topLeft = Offset(-pad - r.size.width, y))
                                val nl = text.indexOf('\n', start)
                                if (nl < 0) break
                                start = nl + 1
                                n++
                            }
                        },
                    textStyle = bodyStyle,
                    cursorBrush = SolidColor(Color.Transparent),
                    outputTransformation = output,
                    interactionSource = interaction,
                    onTextLayout = { get -> layout = get() },
                )
                Canvas(Modifier.matchParentSize()) {
                    val lay = layout ?: return@Canvas
                    if (!focused || !caretOn || !bodyState.selection.collapsed) return@Canvas
                    val r = caretRect(lay, bodyState.selection.start)
                    val h = spec.fontSize.sp.toPx() * 1.25f
                    val mid = (r.top + r.bottom) / 2
                    drawRect(c.accent, Offset(r.left - 1.dp.toPx() / 2, mid - h / 2), Size(2.dp.toPx(), h))
                }
            }
            Spacer(Modifier.height(160.dp))
        }

        // —— 状态栏 ——
        StatusBar(bodyState, changed)
    }
    ConfirmDialog(
        visible = confirm,
        title = "放弃修改？",
        message = "这一章改动的内容不会保存",
        confirm = "放弃",
        ico = Ico.EDIT,
        onConfirm = {
            confirm = false
            onClose()
        },
        onDismiss = { confirm = false },
    )
}

private val NoNewline = InputTransformation {
    val s = asCharSequence()
    if (s.contains('\n')) replace(0, length, s.toString().replace("\n", ""))
}

private fun allMatches(text: CharSequence, q: String): List<Int> {
    if (q.isEmpty()) return emptyList()
    val s = text.toString()
    val out = ArrayList<Int>()
    var i = s.indexOf(q)
    while (i >= 0 && out.size < 2000) {
        out += i
        i = s.indexOf(q, i + q.length)
    }
    return out
}

/** 段尾（后面紧跟段距占位）的光标画在上一行行尾，其他照常 */
private fun caretRect(lay: TextLayoutResult, offset: Int): Rect {
    val t = lay.layoutInput.text
    val o = offset.coerceIn(0, t.length)
    if (o in 1 until t.length && t[o] == PARA_GAP && t[o - 1] != PARA_GAP) {
        val line = lay.getLineForOffset(o - 1)
        val x = lay.getLineRight(line)
        return Rect(x, lay.getLineTop(line), x, lay.getLineBottom(line))
    }
    return lay.getCursorRect(o)
}

@Composable
private fun FindBar(
    query: TextFieldState,
    replacement: TextFieldState,
    total: Int,
    index: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val c = K.c
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldBox(query, "查找", Modifier.weight(1f).focusRequester(focus))
            Spacer(Modifier.width(8.dp))
            T(if (query.text.isEmpty()) "" else "$index/$total", 12.sp, c.text2, modifier = Modifier.width(44.dp), maxLines = 1)
            IconBtn(Ico.CHEVRON_UP, size = 20.dp, onClick = onPrev)
            IconBtn(Ico.CHEVRON_DOWN, size = 20.dp, onClick = onNext)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldBox(replacement, "替换为", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SmallButton("替换", onReplace)
            Spacer(Modifier.width(6.dp))
            SmallButton("全部替换", onReplaceAll)
            IconBtn(Ico.CLOSE, size = 18.dp, color = c.text2, onClick = onClose)
        }
    }
}

@Composable
private fun FieldBox(state: TextFieldState, hint: String, modifier: Modifier) {
    val c = K.c
    Box(
        modifier
            .height(38.dp)
            .background(c.chip, SmoothShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (state.text.isEmpty()) T(hint, 14.sp, c.text3)
        BasicTextField(
            state,
            Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 14.sp, color = c.ink),
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(c.accent),
        )
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    val c = K.c
    Box(
        Modifier
            .height(38.dp)
            .press(scaleTo = 0.94f, onClick = onClick)
            .background(c.ink, SmoothShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 13.sp, c.onInk, FontWeight.Medium, maxLines = 1)
    }
}

/** 像 Notepad++ 底下那条：第几段第几字、选中几个字、全章多少字 */
@Composable
private fun StatusBar(state: TextFieldState, changed: Boolean) {
    val c = K.c
    val text = state.text
    val sel = state.selection
    val at = sel.start.coerceIn(0, text.length)
    var para = 1
    var paras = 1
    var newlines = 0
    for (i in 0 until text.length) if (text[i] == '\n') {
        paras++
        newlines++
        if (i < at) para++
    }
    val col = at - (if (at == 0) 0 else text.lastIndexOf('\n', at - 1) + 1) + 1
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.chip)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("第 $para/$paras 段 · 第 $col 字", 11.sp, c.text2, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        T("选中 ${sel.length}", 11.sp, c.text2, maxLines = 1)
        Spacer(Modifier.weight(1f))
        T("共 ${text.length - newlines} 字", 11.sp, c.text2, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        T(if (changed) "已修改" else "未修改", 11.sp, if (changed) c.accent else c.text3, FontWeight.Medium, maxLines = 1)
    }
}
