package com.kongye.reader.ui.reader

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.Sheet
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 第二段起输入框开头放一个看不见的字：在段首退格会删掉它，借此知道要和上一段合并 */
private const val SENT = '⁠'

/**
 * 编辑本章。布局参考 Notepad++：顶上是标签页和撤销、重做、查找，左边距里是段号，当前段铺一层浅色，底下是状态栏。
 * 正文的字体、字号、行距、段距、缩进、版心宽度都和阅读页一样，打开时正在读的那一行停在原来的高度，改到哪一眼就能看到。
 *
 * 整章放进一个输入框时，每打一个字整章都要重新排版，几千字的章节就会卡。所以只有光标所在的一段是输入框，
 * 其余各段照阅读页的样子画好不动；点哪段就编辑哪段，回车拆段、在段首退格并段都在输入框里处理。
 */
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
    onPromote: (title: String, body: String, paraStart: Int, paraEnd: Int) -> Unit,
    onDemote: (title: String, body: String) -> Unit,
    /** 这一章是第几章、全书的章名；点左上角章名可以换一章编辑 */
    chapter: Int = 0,
    chapters: List<String> = emptyList(),
    /** 换到 target 章：save 不为空就是先把这一章存下（标题, 正文） */
    onSwitch: (target: Int, save: Pair<String, String>?) -> Unit = { _, _ -> },
) {
    val c = K.c
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val doc = remember { EditDoc(title, body, cursor) }
    val query = rememberTextFieldState()
    val replacement = rememberTextFieldState()
    var confirm by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    var switchTo by remember { mutableStateOf<Int?>(null) }
    var finding by remember { mutableStateOf(false) }
    // 设为标题的那一段太长时先问一句
    var longPara by remember { mutableStateOf(false) }
    val titleInteraction = remember { MutableInteractionSource() }
    val titleFocused by titleInteraction.collectIsFocusedAsState()
    val changed by remember { derivedStateOf { doc.changed() } }
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
    val lineH = with(density) { (spec.fontSize * spec.lineHeight).sp.toDp() }
    val gapH = with(density) { (spec.fontSize * spec.paraSpacing).sp.toDp() }

    // 两端对齐：逐段按阅读页同样的办法算字间距，按段落内容缓存
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

    val spacingFn: (String) -> List<Triple<Int, Int, Float>> = remember(spacing) { { p -> spacingOf(p) } }

    // —— 查找 ——（光标位置只在用到的地方读：每打一个字不让整页、两百多段跟着重组）
    val q = query.text.toString()
    val matches by remember { derivedStateOf { doc.find(query.text.toString()) } }
    val current by remember {
        derivedStateOf {
            val t = query.text
            val s = doc.field.selection
            if (t.isEmpty() || s.length != t.length) -1
            else matches.indexOfFirst { it.first == doc.active && it.second == s.start - doc.prefix }
        }
    }
    val hl = c.accent.copy(alpha = 0.18f)
    val hlCur = c.accent.copy(alpha = 0.42f)

    val scroll = rememberScrollState()
    var fieldLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldTop by remember { mutableFloatStateOf(-1f) }
    var viewportTop by remember { mutableFloatStateOf(-1f) }
    var viewportH by remember { mutableFloatStateOf(0f) }
    // 要把输入框里这个位置滚到眼前（查找跳转用）
    var reveal by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    fun select(m: Pair<Int, Int>) {
        doc.load(m.first, m.second, q.length)
        reveal = doc.prefix + m.second
        focus.requestFocus()
    }

    fun findNext() {
        if (matches.isEmpty()) return Toast.show("没有找到")
        val at = doc.active to (doc.field.selection.end - doc.prefix)
        select(matches.firstOrNull { compareValues(it.first, at.first).let { d -> d > 0 || d == 0 && it.second >= at.second } } ?: matches.first())
    }

    fun findPrev() {
        if (matches.isEmpty()) return Toast.show("没有找到")
        val at = doc.active to (doc.field.selection.start - doc.prefix)
        select(matches.lastOrNull { compareValues(it.first, at.first).let { d -> d < 0 || d == 0 && it.second < at.second } } ?: matches.last())
    }

    fun replaceOne() {
        if (q.isEmpty()) return
        if (current >= 0) doc.replaceSelection(replacement.text.toString())
        val next = doc.find(q)
        if (next.isEmpty()) {
            Toast.show("已全部替换")
            return
        }
        val at = doc.active to (doc.field.selection.end - doc.prefix)
        select(next.firstOrNull { compareValues(it.first, at.first).let { d -> d > 0 || d == 0 && it.second >= at.second } } ?: next.first())
    }

    fun replaceAll() {
        if (q.isEmpty()) return
        val n = doc.replaceAll(q, replacement.text.toString())
        Toast.show(if (n == 0) "没有找到" else "替换了 $n 处")
    }

    // —— 正文和标题互换 ——
    fun promote() {
        val (start, end) = doc.activeRange()
        onPromote(doc.title.text.toString(), doc.joined(), start, end)
    }

    fun promoteCurrent() {
        val para = doc.fieldText().trim()
        when {
            para.isEmpty() -> Toast.show("这一段是空的")
            para.length > 40 -> longPara = true
            else -> promote()
        }
    }

    // 打开时：正在读的那一行放回它在阅读页里的高度，然后弹出键盘
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(fieldLayout, fieldTop, viewportTop) }.first { it.first != null && it.second >= 0f && it.third >= 0f }
        withFrameNanos { }
        if (!alignTitle) {
            val lay = fieldLayout!!
            val o = doc.field.selection.start.coerceIn(0, lay.layoutInput.text.length)
            val y = fieldTop + lay.getLineTop(lay.getLineForOffset(o))
            // 工具栏比阅读页的页眉高时，这一行只能贴着工具栏
            scroll.scrollTo((y - (textTop - viewportTop).coerceAtLeast(0f)).roundToInt().coerceAtLeast(0))
        }
        focus.requestFocus()
    }
    // 查找跳到别的段：等那一段变成输入框、排好版，再滚过去
    LaunchedEffect(reveal) {
        val o = reveal ?: return@LaunchedEffect
        val lay = snapshotFlow { fieldLayout }.first { it != null && it.layoutInput.text.length == doc.field.text.length }!!
        withFrameNanos { }
        val y = fieldTop + lay.getLineTop(lay.getLineForOffset(o.coerceIn(0, lay.layoutInput.text.length)))
        if (y < scroll.value || y > scroll.value + viewportH * 0.7f) scroll.animateScrollTo((y - viewportH / 3).roundToInt().coerceAtLeast(0))
        reveal = null
    }

    // 光标自己画：比系统光标细、短，和阅读页的字一样高
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(focused) {
        snapshotFlow { doc.field.selection }.collectLatest {
            caretOn = true
            while (focused) {
                delay(530)
                caretOn = !caretOn
            }
        }
    }
    val numStyle = TextStyle(fontSize = 10.sp, color = c.text3)
    val numCur = TextStyle(fontSize = 10.sp, color = c.ink, fontWeight = FontWeight.Bold)
    val band = c.ink.copy(alpha = if (c.dark) 0.07f else 0.045f)

    // 输入框里这一段：每行补字间距、查找结果铺底色。只有一段，算得很快
    val fieldOutput = remember(spec, q, current, hl) {
        OutputTransformation {
            val all = asCharSequence().toString()
            val p = if (all.isNotEmpty() && all[0] == SENT) 1 else 0
            val raw = all.substring(p)
            for ((s, e, ls) in spacingOf(raw)) addStyle(SpanStyle(letterSpacing = (ls / toSp).sp), p + s, p + e)
            if (q.isNotEmpty()) {
                val curAt = matches.getOrNull(current)?.takeIf { it.first == doc.active }?.second ?: -1
                var i = raw.indexOf(q)
                while (i >= 0) {
                    addStyle(SpanStyle(background = if (i == curAt) hlCur else hl), p + i, p + i + q.length)
                    i = raw.indexOf(q, i + q.length)
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .blockTouches()
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // —— 工具栏：关闭 | 标签页 | 设为标题 撤销 重做 查找 | 保存 ——
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
                        .press(scaleTo = 0.95f, enabled = !saving && chapters.size > 1) {
                            // 先收起键盘，章节列表才不会被挡住
                            keyboard?.hide()
                            picking = true
                        }
                        .background(c.chip, SmoothShape(12.dp))
                        .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(doc.title.text.toString().ifBlank { "无标题" }, 13.sp, c.ink, FontWeight.Medium, Modifier.weight(1f, fill = false), maxLines = 1)
                    if (changed) {
                        Spacer(Modifier.width(7.dp))
                        Box(Modifier.size(7.dp).background(c.accent, CircleShape))
                    }
                    if (chapters.size > 1) {
                        Spacer(Modifier.width(2.dp))
                        Icon(Ico.CHEVRON_DOWN, size = 14.dp, color = c.text2)
                    }
                }
            }
            // 光标在标题里：把标题变回正文；在正文里：把这一段提升为新一章的标题
            SmallChip(if (titleFocused) "转为正文" else "设为标题", enabled = !saving) {
                if (titleFocused) onDemote(doc.title.text.toString(), doc.joined()) else promoteCurrent()
            }
            IconBtn(Ico.UNDO, size = 21.dp, color = if (doc.canUndo) c.ink else c.text3) { doc.undo() }
            IconBtn(Ico.REDO, size = 21.dp, color = if (doc.canRedo) c.ink else c.text3) { doc.redo() }
            IconBtn(Ico.SEARCH, size = 21.dp, color = if (finding) c.accent else c.ink) { finding = !finding }
            Spacer(Modifier.width(4.dp))
            InkButton(if (saving) "保存中" else "保存", Modifier.width(66.dp), height = 34.dp) {
                if (!saving) onSave(doc.title.text.toString(), doc.joined(), doc.cursorInBody())
            }
        }
        if (finding) FindBar(query, replacement, matches.size, current + 1, ::findPrev, ::findNext, ::replaceOne, ::replaceAll) { finding = false }

        // —— 正文：和阅读页同一套版式 ——
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    viewportTop = it.positionInRoot().y
                    viewportH = it.size.height.toFloat()
                }
                .verticalScroll(scroll),
        ) {
            // 标题顶和阅读页第一页标题顶对齐
            val topPad = with(density) { (textTop - viewportTop.coerceAtLeast(0f)).coerceAtLeast(0f).toDp() }
            Spacer(Modifier.height(if (viewportTop < 0f) 0.dp else topPad))
            BasicTextField(
                doc.title,
                Modifier.padding(start = leftDp).width(textW),
                textStyle = titleStyle,
                inputTransformation = doc.titleInput,
                cursorBrush = SolidColor(c.accent),
                interactionSource = titleInteraction,
            )
            // 标题和正文之间空一行正文行高，和阅读页一样
            Spacer(Modifier.height(lineH))
            val active = doc.active
            val hits = if (q.isEmpty()) emptyMap() else matches.groupBy({ it.first }, { it.second })
            for (i in doc.paras.indices) {
                // 输入框换段时整块挪过去，焦点和键盘不会丢
                key(if (i == active) "field" else doc.ids[i]) {
                    Column {
                        Row(Modifier.fillMaxWidth().background(if (i == active) band else Color.Transparent)) {
                            Box(Modifier.width(leftDp).height(lineH), contentAlignment = Alignment.CenterEnd) {
                                T("${i + 1}", 10.sp, if (i == active) numCur.color else numStyle.color, if (i == active) FontWeight.Bold else FontWeight.Normal, Modifier.padding(end = 5.dp), maxLines = 1)
                            }
                            if (i == active) {
                                Box(
                                    Modifier.onGloballyPositioned {
                                        fieldTop = it.positionInRoot().y - viewportTop + scroll.value
                                    },
                                ) {
                                    BasicTextField(
                                        doc.field,
                                        Modifier
                                            .width(textW)
                                            .focusRequester(focus)
                                            .graphicsLayer(),
                                        textStyle = bodyStyle,
                                        cursorBrush = SolidColor(Color.Transparent),
                                        inputTransformation = doc.bodyInput,
                                        outputTransformation = fieldOutput,
                                        interactionSource = interaction,
                                        onTextLayout = { get -> fieldLayout = get() },
                                    )
                                    Canvas(Modifier.matchParentSize().graphicsLayer()) {
                                        val lay = fieldLayout ?: return@Canvas
                                        val s = doc.field.selection
                                        if (!focused || !caretOn || !s.collapsed) return@Canvas
                                        val r = lay.getCursorRect(s.start.coerceIn(0, lay.layoutInput.text.length))
                                        val h = spec.fontSize.sp.toPx() * 1.25f
                                        val mid = (r.top + r.bottom) / 2
                                        drawRect(c.accent, Offset(r.left - 1.dp.toPx() / 2, mid - h / 2), Size(2.dp.toPx(), h))
                                    }
                                }
                            } else {
                                StaticPara(
                                    doc.paras[i], bodyStyle, textW, spacingFn, toSp,
                                    hits[i].orEmpty(), matches.getOrNull(current)?.takeIf { it.first == i }?.second ?: -1, q.length, hl, hlCur,
                                ) { offset ->
                                    doc.load(i, offset)
                                    focus.requestFocus()
                                }
                            }
                        }
                        if (i < doc.paras.lastIndex) Spacer(Modifier.height(gapH))
                    }
                }
            }
            Spacer(Modifier.height(160.dp))
        }

        // —— 状态栏 ——
        StatusBar(doc, changed)
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
    ChapterPicker(picking, chapters, chapter, onDismiss = { picking = false }) { target ->
        picking = false
        if (target == chapter) return@ChapterPicker
        if (changed) switchTo = target else onSwitch(target, null)
    }
    ConfirmDialog(
        visible = switchTo != null,
        title = "先保存这一章？",
        message = "这一章改过还没保存，保存后再换到「${switchTo?.let { chapters.getOrNull(it) } ?: ""}」",
        confirm = "保存并切换",
        ico = Ico.EDIT,
        onConfirm = {
            val t = switchTo
            switchTo = null
            if (t != null) onSwitch(t, doc.title.text.toString() to doc.joined())
        },
        onDismiss = { switchTo = null },
    )
    ConfirmDialog(
        visible = longPara,
        title = "这一段设为标题？",
        message = "这一段有 ${doc.fieldText().trim().length} 个字，不太像标题。设为标题后，它后面的正文会成为新的一章",
        confirm = "设为标题",
        ico = Ico.EDIT,
        onConfirm = {
            longPara = false
            promote()
        },
        onDismiss = { longPara = false },
    )
}

