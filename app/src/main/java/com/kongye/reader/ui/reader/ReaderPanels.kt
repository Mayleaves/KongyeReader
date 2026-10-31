package com.kongye.reader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.AppSettings
import com.kongye.reader.data.Book
import com.kongye.reader.data.BookContent
import com.kongye.reader.data.Library
import com.kongye.reader.data.Settings
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.CatPose
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.InkSlider
import com.kongye.reader.ui.components.InkSwitch
import com.kongye.reader.ui.components.Segmented
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.SwitchRow
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.blockTouches
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import com.kongye.reader.ui.theme.NightPaper
import com.kongye.reader.ui.theme.Papers
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderMenu(
    visible: Boolean,
    panel: Int,
    onPanel: (Int) -> Unit,
    title: String,
    chapterTitle: String,
    chapter: Int,
    last: Int,
    chapterTitleAt: (Int) -> String,
    marked: Boolean,
    night: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onBookmark: () -> Unit,
    onJumpChapter: (Int) -> Unit,
    onToc: () -> Unit,
    onToggleNight: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = K.c
    BackHandler(visible) {
        when (panel) {
            0 -> onDismiss()
            3 -> onPanel(1)
            else -> onPanel(0)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RectangleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                    .background(c.card)
                    .blockTouches()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Ico.BACK, onClick = onBack)
                T(title, 16.sp, c.ink, FontWeight.SemiBold, Modifier.weight(1f), maxLines = 1)
                IconBtn(Ico.EDIT, onClick = onEdit)
                IconBtn(if (marked) Ico.BOOKMARK_ON else Ico.BOOKMARK, color = if (marked) c.accent else c.ink, onClick = onBookmark)
            }
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val shape = SmoothShape(28.dp, topOnly = true)
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.45f))
                    .background(c.card, shape)
                    .blockTouches()
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            ) {
                AnimatedContent(panel, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "panel") { p ->
                    when (p) {
                        1 -> StylePanel(night, onBack = { onPanel(0) }, onLayout = { onPanel(3) })
                        2 -> MorePanel { onPanel(0) }
                        3 -> LayoutPanel { onPanel(1) }
                        4 -> NightPanel(night, onToggleNight) { onPanel(0) }
                        else -> MainPanel(chapterTitle, chapter, last, chapterTitleAt, night, onJumpChapter, onToc, onPanel)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainPanel(
    chapterTitle: String,
    chapter: Int,
    last: Int,
    chapterTitleAt: (Int) -> String,
    night: Boolean,
    onJumpChapter: (Int) -> Unit,
    onToc: () -> Unit,
    onPanel: (Int) -> Unit,
) {
    val c = K.c
    var preview by remember { mutableStateOf<Int?>(null) }
    Column {
        val p = preview
        T(
            if (p != null) chapterTitleAt(p) else chapterTitle,
            13.sp, if (p != null) c.ink else c.text2, if (p != null) FontWeight.SemiBold else FontWeight.Normal,
            Modifier.fillMaxWidth(), maxLines = 1, align = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("上一章", 14.sp, if (chapter > 0) c.ink else c.text3, FontWeight.Medium, Modifier.press { if (chapter > 0) onJumpChapter(chapter - 1) })
            ChapterSlider(
                chapter, last,
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                onPreview = { preview = it },
                onCommit = onJumpChapter,
            )
            T("下一章", 14.sp, if (chapter < last) c.ink else c.text3, FontWeight.Medium, Modifier.press { if (chapter < last) onJumpChapter(chapter + 1) })
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Tool(Ico.TOC, "目录", onClick = onToc)
            Tool(null, "样式") { onPanel(1) }
            Tool(if (night) Ico.SUN else Ico.MOON, "夜间") { onPanel(4) }
            Tool(Ico.MORE, "设置") { onPanel(2) }
        }
    }
}

/** “夜间”的二级菜单：亮度、夜间模式、跟随系统 */
@Composable
private fun NightPanel(night: Boolean, onToggleNight: () -> Unit, onBack: () -> Unit) {
    val s by Settings.state.collectAsState()
    Column {
        PanelHeader("夜间与亮度", onBack)
        Spacer(Modifier.height(4.dp))
        BrightnessRow()
        Spacer(Modifier.height(8.dp))
        PanelSwitch("夜间模式", night) { onToggleNight() }
        PanelSwitch("跟随系统深色模式", s.nightFollowSystem, "系统切到深色时，自动换成夜间纸色") { v -> Settings.update { it.copy(nightFollowSystem = v) } }
    }
}

/** 面板里的开关行，左边和面板里其他内容对齐 */
@Composable
private fun PanelSwitch(title: String, checked: Boolean, desc: String? = null, onChange: (Boolean) -> Unit) {
    val c = K.c
    Row(
        Modifier
            .fillMaxWidth()
            .press(scaleTo = 1f) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(title, 15.sp, c.ink)
            if (desc != null) {
                Spacer(Modifier.height(2.dp))
                T(desc, 12.sp, c.text2)
            }
        }
        InkSwitch(checked, onChange = onChange)
    }
}

@Composable
private fun Tool(ico: Ico?, label: String, onClick: () -> Unit) {
    val c = K.c
    Column(
        Modifier
            .press(scaleTo = 0.9f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            if (ico != null) Icon(ico, size = 24.dp) else T("Aa", 18.sp, c.ink, FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        T(label, 12.sp, c.text2)
    }
}

@Composable
private fun ChapterSlider(value: Int, max: Int, modifier: Modifier, onPreview: (Int?) -> Unit, onCommit: (Int) -> Unit) {
    val c = K.c
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (max <= 0) 0f else value.toFloat() / max
    Canvas(
        modifier
            .height(34.dp)
            .pointerInput(max) {
                detectTapGestures { o -> onCommit(((o.x / size.width).coerceIn(0f, 1f) * max).roundToInt()) }
            }
            .pointerInput(max) {
                var f = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        f = (o.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        onPreview((f * max).roundToInt())
                    },
                    onDragEnd = {
                        onCommit((f * max).roundToInt())
                        drag = null
                        onPreview(null)
                    },
                    onDragCancel = {
                        drag = null
                        onPreview(null)
                    },
                ) { ch, dx ->
                    ch.consume()
                    f = (f + dx / size.width).coerceIn(0f, 1f)
                    drag = f
                    onPreview((f * max).roundToInt())
                }
            },
    ) {
        val y = size.height / 2
        val r = 9.dp.toPx()
        val x = r + (size.width - 2 * r) * frac
        drawLine(c.track, Offset(r, y), Offset(size.width - r, y), 4.dp.toPx(), StrokeCap.Round)
        drawLine(c.ink, Offset(r, y), Offset(x, y), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(c.ink, r, Offset(x, y))
        drawCircle(c.card, 3.5.dp.toPx(), Offset(x, y))
    }
}

@Composable
private fun PanelHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        IconBtn(Ico.BACK, size = 20.dp, onClick = onBack)
        T(title, 16.sp, K.c.ink, FontWeight.SemiBold)
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 14.sp, K.c.text2, modifier = Modifier.width(46.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** 亮度：拖动后用自己的亮度，点“系统”交还给系统 */
@Composable
private fun BrightnessRow() {
    val c = K.c
    val s by Settings.state.collectAsState()
    val context = LocalContext.current
    val system = s.brightness < 0f
    val sysValue = remember {
        runCatching { android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f }
            .getOrDefault(0.5f).coerceIn(0.01f, 1f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Ico.SUN_SMALL, size = 20.dp, color = c.text2)
        InkSlider(
            if (system) sysValue else s.brightness, 0.01f..1f,
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            dimmed = system,
        ) { v -> Settings.update { it.copy(brightness = v) } }
        Icon(Ico.SUN, size = 22.dp, color = c.text2)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .press { Settings.update { it.copy(brightness = if (system) sysValue else -1f) } }
                .background(if (system) c.ink else c.chip, SmoothShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            T("系统", 13.sp, if (system) c.onInk else c.ink, FontWeight.Medium)
        }
    }
}

private fun num(v: Float): String = if (abs(v - v.roundToInt()) < 0.01f) "${v.roundToInt()}" else String.format(Locale.US, "%.1f", v)

@Composable
private fun StylePanel(night: Boolean, onBack: () -> Unit, onLayout: () -> Unit) {
    val c = K.c
    val s by Settings.state.collectAsState()
    Column {
        PanelHeader("阅读样式", onBack)
        Labeled("字号") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(c.chip, SmoothShape(14.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).press { Settings.update { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(13)) } }, contentAlignment = Alignment.Center) {
                    T("A-", 15.sp, c.ink, FontWeight.SemiBold)
                }
                T("${s.fontSize}", 16.sp, c.ink, FontWeight.Bold)
                Box(Modifier.weight(1f).press { Settings.update { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(32)) } }, contentAlignment = Alignment.Center) {
                    T("A+", 18.sp, c.ink, FontWeight.SemiBold)
                }
            }
        }
        Labeled("字体") { FontPicker(s.font) }
        Labeled("背景") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Papers.forEachIndexed { i, p ->
                    val sel = !night && s.paper == i
                    Box(
                        Modifier
                            .size(38.dp)
                            .press {
                                Settings.update { it.copy(paper = i, night = false, nightFollowSystem = if (c.dark || night) false else it.nightFollowSystem) }
                            }
                            .border(if (sel) 2.dp else 1.dp, if (sel) c.ink else c.line, CircleShape)
                            .padding(if (sel) 4.dp else 1.dp)
                            .background(p.bg, CircleShape),
                    )
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .press { Settings.update { it.copy(night = true) } }
                        .border(if (night) 2.dp else 1.dp, if (night) c.ink else c.line, CircleShape)
                        .padding(if (night) 4.dp else 1.dp)
                        .background(NightPaper.bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Ico.MOON, size = 15.dp, color = NightPaper.text) }
            }
        }
        Labeled("翻页") {
            Segmented(listOf("平移", "覆盖", "上下"), s.turnMode, Modifier.fillMaxWidth()) { i -> Settings.update { it.copy(turnMode = i) } }
        }
        Labeled("排版") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .press(scaleTo = 0.98f, onClick = onLayout)
                    .background(c.chip, SmoothShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("行距 ${num(s.lineHeight)} · 段距 ${num(s.paraSpacing)} · 缩进 ${num(s.indent)}", 13.sp, c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Ico.CHEVRON_RIGHT, size = 16.dp, color = c.text2)
            }
        }
    }
}

