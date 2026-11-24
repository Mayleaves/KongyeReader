package com.kongye.reader.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.kongye.reader.data.Shop
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.components.drawGoldFocus
import com.kongye.reader.ui.components.drawGoldMinFocus
import com.kongye.reader.ui.components.drawImportedFocus
import com.kongye.reader.ui.components.drawMinimalFocus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.UUID
import kotlin.math.min

internal data class ReadingReportData(
    val title: String,
    val period: String,
    val millis: Long,
    val days: Int,
    val finished: Int,
    val books: List<Pair<String, Long>>,
    val values: List<Long>,
    val periodType: String = "month",
    val count: Int = 0,
    val streak: Int = 0,
    val word: String = "本月",
    val labels: List<String> = emptyList(),
    val visibleLabels: Set<Int> = values.indices.toSet(),
    val current: Int = -1,
    val future: Set<Int> = emptySet(),
    val recentDays: Map<String, Long> = emptyMap(),
)

/** 直接使用用户 HTML 的 Canvas 排版；只接收本机数据，禁止网络和文件读取。 */
internal object ReadingReport {
    @Keep
    private class Bridge(private val result: CompletableDeferred<String>) {
        @JavascriptInterface fun finished(data: String) { result.complete(data) }
        @JavascriptInterface fun failed(message: String) { result.completeExceptionally(IllegalStateException(message)) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun render(context: Context, report: ReadingReportData, animalId: String, showBooks: Boolean, theme: String = "light", minimalIcon: Boolean = false): Bitmap {
        val animal = withContext(Dispatchers.Default) {
            val image = animalBitmap(context, animalId)
            try { ByteArrayOutputStream().use { bytes -> write(image, bytes); "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP) } }
            finally { image.recycle() }
        }
        val payload = JSONObject().apply {
            put("theme", theme); put("variant", if (minimalIcon) "minimal" else "classic"); put("showBooks", showBooks)
            put("animal", animal); put("period", report.periodType); put("today", Stats.today().toString())
            put("titles", JSONArray(if (showBooks) report.books.map { it.first } else emptyList<String>()))
            put("records", JSONArray().apply { report.recentDays.forEach { (date, millis) -> put(JSONObject().put("date", date).put("minutes", millis / 60_000.0)) } })
            put("model", JSONObject().apply {
                put("label", report.period); put("total", report.millis / 60_000.0); put("days", report.days)
                put("finished", report.finished); put("count", report.count); put("streak", report.streak); put("word", report.word)
                val elapsed = report.values.indices.count { it !in report.future }.coerceAtLeast(1)
                put("avg", report.millis / 60_000.0 / elapsed)
                put("bars", JSONArray().apply { report.values.forEachIndexed { i, millis -> put(JSONObject().apply {
                    put("value", millis / 60_000.0); put("label", report.labels.getOrNull(i) ?: "${i + 1}")
                    put("showLabel", i in report.visibleLabels); put("future", i in report.future); put("current", i == report.current)
                }) } })
                put("dist", JSONArray().apply { if (showBooks) report.books.forEachIndexed { index, book -> put(JSONArray().put(index).put(book.second / 60_000.0)) } })
            })
        }
        val html = withContext(Dispatchers.IO) { context.assets.open("reading_report.html").bufferedReader().use { it.readText() } }
        val encoded = withContext(Dispatchers.Main) {
            val result = CompletableDeferred<String>()
            val web = WebView(context)
            try {
                web.settings.javaScriptEnabled = true
                web.settings.blockNetworkLoads = true
                web.settings.allowFileAccess = false
                web.settings.allowContentAccess = false
                web.settings.domStorageEnabled = false
                web.isVerticalScrollBarEnabled = false
                web.layout(0, 0, 1080, 1620)
                web.addJavascriptInterface(Bridge(result), "ReportBridge")
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript("window.renderNative($payload)", null)
                    }
                }
                web.loadDataWithBaseURL("https://report.kongye.local/", html, "text/html", "UTF-8", null)
                withTimeout(15_000) { result.await() }
            } finally {
                web.stopLoading(); web.removeJavascriptInterface("ReportBridge"); web.destroy()
            }
        }
        return withContext(Dispatchers.Default) {
            check(encoded.startsWith("data:image/png;base64,"))
            val bytes = Base64.decode(encoded.substringAfter(','), Base64.DEFAULT)
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("报告生成失败")
            check(image.width == 1080 && image.height == 1620)
            image
        }
    }

    internal fun animalBitmap(context: Context, id: String): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val animal = Shop.wearer(id) ?: Shop.wearer(Shop.MASCOT)!!
        if (animal.id !in Shop.kongyeCats) {
            val drawable = context.getDrawable(Shop.drawable(animal.res)) ?: return bitmap
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = min(600f / w, 600f / h)
            val canvas = Canvas(bitmap)
            canvas.save(); canvas.translate(320f - w * scale / 2, 620f - h * scale); canvas.scale(scale, scale)
            drawable.setBounds(0, 0, w, h); drawable.draw(canvas); canvas.restore()
            return bitmap
        }
        val minimal = animal.id == Shop.MINCAT || animal.id == Shop.GOLDMIN
        val scale = if (minimal) 640f / 220f else 640f / 260f
        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap.asImageBitmap()), Size(640f, 640f)) {
            withTransform({ translate((640f - (if (minimal) 200f else 240f) * scale) / 2, 0f); scale(scale, scale, Offset.Zero) }) {
                when (animal.id) {
                    Shop.MINCAT -> drawMinimalFocus(false, 0f, leaf = false, plainLeaf = true)
                    Shop.GOLDMIN -> drawGoldMinFocus(false, 0f, leaf = true)
                    Shop.GOLDCAT -> drawGoldFocus(false, 0f, leaf = true)
                    else -> drawImportedFocus(false, 0f, leaf = false, plainLeaf = true)
                }
            }
        }
        return bitmap
    }

    fun write(bitmap: Bitmap, output: OutputStream) {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片保存失败" }
    }

    fun shareIntent(context: Context, bitmap: Bitmap): Intent {
        val dir = File(context.cacheDir, "reading-reports").apply { mkdirs() }
        val file = File(dir, "KongyeReader-${UUID.randomUUID()}.png")
        file.outputStream().use { write(bitmap, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "阅读报告", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