/** 不在编辑的段落：和阅读页一样排好画出来，点一下就换成输入框，光标放在点的位置 */
@Composable
private fun StaticPara(
    text: String,
    style: TextStyle,
    width: Dp,
    spacingOf: (String) -> List<Triple<Int, Int, Float>>,
    toSp: Float,
    hits: List<Int>,
    curHit: Int,
    qLen: Int,
    hl: Color,
    hlCur: Color,
    onTap: (Int) -> Unit,
) {
    val shown = remember(text, hits, curHit, qLen, hl) {
        buildAnnotatedString {
            append(text)
            for ((s, e, ls) in spacingOf(text)) addStyle(SpanStyle(letterSpacing = (ls / toSp).sp), s, e)
            for (h in hits) addStyle(SpanStyle(background = if (h == curHit) hlCur else hl), h, h + qLen)
        }
    }
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    // 前面插段、删段后这一段的序号会变，点击时要用最新的回调
    val tap by rememberUpdatedState(onTap)
    BasicText(
        shown,
        Modifier
            .width(width)
            .pointerInput(text) { detectTapGestures { p -> tap(layout[0]?.getOffsetForPosition(p) ?: text.length) } },
        style = style,
        onTextLayout = { layout[0] = it },
    )
}

/** 正在编辑的这一章：各段文字、哪一段是输入框、撤销记录 */
@Stable
private class EditDoc(title0: String, body0: String, cursor0: Int) {
    private val origTitle = title0
    private val orig = body0.split('\n')
    val paras = mutableStateListOf<String>().also { it.addAll(orig) }
    /** 每段一个编号，插段删段时其余各段不用重画 */
    val ids = ArrayList<Long>()
    private var nextId = 0L
    // 从 -1 开始：第一次 load 时输入框还是空的，不能把它写回某一段
    var active by mutableIntStateOf(-1)
        private set
    val prefix get() = if (active > 0) 1 else 0
    /** 输入框里是当前这一段（第二段起开头带一个 SENT）；paras[active] 只在换段时才同步 */
    val field = TextFieldState()
    val title = TextFieldState(title0)

