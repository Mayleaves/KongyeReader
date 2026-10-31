package com.kongye.reader.data

import android.database.sqlite.SQLiteDatabase
import com.kongye.reader.KongyeReaderApp
import java.io.File
import kotlin.math.ln

/**
 * 长按查词：离线词典 assets/dict.db（由 tools/build_dict.py 生成）+ 简单的最大概率分词。
 *
 * 自动选词的优先级：成语 / 诗句 > 词语、人名、地名 > 单字。
 * 先找覆盖按下位置的成语和诗句；没有就在附近一小段里按词频切词，取按下位置所在的那个词。
 */
object Dict {
    enum class Kind(val label: String) {
        CHAR("单字"), WORD("词语"), IDIOM("成语"), POEM("诗句"), PERSON("人名"), PLACE("地名"),
        ORG("机构"), PROPER("专名"), NAME("书中人物"), EN("英文"),
    }

    class Reading(val pinyin: String, val meaning: String)

    class Entry(
        val word: String,
        val kind: Kind,
        val pinyin: String = "",
        val meaning: String = "",
        /** 单字的各个读音 */
        val readings: List<Reading> = emptyList(),
        /** 诗句、成语的出处；英文的词形说明（went → go 的过去式） */
        val source: String = "",
        /** 诗句所在的整句 */
        val extra: String = "",
        val phonetic: String = "",
        /** 词典里没有释义的词，拆成单字给读音和字义 */
        val parts: List<Entry> = emptyList(),
        val found: Boolean = true,
    )

    class Hit(val start: Int, val end: Int, val entry: Entry?)

    private const val VERSION = 3
    private const val MAX = 12
    private const val K_IDIOM = 1
    private const val K_POEM = 2
    private const val K_NAME = 9
    private val LABELS = setOf("人名", "地名", "机构", "专名")

    @Volatile
    private var db: SQLiteDatabase? = null
    private var logTotal = ln(6.0e7)

    /** 应用启动时在后台调用：把词典从安装包解到应用目录 */
    fun prepare() {
        runCatching { open() }
    }

    @Synchronized
    private fun open(): SQLiteDatabase? {
        db?.let { return it }
        return runCatching {
            val app = KongyeReaderApp.app
            val dir = File(app.filesDir, "dict").apply { mkdirs() }
            val f = File(dir, "dict-$VERSION.db")
            if (!f.exists()) {
                dir.listFiles()?.forEach { it.delete() }
                val tmp = File(dir, "dict.tmp")
                app.assets.open("dict.db").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                if (!tmp.renameTo(f)) error("rename failed")
            }
            val d = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            d.rawQuery("SELECT v FROM meta WHERE k = 'total'", null).use { if (it.moveToFirst()) logTotal = ln(it.getString(0).toDouble()) }
            db = d
            d
        }.getOrNull()
    }

