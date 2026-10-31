package com.kongye.reader.data

import android.net.Uri
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile

/** 只取文字的 EPUB 解析：按书脊顺序读 XHTML，用目录切章，顺带取出封面图 */
object Epub {
    class Parsed(
        val title: String,
        val author: String,
        val chapters: List<Pair<String, String>>,
        val cover: ByteArray?,
        val intro: String = "",
        val tags: List<String> = emptyList(),
    )

    private class Item(val id: String, val href: String, val type: String, val props: String)

    fun parse(file: File): Parsed = ZipFile(file).use { zip ->
        fun read(path: String): ByteArray? {
            val e = zip.getEntry(path) ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, ignoreCase = true) }
            return e?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
        }
        fun text(path: String): String? = read(path)?.let { decodeXml(it) }

        val container = text("META-INF/container.xml") ?: throw ImportError("EPUB 缺少 container.xml")
        val opfPath = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(container)?.groupValues?.get(1)
            ?: throw ImportError("EPUB 结构不完整")
        val opf = text(opfPath) ?: throw ImportError("EPUB 结构不完整")
        val opfDir = opfPath.substringBeforeLast('/', "")

        val title = Html.decodeText(tagText(opf, "title"))
        val author = Html.decodeText(tagText(opf, "creator"))
        val intro = introOf(opf)
        val tags = Regex("<(?:\\w+:)?subject\\b[^>]*>(.*?)</(?:\\w+:)?subject>", RegexOption.DOT_MATCHES_ALL).findAll(opf)
            .flatMap { Html.decodeText(it.groupValues[1].replace(Regex("<[^>]+>"), "")).split(Regex("[,，、;；/|]+")) }
            .map { it.trim() }.filter { it.length in 1..12 }.distinct().take(12).toList()

        val items = Regex("<(?:\\w+:)?item\\b[^>]*>").findAll(opf).mapNotNull { m ->
            val tag = m.value
            val id = attr(tag, "id") ?: return@mapNotNull null
            val href = attr(tag, "href") ?: return@mapNotNull null
            Item(id, href, attr(tag, "media-type").orEmpty(), attr(tag, "properties").orEmpty())
        }.toList()
        val byId = items.associateBy { it.id }
        val spine = Regex("<(?:\\w+:)?itemref\\b[^>]*>").findAll(opf).mapNotNull { attr(it.value, "idref")?.let(byId::get) }.toList()

        // 目录：EPUB3 的 nav 优先，没有再看 EPUB2 的 ncx。结果按文件分组：文件 → [(锚点, 标题)]
        val toc = LinkedHashMap<String, MutableList<Pair<String?, String>>>()
        fun addToc(baseDir: String, href: String, label: String) {
            val l = label.replace(Regex("\\s+"), " ").trim()
            if (l.isEmpty()) return
            val path = resolve(baseDir, href)
            val anchor = href.substringAfter('#', "").ifEmpty { null }
            toc.getOrPut(path) { ArrayList() } += anchor to l
        }
        val nav = items.firstOrNull { "nav" in it.props.split(' ') }
        val navText = nav?.let { text(resolve(opfDir, it.href)) }
        if (navText != null) {
            val navDir = resolve(opfDir, nav.href).substringBeforeLast('/', "")
            val block = Regex("<nav\\b[^>]*toc[^>]*>(.*?)</nav>", RegexOption.DOT_MATCHES_ALL).find(navText)?.groupValues?.get(1) ?: navText
            Regex("<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(block).forEach {
                addToc(navDir, it.groupValues[1], Html.decodeText(it.groupValues[2].replace(Regex("<[^>]+>"), "")))
            }
        }
        if (toc.isEmpty()) {
            val ncx = items.firstOrNull { it.type == "application/x-dtbncx+xml" }
            val ncxText = ncx?.let { text(resolve(opfDir, it.href)) }
            if (ncxText != null) {
                val ncxDir = resolve(opfDir, ncx.href).substringBeforeLast('/', "")
                Regex("<navLabel>\\s*<text>(.*?)</text>\\s*</navLabel>\\s*<content[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.DOT_MATCHES_ALL)
                    .findAll(ncxText).forEach { addToc(ncxDir, it.groupValues[2], Html.decodeText(it.groupValues[1])) }
            }
        }

        val chapters = ArrayList<Pair<String, StringBuilder>>()
        fun appendTo(last: StringBuilder, t: String) {
            if (t.isEmpty()) return
            if (last.isNotEmpty()) last.append('\n')
            last.append(t)
        }
        for (item in spine) {
            if (!item.type.contains("html") && !item.href.endsWith("html", ignoreCase = true) && !item.href.endsWith("htm", ignoreCase = true)) continue
            val path = resolve(opfDir, item.href)
            val html = text(path) ?: continue
            val r = Html.extract(html)
            val entries = toc[path].orEmpty()
            if (entries.isEmpty()) {
                if (r.text.isBlank()) continue
                if (chapters.isEmpty()) {
                    val first = r.text.substringBefore('\n')
                    chapters += (if (first.length <= 24) first else "开篇") to StringBuilder(r.text)
                } else appendTo(chapters.last().second, r.text)
                continue
            }
            // 同一个文件里有多个目录项时按锚点切开
            val points = entries.map { (anchor, label) -> (anchor?.let { r.anchors[it] } ?: 0) to label }
                .distinctBy { it.first }.sortedBy { it.first }
            val pre = r.text.substring(0, points.first().first).trim('\n')
            if (pre.isNotBlank()) {
                if (chapters.isEmpty()) chapters += "开篇" to StringBuilder(pre) else appendTo(chapters.last().second, pre)
            }
            points.forEachIndexed { k, (pos, label) ->
                val end = if (k + 1 < points.size) points[k + 1].first else r.text.length
                chapters += label to StringBuilder(r.text.substring(pos, maxOf(pos, end)).trim('\n'))
            }
        }

        val result = chapters.map { (t, body) -> t to stripTitle(t, body.toString()) }
            // 只有图的章节（插画页）去掉；只有标题的分卷页保留
            .filterIndexed { idx, (t, b) -> b.isNotEmpty() || (idx + 1 < chapters.size && t.isNotEmpty()) }

        // 封面：EPUB3 cover-image → EPUB2 <meta name="cover"> → 名字里带 cover 的图片
        val coverId = Regex("<meta\\b[^>]*>").findAll(opf).map { it.value }
            .firstOrNull { attr(it, "name") == "cover" }?.let { attr(it, "content") }
        val coverItem = items.firstOrNull { "cover-image" in it.props.split(' ') }
            ?: coverId?.let(byId::get)?.takeIf { it.type.startsWith("image/") }
            ?: items.firstOrNull { it.type.startsWith("image/") && (it.id.contains("cover", true) || it.href.contains("cover", true)) }
        val cover = coverItem?.let { read(resolve(opfDir, it.href)) }

        Parsed(title, author, result, cover, intro, tags)
    }

    /** 正文第一段如果就是章节标题，去掉，阅读页会单独排版标题 */
    private fun stripTitle(title: String, body: String): String {
        val first = body.substringBefore('\n')
        val a = first.replace(Regex("\\s"), "")
        val b = title.replace(Regex("\\s"), "")
        return if (a.isNotEmpty() && a.length <= 40 && (a == b || b.contains(a) || a.contains(b))) body.substringAfter('\n', "") else body
    }

    /** dc:description 常常是转义过的 HTML：解开、按段落换行、去掉标签 */
    private fun introOf(opf: String): String {
        val raw = Regex("<(?:\\w+:)?description\\b[^>]*>(.*?)</(?:\\w+:)?description>", RegexOption.DOT_MATCHES_ALL).find(opf)?.groupValues?.get(1)
            ?: return ""
        val html = Html.decodeText(raw.replace(Regex("<!\\[CDATA\\[|]]>"), ""))
        return Html.decodeText(
            html.replace(Regex("(?i)<br\\s*/?>|</p>|</div>"), "\n").replace(Regex("<[^>]+>"), ""),
        ).split('\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").take(2000)
    }

    private fun tagText(xml: String, local: String): String =
        Regex("<(?:\\w+:)?$local\\b[^>]*>(.*?)</(?:\\w+:)?$local>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()

    private fun attr(tag: String, name: String): String? =
        Regex("(?:^|\\s)$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.DOT_MATCHES_ALL).find(tag)?.groupValues?.get(2)

    private fun decodeXml(b: ByteArray): String {
        val head = String(b, 0, minOf(b.size, 200), Charsets.ISO_8859_1)
        val enc = Regex("encoding\\s*=\\s*[\"']([\\w-]+)[\"']").find(head)?.groupValues?.get(1)?.lowercase()
        val cs = when {
            enc == null || enc.startsWith("utf-8") -> Charsets.UTF_8
            enc.startsWith("gb") -> Charset.forName("GB18030")
            else -> runCatching { Charset.forName(enc) }.getOrDefault(Charsets.UTF_8)
        }
        var s = String(b, cs)
        if (s.isNotEmpty() && s[0] == '\uFEFF') s = s.substring(1)
        return s
    }

    /** 把相对路径（可能带 ../ 和 %20）解析成压缩包里的完整路径 */
    fun resolve(baseDir: String, href: String): String {
        val clean = Uri.decode(href.substringBefore('#'))
        val parts = (if (baseDir.isEmpty() || clean.startsWith("/")) clean else "$baseDir/$clean").split('/')
        val stack = ArrayList<String>()
        for (p in parts) when (p) {
            "", "." -> {}
            ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            else -> stack += p
        }
        return stack.joinToString("/")
    }
}

/** 极简 HTML → 纯文本：块级标签换段，记录 id 锚点落在哪一段 */
object Html {
    class Result(val text: String, val anchors: Map<String, Int>)

    private val blocks = setOf(
        "p", "div", "br", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr", "section", "article", "blockquote",
        "pre", "hr", "dt", "dd", "figure", "figcaption", "table", "ul", "ol", "header", "footer", "aside", "nav", "body",
    )
    private val idAttr = Regex("\\s(?:id|name)\\s*=\\s*[\"']([^\"']+)[\"']")

    fun extract(html: String): Result {
        val out = StringBuilder()
        val para = StringBuilder()
        val anchors = HashMap<String, Int>()
        val pending = ArrayList<String>()
        fun flush() {
            var s = 0
            var e = para.length
            while (s < e && TxtParser.isSpace(para[s])) s++
            while (e > s && TxtParser.isSpace(para[e - 1])) e--
            if (s < e) {
                if (out.isNotEmpty()) out.append('\n')
                for (a in pending) anchors[a] = out.length
                pending.clear()
                out.append(para, s, e)
            }
            para.setLength(0)
        }
        val n = html.length
        var i = html.indexOf("<body", ignoreCase = true).let { if (it < 0) 0 else it }
        while (i < n) {
            val c = html[i]
            when {
                c == '<' -> {
                    if (html.startsWith("<!--", i)) {
                        val e = html.indexOf("-->", i + 4)
                        i = if (e < 0) n else e + 3
                        continue
                    }
                    val e = html.indexOf('>', i)
                    if (e < 0) break
                    val tag = html.substring(i + 1, e)
                    val closing = tag.startsWith("/")
                    val name = tag.removePrefix("/").takeWhile { !it.isWhitespace() && it != '/' }.substringAfter(':').lowercase()
                    if (!closing && (name == "script" || name == "style" || name == "head" || name == "title")) {
                        val end = html.indexOf("</$name", e, ignoreCase = true)
                        i = if (end < 0) n else html.indexOf('>', end).let { if (it < 0) n else it + 1 }
                        continue
                    }
                    if (name in blocks) flush()
                    if (!closing) idAttr.find(tag)?.let { pending += it.groupValues[1] }
                    i = e + 1
                }
                c == '&' -> {
                    val semi = html.indexOf(';', i)
                    if (semi > i && semi - i <= 10) {
                        val d = entity(html.substring(i + 1, semi))
                        if (d != null) {
                            para.append(d)
                            i = semi + 1
                            continue
                        }
                    }
                    para.append('&')
                    i++
                }
                c.isWhitespace() -> {
                    // 中文之间的换行不该变成空格
                    val prev = if (para.isEmpty()) ' ' else para[para.length - 1]
                    if (!prev.isWhitespace() && prev.code < 0x2E80) para.append(' ')
                    i++
                }
                else -> {
                    para.append(c)
                    i++
                }
            }
        }
        flush()
        for (a in pending) anchors[a] = out.length
        return Result(out.toString(), anchors)
    }

    fun decodeText(s: String): String {
        if ('&' !in s) return s.trim()
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i)
                if (semi > i && semi - i <= 10) {
                    val d = entity(s.substring(i + 1, semi))
                    if (d != null) {
                        sb.append(d)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString().trim()
    }

    private fun entity(e: String): String? {
        if (e.startsWith("#x") || e.startsWith("#X")) return e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        if (e.startsWith("#")) return e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        return when (e) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            "nbsp" -> "\u00A0"; "ensp" -> " "; "emsp" -> "\u3000"; "thinsp" -> " "
            "mdash" -> "—"; "ndash" -> "–"; "hellip" -> "…"; "middot" -> "·"
            "ldquo" -> "“"; "rdquo" -> "”"; "lsquo" -> "‘"; "rsquo" -> "’"
            "laquo" -> "«"; "raquo" -> "»"; "copy" -> "©"; "reg" -> "®"; "times" -> "×"
            else -> null
        }
    }
}
