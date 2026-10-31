package com.kongye.reader.ui.reader

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.BookContent

/** 版面参数：任何一项变了都要重新分页 */
data class PageSpec(
    val width: Int,
    val height: Int,
    val fontSize: Float,
    val lineHeight: Float,
    val paraSpacing: Float,
    val indent: Float,
    val font: Int,
    val density: Float,
    val fontScale: Float,
)

/**
 * 一页 = 某个排版块里从 top 到 bottom 的若干整行；[start, end) 是这一页在章节正文里的字符范围。
 * 上下滚动时一页接一页往下排：这一页占 next - top 高，再空出 gapAfter（排版块之间的段距、章与章之间的留白）
 */
class PageRef(
    val chunk: Int,
    val top: Float,
    val bottom: Float,
    val next: Float,
    val start: Int,
    val end: Int,
    val gapAfter: Float,
)

class ChapterPages(
    val index: Int,
    val layouts: List<TextLayoutResult>,
    val pages: List<PageRef>,
    private val chunkStart: IntArray,
    private val prefix: IntArray,
) {
    fun pageFor(offset: Int): Int {
        var r = 0
        for (i in pages.indices) if (pages[i].start <= offset) r = i else break
        return r
    }

    /** 正文偏移 → 排版结果里的偏移（第一块前面有标题） */
    fun toLayout(chunk: Int, body: Int): Int = body - chunkStart[chunk] + prefix[chunk]

    fun toBody(chunk: Int, layout: Int): Int = chunkStart[chunk] + (layout - prefix[chunk]).coerceAtLeast(0)

    fun inTitle(chunk: Int, layout: Int): Boolean = layout < prefix[chunk]
}

/** 段落之间的占位字符：换行换成它，字数不变，章节里的字符偏移照旧 */
const val PARA_GAP = '\u200B'