    private class Snap(val paras: List<String>, val title: String, val active: Int, val sel: TextRange)
    private val undos = ArrayList<Snap>()
    private val redos = ArrayList<Snap>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var lastAt = 0L
    private var lastActive = -1

    init {
        repeat(orig.size) { ids += nextId++ }
        var at = cursor0.coerceIn(0, body0.length)
        var i = 0
        while (i < orig.lastIndex && at > orig[i].length) {
            at -= orig[i].length + 1
            i++
        }
        load(i, at)
    }

    fun fieldText(): String = field.text.toString().let { if (it.isNotEmpty() && it[0] == SENT) it.substring(1) else it }

    fun text(i: Int): String = if (i == active) fieldText() else paras[i]

    fun joined(): String = paras.indices.joinToString("\n") { text(it) }

    /** 当前这一段在整章正文里的范围 */
    fun activeRange(): Pair<Int, Int> {
        var s = 0
        for (i in 0 until active) s += paras[i].length + 1
        return s to s + fieldText().length
    }

    fun cursorInBody(): Int = activeRange().first + (field.selection.start - prefix).coerceIn(0, fieldText().length)

    fun changed(): Boolean {
        if (title.text.toString() != origTitle || paras.size != orig.size) return true
        for (i in paras.indices) if (text(i) != orig[i]) return true
        return false
    }

