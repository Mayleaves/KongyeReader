package com.kongye.reader.ui.reader

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kongye.reader.MainActivity
import com.kongye.reader.data.AppSettings
import com.kongye.reader.data.Book
import com.kongye.reader.data.BookContent
import com.kongye.reader.data.Dict
import com.kongye.reader.data.Library
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Session
import com.kongye.reader.data.Settings
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.CatPose
import com.kongye.reader.ui.components.Chip
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.HangingCat
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.blockTouches
import com.kongye.reader.ui.theme.K
import com.kongye.reader.ui.theme.LocalPalette
import com.kongye.reader.ui.theme.NightPaper
import com.kongye.reader.ui.theme.Paper
import com.kongye.reader.ui.theme.Papers
import com.kongye.reader.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private var activeReaders = 0

@Composable
fun ReaderScreen(bookId: String) {
    val book = remember(bookId) { Library.get(bookId) }
    if (book == null) {
        LaunchedEffect(Unit) { Nav.pop() }
        return
    }
    var content by remember { mutableStateOf<BookContent?>(null) }
    var failed by remember { mutableStateOf(false) }
    // 编辑保存后加一，重新读正文
    var version by remember { mutableIntStateOf(0) }
    // 标题和正文互换后章节变了，重新读正文后接着编辑
    var reopenEdit by remember { mutableStateOf(false) }
    LaunchedEffect(bookId, version) {
        val c = withContext(Dispatchers.IO) { Library.loadContent(bookId) }
        if (c == null) failed = true else content = c
    }
    val settings by Settings.state.collectAsState()
    val base = K.c
    val night = settings.night || (base.dark && settings.nightFollowSystem)
    val paper = if (night) NightPaper else Papers[settings.paper.coerceIn(Papers.indices)]
    CompositionLocalProvider(LocalPalette provides paper.palette(base)) {
        Box(Modifier.fillMaxSize().background(paper.bg)) {
            val ct = content
            when {
                failed -> MissingBook(book)
                ct == null -> Opening()
                else -> key(ct) {
                    Reader(book, ct, paper, night, base.dark, settings, reopenEdit) { reopen ->
                        reopenEdit = reopen
                        version++
                    }
                }
            }
        }
    }
}

@Composable
private fun Opening() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CatMascot(size = 120.dp, pose = CatPose.LOAF, sleep = true)
        Spacer(Modifier.height(10.dp))
        T("正在打开…", 14.sp, K.c.text2)
    }
}