/** 正文样式。阅读页排版和编辑页共用，两边断行完全一样 */
fun bodyTextStyle(spec: PageSpec) = TextStyle(
    color = Color.Black,
    fontSize = spec.fontSize.sp,
    lineHeight = (spec.fontSize * spec.lineHeight).sp,
    fontFamily = ReaderFonts.family(spec.font),
    // 不用系统的两端对齐：Android 15 起它会把汉字间距撑开，但取字位置的接口不算这部分，
    // 长按选字和高亮会越往行尾越偏。改成左对齐排一遍，再给每行加字间距补齐（见 Paginator.justify）
    textAlign = TextAlign.Start,
    textIndent = TextIndent(firstLine = (spec.fontSize * spec.indent).sp),
    // 逐行贪心断行：补字间距后断行位置不会变
    lineBreak = LineBreak(LineBreak.Strategy.Simple, LineBreak.Strictness.Strict, LineBreak.WordBreak.Default),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** 段距：段与段之间夹一个只有占位字符的小段落，行高就是段距 */
fun gapParagraphStyle(spec: PageSpec) = ParagraphStyle(
    textIndent = TextIndent.None,
    lineHeight = (spec.fontSize * spec.paraSpacing).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** 章节标题：正文字号的 1.35 倍、加粗，后面空一行正文行高再接正文 */
fun titleTextStyle(spec: PageSpec): TextStyle {
    val ts = spec.fontSize * TITLE_SCALE
    return bodyTextStyle(spec).copy(fontSize = ts.sp, fontWeight = FontWeight.Bold, lineHeight = (ts * 1.45f).sp, textIndent = TextIndent.None)
}

const val TITLE_SCALE = 1.35f

/**
 * 两端对齐要补的字间距：段落中间的每一行，把右边剩下的空白平均摊到这一行的每个字上。
 * 段落最后一行、含英文字母的行、空得太多（超过 2.5 个字）的行不补。返回 [行首, 行尾, 每字多出的像素]
 */
fun justifyLines(layout: TextLayoutResult, raw: String, width: Int, emPx: Float, from: Int = 0): List<Triple<Int, Int, Float>> {
    val out = ArrayList<Triple<Int, Int, Float>>()
    for (l in 0 until layout.lineCount) {
        val s = layout.getLineStart(l)
        val e = layout.getLineEnd(l)
        if (s < from || e >= raw.length || e - s < 2) continue
        if (raw[e - 1] == '\n' || raw[e] == PARA_GAP || raw[s] == PARA_GAP) continue
        if ((s until e).any { raw[it] in 'A'..'Z' || raw[it] in 'a'..'z' }) continue
        val slack = width - layout.getLineRight(l) - 1f
        if (slack <= 0.5f || slack > emPx * 2.5f) continue
        out += Triple(s, e, slack / (e - s))
    }
    return out
}

fun withSpacing(text: AnnotatedString, lines: List<Triple<Int, Int, Float>>, spec: PageSpec): AnnotatedString {
    val toSp = spec.density * spec.fontScale
    return buildAnnotatedString {
        append(text)
        for ((s, e, ls) in lines) addStyle(SpanStyle(letterSpacing = (ls / toSp).sp), s, e)
    }
}

/** 补了字间距以后断行没变才用，宁可右边不齐也不能错行 */
fun sameBreaks(a: TextLayoutResult, b: TextLayoutResult): Boolean {
    if (a.lineCount != b.lineCount) return false
    for (l in 0 until a.lineCount) if (a.getLineEnd(l) != b.getLineEnd(l)) return false
    return true
}

/**
 * 按章节排版分页。一章太长时按段落切成几块分别排版（每块约 6000 字），
 * 每页只取整行，画的时候把对应排版结果平移、裁剪到页面里。
 */
class Paginator(private val content: BookContent, private val spec: PageSpec, private val measurer: TextMeasurer) {
    private val cache = object : LinkedHashMap<Int, ChapterPages>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ChapterPages>?) = size > 7
    }

    private val density = Density(spec.density, spec.fontScale)
    private val spaced = spec.paraSpacing > 0.01f
    private val gapPx = with(density) { (spec.fontSize * spec.paraSpacing).sp.toPx() }
    private val chapterGapPx = with(density) { (spec.fontSize * 3).sp.toPx() }
    private val emPx = with(density) { spec.fontSize.sp.toPx() }

    private val base = bodyTextStyle(spec)
    private val gapStyle = gapParagraphStyle(spec)

    @Synchronized
    fun get(ch: Int): ChapterPages = cache[ch] ?: layout(ch).also { cache[ch] = it }

    private fun AnnotatedString.Builder.appendBody(body: String, from: Int, to: Int) {
        if (!spaced) {
            append(body, from, to)
            return
        }
        var i = from
        while (i < to) {
            var nl = body.indexOf('\n', i)
            if (nl < 0 || nl >= to) nl = to
            append(body, i, nl)
            if (nl < to) withStyle(gapStyle) { withStyle(SpanStyle(fontSize = 1.sp)) { append(PARA_GAP) } }
            i = nl + 1
        }
    }

    /**
     * 自己做两端对齐：段落中间的每一行，把右边剩下的空白平均摊成这一行的字间距，再排一遍。
     * 字间距进了排版本身，画出来的位置和取字位置一致。含英文的行、空得太多的行不拉，保持左对齐
     */
    private fun justify(text: AnnotatedString, first: TextLayoutResult, prefix: Int, constraints: Constraints): TextLayoutResult {
        val lines = justifyLines(first, text.text, spec.width, emPx, prefix)
        if (lines.isEmpty()) return first
        val second = measurer.measure(withSpacing(text, lines, spec), base, constraints = constraints, density = density)
        return if (sameBreaks(first, second)) second else first
    }

    private fun layout(ch: Int): ChapterPages {
        val title = content.chapters[ch].title
        val body = content.body(ch)
        val layouts = ArrayList<TextLayoutResult>()
        val pages = ArrayList<PageRef>()
        val constraints = Constraints(maxWidth = spec.width)
        val parts = chunks(body)
        val starts = IntArray(parts.size)
        val prefixes = IntArray(parts.size)
        parts.forEachIndexed { ci, (cs, ce) ->
            val textEnd = if (ce > cs && body[ce - 1] == '\n') ce - 1 else ce
            val prefix: Int
            val text: AnnotatedString
            if (ci == 0) {
                val ts = spec.fontSize * TITLE_SCALE
                text = buildAnnotatedString {
                    withStyle(ParagraphStyle(textIndent = TextIndent.None, lineHeight = (ts * 1.45f).sp, textAlign = TextAlign.Start)) {
                        withStyle(SpanStyle(fontSize = ts.sp, fontWeight = FontWeight.Bold)) { append(title) }
                    }
                    // 标题后面这个换行会形成一个空行，作为标题与正文的间距
                    append('\n')
                    appendBody(body, cs, textEnd)
                }
                prefix = title.length + 1
            } else {
                text = buildAnnotatedString { appendBody(body, cs, textEnd) }
                prefix = 0
            }
            starts[ci] = cs
            prefixes[ci] = prefix
            val raw = text.text
            val layout = justify(text, measurer.measure(text, base, constraints = constraints, density = density), prefix, constraints)
            layouts += layout
            fun gapLine(l: Int): Boolean {
                if (!spaced) return false
                val s = layout.getLineStart(l)
                return layout.getLineEnd(l) - s <= 1 && raw.getOrNull(s) == PARA_GAP
            }
            fun toBody(o: Int) = (cs + (o - prefix).coerceAtLeast(0)).coerceAtMost(textEnd)
            val n = layout.lineCount
            val spans = ArrayList<IntArray>()
            var line = 0
            while (line < n) {
                // 一页不从段距空行开始
                if (gapLine(line)) {
                    line++
                    continue
                }
                val top = layout.getLineTop(line)
                var end = line
                while (end + 1 < n && layout.getLineBottom(end + 1) - top <= spec.height) end++
                var last = end
                while (last > line && gapLine(last)) last--
                spans += intArrayOf(line, last)
                line = end + 1
            }
            val chunkBottom = if (n > 0) layout.getLineBottom(n - 1) else 0f
            spans.forEachIndexed { k, (a, b) ->
                val top = layout.getLineTop(a)
                val next = if (k + 1 < spans.size) layout.getLineTop(spans[k + 1][0]) else chunkBottom
                val lastInChunk = k == spans.lastIndex
                val gap = when {
                    !lastInChunk -> 0f
                    ci == parts.lastIndex -> chapterGapPx
                    else -> gapPx
                }
                pages += PageRef(ci, top, layout.getLineBottom(b), next, toBody(layout.getLineStart(a)), toBody(layout.getLineEnd(b)), gap)
            }
        }
        if (pages.isEmpty()) pages += PageRef(0, 0f, 0f, 0f, 0, 0, chapterGapPx)
        return ChapterPages(ch, layouts, pages, starts, prefixes)
    }

    private fun chunks(body: String, max: Int = 6000): List<Pair<Int, Int>> {
        if (body.length <= max * 1.3) return listOf(0 to body.length)
        val out = ArrayList<Pair<Int, Int>>()
        var s = 0
        while (s < body.length) {
            var e = minOf(body.length, s + max)
            if (e < body.length) {
                val nl = body.lastIndexOf('\n', e)
                if (nl > s + max / 2) e = nl + 1
            }
            out += s to e
            s = e
        }
        return out
    }
}