    /** 把第 i 段换成输入框，光标放在 offset（带选区时选中 len 个字） */
    fun load(i: Int, offset: Int, len: Int = 0) {
        // 输入框里的字先写回原来那一段（同一段也要写：查找跳到本段时不能丢了刚打的字）
        if (active in paras.indices) paras[active] = fieldText()
        active = i.coerceIn(0, paras.lastIndex)
        val t = paras[active]
        val p = prefix
        val o = offset.coerceIn(0, t.length)
        field.edit {
            replace(0, length, if (p == 1) "$SENT$t" else t)
            selection = TextRange(p + o, p + (o + len).coerceAtMost(t.length))
        }
    }

    fun find(q: String): List<Pair<Int, Int>> {
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        for (i in paras.indices) {
            val t = text(i)
            var k = t.indexOf(q)
            while (k >= 0 && out.size < 2000) {
                out += i to k
                k = t.indexOf(q, k + q.length)
            }
        }
        return out
    }

    fun replaceSelection(r: String) {
        record(null, null, force = true)
        val s = field.selection
        field.edit {
            replace(s.start, s.end, r)
            selection = TextRange(s.start + r.length)
        }
    }

    fun replaceAll(q: String, r: String): Int {
        val n = find(q).size
        if (n == 0) return 0
        record(null, null, force = true)
        val at = (field.selection.start - prefix).coerceAtLeast(0)
        paras[active] = fieldText()
        for (i in paras.indices) if (paras[i].contains(q)) paras[i] = paras[i].replace(q, r)
        val keep = active
        active = -1
        load(keep, at)
        return n
    }

