package com.kongye.reader.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kongye.reader.KongyeReaderApp
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

class ImportError(message: String) : Exception(message)

object Importer {
    /** 正在导入时的提示文字；null 表示空闲 */
    var busy by mutableStateOf<String?>(null)
        private set

    /** 导入失败的原因，显示“完蛋”页 */
    var failure by mutableStateOf<String?>(null)

    private val unsupported = setOf("pdf", "mobi", "azw", "azw3", "doc", "docx", "fb2", "djvu", "cbz", "cbr", "umd", "chm", "rar", "7z")

    fun start(uris: List<Uri>, openAfter: Boolean) {
        if (uris.isEmpty()) return
        if (busy != null) {
            Toast.show("正在导入，请稍候")
            return
        }
        busy = "正在导入…"
        Store.io.launch {
            var lastId: String? = null
            var added = 0
            var existed = 0
            val errors = ArrayList<String>()
            uris.forEachIndexed { i, uri ->
                if (uris.size > 1) withContext(Dispatchers.Main) { busy = "正在导入 ${i + 1}/${uris.size}" }
                try {
                    val (book, dup) = importOne(KongyeReaderApp.app, uri)
                    lastId = book.id
                    if (dup) existed++ else added++
                } catch (e: ImportError) {
                    errors += e.message.orEmpty()
                } catch (e: OutOfMemoryError) {
                    errors += "文件太大，手机内存不够"
                } catch (e: Throwable) {
                    errors += "文件读取失败"
                }
            }
            withContext(Dispatchers.Main) {
                busy = null
                val id = lastId
                when {
                    id == null -> failure = errors.firstOrNull() ?: "文件读取失败"
                    openAfter && uris.size == 1 -> Nav.openBook(id)
                    uris.size == 1 && existed == 1 -> Toast.show("这本书已经在书架上了")
                    uris.size == 1 -> Toast.show("已放上书架")
                    errors.isEmpty() -> Toast.show("已导入 ${added + existed} 本")
                    else -> Toast.show("导入 ${added + existed} 本，${errors.size} 本失败")
                }
            }
        }
    }

    /** 分享过来的一段文字也当成一本书 */
    fun startText(text: String, subject: String) {
        val title = subject.ifBlank { text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(20) ?: "分享的文字" }
        val f = File(KongyeReaderApp.app.cacheDir, "share-${System.nanoTime()}")
        f.writeText(text)
        sharedNames[f.absolutePath] = "$title.txt"
        start(listOf(Uri.fromFile(f)), openAfter = true)
    }

    private val sharedNames = HashMap<String, String>()

    private fun importOne(ctx: Context, uri: Uri): Pair<Book, Boolean> {
        val cr = ctx.contentResolver
        val name = displayName(cr, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in unsupported) throw ImportError("暂不支持 ${ext.uppercase()} 格式")
        val tmp = File(ctx.cacheDir, "import-${System.nanoTime()}")
        val md = MessageDigest.getInstance("SHA-1")
        try {
            val input = cr.openInputStream(uri) ?: throw ImportError("打不开这个文件")
            input.use { DigestInputStream(it, md).use { d -> tmp.outputStream().use { o -> d.copyTo(o, 64 * 1024) } } }
            if (uri.scheme == "file" && uri.path?.startsWith(ctx.cacheDir.absolutePath) == true) File(uri.path!!).delete()
            val fp = md.digest().joinToString("") { "%02x".format(it) }
            Library.findByFingerprint(fp)?.let { return it to true }
            if (tmp.length() == 0L) throw ImportError("这是一个空文件")
            val head = ByteArray(4)
            tmp.inputStream().use { it.read(head) }
            val zip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
            val id = newId()
            val dir = Library.dir(id).apply { mkdirs() }
            try {
                val book = if (zip) importEpub(tmp, dir, id, name, fp) else importTxt(tmp, dir, id, name, fp)
                Library.add(book)
                return book to false
            } catch (e: Throwable) {
                dir.deleteRecursively()
                throw e
            }
        } finally {
            tmp.delete()
        }
    }

    private fun importTxt(file: File, dir: File, id: String, name: String, fp: String): Book {
        if (file.length() > 80L * 1024 * 1024) throw ImportError("文件太大了（超过 80MB）")
        val bytes = file.readBytes()
        if (TextDecode.looksBinary(bytes)) throw ImportError("这不是文本文件")
        val text = TextDecode.decode(bytes)
        val parsed = TxtParser.parse(text)
        if (parsed.content.isBlank()) throw ImportError("文件里没有文字")
        val (title, author) = TxtParser.guessTitle(name, text)
        writeContent(dir, parsed.content, parsed.chapters)
        return Book(
            id = id, title = title, author = author, format = "txt",
            chars = parsed.content.length.toLong(), chapterCount = parsed.chapters.size,
            addedAt = System.currentTimeMillis(), fingerprint = fp,
        )
    }

    private fun importEpub(file: File, dir: File, id: String, name: String, fp: String): Book {
        val ep = try {
            Epub.parse(file)
        } catch (e: ImportError) {
            throw e
        } catch (e: Throwable) {
            throw ImportError("EPUB 已损坏或格式不对")
        }
        if (ep.chapters.isEmpty() || ep.chapters.all { it.second.isEmpty() }) throw ImportError("这本 EPUB 里没有文字")
        val sb = StringBuilder()
        val toc = ArrayList<Chapter>()
        for ((t, body) in ep.chapters) {
            if (sb.isNotEmpty()) sb.append('\n')
            val s = sb.length
            sb.append(body)
            toc += Chapter(t, s, sb.length)
        }
        writeContent(dir, sb.toString(), toc)
        var hasCover = false
        ep.cover?.let { bytes ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth > 0) {
                Library.coverFile(id).writeBytes(bytes)
                hasCover = true
            }
        }
        val fallback = name.substringBeforeLast('.').trim()
        return Book(
            id = id, title = ep.title.ifBlank { fallback }.ifBlank { "未命名" }, author = ep.author, format = "epub",
            chars = sb.length.toLong(), chapterCount = toc.size,
            addedAt = System.currentTimeMillis(), hasCover = hasCover, fingerprint = fp,
        )
    }

    private fun writeContent(dir: File, content: String, toc: List<Chapter>) {
        File(dir, "content.txt").writeText(content)
        File(dir, "toc.json").writeText(Store.json.encodeToString(ListSerializer(Chapter.serializer()), toc))
    }

    private fun displayName(cr: ContentResolver, uri: Uri): String {
        if (uri.scheme == "file") return sharedNames.remove(uri.path.orEmpty()) ?: uri.lastPathSegment.orEmpty().ifEmpty { "未命名.txt" }
        val n = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return n ?: Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/').ifEmpty { "未命名.txt" }
    }

    private fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + (1000..9999).random()
}