/** 阅读位置：第几章第几页；新排版到来前用 pending 记住要恢复的字符位置 */
@Stable
class ReaderState(val content: BookContent, startChapter: Int, startOffset: Int) {
    var paginator by mutableStateOf<Paginator?>(null)
    var chapter by mutableIntStateOf(startChapter.coerceIn(0, content.last))
    var page by mutableIntStateOf(0)
    var pending: Int? = startOffset
    val layouts = mutableStateMapOf<Int, ChapterPages>()

    /** 每次跳转（目录、进度条、重新分页后归位）加一，上下滚动模式据此滚到 seekChapter / seekPage */
    var seek by mutableIntStateOf(0)
    var seekChapter = 0
        private set
    var seekPage = 0
        private set
    /** 跳到的具体字（-1 表示页首），上下滚动时据此滚到那一行 */
    var seekOffset = -1
        private set

    fun mark(offset: Int = -1) {
        seekChapter = chapter
        seekPage = page
        seekOffset = offset
        seek++
    }

    /** 上下滚动时屏幕顶上那一行在本章里的位置；比页首精确，进度和书签按它记 */
    var anchor by mutableStateOf<Int?>(null)

    val last get() = content.last

    fun current(): ChapterPages? = layouts[chapter]

    fun currentRef(): PageRef? = current()?.pages?.getOrNull(page)

    fun currentOffset(): Int = pending ?: anchor ?: currentRef()?.start ?: 0

    fun neighbor(dir: Int): Pair<ChapterPages, Int>? {
        val cp = layouts[chapter] ?: return null
        val p = page + dir
        return when {
            p in cp.pages.indices -> cp to p
            dir > 0 && p >= cp.pages.size && chapter < last -> layouts[chapter + 1]?.let { it to 0 }
            dir < 0 && p < 0 && chapter > 0 -> layouts[chapter - 1]?.let { it to it.pages.lastIndex }
            else -> null
        }
    }

    fun atEnd(): Boolean = chapter == last && layouts[chapter]?.let { page >= it.pages.lastIndex } == true

    fun atStart(): Boolean = chapter == 0 && page == 0

    fun move(dir: Int): Boolean {
        val n = neighbor(dir) ?: return false
        chapter = n.first.index
        page = n.second
        anchor = null
        return true
    }

    fun jump(ch: Int, offset: Int = 0) {
        val target = ch.coerceIn(0, last)
        val cp = layouts[target]
        chapter = target
        anchor = null
        if (cp != null) {
            page = cp.pageFor(offset)
            pending = null
            mark(offset)
        } else {
            page = 0
            pending = offset
        }
    }

    /** 新排好一章时，如果正等着回到某个字，就定位过去 */
    fun resolve(cp: ChapterPages) {
        if (cp.index != chapter) return
        val off = pending ?: return
        page = cp.pageFor(off)
        pending = null
        mark(off)
    }

    /** 只留当前章前后两章的排版，长时间阅读不至于越占越多内存 */
    fun trim() {
        val keep = chapter - 2..chapter + 2
        layouts.keys.filter { it !in keep }.forEach { layouts.remove(it) }
    }
}