    // —— 撤销：结构变化（拆段、并段、替换）单独记一步，连着打字一秒内的算一步 ——
    private fun snapshot(fieldBefore: CharSequence?, selBefore: TextRange?, titleBefore: CharSequence? = null): Snap {
        val list = ArrayList<String>(paras.size)
        for (i in paras.indices) {
            list += if (i == active) fieldBefore?.toString()?.removePrefix(SENT.toString()) ?: fieldText() else paras[i]
        }
        return Snap(list, titleBefore?.toString() ?: title.text.toString(), active, selBefore ?: field.selection)
    }

    private fun record(fieldBefore: CharSequence?, selBefore: TextRange?, force: Boolean = false, titleBefore: CharSequence? = null) {
        val now = SystemClock.uptimeMillis()
        if (force || now - lastAt > 1000 || lastActive != active) {
            undos += snapshot(fieldBefore, selBefore, titleBefore)
            if (undos.size > 200) undos.removeAt(0)
        }
        redos.clear()
        lastAt = now
        lastActive = active
        canUndo = undos.isNotEmpty()
        canRedo = false
    }

    fun undo() {
        val s = undos.removeLastOrNull() ?: return
        redos += snapshot(null, null)
        apply(s)
    }

    fun redo() {
        val s = redos.removeLastOrNull() ?: return
        undos += snapshot(null, null)
        apply(s)
    }

    private fun apply(s: Snap) {
        paras.clear()
        paras.addAll(s.paras)
        ids.clear()
        repeat(s.paras.size) { ids += nextId++ }
        title.edit { replace(0, length, s.title) }
        active = -1
        val i = s.active.coerceIn(0, paras.lastIndex)
        load(i, s.sel.start - if (i > 0) 1 else 0)
        lastAt = 0
        canUndo = undos.isNotEmpty()
        canRedo = redos.isNotEmpty()
    }

    val titleInput = InputTransformation {
        val s = asCharSequence()
        if (s.contains('\n')) replace(0, length, s.toString().replace("\n", ""))
        record(null, null, titleBefore = originalText)
    }

