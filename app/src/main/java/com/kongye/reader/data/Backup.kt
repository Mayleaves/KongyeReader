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
import java.net.URL
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
    private const val DIR = "KongyeReader"
    private const val LEGACY_DIR = "kongye-reader"

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

    private fun encode(url: String) = davEncodedUrl(url)

    /** GET / PUT 走系统的 HttpURLConnection */
    private fun open(c: CloudConfig, url: String, method: String): HttpURLConnection {
        val conn = URL(encode(url)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        auth(c)?.let { conn.setRequestProperty("Authorization", it) }
        return conn
    }

    private fun davStatus(c: CloudConfig, url: String, method: String, headers: Map<String,String> = emptyMap()): Int =
        CloudDav.request(url,method,auth(c),headers).code

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

    /** 放备份的目录：先试着建 KongyeReader 子目录，建不了就放在填的地址下面 */
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
        // 新目录优先；升级前的旧目录及原有根目录备份仍可恢复。
        for (dir in listOf("${base(c)}/$DIR", "${base(c)}/$LEGACY_DIR", base(c))) {
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

    private fun cloudFolders(c: CloudConfig) = listOf("${base(c)}/$DIR", "${base(c)}/$LEGACY_DIR", base(c)).distinct()

    /** 汇总新旧目录和根目录的备份；不递归列出其他文件夹。 */
    fun listCloud(c: CloudConfig): CloudBackupListing {
        if(base(c).isEmpty()) throw IOException("先填 WebDAV 地址")
        val xml="""<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>""".toByteArray()
        val all=mutableListOf<CloudBackup>()
        var failures=0;var readable=0;var firstError: Exception?=null
        for(folder in cloudFolders(c)) {
            try {
                val reply=CloudDav.request(folder+"/","PROPFIND",auth(c),mapOf("Depth" to "1","Content-Type" to "application/xml; charset=utf-8"),xml)
                if(reply.code==404) continue
                if(reply.code==401) throw failure("读取备份失败",reply.code)
                if(reply.code !in 200..299) throw failure("读取备份失败",reply.code)
                all+=parseCloudBackups(folder,reply.body);readable++
            } catch(e: Exception) {
                failures++;if(firstError==null) firstError=e
            }
        }
        if(failures>0 && readable==0) throw firstError ?: IOException("读取云端备份失败")
        val sorted=all.distinctBy { it.url }.sortedWith(compareByDescending<CloudBackup> { it.name.removePrefix("$MARK-") }.thenByDescending { it.modified })
        return CloudBackupListing(sorted,failures>0)
    }

    private fun validateCloudFile(c: CloudConfig, file: CloudBackup) {
        if(!isCloudBackupName(file.name) || cloudFolders(c).none { file.url=="$it/${file.name}" }) throw IOException("备份地址无效，请刷新后重试")
    }

    /** 恢复用户选中的那一份；文件若已删除，不改为恢复其他备份。 */
    fun download(c: CloudConfig, file: CloudBackup) {
        validateCloudFile(c,file)
        val conn=open(c,file.url,"GET").apply { instanceFollowRedirects=false }
        try {
            if(conn.responseCode==404) throw IOException("这份备份已不存在，请刷新列表")
            check(conn,"下载失败")
            conn.inputStream.use { restore(it) }
        } finally { conn.disconnect() }
    }

    /** 只删除明确选中的文件，目录和未勾选备份均不受影响。返回每一份的实际结果。 */
    fun deleteCloud(c: CloudConfig, files: List<CloudBackup>): CloudDeleteResult {
        files.forEach { validateCloudFile(c,it) }
        val removed=mutableSetOf<String>();val failed=mutableMapOf<String,String>()
        for(file in files.distinctBy { it.url }) {
            try {
                val reply=CloudDav.request(file.url,"DELETE",auth(c))
                if(reply.code !in setOf(200,202,204,404)) throw failure("删除失败",reply.code)
                removed+=file.url
            } catch(e: Exception) { failed[file.url]=e.message ?: "删除失败" }
        }
        return CloudDeleteResult(removed,failed)
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