@Composable
private fun MissingBook(book: Book) {
    val c = K.c
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.6f))
        HangingCat(width = 160.dp)
        Spacer(Modifier.height(18.dp))
        T("完蛋！书的文件找不到了", 20.sp, c.ink, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        T("《${book.title}》可能被清理掉了，重新导入一次就好", 13.sp, c.text2)
        Spacer(Modifier.weight(1f))
        InkButton("返回", Modifier.size(width = 280.dp, height = 60.dp)) { Nav.pop() }
        Spacer(Modifier.height(12.dp))
        GhostButton("从书架移除", Ico.TRASH) {
            Nav.pop()
            Library.remove(book.id)
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** 记一次阅读：只算翻页后 5 分钟内的时间，放下手机发呆不算 */
private class ReadTracker(private val bookId: String, private val title: String) {
    private var running = false
    private var start = 0L
    private var lastTick = 0L
    private var lastInteract = 0L
    private var pages = 0
    private var fromCh = ""
    private var toCh = ""
    var active = 0L
        private set
    var finished = false

    fun resume(ch: String) {
        val now = System.currentTimeMillis()
        if (!running) {
            running = true
            start = now
            active = 0
            pages = 0
            fromCh = ch
            toCh = ch
            finished = false
        }
        lastTick = now
        lastInteract = now
    }

    fun tick() {
        if (!running) return
        val now = System.currentTimeMillis()
        if (now - lastInteract < 5 * 60_000) active += now - lastTick
        lastTick = now
    }

    fun touch() {
        lastInteract = System.currentTimeMillis()
    }

    fun turned(ch: String) {
        pages++
        toCh = ch
        touch()
    }

    fun pause() {
        if (!running) return
        tick()
        running = false
        val spent = active
        Stats.record(Session(bookId, title, start, System.currentTimeMillis(), spent, pages, fromCh, toCh, finished))
        if (spent > 0) Library.update(bookId) { it.copy(readMillis = it.readMillis + spent) }
    }
}

private class Geo(
    val marginPx: Int,
    val textTopPx: Int,
    val textW: Dp,
    val textH: Dp,
    val margin: Dp,
    val headerTop: Dp,
    val footerBottom: Dp,
)

class StatusInfo(val time: String, val battery: Int)

@Composable
private fun rememberStatus(): StatusInfo {
    val context = LocalContext.current
    var time by remember { mutableStateOf(Fmt.time(System.currentTimeMillis())) }
    var battery by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_BATTERY_CHANGED) {
                    val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (l >= 0 && s > 0) battery = l * 100 / s
                } else {
                    time = Fmt.time(System.currentTimeMillis())
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        ContextCompat.registerReceiver(context, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    return StatusInfo(time, battery)
}

private fun pageKey(ch: Int, page: Int): Long = (ch.toLong() shl 32) or page.toLong()

private fun countIn(text: String, w: String): Int {
    var n = 0
    var i = text.indexOf(w)
    while (i >= 0) {
        n++
        i = text.indexOf(w, i + w.length)
    }
    return n
}

private fun windowBrightness(window: android.view.Window, value: Float) {
    val lp = window.attributes
    lp.screenBrightness = if (value < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else value.coerceIn(0.01f, 1f)
    window.attributes = lp
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reader(
    book: Book,
    content: BookContent,
    paper: Paper,
    night: Boolean,
    appDark: Boolean,
    settings: AppSettings,
    reopenEdit: Boolean,
    onEdited: (reopen: Boolean) -> Unit,
) {
    val state = remember {
        val b = Library.get(book.id) ?: book
        ReaderState(content, b.chapter, b.offset)
    }
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val books by Library.books.collectAsState()
    val live = remember(books) { books.firstOrNull { it.id == book.id } ?: book }
    var menu by remember { mutableStateOf(false) }
    var panel by remember { mutableIntStateOf(0) }
    var toc by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // 重新打开编辑页时光标放在这里（章节刚拆开或合并，阅读页这时还没排好版）
    var editAt by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(reopenEdit) {
        if (!reopenEdit) return@LaunchedEffect
        editAt = Library.get(book.id)?.offset
        snapshotFlow { state.currentRef() }.first { it != null }
        editing = true
    }
    var saving by remember { mutableStateOf(false) }
    var finishedShown by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<Sel?>(null) }
    var entries by remember { mutableStateOf<List<Dict.Entry>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val tracker = remember(book.id) { ReadTracker(book.id, book.title) }
    fun chTitle() = content.chapters[state.chapter].title
    fun clearSel() {
        sel = null
        entries = null
        dragging = false
    }

    // —— 阅读时长 ——
    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> tracker.resume(chTitle())
                Lifecycle.Event.ON_PAUSE -> tracker.pause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            tracker.pause()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tracker.tick()
        }
    }

    // —— 沉浸式：菜单收起时隐藏状态栏和导航栏 ——
    val window = (context as Activity).window
    DisposableEffect(menu, toc, editing, searching, night) {
        val ctl = WindowCompat.getInsetsController(window, view)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (menu || toc || editing || searching) ctl.show(WindowInsetsCompat.Type.systemBars()) else ctl.hide(WindowInsetsCompat.Type.systemBars())
        ctl.isAppearanceLightStatusBars = !night
        ctl.isAppearanceLightNavigationBars = !night
        onDispose {}
    }
    // 直接从一本书打开另一本时，新旧阅读页会短暂同时存在：只有最后一个离开的阅读页才恢复状态栏、亮度、取消常亮
    DisposableEffect(Unit) {
        activeReaders++
        onDispose {
            activeReaders--
            if (activeReaders == 0) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
                windowBrightness(window, -1f)
                (context as? MainActivity)?.applyBars(force = true)
            }
        }
    }
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { if (activeReaders <= 1) view.keepScreenOn = false }
    }
    LaunchedEffect(settings.brightness) { windowBrightness(window, settings.brightness) }

    val statusTop = max(WindowInsets.statusBarsIgnoringVisibility.getTop(density), WindowInsets.displayCutout.getTop(density))
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val geo = with(density) {
            // 版心宽度取整字数，多出来的零头平分到左右边距，纯中文的行不用两端对齐也是齐的
            val em = settings.fontSize.sp.toPx()
            val room = (w - settings.marginH.dp.roundToPx() * 2).coerceAtLeast(100)
            val cols = (room / em).toInt().coerceAtLeast(4)
            val tw = kotlin.math.ceil(cols * em).toInt().coerceAtMost(room) + 1
            val marginPx = (w - tw) / 2
            val textTop = statusTop + 28.dp.roundToPx() + settings.marginV.dp.roundToPx()
            val footer = 26.dp.roundToPx() + min(navBottom, 14.dp.roundToPx()) + settings.marginV.dp.roundToPx()
            val th = (h - textTop - footer).coerceAtLeast(100)
            Geo(marginPx, textTop, tw.toDp(), th.toDp(), marginPx.toDp(), statusTop.toDp() + 13.dp, min(navBottom, 14.dp.roundToPx()).toDp() + 12.dp)
        }
        val spec = with(density) {
            PageSpec(
                geo.textW.roundToPx(), geo.textH.roundToPx(), settings.fontSize.toFloat(),
                settings.lineHeight, settings.paraSpacing, settings.indent, settings.font, density.density, density.fontScale,
            )
        }

        // 版面参数变化：记住当前读到的字，重新分页后回到这里
        LaunchedEffect(spec) {
            val off = state.currentOffset()
            clearSel()
            withContext(Dispatchers.IO) { ReaderFonts.family(spec.font) }
            val measurer = TextMeasurer(createFontFamilyResolver(context), Density(spec.density, spec.fontScale), LayoutDirection.Ltr, 0)
            state.layouts.clear()
            state.pending = off
            state.paginator = Paginator(content, spec, measurer)
        }
        // 排好当前章和前后各一章
        val pg = state.paginator
        LaunchedEffect(pg, state.chapter) {
            pg ?: return@LaunchedEffect
            val ch = state.chapter
            state.layouts[ch]?.let { state.resolve(it) }
            for (target in intArrayOf(ch, ch + 1, ch - 1)) {
                if (target !in 0..state.last || state.layouts.containsKey(target)) continue
                val cp = withContext(Dispatchers.Default) { pg.get(target) }
                if (state.paginator !== pg) return@LaunchedEffect
                state.layouts[target] = cp
                state.resolve(cp)
            }
            state.trim()
        }
        // 保存进度
        val ref = state.currentRef()
        val anchor = state.anchor
        LaunchedEffect(state.chapter, ref, anchor) {
            val r = ref ?: return@LaunchedEffect
            if (state.pending != null) return@LaunchedEffect
            // 上下滚动时位置变得很勤，停稳一会儿再记
            if (anchor != null) delay(400)
            val off = anchor ?: r.start
            val prog = content.progressOf(state.chapter, off)
            val ch = state.chapter
            Library.update(book.id) { it.copy(chapter = ch, offset = off, progress = prog, lastReadAt = System.currentTimeMillis()) }
        }

        // —— 翻页 ——
        val wf = w.toFloat()
        val scrollMode = settings.turnMode == 2
        val listState = rememberLazyListState()
        var offset by remember { mutableFloatStateOf(0f) }
        val job = remember { arrayOfNulls<Job>(1) }
        val anim = settings.turnMode

        fun finishBook() {
            tracker.finished = true
            Library.update(book.id) { it.copy(finished = true, progress = 1f) }
            finishedShown = true
        }

        fun commit(dir: Int) {
            if (state.move(dir)) tracker.turned(chTitle())
            offset = 0f
        }

        fun turn(dir: Int) {
            if (menu) {
                menu = false
                return
            }
            if (sel != null) {
                clearSel()
                return
            }
            tracker.touch()
            if (scrollMode) {
                if (dir > 0 && !listState.canScrollForward && state.chapter == state.last) {
                    finishBook()
                    return
                }
                if (dir < 0 && !listState.canScrollBackward && state.chapter == 0) {
                    Toast.show("已经是第一页了")
                    return
                }
                val by = with(density) { geo.textH.toPx() } * 0.92f * dir
                scope.launch { listState.animateScrollBy(by, tween(320, easing = FastOutSlowInEasing)) }
                return
            }
            if (dir > 0 && state.atEnd()) {
                finishBook()
                return
            }
            if (dir < 0 && state.atStart()) {
                Toast.show("已经是第一页了")
                return
            }
            val prev = job[0]
            job[0] = scope.launch {
                prev?.cancelAndJoin()
                if (state.neighbor(dir) == null) {
                    offset = 0f
                    return@launch
                }
                try {
                    animate(offset, -dir * wf, animationSpec = tween(260, easing = FastOutSlowInEasing)) { v, _ -> offset = v }
                } finally {
                    commit(dir)
                }
            }
        }

        fun settle() {
            val threshold = wf * 0.16f
            val dir = when {
                offset < -threshold -> 1
                offset > threshold -> -1
                else -> 0
            }
            job[0] = scope.launch {
                if (dir != 0 && state.neighbor(dir) != null) {
                    try {
                        animate(offset, -dir * wf, animationSpec = tween(200, easing = FastOutSlowInEasing)) { v, _ -> offset = v }
                    } finally {
                        commit(dir)
                    }
                } else {
                    animate(offset, 0f, animationSpec = tween(180)) { v, _ -> offset = v }
                    if (dir > 0 && state.atEnd()) finishBook()
                    if (dir < 0 && state.atStart()) Toast.show("已经是第一页了")
                }
            }
        }

        // —— 长按选字 ——
        fun longPress(p: Offset) {
            val hit: Triple<ChapterPages, Int, Float> = if (scrollMode) {
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                    val top = geo.textTopPx + it.offset
                    p.y >= top && p.y < top + it.size
                } ?: return
                val k = info.key as? Long ?: return
                val cp = state.layouts[(k shr 32).toInt()] ?: return
                Triple(cp, (k and 0xffffffffL).toInt(), (geo.textTopPx + info.offset).toFloat())
            } else {
                Triple(state.current() ?: return, state.page, geo.textTopPx.toFloat())
            }
            val (cp, pageIdx, originY) = hit
            val r = cp.pages.getOrNull(pageIdx) ?: return
            val layout = cp.layouts[r.chunk]
            val local = Offset(p.x - geo.marginPx, p.y - originY + r.top)
            if (local.y < r.top || local.y > r.bottom || local.x < -8f || local.x > layout.size.width + 8f) return
            var lo = layout.getOffsetForPosition(local)
            // 拿到的是光标位置：手指在字的右半边时会落到下一个字前面，退回手指下的那个字
            if (lo > 0) {
                val b = layout.getBoundingBox(lo - 1)
                if (local.x >= b.left && local.x <= b.right && local.y >= b.top && local.y <= b.bottom) lo--
            }
            if (cp.inTitle(r.chunk, lo)) return
            val pos = cp.toBody(r.chunk, lo)
            if (pos !in r.start until r.end) return
            val text = content.body(cp.index)
            if (text[pos].isWhitespace()) return
            menu = false
            panel = 0
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            tracker.touch()
            job[0]?.cancel()
            offset = 0f
            sel = Sel(cp, pageIdx, pos, pos + 1, originY)
            entries = null
            scope.launch {
                val res = withContext(Dispatchers.IO) { Dict.select(text, pos) { s -> countIn(content.text, s) } }
                Profiles.count("dict")
                res.entry?.takeIf { it.found }?.let { com.kongye.reader.data.AchievementTracking.lookedUp(book.id,it.word) }
                val cur = sel ?: return@launch
                if (!cur.on(cp, pageIdx)) return@launch
                val s = res.start.coerceIn(r.start, r.end - 1)
                val e = res.end.coerceIn(s + 1, r.end)
                sel = cur.copy(start = s, end = e)
                entries = res.entry?.let { listOf(it) }
            }
        }

        fun selectedText(): String = sel?.let { content.body(it.cp.index).substring(it.start, it.end) }.orEmpty()

        val turnRef = rememberUpdatedState(::turn)
        val settleRef = rememberUpdatedState(::settle)
        val longPressRef = rememberUpdatedState(::longPress)
        val tapLeftNext = rememberUpdatedState(settings.tapLeftNext)
        val menuState = rememberUpdatedState(menu)
        val selecting = rememberUpdatedState(sel != null)
        DisposableEffect(Unit) {
            val handler: (Int) -> Unit = { d -> turnRef.value(d) }
            MainActivity.volumeHandler = handler
            onDispose { if (MainActivity.volumeHandler === handler) MainActivity.volumeHandler = null }
        }

        val status = rememberStatus()
        val tap = Modifier.pointerInput(wf) {
            detectTapGestures(onLongPress = { p -> longPressRef.value(p) }) { p ->
                val x = p.x / size.width
                when {
                    menuState.value -> menu = false
                    x < 0.3f -> turnRef.value(if (tapLeftNext.value) 1 else -1)
                    x > 0.7f -> turnRef.value(1)
                    else -> {
                        panel = 0
                        menu = true
                    }
                }
            }
        }

        if (scrollMode) {
            ScrollReader(state, content, paper, geo, status, settings.showStatus, listState, sel, tap) { ch, p, dy ->
                val cp = state.layouts[ch] ?: return@ScrollReader
                val r = cp.pages.getOrNull(p) ?: return@ScrollReader
                // 屏幕顶上那一行（被切掉一半的算下一行）
                val lay = cp.layouts[r.chunk]
                var line = lay.getLineForVerticalPosition(r.top + dy)
                if (lay.getLineTop(line) < r.top + dy - 1f && line + 1 < lay.lineCount) line++
                state.anchor = cp.toBody(r.chunk, lay.getLineStart(line)).coerceIn(r.start, max(r.start, r.end))
                if (ch != state.chapter || p != state.page) {
                    state.chapter = ch
                    state.page = p
                    tracker.turned(chTitle())
                }
            }
        } else {
            val cur = state.current()?.let { it to state.page }
            val prevN = state.neighbor(-1)
            val nextN = state.neighbor(1)
            Box(
                Modifier
                    .fillMaxSize()
                    .then(tap)
                    .pointerInput(wf) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                job[0]?.cancel()
                                tracker.touch()
                                if (menuState.value) menu = false
                            },
                            onDragEnd = { settleRef.value() },
                            onDragCancel = { settleRef.value() },
                        ) { change, dx ->
                            change.consume()
                            if (selecting.value) return@detectHorizontalDragGestures
                            var o = offset + dx
                            if (o < 0 && state.neighbor(1) == null) o = max(o, -wf * 0.12f)
                            if (o > 0 && state.neighbor(-1) == null) o = min(o, wf * 0.12f)
                            offset = o.coerceIn(-wf, wf)
                        }
                    },
            ) {
                val shadowW = with(density) { 18.dp.toPx() }
                fun Modifier.edgeShadow(show: () -> Boolean) = drawWithContent {
                    drawContent()
                    if (show()) {
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = if (night) 0.4f else 0.16f), Color.Transparent), startX = size.width, endX = size.width + shadowW),
                            topLeft = Offset(size.width, 0f),
                            size = Size(shadowW, size.height),
                        )
                    }
                }
                if (anim == 1) {
                    PageView(nextN, content, live.title, paper, geo, status, settings.showStatus, sel, Modifier)
                    PageView(cur, content, live.title, paper, geo, status, settings.showStatus, sel, Modifier.graphicsLayer { translationX = min(offset, 0f) }.edgeShadow { offset < 0f })
                    PageView(prevN, content, live.title, paper, geo, status, settings.showStatus, sel, Modifier.graphicsLayer { translationX = if (offset > 0f) offset - wf else -wf - shadowW * 2 }.edgeShadow { offset > 0f })
                } else {
                    PageView(prevN, content, live.title, paper, geo, status, settings.showStatus, sel, Modifier.graphicsLayer { translationX = offset - wf })
                    PageView(cur, content, live.title, paper, geo, status, settings.showStatus, sel, Modifier.graphicsLayer { translationX = offset })
                    PageView(nextN, content, live.title, paper, geo, status, settings.showStatus, sel, Modifier.graphicsLayer { translationX = offset + wf })
                }
            }
        }

        // —— 选字浮层 ——
        BackHandler(sel != null) { clearSel() }
        sel?.let { s ->
            SelectionLayer(
                sel = s,
                originX = geo.marginPx.toFloat(),
                safeTop = statusTop + with(density) { 8.dp.toPx() },
                safeBottom = h - navBottom - with(density) { 8.dp.toPx() },
                entries = entries,
                dragging = dragging,
                onDragging = { d ->
                    dragging = d
                    if (d) entries = null
                },
                onChange = { a, b -> sel = s.copy(start = a, end = b) },
                onClear = { clearSel() },
                onCopy = {
                    val t = selectedText()
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("空叶阅读", t))
                    // Android 13 起系统自己会提示已复制
                    if (Build.VERSION.SDK_INT < 33) Toast.show("已复制")
                    clearSel()
                },
                onDict = {
                    val t = selectedText()
                    scope.launch {
                        val list = withContext(Dispatchers.IO) { Dict.lookup(t) }
                        list.filter { it.found }.forEach { com.kongye.reader.data.AchievementTracking.lookedUp(book.id,it.word) }
                        if (sel != null) entries = list
                    }
                },
                onShare = {
                    val t = selectedText()
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t)
                    runCatching { context.startActivity(Intent.createChooser(send, "分享")) }
                    clearSel()
                },
                onSelectAll = {
                    val r = s.ref
                    sel = s.copy(start = r.start, end = max(r.end, r.start + 1))
                    entries = null
                },
            )
        }

        // —— 菜单、目录、读完 ——
        val bookmarks = live.bookmarks
        val marked = ref != null && run {
            val nextStart = state.current()?.pages?.getOrNull(state.page + 1)?.start ?: Int.MAX_VALUE
            bookmarks.any { it.chapter == state.chapter && it.offset >= ref.start && it.offset < nextStart }
        }
        ReaderMenu(
            visible = menu,
            panel = panel,
            onPanel = { panel = it },
            title = live.title,
            chapterTitle = chTitle(),
            chapter = state.chapter,
            last = state.last,
            chapterTitleAt = { content.chapters[it].title },
            marked = marked,
            night = night,
            onBack = { Nav.pop() },
            onEdit = {
                menu = false
                editing = true
            },
            onSearch = {
                menu = false
                searching = true
            },
            onBookmark = {
                val r = state.currentRef()
                val cp = state.current()
                if (r != null && cp != null) {
                    val ch = state.chapter
                    val nextStart = cp.pages.getOrNull(state.page + 1)?.start ?: Int.MAX_VALUE
                    val body = content.body(ch)
                    val excerpt = body.substring(r.start.coerceAtMost(body.length), min(body.length, r.start + 60)).replace('\n', ' ').trim()
                    var added = false
                    Library.update(book.id) { b ->
                        val inPage = b.bookmarks.filter { it.chapter == ch && it.offset >= r.start && it.offset < nextStart }
                        if (inPage.isNotEmpty()) b.copy(bookmarks = b.bookmarks - inPage.toSet())
                        else {
                            added = true
                            b.copy(bookmarks = b.bookmarks + com.kongye.reader.data.Bookmark(ch, r.start, excerpt, System.currentTimeMillis()))
                        }
                    }
                    Toast.show(if (added) "已添加书签" else "已移除书签")
                }
            },
            onJumpChapter = { state.jump(it) },
            onToc = {
                menu = false
                toc = true
            },
            onToggleNight = {
                Settings.update { s ->
                    if (night) s.copy(night = false, nightFollowSystem = if (appDark) false else s.nightFollowSystem)
                    else s.copy(night = true)
                }
            },
            onDismiss = { menu = false },
        )

        BackHandler(toc) { toc = false }
        AnimatedVisibility(toc, enter = slideInHorizontally(tween(260)) { -it } + fadeIn(), exit = slideOutHorizontally(tween(220)) { -it } + fadeOut()) {
            TocPage(
                book = live,
                content = content,
                current = state.chapter,
                onClose = { toc = false },
                onJump = { ch, off ->
                    state.jump(ch, off)
                    toc = false
                },
            )
        }

        BackHandler(searching) { searching = false }
        AnimatedVisibility(searching, enter = slideInVertically(tween(240)) { it / 6 } + fadeIn(), exit = fadeOut(tween(180))) {
            SearchPage(
                content = content,
                onClose = { searching = false },
                onJump = { ch, off ->
                    state.jump(ch, off)
                    searching = false
                },
            )
        }

        BackHandler(finishedShown) { finishedShown = false }
        AnimatedVisibility(finishedShown, enter = fadeIn(), exit = fadeOut()) {
            FinishPage(live, tracker.active) { finishedShown = false }
        }

        // 编辑页和阅读页版式一样，淡入淡出就好，不滑动，免得看着像换了一页
        AnimatedVisibility(editing, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
            val ch = state.chapter
            val at = editAt ?: state.anchor ?: state.currentRef()?.start ?: 0
            fun restructure(done: String, work: () -> Boolean) {
                saving = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { work() }
                    saving = false
                    if (ok) {
                        Toast.show(done)
                        onEdited(true)
                    } else {
                        Toast.show("没有改成")
                    }
                }
            }
            key(ch) { EditPage(
                spec = spec,
                textColor = paper.text,
                textLeft = geo.marginPx,
                textTop = geo.textTopPx,
                title = content.chapters[ch].title,
                body = content.body(ch).trimEnd('\n'),
                cursor = at,
                alignTitle = at == 0,
                saving = saving,
                onClose = {
                    editing = false
                    editAt = null
                },
                onSave = { t, b, cur ->
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Library.saveChapter(book.id, ch, t, b, cur) }
                        saving = false
                        if (ok) {
                            Toast.show("已保存")
                            Profiles.count("edit")
                            editing = false
                            onEdited(false)
                        } else {
                            Toast.show("保存失败")
                        }
                    }
                },
                onPromote = { t, b, ps, pe ->
                    restructure("这一段成了新一章的标题") { Library.splitChapter(book.id, ch, t, b, ps, pe) }
                },
                onDemote = { t, b ->
                    restructure(if (ch == 0) "标题放回了正文" else "这一章并进了上一章") { Library.demoteTitle(book.id, ch, t, b) }
                },
                chapter = ch,
                chapters = content.chapters.map { it.title },
                // 换一章编辑：改过的先存，存好后整本重新载入，编辑页停在那一章开头；没改过直接换
                onSwitch = { target, save ->
                    if (save == null) {
                        editAt = 0
                        state.jump(target, 0)
                    } else {
                        saving = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { Library.saveChapter(book.id, ch, save.first, save.second, -1) }
                            saving = false
                            if (ok) {
                                Profiles.count("edit")
                                Library.update(book.id) { it.copy(chapter = target, offset = 0) }
                                Toast.show("已保存")
                                onEdited(true)
                            } else {
                                Toast.show("保存失败")
                            }
                        }
                    }
                },
            ) }
        }
    }
}