/** 三种字体各用自己的字形写出名字 */
@Composable
private fun FontPicker(selected: Int) {
    val c = K.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ReaderFonts.names.forEachIndexed { i, name ->
            val family by produceState<FontFamily?>(if (i == 0) FontFamily.Default else null, i) {
                if (value == null) value = withContext(Dispatchers.IO) { ReaderFonts.family(i) }
            }
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .press { Settings.update { it.copy(font = i) } }
                    .background(if (sel) c.ink else c.chip, SmoothShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    name,
                    style = TextStyle(color = if (sel) c.onInk else c.ink, fontSize = 15.sp, fontFamily = family ?: FontFamily.Default),
                    maxLines = 1,
                )
            }
        }
    }
}

/** 行间距、段间距、首行缩进、上下左右边距 */
@Composable
private fun LayoutPanel(onBack: () -> Unit) {
    val c = K.c
    val s by Settings.state.collectAsState()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PanelHeader("排版", onBack) }
            T(
                "恢复默认", 13.sp, c.text2,
                modifier = Modifier
                    .press {
                        val d = AppSettings()
                        Settings.update { it.copy(lineHeight = d.lineHeight, paraSpacing = d.paraSpacing, indent = d.indent, marginV = d.marginV, marginH = d.marginH) }
                    }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
        SliderRow("行间距", s.lineHeight, 1.2f..2.6f, 0.1f, num(s.lineHeight)) { v -> Settings.update { it.copy(lineHeight = v) } }
        SliderRow("段间距", s.paraSpacing, 0f..1.5f, 0.1f, num(s.paraSpacing)) { v -> Settings.update { it.copy(paraSpacing = v) } }
        SliderRow("首行缩进", s.indent, 0f..4f, 0.5f, "${num(s.indent)} 字") { v -> Settings.update { it.copy(indent = v) } }
        SliderRow("上下边距", s.marginV.toFloat(), 0f..60f, 2f, "${s.marginV}") { v -> Settings.update { it.copy(marginV = v.roundToInt()) } }
        SliderRow("左右边距", s.marginH.toFloat(), 8f..56f, 2f, "${s.marginH}") { v -> Settings.update { it.copy(marginH = v.roundToInt()) } }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, text: String, onChange: (Float) -> Unit) {
    val c = K.c
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 14.sp, c.text2, modifier = Modifier.width(68.dp))
        InkSlider(value, range, Modifier.weight(1f), step = step, onChange = onChange)
        T(text, 13.sp, c.ink, FontWeight.Medium, Modifier.width(46.dp), align = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun MorePanel(onBack: () -> Unit) {
    val s by Settings.state.collectAsState()
    Column {
        PanelHeader("阅读设置", onBack)
        Column {
            SwitchRow("音量键翻页", s.volumeKeys) { v -> Settings.update { it.copy(volumeKeys = v) } }
            SwitchRow("屏幕常亮", s.keepScreenOn) { v -> Settings.update { it.copy(keepScreenOn = v) } }
            SwitchRow("点左边也翻下一页", s.tapLeftNext, "单手拿手机时更顺手") { v -> Settings.update { it.copy(tapLeftNext = v) } }
            SwitchRow("显示时间和电量", s.showStatus) { v -> Settings.update { it.copy(showStatus = v) } }
        }
    }
}

/** 目录 / 书签 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TocPage(book: Book, content: BookContent, current: Int, onClose: () -> Unit, onJump: (Int, Int) -> Unit) {
    val c = K.c
    var tab by remember { mutableIntStateOf(0) }
    val navPad = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .blockTouches()
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.CLOSE, onClick = onClose)
            T(book.title, 17.sp, c.ink, FontWeight.SemiBold, Modifier.weight(1f), maxLines = 1)
            Spacer(Modifier.width(44.dp))
        }
        Segmented(
            listOf("目录 ${content.chapters.size}", "书签 ${book.bookmarks.size}"), tab,
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
        ) { tab = it }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 5).coerceAtLeast(0))
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = navPad + 20.dp)) {
                itemsIndexed(content.chapters) { i, ch ->
                    val cur = i == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .press(scaleTo = 0.98f) { onJump(i, 0) }
                            .background(if (cur) c.chip else c.bg, SmoothShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (cur) {
                            Box(Modifier.size(7.dp).background(c.accent, CircleShape))
                            Spacer(Modifier.width(10.dp))
                        }
                        T(
                            ch.title, 15.sp,
                            if (cur) c.ink else if (i < current) c.text2 else c.ink,
                            if (cur) FontWeight.Bold else FontWeight.Normal,
                            Modifier.weight(1f), maxLines = 1,
                        )
                        if (ch.end == ch.start) T("卷", 11.sp, c.text3)
                    }
                }
            }
        } else if (book.bookmarks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CatMascot(size = 120.dp, pose = CatPose.LOAF, sleep = true)
                Spacer(Modifier.height(10.dp))
                T("还没有书签", 16.sp, c.ink, FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                T("阅读时点一下屏幕中间，右上角可以加书签", 13.sp, c.text2)
            }
        } else {
            val fmt = remember { SimpleDateFormat("M月d日 HH:mm", Locale.CHINA) }
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 12.dp, bottom = navPad + 20.dp)) {
                items(book.bookmarks.sortedByDescending { it.createdAt }, key = { it.createdAt }) { bm ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .press(scaleTo = 0.98f) { onJump(bm.chapter, bm.offset) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(content.chapters.getOrNull(bm.chapter)?.title.orEmpty(), 14.sp, c.ink, FontWeight.SemiBold, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            T(bm.excerpt, 13.sp, c.text2, maxLines = 2, lineHeight = 19.sp)
                            Spacer(Modifier.height(4.dp))
                            T(fmt.format(Date(bm.createdAt)), 11.sp, c.text3)
                        }
                        IconBtn(Ico.CLOSE, size = 16.dp, color = c.text3) {
                            Library.update(book.id) { b -> b.copy(bookmarks = b.bookmarks.filterNot { it.createdAt == bm.createdAt }) }
                        }
                    }
                }
            }
        }
    }
}
