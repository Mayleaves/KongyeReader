package com.kongye.reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kongye.reader.KongyeReaderApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新：看 GitHub 上仓库最新的 Release（tag 是 v2.0.7 这样），比现在装的新就在 App 里下载安装包、交给系统安装，
 * 不用跳到 GitHub。安装包是 Release 里附的 .apk。
 */
object Updates {
    private const val REPO = "Mayleaves/KongyeReader"
    /** 关于里的 GitHub：这个项目的仓库页 */
    const val GITHUB = "https://github.com/$REPO"

    @Serializable
    data class Asset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val url: String,
    )

    @Serializable
    data class Release(
        @SerialName("tag_name") val tag: String,
        val name: String = "",
        val body: String = "",
        @SerialName("html_url") val url: String = "",
        @SerialName("published_at") val published: String = "",
        val assets: List<Asset> = emptyList(),
    ) {
        val version: String get() = tag.trim().removePrefix("v").removePrefix("V")
        val apk: Asset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
    }

    enum class Phase { IDLE, CHECKING, LATEST, FOUND, DOWNLOADING, READY, FAILED }

    data class State(
        val phase: Phase = Phase.IDLE,
        val release: Release? = null,
        val done: Long = 0,
        val total: Long = 0,
        val file: File? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var job: Job? = null

    val current: String by lazy {
        val app = KongyeReaderApp.app
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "KongyeReader")
        }

    /** 看看 GitHub 上有没有新版本；下载中、已经下好就不重复查 */
    fun check() {
        if (_state.value.phase in setOf(Phase.CHECKING, Phase.DOWNLOADING)) return
        _state.value = State(Phase.CHECKING)
        job = Store.io.launch {
            val r = runCatching {
                val conn = open("https://api.github.com/repos/$REPO/releases/latest")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) error("GitHub 返回 ${conn.responseCode}")
                    Store.json.decodeFromString(Release.serializer(), conn.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    conn.disconnect()
                }
            }
            _state.value = r.fold(
                onSuccess = { rel ->
                    if (!newer(rel.version, current)) State(Phase.LATEST, rel)
                    else {
                        // 上次下好的同一版还在，就直接可以装
                        val ready = apkFile(rel).takeIf { f -> f.exists() && f.length() == rel.apk?.size }
                        if (ready != null) State(Phase.READY, rel, ready.length(), ready.length(), ready) else State(Phase.FOUND, rel)
                    }
                },
                onFailure = { State(Phase.FAILED, error = "没连上 GitHub，过会儿再试") },
            )
        }
    }

    private fun dir() = File(KongyeReaderApp.app.cacheDir, "update").apply { mkdirs() }
    private fun apkFile(rel: Release) = File(dir(), "KongyeReader-${rel.version}.apk")

    /** 下载安装包，边下边报进度；下完看一眼包名对不对 */
    fun download() {
        val rel = _state.value.release ?: return
        val asset = rel.apk ?: run {
            _state.value = _state.value.copy(phase = Phase.FAILED, error = "这一版没有附安装包")
            return
        }
        if (_state.value.phase == Phase.DOWNLOADING) return
        _state.value = State(Phase.DOWNLOADING, rel, 0, asset.size)
        job = Store.io.launch {
            val target = apkFile(rel)
            val part = File(dir(), target.name + ".part")
            dir().listFiles()?.filter { it != part }?.forEach { it.delete() }
            val r = runCatching {
                val conn = open(asset.url)
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) error("下载失败（${conn.responseCode}）")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: asset.size
                    var done = 0L
                    var shown = 0L
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(1 shl 16)
                            while (isActive) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (done - shown > total / 200) {
                                    shown = done
                                    _state.update { it.copy(done = done, total = total) }
                                }
                            }
                        }
                    }
                    if (!isActive) error("取消了")
                    if (total > 0 && done != total) error("没下完整，再试一次")
                } finally {
                    conn.disconnect()
                }
                val app = KongyeReaderApp.app
                val info = app.packageManager.getPackageArchiveInfo(part.path, 0)
                if (info?.packageName != app.packageName) error("下到的不是空叶阅读的安装包")
                target.delete()
                if (!part.renameTo(target)) error("存不下安装包")
                target
            }
            _state.value = r.fold(
                onSuccess = { f -> State(Phase.READY, rel, f.length(), f.length(), f) },
                onFailure = { e ->
                    part.delete()
                    State(Phase.FAILED, rel, error = e.message ?: "下载失败")
                },
            )
        }
    }

    fun cancel() {
        job?.cancel()
        val rel = _state.value.release
        _state.value = if (rel != null) State(Phase.FOUND, rel) else State()
    }

    /** 不在下载就收起来 */
    fun dismiss() {
        if (_state.value.phase != Phase.DOWNLOADING && _state.value.phase != Phase.READY) _state.value = State()
    }

    /**
     * 交给系统安装。第一次要在系统设置里允许空叶阅读“安装未知应用”：先跳过去，回来再点一次安装。
     * 返回 false 表示去设置了
     */
    fun install(context: Context): Boolean {
        val file = _state.value.file ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return true
    }

    /** a 比 b 新：按点分开逐段比数字（2.0.10 比 2.0.9 新） */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
