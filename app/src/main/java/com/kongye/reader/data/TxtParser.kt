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

    private val heading = Regex(
        "^(第[0-9０-９零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,9}[章节回卷集部篇幕].{0,30}" +
            "|(序章|序言|序|楔子|引子|引言|前言|后记|尾声|终章|番外|外传|完本感言)([\\s　：:·、\\-—].{0,30})?" +
            "|(Chapter|CHAPTER)\\s*[0-9IVXLC]+.{0,30}" +
            "|卷[0-9零〇一二三四五六七八九十百]{1,5}.{0,30})$",
    )
    private val ruler = Regex("^[=\\-*_~—·.。]{3,}$")

    fun isSpace(c: Char) = c.isWhitespace() || c == '　' || c == ' ' || c == '﻿' || c == '​'

    fun parse(text: String): Parsed {
        val sb = StringBuilder(text.length)
        // 每个标题记下“下一段会从哪里开始”
        val marks = ArrayList<Pair<String, Int>>()
        marks += "开篇" to 0
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
            if (s == t) continue
            val line = text.substring(s, t)
            if (line.length <= 40 && heading.matches(line)) {
                marks += line.replace(Regex("[\\s　]+"), " ") to (if (sb.isEmpty()) 0 else sb.length + 1)
                continue
            }
            if (ruler.matches(line)) continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
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
