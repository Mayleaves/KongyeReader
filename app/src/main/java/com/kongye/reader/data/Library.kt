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

    fun loadContent(id: String): BookContent? = runCatching {
        val d = dir(id)
        val text = File(d, "content.txt").readText()
        val toc = Store.json.decodeFromString(ListSerializer(Chapter.serializer()), File(d, "toc.json").readText())
        if (toc.isEmpty()) null else BookContent(text, toc)
    }.getOrNull()

    /**
     * 改写一章：替换 content.txt 里这一章的正文，后面各章的范围整体平移，书签和进度不越界。
     * 正文按导入时的规矩整理：去掉每行首尾空白和空行，段落之间一个换行
     */
    fun saveChapter(id: String, index: Int, title: String, body: String, cursor: Int = -1): Boolean = runCatching {
        val d = dir(id)
        val old = loadContent(id) ?: return false
        val ch = old.chapters[index]
        fun tidy(s: String) = s.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            .map { line -> line.trim { TxtParser.isSpace(it) } }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
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
