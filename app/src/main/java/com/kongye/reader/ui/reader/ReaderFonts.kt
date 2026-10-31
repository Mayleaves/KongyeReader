package com.kongye.reader.ui.reader

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.kongye.reader.KongyeReaderApp
import java.io.File

/**
 * 正文字体。两款楷体打包在 assets/fonts 里（只留了常用汉字、拉丁字母和标点），
 * 第一次用时解到应用目录，之后直接从文件读，不必每次把十几 MB 读进内存。
 */
object ReaderFonts {
    val names = listOf("系统默认", "霞鹜文楷", "仓耳今楷")
    private val assets = arrayOf(null, "wenkai.ttf", "jinkai.ttf")
    private const val VERSION = 1
    private val families = arrayOfNulls<FontFamily>(assets.size)

    fun ready(i: Int): Boolean = i == 0 || families.getOrNull(i) != null

    /** 会读文件，第一次调用放在后台线程 */
    @Synchronized
    fun family(i: Int): FontFamily {
        val k = i.coerceIn(0, assets.lastIndex)
        families[k]?.let { return it }
        val name = assets[k] ?: return FontFamily.Default
        // 字体文件不在仓库里（见 README），没放进 assets 就用系统字体
        val file = runCatching { extract(name) }.getOrNull()
        return (if (file == null) FontFamily.Default else FontFamily(Font(file))).also { families[k] = it }
    }

    private fun extract(name: String): File {
        val app = KongyeReaderApp.app
        val dir = File(app.filesDir, "fonts").apply { mkdirs() }
        val out = File(dir, "v$VERSION-$name")
        if (out.length() > 0) return out
        dir.listFiles()?.filter { it.name.endsWith(name) }?.forEach { it.delete() }
        val tmp = File(dir, "$name.tmp")
        app.assets.open("fonts/$name").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
        if (!tmp.renameTo(out)) error("rename failed")
        return out
    }
}
