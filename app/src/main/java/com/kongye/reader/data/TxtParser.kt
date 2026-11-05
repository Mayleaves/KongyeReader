package com.kongye.reader.data

import java.nio.charset.Charset

/** 识别 TXT 编码：BOM → UTF-16 → 严格 UTF-8 → GB18030（兼容 GBK/GB2312） */
object TextDecode {
    fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        utf16NoBom(b)?.let { return String(b, it) }
        if (isUtf8(b)) return String(b, Charsets.UTF_8)
        return String(b, Charset.forName("GB18030"))
    }

    /** 有 0 字节、又不像 UTF-16，基本可以断定不是文本 */
    fun looksBinary(b: ByteArray): Boolean {
        if (b.size >= 2 && (b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() || b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte())) return false
        val n = minOf(b.size, 8192)
        var zeros = 0
        for (i in 0 until n) if (b[i] == 0.toByte()) zeros++
        return zeros > n / 100 && utf16NoBom(b) == null
    }

    private fun utf16NoBom(b: ByteArray): Charset? {
        val n = minOf(b.size, 4096) and -2
        if (n < 4) return null
        var even = 0
        var odd = 0
        for (i in 0 until n) if (b[i] == 0.toByte()) { if (i % 2 == 0) even++ else odd++ }
        val half = n / 2
        return when {
            odd > half * 0.3 && even < half * 0.05 -> Charsets.UTF_16LE
            even > half * 0.3 && odd < half * 0.05 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun isUtf8(b: ByteArray): Boolean {
        var i = 0
        val n = b.size
        while (i < n) {
            val c = b[i].toInt() and 0xFF
            val need = when {
                c < 0x80 -> 0
                c in 0xC2..0xDF -> 1
                c in 0xE0..0xEF -> 2
                c in 0xF0..0xF4 -> 3
                else -> return false
            }
            if (i + need >= n) return true // 末尾被截断的半个字符不算错
            for (k in 1..need) if ((b[i + k].toInt() and 0xC0) != 0x80) return false
            i += need + 1
        }
        return true
    }
}

/** 把 TXT 整理成“段落用 \n 分隔的正文 + 章节范围” */
object TxtParser {
    class Parsed(val content: String, val chapters: List<Chapter>)

    /** 文件开头写的书籍信息 */
    class Meta(val author: String, val intro: String, val tags: List<String>)

    private const val NUMS = "0-9０-９零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟"
    private const val CN = "零〇一二两三四五六七八九十百千"

    // 明确的标题：第X章、卷X、序章楔子尾声、Chapter N
    private val chapterRe = Regex("^第\\s*([$NUMS]{1,9})\\s*[章节回卷集部篇幕话](?:[\\s　:：·、.．\\-—]*.{0,30})?$")
    private val volumeRe = Regex("^卷\\s*[$NUMS]{1,5}(?:[\\s　:：·、\\-—]*.{0,30})?$")
    private val specialRe = Regex("^(序章|序言|序|楔子|引子|引言|前言|后记|尾声|终章|番外|外传|完本感言|作者的话|写在前面)[$NUMS]{0,4}([\\s　：:·、\\-—].{0,30})?$")
    private val englishRe = Regex("^(?:Chapter|CHAPTER|Part|PART)\\s*([0-9]{1,4}|[IVXLCivxlc]{1,8})\\b.{0,30}$")
    private val englishSpecial = Regex("^(?:Prologue|PROLOGUE|Epilogue|EPILOGUE)\\b.{0,30}$")

    // 作者没写“第X章”时才看的几种写法
    private val numDotRe = Regex("^(?:([0-9０-９]{1,4})[、.．:：]|([$CN]{1,6})[、.．:：]|[（(]([0-9０-９]{1,4}|[$CN]{1,6})[)）])(?![0-9０-９])\\s*(.{0,25})$")
    private val numPlainRe = Regex("^([0-9０-９]{1,4})(?:[\\s　]+(.{1,25}))?$")
    private val bracketRe = Regex("^[【〔\\[［「『](.{1,24})[】〕\\]］」』]$")
    private val ruler = Regex("^[=\\-*_~—·.。]{3,}$")

    private const val CH = 0
    private const val SPECIAL = 1
    private const val NUM = 2
    private const val NUM_PLAIN = 3
    private const val BRACKET = 4
    private const val SHORT = 5

    private class Kind(val kind: Int, val num: Int = -1)

    fun isSpace(c: Char) = c.isWhitespace() || c == '　' || c == ' ' || c == '\uFEFF' || c == '\u200B'

    fun parse(text: String): Parsed {
        // 先切成行：去掉首尾空白，记下每行前后是不是空行（单独占一行的短句才可能是标题）
        val lines = ArrayList<String>()
        val blankBefore = ArrayList<Boolean>()
        // 行首有没有缩进：很多 TXT 正文每段开头空两格，标题顶格
        val indented = ArrayList<Boolean>()
        var blank = true
        var i = 0
        val n = text.length
        while (i < n) {
            var e = text.indexOf('\n', i)
            if (e < 0) e = n
            var s = i
            var t = e
            while (s < t && isSpace(text[s])) s++
            while (t > s && isSpace(text[t - 1])) t--
            i = e + 1
            val line = if (s == t) "" else text.substring(s, t)
            if (line.isEmpty() || ruler.matches(line)) {
                blank = true
                continue
            }
            lines += line
            blankBefore += blank
            // 跳过了行首空白，前一个字符就不是换行
            indented += s > 0 && text[s - 1] != '\n'
            blank = false
        }
        // 正文大多缩进时，顶格的短句很可能是标题，前后没有空行也算
        val bodyIndented = indented.count { it } > lines.size * 0.6
        val alone = BooleanArray(lines.size) { k ->
            if (bodyIndented) !indented[k] else blankBefore[k] && (k + 1 >= lines.size || blankBefore[k + 1])
        }
        val heads = pickHeadings(lines, alone)

        val sb = StringBuilder(text.length)
        // 每个标题记下“下一段会从哪里开始”
        val marks = ArrayList<Pair<String, Int>>()
        marks += "开篇" to 0
        for (k in lines.indices) {
            if (heads[k]) {
                marks += lines[k].replace(Regex("[\\s　]+"), " ") to (if (sb.isEmpty()) 0 else sb.length + 1)
                continue
            }
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(lines[k])
        }
        val content = sb.toString()
        val len = content.length
        var chapters = ArrayList<Chapter>()
        for (k in marks.indices) {
            val start = minOf(marks[k].second, len)
            val end = if (k + 1 < marks.size) maxOf(start, minOf(marks[k + 1].second - 1, len)) else len
            chapters += Chapter(marks[k].first, start, end)
        }
        // “开篇”没有内容就去掉；开头那种整页目录（只有标题没有正文、后面又出现同名章）也去掉
        val real = chapters.filter { it.end > it.start }.map { it.title }.toHashSet()
        chapters = chapters.filterIndexedTo(ArrayList()) { idx, c ->
            if (c.end > c.start) true
            else if (idx == 0) false
            else c.title !in real
        }
        if (chapters.size <= 1) chapters = splitEven(content)
        return Parsed(content, chapters)
    }

    /**
     * 哪些行是章节标题。有两个以上“第X章”就只认这种写法；没有的话，再从
     * “1、”“一、”“(1)”“001 标题”“【标题】”和单独一行的短句里挑一套最像目录的：
     * 数量够、彼此隔得开（一章至少几百字），编号的还要大体按 1、2、3 往下排。序章、楔子、尾声这类总是算。
     */
    private fun pickHeadings(lines: List<String>, alone: BooleanArray): BooleanArray {
        val n = lines.size
        val kinds = Array(n) { classify(lines[it], alone[it]) }
        val pick = BooleanArray(n)
        val strong = kinds.count { it?.kind == CH }
        var chosen = -1
        if (strong < 2) {
            val pos = LongArray(n + 1)
            for (k in 0 until n) pos[k + 1] = pos[k] + lines[k].length
            fun ok(kind: Int): Boolean {
                val idx = (0 until n).filter { kinds[it]?.kind == kind }
                if (idx.size < (if (kind == SHORT) 4 else 3)) return false
                val gaps = idx.zipWithNext { a, b -> pos[b] - pos[a] }
                val minGap = when (kind) {
                    NUM, NUM_PLAIN -> 500
                    BRACKET -> 800
                    else -> 1500
                }
                if (gaps.average() < minGap) return false
                if (kind == NUM || kind == NUM_PLAIN) {
                    // 编号大体连续：下一个是上一个加一，或者新的一卷从 1 重新开始
                    val nums = idx.map { kinds[it]!!.num }
                    val steps = nums.zipWithNext().count { (a, b) -> b == a + 1 || (b <= 1 && a >= 1) }
                    if (steps < (nums.size - 1) * 0.6) return false
                } else if (gaps.count { it < 300 } > gaps.size / 5) {
                    // 没有编号的，挨得太近的多半是正文里的短句
                    return false
                }
                return true
            }
            chosen = listOf(NUM, NUM_PLAIN).filter(::ok).maxByOrNull { k -> kinds.count { it?.kind == k } }
                ?: listOf(BRACKET, SHORT).firstOrNull(::ok) ?: -1
        }
        for (k in 0 until n) {
            val kd = kinds[k]?.kind ?: continue
            if (kd == CH || kd == SPECIAL || kd == chosen) pick[k] = true
        }
        return pick
    }

    private fun classify(line: String, alone: Boolean): Kind? {
        if (line.length > 40) return null
        chapterRe.matchEntire(line)?.let { return Kind(CH, number(it.groupValues[1])) }
        if (volumeRe.matches(line)) return Kind(CH)
        if (specialRe.matches(line) || englishSpecial.matches(line)) return Kind(SPECIAL)
        englishRe.matchEntire(line)?.let { return Kind(CH, number(it.groupValues[1])) }
        bracketRe.matchEntire(line)?.let { m ->
            val inner = m.groupValues[1].trim()
            classify(inner, false)?.let { if (it.kind == CH || it.kind == SPECIAL) return it }
            return if (titleLike(inner, strict = false)) Kind(BRACKET) else null
        }
        numDotRe.matchEntire(line)?.let { m ->
            val num = m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }
            val rest = m.groupValues[4].trim()
            return if (rest.isEmpty() || titleLike(rest, strict = false)) Kind(NUM, number(num)) else null
        }
        numPlainRe.matchEntire(line)?.let { m ->
            val rest = m.groupValues[2].trim()
            return if (rest.isEmpty() || titleLike(rest, strict = false)) Kind(NUM_PLAIN, number(m.groupValues[1])) else null
        }
        if (alone && line.length in 2..16 && titleLike(line, strict = true)) return Kind(SHORT)
        return null
    }

    /** 像个标题：不以引号开头，不以句号逗号之类结尾；strict 时连感叹号、问号、句中逗号也不要 */
    private fun titleLike(s: String, strict: Boolean): Boolean {
        if (s.isEmpty() || s[0] in "“\"「『‘'—…") return false
        val last = s.last()
        if (last in "。，、；：,;:…—\"”’'」』）)") return false
        if (strict && (last in "！？!?~～" || s.any { it in "，。；、,;" })) return false
        return s.any { Character.isLetter(it) }
    }

    /** 阿拉伯、全角、中文、罗马数字都认；认不出返回 -1 */
    fun number(s: String): Int {
        if (s.isEmpty()) return -1
        val ascii = buildString { for (c in s) append(if (c in '０'..'９') '0' + (c - '０') else c) }
        ascii.toIntOrNull()?.let { return it }
        val roman = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100)
        if (ascii.uppercase().all { it in roman }) {
            val v = ascii.uppercase().map { roman[it]!! }
            return v.indices.sumOf { k -> if (k + 1 < v.size && v[k] < v[k + 1]) -v[k] else v[k] }
        }
        val digit = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '壹' to 1, '二' to 2, '两' to 2, '贰' to 2, '三' to 3, '叁' to 3,
            '四' to 4, '肆' to 4, '五' to 5, '伍' to 5, '六' to 6, '陆' to 6, '七' to 7, '柒' to 7,
            '八' to 8, '捌' to 8, '九' to 9, '玖' to 9,
        )
        val unit = mapOf('十' to 10, '拾' to 10, '百' to 100, '佰' to 100, '千' to 1000, '仟' to 1000)
        if (s.length > 1 && s.all { it in digit }) return s.fold(0) { acc, c -> acc * 10 + digit[c]!! }
        var total = 0
        var section = 0
        var cur = 0
        for (c in s) {
            when (c) {
                in digit -> cur = digit[c]!!
                in unit -> {
                    section += (if (cur == 0) 1 else cur) * unit[c]!!
                    cur = 0
                }
                '万' -> {
                    total += (section + cur) * 10000
                    section = 0
                    cur = 0
                }
                else -> return -1
            }
        }
        return total + section + cur
    }

    private val metaLine = Regex("^([【\\[]?)(作\\s*者|简\\s*介|内容简介|作品简介|内容介绍|文案|标\\s*签|类\\s*型|分\\s*类|题\\s*材|Tags?|TAGS?)([】\\]]?)\\s*([:：]?)\\s*(.*)$")
    private val introKeys = setOf("简介", "内容简介", "作品简介", "内容介绍", "文案")
    private val tagKeys = setOf("标签", "类型", "分类", "题材", "Tag", "Tags", "TAG", "TAGS")

    /**
     * 从开头（第一章之前）找“作者：”“简介：”“标签：”。简介可以有好几段，
     * 碰到下一项信息、章节标题、分隔线或者连着两个空行就停
     */
    fun meta(text: String): Meta {
        val lines = text.take(8000).split('\n').map { l -> l.trim { isSpace(it) } }
        var author = ""
        val intro = ArrayList<String>()
        val tags = LinkedHashSet<String>()
        fun keyOf(line: String): Pair<String, String>? {
            val m = metaLine.matchEntire(line) ?: return null
            val (open, key, close, colon, value) = m.destructured
            // “简介里写着……”这种正文不算：要有冒号、括号，或者这一行只有这个词
            if (open.isEmpty() && close.isEmpty() && colon.isEmpty() && value.isNotEmpty()) return null
            return key.replace(Regex("\\s"), "") to value.trim()
        }
        var k = 0
        while (k < lines.size) {
            val line = lines[k]
            if (k > 0 && (chapterRe.matches(line) || volumeRe.matches(line))) break
            val kv = keyOf(line)
            k++
            if (kv == null) continue
            val (key, value) = kv
            when (key) {
                "作者" -> if (author.isEmpty()) author = value.take(30)
                in tagKeys -> value.split(Regex("[\\s,，、/|；;#]+")).filter { it.length in 1..12 }.forEach { tags += it }
                in introKeys -> if (intro.isEmpty()) {
                    if (value.isNotEmpty()) intro += value
                    var blanks = 0
                    while (k < lines.size && intro.sumOf { it.length } < 1500 && intro.size < 30) {
                        val l = lines[k]
                        if (l.isEmpty()) {
                            if (++blanks >= 2 && intro.isNotEmpty()) break
                            k++
                            continue
                        }
                        if (keyOf(l) != null || ruler.matches(l) || classify(l, false)?.kind.let { it == CH || it == SPECIAL }) break
                        blanks = 0
                        intro += l
                        k++
                    }
                }
            }
        }
        return Meta(author, intro.joinToString("\n"), tags.take(12))
    }

    private val synthetic = Regex("^(开篇|正文|第 \\d+ 节)$")

    /** 已经导入的书重新分章：把原来的标题放回正文再认一遍（自动起的“开篇”“第 N 节”不放） */
    fun reparse(old: BookContent): Parsed {
        val sb = StringBuilder(old.text.length + old.chapters.size * 24)
        for (c in old.chapters) {
            if (!synthetic.matches(c.title)) sb.append(c.title).append("\n\n")
            sb.append(old.text, c.start, c.end).append("\n\n")
        }
        return parse(sb.toString())
    }

    /** 没识别出章节时，按段落把全文切成大约一万字一节，方便跳转 */
    private fun splitEven(content: String): ArrayList<Chapter> {
        val out = ArrayList<Chapter>()
        if (content.length <= 12000) {
            out += Chapter("正文", 0, content.length)
            return out
        }
        var s = 0
        var k = 1
        while (s < content.length) {
            var e = minOf(content.length, s + 10000)
            if (e < content.length) {
                val nl = content.indexOf('\n', e)
                e = if (nl in 0..(e + 3000)) nl else e
            }
            out += Chapter("第 $k 节", s, e)
            k++
            s = e + 1
        }
        return out
    }

    /** 从文件名和开头几行猜书名、作者 */
    fun guessTitle(fileName: String, text: String): Pair<String, String> {
        val base = fileName.substringBeforeLast('.').trim()
        val title = Regex("《(.+?)》").find(base)?.groupValues?.get(1)
            ?: base.substringBefore("作者").trim().trimEnd('_', '-', ' ', '(', '（').ifEmpty { base }
        var author = Regex("作者[:：\\s]*([^\\s_\\-()（）.]+)").find(base)?.groupValues?.get(1).orEmpty()
        if (author.isEmpty()) {
            val head = text.take(2000)
            author = Regex("作者[:：]\\s*([^\\s\\n]{1,20})").find(head)?.groupValues?.get(1).orEmpty()
        }
        return title.ifEmpty { "未命名" } to author
    }
}