/** 选中的字底下铺一层浅色 */
private fun DrawScope.drawSelection(sel: Sel?, cp: ChapterPages, idx: Int, layout: TextLayoutResult, color: Color) {
    if (sel == null || !sel.on(cp, idx)) return
    drawPath(layout.getPathForRange(sel.layoutStart, sel.layoutEnd), color)
}

@Composable
private fun PageView(
    n: Pair<ChapterPages, Int>?,
    content: BookContent,
    bookTitle: String,
    paper: Paper,
    geo: Geo,
    status: StatusInfo,
    showStatus: Boolean,
    sel: Sel?,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().background(paper.bg)) {
        if (n == null) return@Box
        val (cp, idx) = n
        val ref = cp.pages.getOrNull(idx) ?: return@Box
        val layout = cp.layouts[ref.chunk]
        val header = if (idx == 0) bookTitle else content.chapters[cp.index].title
        val selColor = K.c.accent.copy(alpha = if (paper.dark) 0.32f else 0.2f)
        T(
            header, 11.sp, paper.sub,
            modifier = Modifier
                .padding(start = geo.margin, end = geo.margin, top = geo.headerTop)
                .fillMaxWidth(),
            maxLines = 1,
        )
        Canvas(
            Modifier
                .offset { IntOffset(geo.marginPx, geo.textTopPx) }
                .size(geo.textW, geo.textH),
        ) {
            clipRect(-12f, 0f, size.width + 12f, ref.bottom - ref.top) {
                translate(0f, -ref.top) {
                    drawSelection(sel, cp, idx, layout, selColor)
                    drawText(layout, color = paper.text)
                }
            }
        }
        if (showStatus) {
            StatusRow(status, paper, geo, "${idx + 1}/${cp.pages.size}", Fmt.percent(content.progressOf(cp.index, ref.start)))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StatusRow(status: StatusInfo, paper: Paper, geo: Geo, pages: String?, percent: String) {
    Row(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(start = geo.margin, end = geo.margin, bottom = geo.footerBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(status.time, 11.sp, paper.sub)
        if (status.battery >= 0) {
            Spacer(Modifier.width(8.dp))
            Battery(status.battery, paper.sub)
        }
        Spacer(Modifier.weight(1f))
        if (pages != null) {
            T(pages, 11.sp, paper.sub)
            Spacer(Modifier.width(12.dp))
        }
        T(percent, 11.sp, paper.sub)
    }
}

/** 上下滚动：把排好的页一页接一页竖着排，前后章排好了就接上 */
@Composable
private fun ScrollReader(
    state: ReaderState,
    content: BookContent,
    paper: Paper,
    geo: Geo,
    status: StatusInfo,
    showStatus: Boolean,
    listState: LazyListState,
    sel: Sel?,
    tap: Modifier,
    onPosition: (Int, Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val loaded = state.layouts
    val chapters = run {
        if (!loaded.containsKey(state.chapter)) return@run emptyList()
        var lo = state.chapter
        while (loaded.containsKey(lo - 1)) lo--
        var hi = state.chapter
        while (loaded.containsKey(hi + 1)) hi++
        (lo..hi).mapNotNull { loaded[it] }
    }
    // 跳转（目录、进度条、重新分页）后滚到目标页；没对上之前不拿滚动位置去改阅读位置
    var synced by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) { state.mark() }
    LaunchedEffect(state.seek, chapters) {
        if (synced == state.seek) return@LaunchedEffect
        var idx = 0
        for (cp in chapters) {
            if (cp.index == state.seekChapter) {
                // 下一章还没排好时列表可能还不到一屏长，滚过去会被截住：等它接上再滚
                if (cp.index < state.last && chapters.none { it.index == cp.index + 1 }) return@LaunchedEffect
                val p = state.seekPage.coerceIn(0, cp.pages.lastIndex)
                // 要回到的那一行在这一页里往下多少（0 是章首，连标题一起露出来）
                var dy = 0
                val off = state.seekOffset
                if (off > 0) {
                    val r = cp.pages[p]
                    val lay = cp.layouts[r.chunk]
                    val lo = cp.toLayout(r.chunk, off).coerceIn(0, lay.layoutInput.text.length)
                    dy = (lay.getLineTop(lay.getLineForOffset(lo)) - r.top).toInt().coerceAtLeast(0)
                }
                listState.scrollToItem(idx + p, dy)
                synced = state.seek
                return@LaunchedEffect
            }
            idx += cp.pages.size
        }
    }
    val position by rememberUpdatedState(onPosition)
    LaunchedEffect(listState) {
        // 当前位置看屏幕上方约五分之一处的那一页：一章很短、上一章只露个尾巴时，算作已经读到下一章
        snapshotFlow {
            val info = listState.layoutInfo
            val probe = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 5
            val item = info.visibleItemsInfo.firstOrNull { it.offset + it.size > probe } ?: info.visibleItemsInfo.firstOrNull()
            item?.let { (it.key as? Long)?.let { k -> k to (-it.offset).coerceAtLeast(0) } }
        }.collect { v ->
            val (k, dy) = v ?: return@collect
            if (synced == state.seek) position((k shr 32).toInt(), (k and 0xffffffffL).toInt(), dy)
        }
    }
    val selColor = K.c.accent.copy(alpha = if (paper.dark) 0.32f else 0.2f)

    Box(Modifier.fillMaxSize().then(tap)) {
        T(
            content.chapters[state.chapter].title, 11.sp, paper.sub,
            modifier = Modifier
                .padding(start = geo.margin, end = geo.margin, top = geo.headerTop)
                .fillMaxWidth(),
            maxLines = 1,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .offset { IntOffset(0, geo.textTopPx) }
                .fillMaxWidth()
                .height(geo.textH),
        ) {
            for (cp in chapters) {
                items(cp.pages.size, key = { p -> pageKey(cp.index, p) }) { p ->
                    val ref = cp.pages[p]
                    val layout = cp.layouts[ref.chunk]
                    val hDp = with(density) { (ref.next - ref.top + ref.gapAfter).coerceAtLeast(1f).toDp() }
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(hDp)
                            .padding(horizontal = geo.margin),
                    ) {
                        clipRect(-12f, 0f, size.width + 12f, ref.next - ref.top) {
                            translate(0f, -ref.top) {
                                drawSelection(sel, cp, p, layout, selColor)
                                drawText(layout, color = paper.text)
                            }
                        }
                    }
                }
            }
        }
        if (showStatus) {
            val ref = state.currentRef()
            StatusRow(status, paper, geo, null, Fmt.percent(content.progressOf(state.chapter, state.anchor ?: ref?.start ?: 0)))
        }
    }
}

@Composable
private fun Battery(level: Int, color: Color) {
    Canvas(Modifier.size(22.dp, 11.dp)) {
        val sw = 1.dp.toPx()
        val bw = size.width - 2.5.dp.toPx()
        drawRoundRect(color, Offset(sw / 2, sw / 2), Size(bw - sw, size.height - sw), CornerRadius(2.5.dp.toPx()), style = Stroke(sw))
        val pad = 2.dp.toPx()
        drawRoundRect(color, Offset(pad, pad), Size((bw - pad * 2) * level / 100f, size.height - pad * 2), CornerRadius(1.dp.toPx()))
        drawRoundRect(color, Offset(bw + 0.5.dp.toPx(), size.height * 0.32f), Size(1.5.dp.toPx(), size.height * 0.36f), CornerRadius(1.dp.toPx()))
    }
}

/** 读到最后一页再往后翻：参考“继续”那一页，猫头上长出小叶子 */
@Composable
private fun FinishPage(book: Book, live: Long, onStay: () -> Unit) {
    val c = K.c
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .blockTouches(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        CatMascot(size = 200.dp, accent = true, blink = true)
        Spacer(Modifier.height(16.dp))
        T("读完啦！", 24.sp, c.ink, FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Chip("《${book.title}》共读 ${Fmt.cn(book.readMillis + live)}", ico = Ico.BOOK)
        Spacer(Modifier.weight(1f))
        InkButton("回到书架", Modifier.size(width = 280.dp, height = 60.dp)) {
            Nav.pop()
            if (Nav.top != Screen.Shelf) Nav.push(Screen.Shelf)
        }
        Spacer(Modifier.height(12.dp))
        GhostButton("再翻翻", Ico.BACK, onClick = onStay)
        Spacer(Modifier.height(40.dp))
    }
}
