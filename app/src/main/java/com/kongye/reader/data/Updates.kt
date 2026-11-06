package com.kongye.reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL

/** 检查更新：看 GitHub 上仓库最新的 Release（tag 是 v2.0.6 这样），比现在装的新就提示去看看 */
object Updates {
    private const val REPO = "Mayleaves/KongyeReader"
    /** 关于里的 GitHub：这个项目的仓库页 */
    const val GITHUB = "https://github.com/$REPO"

    @Serializable
    data class Release(
        @SerialName("tag_name") val tag: String,
        val name: String = "",
        val body: String = "",
        @SerialName("html_url") val url: String = "",
    ) {
        val version: String get() = tag.trim().removePrefix("v").removePrefix("V")
    }

    /** 最新的 Release；连不上、仓库还没发过 Release 都抛异常 */
    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "KongyeReader")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("GitHub 返回 ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            Store.json.decodeFromString(Release.serializer(), text)
        } finally {
            conn.disconnect()
        }
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

    /** Release 说明去掉 Markdown 的井号、星号，只留前几行 */
    fun summary(body: String, lines: Int = 6): String =
        body.replace("\r", "").lines()
            .map { it.trim().trimStart('#').trim().replace("**", "") }
            .filter { it.isNotEmpty() }
            .take(lines)
            .joinToString("\n")

    fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
