package com.kongye.reader

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.kongye.reader.data.Dict
import com.kongye.reader.data.Importer
import com.kongye.reader.data.Library
import com.kongye.reader.data.Bookmark
import com.kongye.reader.data.Profile
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Settings
import com.kongye.reader.data.Shop
import com.kongye.reader.data.TxtParser
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.badgeStyles
import com.kongye.reader.ui.reader.ReaderMenu
import com.kongye.reader.ui.theme.LightPalette
import com.kongye.reader.ui.theme.LocalPalette
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual dictionary, parser and Compose screens in a separate debug install. */
internal class ReaderUpdateChecks(private val test: Instrumentation) {
    private val output = File(test.targetContext.filesDir, "reader-update-checks").apply { mkdirs() }
    private val log = StringBuilder()
    private lateinit var activity: MainActivity
    private val composeOrigin = IntArray(2)

    fun run() {
        try {
            parser()
            dictionary()
            novelAndMetadata()
            screens()
            File(output, "result.txt").writeText(log.toString())
        } finally {
            File(output, "result.txt").writeText(log.toString())
            if (::activity.isInitialized) test.runOnMainSync { activity.finish() }
        }
    }

    private fun parser() {
        val body = "　　这是一段完整的小说正文，人物正在街道上谈论各自的故事，随后走进了安静的书店。\n".repeat(35)
        val families = listOf(
            listOf("第一章 开始", "第二章 进入城市", "第三章 新旅程"),
            listOf("第1話 はじまり", "第2話 出会い", "第3話 旅立ち"),
            listOf("제1장 시작", "제2장 도시", "제3장 여행"),
            listOf("제일장 시작", "제이장 도시", "제삼장 여행"),
            listOf("Chapter 1: Start", "chapter 2: City", "CHAPTER 3: Journey"),
            listOf("Volume I Spring", "Volume II Summer", "Volume III Autumn"),
            listOf("卷一 春天", "卷二 夏天", "卷三 秋天"),
            listOf("【新的开始】", "【进入城市】", "【新的旅程】"),
            listOf("[新的开始]", "[进入城市]", "[新的旅程]"),
            listOf("<新的开始>", "<进入城市>", "<新的旅程>"),
            listOf("1. 开始", "2、进入城市", "第3：新的旅程"),
            listOf("☆、第一章", "☆、第二章", "☆、第三章"),
            listOf("★、新的开始", "◆：进入城市", "☆、新的旅程"),
        )
        for (titles in families) {
            val text = titles.joinToString("\n\n") { "$it\n\n$body" }
            val parsed = TxtParser.parse(text)
            check(parsed.chapters.map { it.title } == titles) {
                "TXT headings lost: expected=$titles actual=${parsed.chapters.map { it.title }}"
            }
            check(parsed.chapters.all { it.end > it.start && parsed.content.substring(it.start, it.end).contains("小说正文") })
            log.append("TXT: $titles passed\n")
        }
        val prose = "【注意】\n★、不要忘记带钥匙\n<span>这是一段正文</span>\n" + body
        check(TxtParser.parse(prose).chapters.size == 1) { "Isolated decorative prose became chapters" }
        check(TxtParser.number("十二") == 12 && TxtParser.number("십이") == 12 && TxtParser.number("１２") == 12)
        log.append("TXT negative cases and numbering passed\n")
    }

    private fun dictionary() {
        Dict.prepare()
        val book = ("阮阮说了她的想法。吴玫对着阮阮说：“好听人啊。”\n" +
            "吴玫问起阮阮的工作，阮阮回答了问题。王小明来到书店，周恩来走进房间。陈玄道走进房间。\n").repeat(24)
        fun count(word: String): Int = Regex(Regex.escape(word)).findAll(book).count()
        for ((sentence, name) in listOf("对着阮阮说：“好听人啊。”" to "阮阮",
            "阮阮说了她的想法" to "阮阮", "阮阮的工作" to "阮阮", "吴玫问起工作" to "吴玫",
            "王小明来到书店" to "王小明", "周恩来走进房间" to "周恩来", "陈玄道走进房间" to "陈玄道")) {
            val start = sentence.indexOf(name)
            for (index in name.indices) {
                val hit = Dict.select(sentence, start + index, ::count)
                val actual = sentence.substring(hit.start, hit.end)
                check(actual == name) { "Character selection merged a verb: $sentence tap=$index selected=$actual" }
                check(hit.entry != null) { "No dictionary entry returned for $name" }
            }
            log.append("Dictionary: $sentence -> $name passed\n")
        }
    }