    /** 输入框里的改动：段首退格并到上一段，回车（或粘贴多段）拆成几段，其余照常 */
    val bodyInput = InputTransformation {
        var s = asCharSequence().toString()
        val lost = active > 0 && s.indexOf(SENT) < 0
        record(originalText, originalSelection, force = lost || '\n' in s)
        if (active > 0) {
            val k = s.indexOf(SENT)
            if (k < 0) {
                mergeUp(this)
            } else if (k > 0) {
                // 光标跑到了 SENT 前面又打了字：把 SENT 挪回开头
                val c0 = selection.start
                replace(k, k + 1, "")
                replace(0, 0, SENT.toString())
                selection = TextRange(if (c0 <= k) c0 + 1 else c0)
            }
            s = asCharSequence().toString()
        }
        if ('\n' in s) split(this)
    }

    private fun mergeUp(buf: TextFieldBuffer) {
        val cur = buf.asCharSequence().toString()
        val at = buf.selection.start
        val i = active
        val prev = paras[i - 1]
        paras.removeAt(i)
        ids.removeAt(i)
        active = i - 1
        val merged = prev + cur
        paras[active] = merged
        val p = prefix
        buf.replace(0, buf.length, if (p == 1) "$SENT$merged" else merged)
        buf.selection = TextRange(p + prev.length + at)
    }

    private fun split(buf: TextFieldBuffer) {
        val p = prefix
        val raw = buf.asCharSequence().toString().substring(p)
        var off = (buf.selection.start - p).coerceIn(0, raw.length)
        val parts = raw.split('\n')
        var idx = parts.lastIndex
        for (k in parts.indices) {
            if (off <= parts[k].length) {
                idx = k
                break
            }
            off -= parts[k].length + 1
        }
        val i = active
        paras[i] = parts[0]
        for (k in 1 until parts.size) {
            paras.add(i + k, parts[k])
            ids.add(i + k, nextId++)
        }
        active = i + idx
        val np = prefix
        buf.replace(0, buf.length, if (np == 1) "$SENT${parts[idx]}" else parts[idx])
        buf.selection = TextRange(np + off.coerceIn(0, parts[idx].length))
    }
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

/** 工具栏里的文字小按钮 */
@Composable
private fun SmallChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = K.c
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .height(30.dp)
            .press(scaleTo = 0.94f) { if (enabled) onClick() }
            .background(c.chip, SmoothShape(10.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 12.sp, if (enabled) c.ink else c.text3, FontWeight.Medium, maxLines = 1)
    }
}

/** 像 Notepad++ 底下那条：第几段第几字、选中几个字、全章多少字 */
@Composable
private fun StatusBar(doc: EditDoc, changed: Boolean) {
    val c = K.c
    val sel = doc.field.selection
    val col = (sel.start - doc.prefix).coerceAtLeast(0) + 1
    val total by remember { derivedStateOf { doc.paras.indices.sumOf { doc.text(it).length } } }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.chip)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("第 ${doc.active + 1}/${doc.paras.size} 段 · 第 $col 字", 11.sp, c.text2, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        T("选中 ${sel.length}", 11.sp, c.text2, maxLines = 1)
        Spacer(Modifier.weight(1f))
        T("共 $total 字", 11.sp, c.text2, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        T(if (changed) "已修改" else "未修改", 11.sp, if (changed) c.accent else c.text3, FontWeight.Medium, maxLines = 1)
    }
}

/** 换章：从底下升起的章节列表，当前章打点，打开时滚到当前章附近 */
@Composable
private fun ChapterPicker(visible: Boolean, chapters: List<String>, current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = K.c
    Sheet(visible = visible, onDismiss = onDismiss) {
        T("换一章编辑", 17.sp, c.ink, FontWeight.Bold, Modifier.padding(start = 4.dp, bottom = 10.dp))
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), state = listState) {
            itemsIndexed(chapters) { i, title ->
                val cur = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .press(scaleTo = 0.98f) { onPick(i) }
                        .background(if (cur) c.chip else Color.Transparent, SmoothShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cur) {
                        Box(Modifier.size(7.dp).background(c.accent, CircleShape))
                        Spacer(Modifier.width(10.dp))
                    }
                    T(title.ifBlank { "无标题" }, 15.sp, c.ink, if (cur) FontWeight.Bold else FontWeight.Normal, Modifier.weight(1f), maxLines = 1)
                }
            }
        }
    }
}
