package com.kongye.reader

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kongye.reader.data.ReportStyles
import com.kongye.reader.data.Profile
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Shop
import kotlinx.coroutines.runBlocking
import com.kongye.reader.ui.screens.ReadingReport
import com.kongye.reader.ui.screens.ReadingReportData
import com.kongye.reader.ui.screens.ReadingReportSheet
import com.kongye.reader.ui.screens.StatsScreen
import com.kongye.reader.ui.screens.drawTrendColumn
import com.kongye.reader.ui.theme.DarkPalette
import com.kongye.reader.ui.theme.LightPalette
import com.kongye.reader.ui.theme.LocalPalette
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class ReadingReportChecks(private val test: Instrumentation) {
    fun run() = runBlocking {
        val context = test.targetContext
        check(context.packageName.endsWith(".debug"))
        test.runOnMainSync {
            val nav = com.kongye.reader.ui.Nav
            val saved = nav.stack.toList()
            try {
                nav.home()
                nav.push(com.kongye.reader.ui.Screen.Stats)
                repeat(30) {
                    nav.push(com.kongye.reader.ui.Screen.Shop)
                    nav.push(com.kongye.reader.ui.Screen.ReportPreview("gold"))
                    nav.push(com.kongye.reader.ui.Screen.Shop)
                    check(nav.stack.toList() == listOf(com.kongye.reader.ui.Screen.Home, com.kongye.reader.ui.Screen.Stats, com.kongye.reader.ui.Screen.Shop)) { "Repeated route: ${nav.stack.map { it.key }}" }
                }
                nav.dress(3)
                nav.push(com.kongye.reader.ui.Screen.Shop)
                check(nav.stack.size == 3)
                nav.pop()
                check(nav.top == com.kongye.reader.ui.Screen.Stats)
                nav.pop()
                check(nav.top == com.kongye.reader.ui.Screen.Home)
                nav.push(com.kongye.reader.ui.Screen.Prefs)
                nav.push(com.kongye.reader.ui.Screen.About)
                nav.push(com.kongye.reader.ui.Screen.History)
                nav.push(com.kongye.reader.ui.Screen.About)
                check(nav.stack.size == 3)
                nav.push(com.kongye.reader.ui.Screen.Prefs)
                nav.pop()
                check(nav.top == com.kongye.reader.ui.Screen.Home)
            } finally { nav.stack.clear(); nav.stack.addAll(saved) }
        }
        val output = File(context.filesDir, "reading-report").apply { mkdirs() }
        val sample = ReadingReportData("我的阅读月报", "2026年11月", 12_480_000, 12, 2,
            listOf("在自己的节奏里阅读一本很长很长的书名用于检查截断" to 8_400_000, "漫长的旅途" to 2_400_000, "一页秋天" to 1_680_000),
            (1..30).map { if (it % 3 == 0) it * 40_000L else 0L }, count = 18, streak = 5, visibleLabels = setOf(0, 4, 9, 14, 19, 24, 29))
        fun save(bitmap: Bitmap, name: String) {
            File(output, "$name.png").outputStream().use { ReadingReport.write(bitmap, it) }
        }
        check(ReportStyles.available(Profile(), "light"))
        for (style in ReportStyles.all.drop(1)) check(!ReportStyles.available(Profile(), style.id))
        check(ReportStyles.available(Profile(owned = setOf("icon_minimal")), "sunset"))
        for ((achievement, theme) in listOf("e7" to "gold", "e8" to "neon", "e9" to "pixel")) {
            check(ReportStyles.available(Profile(achievements = mapOf(achievement to 1L)), theme))
            check(Shop.lockOf("report_$theme") == achievement)
        }
        check(Shop.priceOf("report_sunset") == Shop.priceOf("icon_sunset"))
        val previousProfile = Profiles.value
        try {
            test.runOnMainSync { Profiles.update { Profile(leaves = 1000) } }
            val before = Profiles.value.leaves
            check(Profiles.buy("report_sunset"))
            check(Profiles.value.leaves == before - 200)
            check(Profiles.value.has("report_sunset") && Profiles.value.has("icon_sunset"))
            check(!Profiles.buy("report_sunset"))
            check(!Profiles.buy("report_gold"))
            test.runOnMainSync { Profiles.update { it.copy(egg = true, reportStyle = "gold") }; Profiles.setEgg(false) }
            check(Profiles.value.reportStyle == "light")
        } finally { test.runOnMainSync { Profiles.update { previousProfile } } }
        for (style in ReportStyles.all) {
            val bitmap = ReadingReport.render(context, sample, Shop.MASCOT, true, style.id)
            if (style.id == "gold" || style.id == "neon") {
                val expected = if (style.id == "gold") 0x231F1A else 0x1E1735
                check((bitmap.getPixel(710, 488) and 0xFFFFFF) == expected) { "Report halo differs from supplied artwork: ${style.id}" }
            }
            if (style.id == "sunset") {
                val sun = bitmap.getPixel(750, 425)
                check(android.graphics.Color.red(sun) > android.graphics.Color.green(sun) + 20) { "Fixed sunset missing behind animal" }
            }
            save(bitmap, "theme-${style.id}")
            bitmap.recycle()
        }
        val hidden = ReadingReport.render(context, sample, Shop.MASCOT, false)
        val emptyReport = sample.copy(millis = 0, days = 0, finished = 0, books = emptyList(), values = emptyList(), count = 0, streak = 0)
        for (style in ReportStyles.all) {
            val emptyHidden = ReadingReport.render(context, emptyReport, Shop.MASCOT, false, style.id)
            val emptyShown = ReadingReport.render(context, emptyReport, Shop.MASCOT, true, style.id)
            check(!emptyHidden.sameAs(emptyShown)) { "Empty book-list toggle had no effect: ${style.id}" }
            save(emptyShown, "empty-books-${style.id}")
            emptyHidden.recycle()
            emptyShown.recycle()
        }
        val renamed = ReadingReport.render(context, sample.copy(books = listOf("PRIVATE BOOK TITLE MUST NOT APPEAR" to 8_400_000)), Shop.MASCOT, false)
        check(hidden.sameAs(renamed)) { "Hidden titles changed exported pixels" }
        renamed.recycle()
        save(hidden, "report-private")
        val shown = ReadingReport.render(context, sample, Shop.MASCOT, true)
        check(!hidden.sameAs(shown)) { "Title toggle had no effect" }
        save(shown, "report-books")
        val bytes = ByteArrayOutputStream().also { ReadingReport.write(shown, it) }.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        check(decoded.width == 1080 && decoded.height == 1620)
        decoded.recycle()
        val share = ReadingReport.shareIntent(context, shown)
        check(share.action == Intent.ACTION_SEND && share.type == "image/png")
        check(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uri = share.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        check(uri.scheme == "content" && uri.authority == "${context.packageName}.files")
        check(share.clipData!!.getItemAt(0).uri == uri)
        context.contentResolver.openInputStream(uri).use { check(it!!.readBytes().contentEquals(bytes)) }
        hidden.recycle()
        shown.recycle()
        for (id in listOf(Shop.MINCAT, Shop.GOLDCAT, Shop.GOLDMIN) + Shop.catalog.animals.filter { it.id !in Shop.kongyeCats && Shop.drawable(it.res) != 0 }.take(2).map { it.id }) {
            val bitmap = ReadingReport.render(context, sample, id, true)
            save(bitmap, "report-$id")
            bitmap.recycle()
        }
        for ((name, report) in listOf("empty" to sample.copy(millis = 0, days = 0, finished = 0, books = emptyList(), values = emptyList()),
            "long" to sample.copy(title = "我的阅读足迹", millis = 999_999_999_999L, days = 36500, values = List(1000) { it * 99_000L }))) {
            val bitmap = ReadingReport.render(context, report, Shop.MASCOT, false)
            save(bitmap, "report-$name")
            bitmap.recycle()
        }
        for (density in listOf(1, 2, 3)) for (dark in listOf(false, true)) for (height in listOf(3, 15, 50)) {
            val bitmap = Bitmap.createBitmap(100 * density, 100 * density, Bitmap.Config.ARGB_8888)
            val background = if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            val foreground = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            bitmap.eraseColor(background)
            CanvasDrawScope().draw(Density(density.toFloat()), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                drawTrendColumn(30f * density, (80f - height) * density, 80f * density, 8f * density, Color(foreground))
            }
            for (x in 31 * density until 37 * density) {
                check(bitmap.getPixel(x, 80 * density - 1) == foreground) { "Gap at column base: $density/$height/$x" }
                check(bitmap.getPixel(x, 80 * density + 1) == background) { "Column extends below baseline" }
            }
            bitmap.recycle()
        }
        val activity = test.startActivitySync(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }) as MainActivity
        try {
            test.runOnMainSync {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContent { CompositionLocalProvider(LocalPalette provides LightPalette) { StatsScreen() } }
            }
            SystemClock.sleep(1800)
            check(activity.hasWindowFocus())
            fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
                for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
                return null
            }
            val root = test.uiAutomation.rootInActiveWindow
            check(root.packageName == context.packageName)
            var shareNode = find(root, "分享阅读报告") ?: error("Stats share entry missing")
            while (!shareNode.isClickable) shareNode = shareNode.parent ?: error("Share entry cannot be clicked")
            check(shareNode.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            SystemClock.sleep(1800)
            check(find(test.uiAutomation.rootInActiveWindow, "阅读报告预览") != null)
            check(find(test.uiAutomation.rootInActiveWindow, "保存图片") != null)
            for (dark in listOf(false, true)) {
                test.runOnMainSync { activity.setContent {
                    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
                        ReadingReportSheet(sample, onDismiss = {})
                    }
                } }
                SystemClock.sleep(1800)
                val latch = CountDownLatch(1)
                var status = -1
                val bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                test.runOnMainSync {
                    PixelCopy.request(activity.window, bitmap, { status = it; latch.countDown() }, Handler(Looper.getMainLooper()))
                }
                check(latch.await(5, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS)
                save(bitmap, "preview-${if (dark) "dark" else "light"}")
                bitmap.recycle()
            }
            fun tap(label: String) {
                val active = test.uiAutomation.rootInActiveWindow
                check(active.packageName == context.packageName)
                var node = find(active, label) ?: error("Missing action: $label")
                while (!node.isClickable) node = node.parent ?: error("Not clickable: $label")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                SystemClock.sleep(900)
            }
            fun scrollTo(label: String) {
                repeat(8) {
                    val active = test.uiAutomation.rootInActiveWindow
                    check(active.packageName == context.packageName)
                    if (find(active, label)?.isVisibleToUser == true) return
                    fun scrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                        var result = node.takeIf { it.isScrollable }
                        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> scrollable(child)?.let { result = it } }
                        return result
                    }
                    val target = scrollable(active) ?: error("No scrollable list for $label")
                    check(target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                    SystemClock.sleep(650)
                }
                error("Cannot reach $label")
            }
            fun captureUi(name: String) {
                SystemClock.sleep(1000)
                val latch = CountDownLatch(1)
                var status = -1
                val bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                test.runOnMainSync { PixelCopy.request(activity.window, bitmap, { status = it; latch.countDown() }, Handler(Looper.getMainLooper())) }
                check(latch.await(5, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS)
                save(bitmap, name); bitmap.recycle()
            }
            test.runOnMainSync {
                Profiles.update { Profile(leaves = 1000) }
                activity.setContent { CompositionLocalProvider(LocalPalette provides LightPalette) { com.kongye.reader.ui.screens.ShopScreen() } }
            }
            SystemClock.sleep(1200)
            tap("风格")
            scrollTo("像素复古")
            check(find(test.uiAutomation.rootInActiveWindow, "浅色") != null)
            check(find(test.uiAutomation.rootInActiveWindow, "暗金浮雕") != null)
            captureUi("shop-reports")
            tap("落日")
            val before = Profiles.value.leaves
            tap("用 200 片叶子换")
            check(Profiles.value.leaves == before - 200)
            check(Profiles.value.has("icon_sunset") && Profiles.value.has("report_sunset"))
            tap("去装扮")
            check(com.kongye.reader.ui.Nav.dressTab == 3)
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalPalette provides LightPalette) { com.kongye.reader.ui.screens.DressScreen() } } }
            SystemClock.sleep(1300)
            tap("报告主题：落日")
            check(Profiles.value.reportStyle == "sunset")
            captureUi("dress-reports")
            test.runOnMainSync {
                Profiles.update { it.copy(homeAnimal = Shop.MINCAT, owned = it.owned + Shop.MINCAT) }
                activity.setContent { CompositionLocalProvider(LocalPalette provides LightPalette) { ReadingReportSheet(sample, onDismiss = {}) } }
            }
            SystemClock.sleep(1800)
            check(find(test.uiAutomation.rootInActiveWindow, "阅读报告预览") != null)
            tap("报告主题：暗金浮雕，未解锁")
            check(Profiles.value.reportStyle == "sunset")
            check(find(test.uiAutomation.rootInActiveWindow, "试看 · 暗金浮雕") != null)
            captureUi("preview-locked-theme")
            tap("保存图片")
            check(com.kongye.reader.ui.components.Toast.message == "解锁后即可保存或分享该主题")
            check(test.uiAutomation.rootInActiveWindow.packageName == context.packageName)
            tap("分享海报")
            check(test.uiAutomation.rootInActiveWindow.packageName == context.packageName)
            check(Profiles.value.reportStyle == "sunset")
            tap("报告主题：落日，已解锁")
            check(find(test.uiAutomation.rootInActiveWindow, "试看 · 暗金浮雕") == null)
            check(Profiles.value.reportStyle == "sunset")
            captureUi("preview-home-mincat")
            tap("主题商城")
            check(com.kongye.reader.ui.Nav.top == com.kongye.reader.ui.Screen.Shop)
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalPalette provides LightPalette) { com.kongye.reader.ui.screens.ShopScreen() } } }
            SystemClock.sleep(1300)
            check(find(test.uiAutomation.rootInActiveWindow, "桌面图标") != null) { "Theme shop did not open the style tab" }
            scrollTo("暗金浮雕")
            tap("暗金浮雕")
            SystemClock.sleep(1800)
            check(find(test.uiAutomation.rootInActiveWindow, "暗金浮雕阅读报告局部预览") != null)
            captureUi("shop-report-summary")
            tap("试看阅读报告")
            check(com.kongye.reader.ui.Nav.top == com.kongye.reader.ui.Screen.ReportPreview("gold"))
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalPalette provides LightPalette) { StatsScreen(reportTheme = "gold") } } }
            SystemClock.sleep(1800)
            check(find(test.uiAutomation.rootInActiveWindow, "试看 · 暗金浮雕") != null)
            check(Profiles.value.reportStyle == "sunset")
            captureUi("shop-report-trial")
            test.runOnMainSync {
                Profiles.update { Profile() }
                com.kongye.reader.ui.Nav.dress(1)
                activity.setContent { CompositionLocalProvider(LocalPalette provides LightPalette) { com.kongye.reader.ui.screens.DressScreen() } }
            }
            SystemClock.sleep(1300)
            scrollTo("不挂")
            check(find(test.uiAutomation.rootInActiveWindow, "商城的「挂件」里能换到") == null)
            check(find(test.uiAutomation.rootInActiveWindow, Shop.catalog.ornaments.first().name) == null)
            captureUi("dress-empty-ornaments")
            tap("饰品")
            scrollTo("不戴")
            check(find(test.uiAutomation.rootInActiveWindow, "—") != null)
            check(find(test.uiAutomation.rootInActiveWindow, "无") == null)
            captureUi("dress-no-accessory")
        } finally {
            test.runOnMainSync { Profiles.update { previousProfile }; activity.finish() }
        }
        File(output, "result.txt").writeText("PASS: private/public report, zero/long totals, native/vector animals, readable PNG, content URI read grant, 18 flat-baseline cases, stats share entry and both preview themes")
    }
}
