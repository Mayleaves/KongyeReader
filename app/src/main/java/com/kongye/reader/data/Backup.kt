package com.kongye.reader.data

import android.net.Uri
import android.util.Base64
import com.kongye.reader.KongyeReaderApp
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** WebDAV 账号：存在 cloud.json，不进备份包 */
@Serializable
data class CloudConfig(
    val url: String = "",
    val user: String = "",
    val password: String = "",
    /** 每天离开应用时自动备份一次 */
    val auto: Boolean = false,
    val lastBackup: Long = 0,
    val lastError: String = "",
)

/**
 * 备份：书（正文、目录、封面）、书架、阅读记录、设置、叶子和装扮打成一个 zip。
 * 词典和字体是安装包里解出来的，不打包；WebDAV 密码也不打包。
 */
object Backup {
    private val skipTop = setOf("dict", "fonts", CLOUD_FILE)
    private const val CLOUD_FILE = "cloud.json"
    private const val MARK = "kongye-reader-backup"
    private const val DIR = "kongye-reader"

    fun cloud(): CloudConfig = Store.read(CLOUD_FILE, CloudConfig.serializer()) ?: CloudConfig()

    fun saveCloud(c: CloudConfig) = Store.write(CLOUD_FILE, CloudConfig.serializer(), c)

    private fun flushAll() {
        Library.flush()
        Profiles.flush()
    }