    private fun semantics(): List<SemanticsNode> {
        val result = ArrayList<SemanticsNode>()
        test.runOnMainSync {
            fun add(node: SemanticsNode) { result += node; node.children.forEach(::add) }
            fun visit(view: View) {
                val getter = view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                if (getter != null) {
                    view.getLocationInWindow(composeOrigin)
                    add((getter.invoke(view) as SemanticsOwner).rootSemanticsNode)
                }
                else if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(activity.window.decorView)
        }
        check(result.isNotEmpty()) { "Compose semantics tree unavailable" }
        return result
    }

    private fun novelAndMetadata() {
        val novel = File(test.targetContext.filesDir, "shenkan.txt")
        // Optional private fixture supplied on the test device; never bundle a user's novel.
        if (novel.exists()) {
        val text = novel.readText().trimStart('\uFEFF')
        val counts = HashMap<String, Int>()
        fun count(s: String) = counts.getOrPut(s) { Regex(Regex.escape(s)).findAll(text).count() }
        check(count("阮阮") == 2934 && count("阮阮让") == 4)
        val contexts = text.lineSequence().filter { line ->
            listOf("阮阮让", "阮阮说", "阮阮问", "阮阮笑", "阮阮心", "阮阮将").any { it in line }
        }.take(80).toList()
        var tested = 0
        for (line in contexts) {
            for (match in Regex("阮阮").findAll(line)) for (offset in 0..1) {
                val hit = Dict.select(line, match.range.first + offset, ::count)
                check(line.substring(hit.start, hit.end) == "阮阮") {
                    "Actual novel name merged: ${line.substring(hit.start, hit.end)} in $line"
                }
                tested++
            }
        }
        val originalTitle = TxtParser.guessTitle("random.txt", text)
        check(originalTitle == ("神龛" to "七小皇叔")) { "Actual book cover metadata lost: $originalTitle" }
        check(TxtParser.meta(text).intro.contains("施然 x 阮阮"))
        log.append("Actual local 神龛: 2934 names vs 4 merged verbs; $tested selections passed; title, author, intro recognized\n")
        }

        val body = "　　阮阮走进了书店，这是一段完整的小说正文，随后大家谈论今天的见闻。\n".repeat(40)
        val input = File(test.targetContext.filesDir, "recognition-fixture.txt")
        input.writeText("书名：识别测试书\n作者：测试作者\n标签：都市 日常\n简介：这本书有三章。\n\n" +
            (1..3).joinToString("\n\n") { "第${it}章\n\n$body" })
        test.runOnMainSync { Importer.start(listOf(Uri.fromFile(input)), false) }
        val until = SystemClock.uptimeMillis() + 15000
        while (Importer.busy != null && SystemClock.uptimeMillis() < until) SystemClock.sleep(100)
        check(Importer.failure == null && Importer.busy == null) { "Import failed: ${Importer.failure}" }
        val book = Library.books.value.last { it.sourceName == input.name }
        try {
            check(book.title == "识别测试书" && book.author == "测试作者" && book.tags == listOf("都市", "日常") && book.intro == "这本书有三章。")
            check(book.chapterCount == 4) // metadata preface + three chapters
            Library.update(book.id) { it.copy(chapter=2, offset=30, bookmarks=listOf(Bookmark(2, 30, "见闻", 1))) }
            Library.updateInfo(book.id, "手动改名", "手动作者", "手动简介", listOf("手动"))
            val found = Library.recognize(book.id) ?: error("Recognition failed")
            check(found.title==book.title && found.author==book.author && found.intro==book.intro && found.tags==book.tags) { "Rerecognized metadata differs: $found expected=$book" }
            val content = Library.loadContent(book.id)!!
            check(found.chapter in content.chapters.indices && found.offset in 0..content.body(found.chapter).length)
            check(found.bookmarks.size == 1 && found.bookmarks[0].chapter in content.chapters.indices)
            check(content.text.contains(body.trim().substringBefore('\n').trim()))
            log.append("First import and re-recognition: all five fields restored; body, progress and bookmark retained\n")
        } finally { Library.remove(book.id); input.delete() }
    }

    private fun back() {
        test.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        SystemClock.sleep(700)
    }

    private fun shopReturn() {
        test.runOnMainSync { Nav.pop() }
        SystemClock.sleep(700)
        check(Nav.top == Screen.Shop) { "Dress discarded the shop entry" }
        for ((index, tab) in listOf("动物", "背景", "饰品", "挂件", "图标").withIndex()) {
            val scroll = semantics().last { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
            test.runOnMainSync { scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f,-100000f) }
            SystemClock.sleep(350)
            click(tab)
            val grid = semantics().last { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
            test.runOnMainSync { grid.config[SemanticsActions.ScrollBy].action!!.invoke(0f,360f) }
            SystemClock.sleep(450)
            fun position() = semantics().last { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            val before = position()
            val beforeLabels = allLabels().toSet()
            test.runOnMainSync { Nav.push(Screen.Achievements) }
            SystemClock.sleep(700)
            click("夜之城")
            check(allLabels().any { it == "试看 · 夜之城" }) { "Preview did not start" }
            back()
            check(Nav.top==Screen.Shop) { "Back merely reset the badge preview" }
            check(kotlin.math.abs(position()-before) < 0.05f && allLabels().toSet()==beforeLabels) { "Shop category/position lost after achievements: $tab" }
            test.runOnMainSync { Nav.dress(listOf(0,1,2,1,3)[index]) }
            SystemClock.sleep(650)
            back()
            check(Nav.top==Screen.Shop && kotlin.math.abs(position()-before)<0.05f && allLabels().toSet()==beforeLabels) { "Shop category/position lost after dress: $tab" }
            screenshot("return-shop-$index")
            log.append("Shop $tab: achievements preview back and dress back retained category/scroll\n")
        }
    }

    private fun readerLongPress() {
        val input = File(test.targetContext.filesDir, "long-press-fixture.txt")
        input.writeText("　　阮阮和施然走进安静的书店，大家正在谈论最近读过的小说，还有今天路上发生的事情。\n".repeat(80))
        test.runOnMainSync { Importer.start(listOf(Uri.fromFile(input)), false) }
        val until = SystemClock.uptimeMillis()+15000
        while (Importer.busy!=null && SystemClock.uptimeMillis()<until) SystemClock.sleep(100)
        val book = Library.books.value.last { it.sourceName==input.name }
        fun touch(long: Boolean) {
            val x=activity.window.decorView.width*0.43f
            val y=activity.window.decorView.height*0.45f
            val down=SystemClock.uptimeMillis()
            fun event(action: Int) {
                val ev=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
                ev.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                test.runOnMainSync { activity.dispatchTouchEvent(ev) }
                ev.recycle()
            }
            event(MotionEvent.ACTION_DOWN)
            SystemClock.sleep(if(long) 800 else 40)
            event(MotionEvent.ACTION_UP)
            SystemClock.sleep(900)
        }
        try {
            for(mode in listOf(0,2)) {
                test.runOnMainSync { Settings.update { it.copy(turnMode=mode) }; Nav.openBook(book.id) }
                SystemClock.sleep(1400)
                touch(false)
                check("目录" in allLabels() && "样式" in allLabels()) { "Reader menu did not open, mode=$mode" }
                touch(true)
                val labels=allLabels()
                check("复制" in labels && "字典" in labels && "全选" in labels) { "Long press failed to select: mode=$mode labels=$labels" }
                check(labels.any { it in setOf("词语", "单字", "书中人物") }) { "Dictionary card missing: mode=$mode labels=$labels" }
                check("目录" !in labels && "样式" !in labels) { "Long press left reader menu open: $mode" }
                screenshot("menu-long-press-$mode")
                log.append("Reader mode=$mode: long press on expanded-menu body closed bars and selected dictionary text\n")
                back() // selection
                back() // reader
            }
        } finally { Library.remove(book.id); input.delete() }
    }

    private fun labels(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private fun allLabels(): List<String> = semantics().flatMap(::labels)

    private fun click(label: String) {
        val nodes = semantics()
        val target = nodes.lastOrNull { label in labels(it) } ?: error("Missing label $label; found=${nodes.flatMap(::labels)}")
        val center = target.boundsInRoot.center
        val clickable = nodes.filter { it.config.getOrNull(SemanticsActions.OnClick)?.action != null && it.boundsInRoot.contains(center) }
            .minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height } ?: error("Not clickable: $label")
        log.append("Click: $label target=${target.boundsInRoot} clickable=${clickable.boundsInRoot} origin=${composeOrigin.toList()}\n")
        // Exercise the actual pointer route, not a semantics callback that could
        // belong to a node detached by a navigation animation.
        val point = clickable.boundsInRoot.center + androidx.compose.ui.geometry.Offset(composeOrigin[0].toFloat(),composeOrigin[1].toFloat())
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            test.runOnMainSync { activity.dispatchTouchEvent(event) }
            event.recycle()
            SystemClock.sleep(40)
        }
        SystemClock.sleep(450)
    }

    private fun screenshot(name: String) {
        val bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var status = -1
        test.runOnMainSync { PixelCopy.request(activity.window, bitmap, { status=it; latch.countDown() }, Handler(Looper.getMainLooper())) }
        check(latch.await(3, TimeUnit.SECONDS) && status==PixelCopy.SUCCESS)
        File(output,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    private fun screens() {
        check(badgeStyles.first { it.id=="nightcity" }.name=="夜之城")
        check(badgeStyles.first { it.id=="ncpd" }.name=="NCPD")
        test.runOnMainSync {
            Settings.update { it.copy(theme=1,resumeLast=false) }
            Profiles.update { Profile(owned=setOf("icon_neon"), egg=false) }
        }
        activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }) as MainActivity
        SystemClock.sleep(3500)
        test.runOnMainSync { Nav.dress(1) }
        SystemClock.sleep(500)
        val shownBackgrounds = allLabels().filter { it.startsWith("猫园背景：") }
        val unlockedBackgrounds = Shop.catalog.backgrounds.filter { Profiles.value.has(it.id) }.map { "猫园背景：${it.name}" }
        check(shownBackgrounds.toSet()==unlockedBackgrounds.toSet()) { "Locked background is visible: $shownBackgrounds vs $unlockedBackgrounds" }
        screenshot("dress-garden-owned")
        test.runOnMainSync { Nav.dress(3,"icon_neon") }
        SystemClock.sleep(650)
        val expected = Shop.catalog.icons.filter { Profiles.value.has("icon_${it.id}") }.map { "桌面图标：${it.name}" }
        check(allLabels().filter { it.startsWith("桌面图标：") }.toSet()==expected.toSet()) { "Locked icons visible in dress" }
        check(Profiles.value.icon=="default") { "Navigation automatically changed the active icon" }
        val neon = Shop.catalog.icons.first { it.id=="neon" }
        val focused = semantics().first { "桌面图标：${neon.name}" in labels(it) }
        check(focused.boundsInRoot.top>=0 && focused.boundsInRoot.bottom<=activity.window.decorView.height) { "Target icon was not scrolled into view" }
        screenshot("dress-icon-focused")
        test.runOnMainSync { Nav.push(Screen.Shop) }
        SystemClock.sleep(1100)
        screenshot("shop-before-icons")
        click("图标")
        screenshot("shop-icons")
        click(neon.name)
        click("去装扮")
        test.runOnMainSync { check(Nav.top==Screen.Dress && Nav.dressTab==3 && Nav.dressItem=="icon_neon") }
        check(Profiles.value.icon=="default")
        click("桌面图标：${neon.name}")
        check(Profiles.value.icon=="neon") { "Focused icon could not be applied" }
        shopReturn()
        // Avoid actually changing the debug launcher's icon when this test exits.
        test.runOnMainSync { Profiles.update { it.copy(icon="default") }; Nav.push(Screen.Prefs) }
        SystemClock.sleep(450)
        check("清空阅读记录" !in allLabels()) { "Destructive clear-records action remains in settings" }
        log.append("UI: owned-only backgrounds/icons, shop target round trip, manual apply, clear-records removal passed\n")
        readerLongPress()
        test.runOnMainSync {
            activity.setContent {
                CompositionLocalProvider(LocalPalette provides LightPalette) {
                    ReaderMenu(visible=true,panel=2,onPanel={},title="测试书籍",chapterTitle="第1章",chapter=0,last=1,
                        chapterTitleAt={"第1章"},marked=false,night=false,onBack={},onEdit={},onSearch={},onBookmark={},
                        onJumpChapter={},onToc={},onToggleNight={},onDismiss={})
                }
            }
        }
        SystemClock.sleep(550)
        check("启动时继续上次阅读" in allLabels()) { "Reader settings lack resume switch" }
        val before=Settings.value.resumeLast
        click("启动时继续上次阅读")
        check(Settings.value.resumeLast!=before) { "Resume switch did not update shared settings" }
        screenshot("reader-resume-setting")
        log.append("UI: reader resume switch and shared settings passed\n")
    }
}
