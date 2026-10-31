package com.kongye.reader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

object Library {
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books

    private val saver = Debounced(500) { Store.write(FILE, ListSerializer(Book.serializer()), _books.value) }

    fun load() {
        _books.value = Store.read(FILE, ListSerializer(Book.serializer())) ?: emptyList()
        if (_books.value.any { !it.metaScanned }) Store.io.launch { scanOldMeta() }
    }

    /** 1.2.0 之前导入的书没有简介标签：TXT 开头没认成章节的“开篇”里往往写着，补一次 */
    private fun scanOldMeta() {
        for (b in _books.value.filter { !it.metaScanned }) {
            val c = loadContent(b.id)
            val head = c?.chapters?.firstOrNull()?.takeIf { it.title == "开篇" }?.let { c.text.substring(it.start, it.end) }
            val m = head?.let { TxtParser.meta(it) }
            update(b.id) {
                it.copy(
                    metaScanned = true,
                    author = it.author.ifEmpty { m?.author.orEmpty() },
                    intro = it.intro.ifEmpty { m?.intro.orEmpty() },
                    tags = it.tags.ifEmpty { m?.tags.orEmpty() },
                )
            }
        }
    }

    fun get(id: String): Book? = _books.value.firstOrNull { it.id == id }

    fun dir(id: String) = File(Store.root, "books/$id")

    fun coverFile(id: String) = File(dir(id), "cover")

    fun findByFingerprint(fp: String): Book? = _books.value.firstOrNull { it.fingerprint == fp }

    fun update(id: String, f: (Book) -> Book) {
        _books.update { list -> list.map { if (it.id == id) f(it) else it } }
        saver.poke()
    }

    fun add(book: Book) {
        _books.update { it + book }
        saver.flush()
    }

    fun remove(id: String) {
        _books.update { list -> list.filterNot { it.id == id } }
        saver.flush()
        Store.io.launch { dir(id).deleteRecursively() }
    }

    fun flush() = saver.flush()

    /** 书架长按里改书名、作者、简介、标签 */
    fun updateInfo(id: String, title: String, author: String, intro: String, tags: List<String>) {
        update(id) {
            it.copy(
                title = title.trim().ifEmpty { it.title },
                author = author.trim(),
                intro = intro.split('\n').map { l -> l.trim() }.filter { l -> l.isNotEmpty() }.joinToString("\n"),
                tags = tags.map { t -> t.trim() }.filter { t -> t.isNotEmpty() }.distinct(),
            )
        }
        flush()
    }

    fun loadContent(id: String): BookContent? = runCatching {
        val d = dir(id)
        val text = File(d, "content.txt").readText()
        val toc = Store.json.decodeFromString(ListSerializer(Chapter.serializer()), File(d, "toc.json").readText())
        if (toc.isEmpty()) null else BookContent(text, toc)
    }.getOrNull()