    fun write(out: OutputStream) {
        flushAll()
        val root = Store.root
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("$MARK.txt"))
            zip.write("空叶阅读备份 ${Date()}".toByteArray())
            zip.closeEntry()
            root.walkTopDown()
                .onEnter { it == root || it.relativeTo(root).invariantSeparatorsPath.substringBefore('/') !in skipTop }
                .filter { it.isFile && !it.name.endsWith(".tmp") && it.relativeTo(root).invariantSeparatorsPath.substringBefore('/') !in skipTop }
                .forEach { f ->
                    zip.putNextEntry(ZipEntry(f.relativeTo(root).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    fun toBytes(): ByteArray = ByteArrayOutputStream().also { write(it) }.toByteArray()

    /** 恢复：先确认是空叶阅读的备份，再整个替换书和记录，然后重新载入 */
    fun restore(input: InputStream) {
        val tmp = File(KongyeReaderApp.app.cacheDir, "restore-${System.nanoTime()}")
        try {
            var valid = false
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    if (name == "$MARK.txt") valid = true
                    if (e.isDirectory || name.contains("..") || name.startsWith("/")) continue
                    val f = File(tmp, name)
                    f.parentFile?.mkdirs()
                    f.outputStream().use { zip.copyTo(it) }
                }
            }
            if (!valid) throw IOException("这不是空叶阅读的备份文件")
            val root = Store.root
            File(root, "books").deleteRecursively()
            tmp.walkTopDown().filter { it.isFile && it.name != "$MARK.txt" }.forEach { f ->
                val dest = File(root, f.relativeTo(tmp).path)
                dest.parentFile?.mkdirs()
                f.copyTo(dest, overwrite = true)
            }
        } finally {
            tmp.deleteRecursively()
        }
        Settings.load()
        Library.load()
        Stats.load()
        CatRest.load()
        Profiles.load()
        Profiles.syncReading(Stats.sessions.value)
    }

    fun restore(uri: Uri) {
        val input = KongyeReaderApp.app.contentResolver.openInputStream(uri) ?: throw IOException("打不开这个文件")
        input.use { restore(it) }
    }

    fun fileName(): String = "空叶阅读备份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.zip"

    // —— WebDAV ——

    private fun base(c: CloudConfig) = c.url.trim().trimEnd('/')

    private fun auth(c: CloudConfig): String? =
        if (c.user.isEmpty()) null else "Basic " + Base64.encodeToString("${c.user}:${c.password}".toByteArray(), Base64.NO_WRAP)

    /** 地址里的中文、空格转成 %XX，已经转过的不动 */
    private fun encode(url: String): String = buildString {
        for (b in url.toByteArray()) {
            val ch = b.toInt() and 0xff
            if (ch <= 0x20 || ch >= 0x7f) append('%').append("%02X".format(ch)) else append(ch.toChar())
        }
    }

    /** GET / PUT 走系统的 HttpURLConnection */
    private fun open(c: CloudConfig, url: String, method: String): HttpURLConnection {
        val conn = URL(encode(url)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        auth(c)?.let { conn.setRequestProperty("Authorization", it) }
        return conn
    }

    /**
     * MKCOL、PROPFIND：HttpURLConnection 不认这两个方法，改它的私有字段在 Android 9 以后会被拦，
     * 所以自己发一个最简单的请求，只要状态码。HTTPS 照常校验证书和域名。
     */
    private fun davStatus(c: CloudConfig, url: String, method: String, headers: Map<String, String> = emptyMap()): Int {
        val u = URL(encode(url))
        val https = u.protocol.equals("https", ignoreCase = true)
        val port = if (u.port != -1) u.port else if (https) 443 else 80
        val plain = Socket().apply {
            connect(InetSocketAddress(u.host, port), 15_000)
            soTimeout = 30_000
        }
        val socket: Socket = if (!https) plain else try {
            val s = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, u.host, port, true) as SSLSocket
            s.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(u.host, s.session)) throw IOException("证书和地址对不上")
            s
        } catch (e: Exception) {
            plain.close()
            throw e
        }
        socket.use { s ->
            val host = if (u.port == -1) u.host else "${u.host}:$port"
            val req = buildString {
                append("$method ${u.file.ifEmpty { "/" }} HTTP/1.1\r\n")
                append("Host: $host\r\n")
                auth(c)?.let { append("Authorization: $it\r\n") }
                for ((k, v) in headers) append("$k: $v\r\n")
                append("Content-Length: 0\r\nConnection: close\r\nUser-Agent: KongyeReader\r\n\r\n")
            }
            s.getOutputStream().apply { write(req.toByteArray()); flush() }
            val input = s.getInputStream()
            val line = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1 || b == '\n'.code) break
                line.append(b.toChar())
            }
            return line.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("服务器回的不是 HTTP")
        }
    }

    private fun failure(what: String, code: Int) = IOException(
        "$what：" + when (code) {
            401 -> "账号或密码不对"
            403 -> "没有权限"
            404 -> "找不到"
            else -> "服务器返回 $code"
        },
    )

    private fun check(conn: HttpURLConnection, what: String) {
        val code = conn.responseCode
        if (code !in 200..299) throw failure(what, code)
    }

    /** 放备份的目录：先试着建 kongye-reader 子目录，建不了就放在填的地址下面 */
    private fun folder(c: CloudConfig): String {
        val dir = "${base(c)}/$DIR"
        val code = davStatus(c, "$dir/", "MKCOL")
        // 201 新建，405 已经有了
        return when {
            code == 201 || code == 405 || code in 200..299 -> dir
            code == 401 -> throw failure("上传失败", code)
            else -> base(c)
        }
    }

    private fun put(c: CloudConfig, url: String, bytes: ByteArray) {
        val conn = open(c, url, "PUT")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/zip")
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
        check(conn, "上传失败")
        conn.disconnect()
    }

    /** 上传两份：latest（恢复用）和带日期的（同一天覆盖） */
    fun upload(c: CloudConfig) {
        if (base(c).isEmpty()) throw IOException("先填 WebDAV 地址")
        val bytes = toBytes()
        val dir = folder(c)
        put(c, "$dir/$MARK-latest.zip", bytes)
        put(c, "$dir/$MARK-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.zip", bytes)
        saveCloud(cloud().copy(lastBackup = System.currentTimeMillis(), lastError = ""))
        Profiles.count("backup")
    }

    fun download(c: CloudConfig) {
        if (base(c).isEmpty()) throw IOException("先填 WebDAV 地址")
        for (dir in listOf("${base(c)}/$DIR", base(c))) {
            val conn = open(c, "$dir/$MARK-latest.zip", "GET")
            if (conn.responseCode == 404) {
                conn.disconnect()
                continue
            }
            check(conn, "下载失败")
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            restore(ByteArrayInputStream(bytes))
            return
        }
        throw IOException("云端还没有备份")
    }

    /** 测试连接：能列出目录就算通 */
    fun test(c: CloudConfig) {
        if (base(c).isEmpty()) throw IOException("先填 WebDAV 地址")
        val code = davStatus(c, "${base(c)}/", "PROPFIND", mapOf("Depth" to "0"))
        if (code !in 200..299) throw failure("连不上", code)
    }

    /** 离开应用时：开了自动备份、距上次超过一天，就悄悄传一份 */
    fun autoIfDue() {
        val c = cloud()
        if (!c.auto || base(c).isEmpty() || System.currentTimeMillis() - c.lastBackup < 24 * 3600_000L) return
        try {
            upload(c)
        } catch (e: Exception) {
            saveCloud(cloud().copy(lastError = e.message ?: "自动备份失败"))
        }
    }
}