    fun isHan(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'

    private fun isLatin(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    private fun isWordChar(c: Char) = isLatin(c) || c == '\'' || c == '-' || c == '’'

    // 常见单姓。书里反复出现、以它们开头又不在词典里的两三个字，当作书中人物
    private const val SURNAMES = "王李张刘陈杨黄赵吴周徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾萧田董袁潘蒋蔡余杜叶程苏魏吕丁任沈姚卢姜崔钟谭陆汪范" +
        "金石廖贾夏韦付邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵钱严覃武戴莫孔汤温康施牛樊葛邢齐乔伍庞颜倪庄聂章鲁岳翟殷詹申欧耿兰焦俞" +
        "柳甘祝包宁符舒阮柯纪梅童凌毕裴霍涂苗谷盛翁冉骆蓝游辛靳管柴蒙鲍喻祁蒲滕屈饶牟艾尤穆卓吉简项麦褚娄窦戚岑宫费卜冷晏卫柏宗瞿桂佟臧闵" +
        "邬卞姬栾隋刁巫寇桑郎甄仲虞敖巩佘邝鞠荆冀郁胥储栗燕楚鄢奚粟冼蔺楼厉阚帅屠朴廉禹祖漆狄芮慕"

    // 紧跟在姓后面、多半不是名字的字（“王说”“李的”）
    private const val NOT_GIVEN = "说道是的了在和与对把被从向也都就又还不没有着过去来要会能这那个们吗呢吧啊么一"

    private fun query(d: SQLiteDatabase, sql: String, keys: Collection<String>, row: (android.database.Cursor) -> Unit) {
        if (keys.isEmpty()) return
        keys.chunked(400).forEach { part ->
            val q = sql.replace("?*", part.joinToString(",") { "?" })
            d.rawQuery(q, part.toTypedArray()).use { while (it.moveToNext()) row(it) }
        }
    }

    /** 在 win 里查出所有 2~12 字的词，返回 词 → [词频, 类别] */
    private fun words(d: SQLiteDatabase, win: String): HashMap<String, IntArray> {
        val cands = LinkedHashSet<String>()
        for (i in win.indices) for (l in 2..minOf(MAX, win.length - i)) cands += win.substring(i, i + l)
        val found = HashMap<String, IntArray>()
        query(d, "SELECT w, f, k FROM ci WHERE w IN (?*)", cands) { found[it.getString(0)] = intArrayOf(it.getInt(1), it.getInt(2)) }
        return found
    }

    private fun charFreq(d: SQLiteDatabase, win: String): HashMap<Char, Int> {
        val m = HashMap<Char, Int>()
        query(d, "SELECT c, f FROM zi WHERE c IN (?*)", win.toSet().map { it.toString() }) { m[it.getString(0)[0]] = it.getInt(1) }
        return m
    }

    /** 最大概率切词，返回每个词的 [起, 止) */
    private fun segment(win: String, found: Map<String, IntArray>, chars: Map<Char, Int>): List<IntArray> {
        val n = win.length
        val best = DoubleArray(n + 1)
        val next = IntArray(n + 1)
        for (i in n - 1 downTo 0) {
            var bv = ln((chars[win[i]] ?: 1).coerceAtLeast(1).toDouble()) - logTotal + best[i + 1]
            var bj = i + 1
            for (j in i + 2..minOf(n, i + MAX)) {
                val f = found[win.substring(i, j)] ?: continue
                if (f[1] == K_POEM) continue
                val v = ln(f[0].coerceAtLeast(1).toDouble()) - logTotal + best[j]
                if (v > bv) {
                    bv = v
                    bj = j
                }
            }
            best[i] = bv
            next[i] = bj
        }
        val out = ArrayList<IntArray>()
        var i = 0
        while (i < n) {
            out += intArrayOf(i, next[i])
            i = next[i]
        }
        return out
    }

    /** 长按 text[pos] 时自动选中的范围和词条；countInBook 数某个词在整本书里出现几次 */
    fun select(text: String, pos: Int, countInBook: (String) -> Int): Hit {
        if (pos !in text.indices) return Hit(pos, pos, null)
        val c = text[pos]
        if (isLatin(c)) {
            var s = pos
            var e = pos + 1
            while (s > 0 && isWordChar(text[s - 1])) s--
            while (e < text.length && isWordChar(text[e])) e++
            while (s < e && !isLatin(text[s])) s++
            while (e > s && !isLatin(text[e - 1])) e--
            if (s >= e) return Hit(pos, pos + 1, null)
            return Hit(s, e, english(text.substring(s, e)))
        }
        if (!isHan(c)) return Hit(pos, pos + 1, null)
        val d = open() ?: return Hit(pos, pos + 1, null)
        var ws = pos
        var we = pos + 1
        while (ws > 0 && isHan(text[ws - 1]) && pos - ws < MAX - 1) ws--
        while (we < text.length && isHan(text[we]) && we - pos < MAX) we++
        val win = text.substring(ws, we)
        val p = pos - ws
        val found = words(d, win)

        // 1. 成语、诗句
        var top: IntArray? = null
        for ((w, f) in found) {
            if (f[1] != K_IDIOM && f[1] != K_POEM) continue
            var i = win.indexOf(w)
            while (i >= 0) {
                if (p in i until i + w.length && (top == null || w.length > top[1] - top[0])) top = intArrayOf(i, i + w.length)
                i = win.indexOf(w, i + 1)
            }
        }
        top?.let { (a, b) -> return Hit(ws + a, ws + b, wordEntry(d, win.substring(a, b))) }

        // 2. 书里反复出现的人名
        val names = HashMap<String, Int>()
        for (l in 3 downTo 2) for (i in maxOf(0, p - l + 1)..p) {
            if (i + l > win.length) continue
            val s = win.substring(i, i + l)
            if (s in found || s[0] !in SURNAMES || s[1] in NOT_GIVEN) continue
            val n = countInBook(s)
            if (n >= (if (l == 3) 3 else 8)) names[s] = n
        }
        // 三字名要比它的前两个字出现得差不多多，才说明后面总跟着同一个字
        names.keys.filter { it.length == 2 }.forEach { two ->
            val three = names.keys.firstOrNull { it.length == 3 && it.startsWith(two) }
            if (three != null && names[three]!! >= names[two]!! * 0.6) names.remove(two)
        }
        names.forEach { (s, n) -> found[s] = intArrayOf((n * 40).coerceAtMost(5000), K_NAME) }

        // 3. 切词，取按下位置所在的词
        val seg = segment(win, found, charFreq(d, win)).first { p >= it[0] && p < it[1] }
        val w = win.substring(seg[0], seg[1])
        if (w.length == 1) return Hit(pos, pos + 1, charEntry(d, c))
        val entry = names[w]?.let { Entry(w, Kind.NAME, pinyinOf(d, w), "书中出现 ${if (it >= 50) "50+" else it} 次") } ?: wordEntry(d, w)
        return Hit(ws + seg[0], ws + seg[1], entry)
    }

    /** “字典”按钮：查手动选中的一段字 */
    fun lookup(raw: String): List<Entry> {
        val t = raw.trim()
        if (t.isEmpty()) return emptyList()
        val d = open() ?: return emptyList()
        if (t.length == 1 && isHan(t[0])) return listOf(charEntry(d, t[0]))
        if (t.length <= MAX && t.all { isHan(it) }) {
            val e = wordEntry(d, t)
            if (e.found) return listOf(e)
        }
        val out = ArrayList<Entry>()
        var i = 0
        while (i < t.length && out.size < 8) {
            val c = t[i]
            when {
                isLatin(c) -> {
                    var j = i
                    while (j < t.length && isWordChar(t[j])) j++
                    english(t.substring(i, j))?.let { out += it }
                    i = j
                }
                isHan(c) -> {
                    var j = i
                    while (j < t.length && isHan(t[j]) && j - i < 40) j++
                    val win = t.substring(i, j)
                    for ((a, b) in segment(win, words(d, win), charFreq(d, win))) {
                        if (out.size >= 8) break
                        out += if (b - a == 1) charEntry(d, win[a]) else wordEntry(d, win.substring(a, b))
                    }
                    i = j
                }
                else -> i++
            }
        }
        return out
    }

    private fun pinyinOf(d: SQLiteDatabase, w: String): String {
        val m = HashMap<Char, String>()
        query(d, "SELECT c, py FROM zi WHERE c IN (?*)", w.toSet().map { it.toString() }) { m[it.getString(0)[0]] = it.getString(1).substringBefore(',') }
        return w.map { m[it] ?: "" }.filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun charEntry(d: SQLiteDatabase, c: Char): Entry {
        d.rawQuery("SELECT py, def FROM zi WHERE c = ?", arrayOf(c.toString())).use { cur ->
            if (!cur.moveToFirst()) return Entry(c.toString(), Kind.CHAR, found = false)
            val py = cur.getString(0).orEmpty()
            val def = if (cur.isNull(1)) "" else cur.getString(1)
            val readings = if (def.isNotEmpty()) def.split('\n').map { Reading(it.substringBefore('\t'), it.substringAfter('\t', "")) }
            else py.split(',').filter { it.isNotBlank() }.map { Reading(it, "") }
            return Entry(c.toString(), Kind.CHAR, py.replace(",", " / "), readings = readings)
        }
    }

    private fun wordEntry(d: SQLiteDatabase, w: String): Entry {
        d.rawQuery("SELECT k, py, def FROM ci WHERE w = ?", arrayOf(w)).use { cur ->
            if (!cur.moveToFirst()) {
                val parts = w.filter { isHan(it) }.take(6).map { charEntry(d, it) }
                return Entry(w, Kind.WORD, pinyinOf(d, w), parts = parts, found = false)
            }
            val k = cur.getInt(0)
            val py = if (cur.isNull(1)) pinyinOf(d, w) else cur.getString(1)
            val def = if (cur.isNull(2)) "" else cur.getString(2)
            return when (k) {
                K_POEM -> Entry(w, Kind.POEM, py, source = def.substringBefore('\n'), extra = def.substringAfter('\n', ""))
                K_IDIOM -> Entry(w, Kind.IDIOM, py, def.substringBefore("\n出自"), source = def.substringAfter("\n出自", "").let { if (it.isEmpty()) "" else "出自$it" })
                // 专名大多只有“人名”“地名”这样的标签；也有带释义的（林黛玉：《红楼梦》人物）
                3, 4, 5, 6 -> {
                    val kind = when (k) {
                        3 -> Kind.PERSON
                        4 -> Kind.PLACE
                        5 -> Kind.ORG
                        else -> Kind.PROPER
                    }
                    Entry(w, kind, py, if (def == kind.label || def in LABELS) "" else def)
                }
                else -> if (def.isEmpty()) Entry(w, Kind.WORD, py, parts = w.take(6).map { charEntry(d, it) }) else Entry(w, Kind.WORD, py, def)
            }
        }
    }

    private class EnRow(val word: String, val ph: String, val def: String, val lemma: String?, val note: String)

    private fun enRow(d: SQLiteDatabase, key: String): EnRow? =
        d.rawQuery("SELECT word, ph, def, lemma, note FROM en WHERE w = ?", arrayOf(key)).use { c ->
            if (!c.moveToFirst()) null
            else EnRow(c.getString(0) ?: key, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3), c.getString(4).orEmpty())
        }

    fun english(w: String): Entry? {
        val d = open() ?: return null
        val key = w.lowercase().replace('’', '\'')
        val tries = buildList {
            add(key)
            add(key.removeSuffix("'s"))
            if (key.endsWith("ies")) add(key.dropLast(3) + "y")
            if (key.endsWith("es")) add(key.dropLast(2))
            if (key.endsWith("s")) add(key.dropLast(1))
            if (key.endsWith("ed")) { add(key.dropLast(2)); add(key.dropLast(1)) }
            if (key.endsWith("ing")) { add(key.dropLast(3)); add(key.dropLast(3) + "e") }
            if (key.endsWith("ly")) add(key.dropLast(2))
        }
        val first = tries.distinct().firstNotNullOfOrNull { enRow(d, it) } ?: return Entry(w, Kind.EN, found = false)
        var r = first
        var note = ""
        val lemma = first.lemma
        if (lemma != null) {
            note = "$lemma ${first.note}"
            enRow(d, lemma.lowercase())?.let { r = it }
        }
        return Entry(w, Kind.EN, meaning = r.def, phonetic = r.ph, source = note)
    }
}