    /** 正文按导入时的规矩整理：去掉每行首尾空白和空行，段落之间一个换行 */
    private fun tidy(s: String) = s.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        .map { line -> line.trim { TxtParser.isSpace(it) } }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    private fun write(id: String, text: String, toc: List<Chapter>) {
        val d = dir(id)
        fun save(name: String, s: String) {
            val f = File(d, name)
            val tmp = File(d, "$name.tmp")
            tmp.writeText(s)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
        save("content.txt", text)
        save("toc.json", Store.json.encodeToString(ListSerializer(Chapter.serializer()), toc))
    }

    /**
     * 改写一章：替换 content.txt 里这一章的正文，后面各章的范围整体平移，书签和进度不越界。
     */
    fun saveChapter(id: String, index: Int, title: String, body: String, cursor: Int = -1): Boolean = runCatching {
        val old = loadContent(id) ?: return false
        val ch = old.chapters[index]
        var clean = tidy(body)
        // 光标在整理后正文里的位置：保存后阅读页回到这里，改了哪儿一眼就能看到
        val at = if (cursor < 0) -1 else tidy(body.substring(0, cursor.coerceIn(0, body.length))).length.coerceAtMost(clean.length)
        // 原来这一章以换行结尾（和下一章隔开），改完也要留着
        if (ch.end > ch.start && old.text[ch.end - 1] == '\n' && clean.isNotEmpty()) clean += "\n"
        val text = old.text.substring(0, ch.start) + clean + old.text.substring(ch.end)
        val delta = clean.length - (ch.end - ch.start)
        val toc = old.chapters.mapIndexed { i, c ->
            when {
                i < index -> c
                i == index -> c.copy(title = title.trim().ifEmpty { c.title }, end = c.end + delta)
                else -> c.copy(start = c.start + delta, end = c.end + delta)
            }
        }
        write(id, text, toc)
        val len = clean.length
        update(id) { b ->
            b.copy(
                chars = text.length.toLong(),
                chapter = if (at >= 0) index else b.chapter,
                offset = if (at >= 0) at else if (b.chapter == index) b.offset.coerceAtMost(len) else b.offset,
                bookmarks = b.bookmarks.map { if (it.chapter == index) it.copy(offset = it.offset.coerceAtMost(len)) else it },
            )
        }
        flush()
        true
    }.getOrDefault(false)

    /**
     * 编辑时把正文里的一段（[paraStart, paraEnd)）提升为新一章的标题：
     * 本章正文到这一段之前为止，这一段后面的正文归新的一章。读到的位置放在新一章开头
     */
    fun splitChapter(id: String, index: Int, title: String, body: String, paraStart: Int, paraEnd: Int): Boolean = runCatching {
        val old = loadContent(id) ?: return false
        val head = body.substring(paraStart, paraEnd).trim { TxtParser.isSpace(it) }
        if (head.isEmpty()) return false
        val before = tidy(body.substring(0, paraStart))
        val after = tidy(body.substring(minOf(paraEnd + 1, body.length)))
        replaceChapters(
            id, old, index, 1,
            listOf(title.trim().ifEmpty { old.chapters[index].title } to before, head to after),
            move = { _, off ->
                if (off < paraStart) index to off.coerceAtMost(before.length)
                else index + 1 to (off - paraEnd - 1).coerceIn(0, after.length)
            },
            at = index + 1 to 0,
        )
    }.getOrDefault(false)

    /**
     * 把认错的标题变回正文：标题和本章正文接到上一章末尾，本章从目录里去掉。
     * 第一章前面没有章可接，就放回本章开头，标题改叫“开篇”。读到的位置放在这段原来的标题上
     */
    fun demoteTitle(id: String, index: Int, title: String, body: String): Boolean = runCatching {
        val old = loadContent(id) ?: return false
        val t = title.trim { TxtParser.isSpace(it) }
        val b = tidy(body)
        val prev = if (index == 0) "" else old.body(index - 1).trimEnd('\n')
        val merged = listOf(prev, t, b).filter { it.isNotEmpty() }.joinToString("\n")
        val base = if (prev.isEmpty()) 0 else prev.length + 1
        val shift = base + (if (t.isEmpty()) 0 else t.length + 1)
        if (index == 0) {
            replaceChapters(id, old, 0, 1, listOf("开篇" to merged), move = { _, off -> 0 to (off + shift).coerceAtMost(merged.length) }, at = 0 to 0)
        } else {
            replaceChapters(
                id, old, index - 1, 2, listOf(old.chapters[index - 1].title to merged),
                move = { ch, off -> if (ch == index - 1) ch to off else index - 1 to (off + shift).coerceAtMost(merged.length) },
                at = index - 1 to base.coerceAtMost(merged.length),
            )
        }
    }.getOrDefault(false)

    /** TXT 按新的规则重新分章，返回新的章数，失败返回 -1。正文只是标题进出，位置按全文比例换算 */
    fun redetect(id: String): Int = runCatching {
        val old = loadContent(id) ?: return -1
        val p = TxtParser.reparse(old)
        val now = BookContent(p.content, p.chapters)
        val ratio = if (old.text.isEmpty()) 1.0 else p.content.length.toDouble() / old.text.length
        fun map(ch: Int, off: Int): Pair<Int, Int> {
            val g = ((old.chapters[ch.coerceIn(0, old.last)].start + off) * ratio).toInt().coerceIn(0, p.content.length)
            val k = p.chapters.indexOfLast { it.start <= g }.coerceAtLeast(0)
            val c = p.chapters[k]
            return k to (g - c.start).coerceIn(0, c.end - c.start)
        }
        write(id, p.content, p.chapters)
        update(id) { b ->
            val (ch, off) = map(b.chapter, b.offset)
            b.copy(
                chars = p.content.length.toLong(), chapterCount = p.chapters.size,
                chapter = ch, offset = off, progress = now.progressOf(ch, off),
                bookmarks = b.bookmarks.map { bm -> map(bm.chapter, bm.offset).let { (c, o) -> bm.copy(chapter = c, offset = o) } },
            )
        }
        flush()
        p.chapters.size
    }.getOrDefault(-1)

    /**
     * 把从 from 开始的 count 章换成 parts（标题, 正文），后面各章整体平移。
     * 阅读位置和书签：落在被换掉的章里的交给 move 换算，之后的章号跟着增减；at 给了就用它当新的阅读位置
     */
    private fun replaceChapters(
        id: String,
        old: BookContent,
        from: Int,
        count: Int,
        parts: List<Pair<String, String>>,
        move: (Int, Int) -> Pair<Int, Int>,
        at: Pair<Int, Int>?,
    ): Boolean {
        val first = old.chapters[from]
        val last = old.chapters[from + count - 1]
        val sb = StringBuilder(old.text.length + 64)
        sb.append(old.text, 0, first.start)
        val toc = ArrayList<Chapter>(old.chapters.size - count + parts.size)
        toc += old.chapters.subList(0, from)
        parts.forEachIndexed { k, (t, b) ->
            if (k > 0) sb.append('\n')
            val s = sb.length
            sb.append(b)
            toc += Chapter(t, s, sb.length)
        }
        val delta = sb.length - last.end
        sb.append(old.text, last.end, old.text.length)
        for (c in old.chapters.subList(from + count, old.chapters.size)) toc += c.copy(start = c.start + delta, end = c.end + delta)
        val text = sb.toString()
        write(id, text, toc)
        val shift = parts.size - count
        fun map(ch: Int, off: Int): Pair<Int, Int> = when {
            ch < from -> ch to off
            ch >= from + count -> ch + shift to off
            else -> move(ch, off)
        }
        val now = BookContent(text, toc)
        update(id) { b ->
            val (ch, off) = at ?: map(b.chapter, b.offset)
            b.copy(
                chars = text.length.toLong(), chapterCount = toc.size,
                chapter = ch, offset = off, progress = now.progressOf(ch, off),
                bookmarks = b.bookmarks.map { bm -> map(bm.chapter, bm.offset).let { (c, o) -> bm.copy(chapter = c, offset = o) } },
            )
        }
        flush()
        return true
    }

    private const val FILE = "library.json"
}

/** 打开一本书时载入内存的正文：全文一个字符串 + 章节范围 */
class BookContent(val text: String, val chapters: List<Chapter>) {
    val last get() = chapters.lastIndex

    fun body(i: Int): String = chapters[i].let { text.substring(it.start, it.end) }

    fun progressOf(chapter: Int, offset: Int): Float {
        if (text.isEmpty()) return 0f
        val c = chapters[chapter.coerceIn(0, last)]
        return ((c.start + offset).toFloat() / text.length).coerceIn(0f, 1f)
    }
}
