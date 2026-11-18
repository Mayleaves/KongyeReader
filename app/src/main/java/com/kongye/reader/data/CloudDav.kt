package com.kongye.reader.data

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A single recognized backup file, never a directory or arbitrary WebDAV document. */
data class CloudBackup(val url: String, val name: String, val size: Long = 0, val modified: Long = 0, val location: String = "") {
    val title: String get() = if (name.endsWith("-latest.zip")) "最新备份" else
        name.removePrefix("kongye-reader-backup-").removeSuffix(".zip").let { "${it.take(4)}.${it.substring(4,6)}.${it.takeLast(2)}" }
}
data class CloudBackupListing(val backups: List<CloudBackup>, val incomplete: Boolean = false)
data class CloudDeleteResult(val deleted: Set<String>, val failed: Map<String,String>)

internal fun isCloudBackupName(name: String): Boolean {
    if (name == "kongye-reader-backup-latest.zip") return true
    val match = Regex("kongye-reader-backup-([0-9]{8})\\.zip").matchEntire(name) ?: return false
    return try { LocalDate.parse(match.groupValues[1],DateTimeFormatter.BASIC_ISO_DATE);true } catch (_: DateTimeParseException) { false }
}
internal fun davEncodedUrl(url: String): String = buildString {
    for (b in url.toByteArray(Charsets.UTF_8)) {
        val ch=b.toInt() and 255
        if(ch<=32 || ch>=127) append('%').append("%02X".format(ch)) else append(ch.toChar())
    }
}

/** DAV hrefs may be absolute, relative, or percent-encoded. Only direct child backup files qualify. */
internal fun cloudBackupUrl(folder: String, href: String): Pair<String,String>? = runCatching {
    val parent=URI(davEncodedUrl(folder.trimEnd('/')+"/")).normalize()
    val child=parent.resolve(davEncodedUrl(href)).normalize()
    fun port(u: URI)=if(u.port!=-1)u.port else if(u.scheme.equals("https",true))443 else 80
    if(!parent.scheme.equals(child.scheme,true) || !parent.host.equals(child.host,true) || port(parent)!=port(child) || child.rawQuery!=null || child.rawFragment!=null) return null
    val prefix=Uri.decode(parent.rawPath)
    val path=Uri.decode(child.rawPath)
    if(!path.startsWith(prefix)) return null
    val name=path.removePrefix(prefix)
    if(!isCloudBackupName(name)) return null
    folder.trimEnd('/')+"/"+name to name
}.getOrNull()

internal fun parseCloudBackups(folder: String, bytes: ByteArray): List<CloudBackup> {
    val parser=Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);setInput(ByteArrayInputStream(bytes),null) }
    val items=mutableListOf<CloudBackup>()
    var response=false;var href="";var directStatus=200;var ok=false;var collection=false;var typeKnown=false;var size=0L;var modified=0L
    var propStatus=0;var propCollection=false;var propTypeKnown=false;var propSize=0L;var propModified=0L;var inProp=false;var root=false
    while(true) {
        val event=parser.nextToken()
        if(event==XmlPullParser.END_DOCUMENT) break
        if(event==XmlPullParser.DOCDECL) throw IOException("云端备份列表格式不支持")
        if(event==XmlPullParser.START_TAG) {
            val name=parser.name
            if(parser.depth==1) { if(name!="multistatus") throw IOException("云端返回的不是备份列表");root=true }
            if(parser.namespace !in listOf("DAV:","")) continue
            when(name) {
                "response" -> { response=true;href="";directStatus=200;ok=false;collection=false;typeKnown=false;size=0;modified=0 }
                "href" -> if(response && !inProp) href=parser.nextText().trim()
                "propstat" -> { inProp=true;propStatus=0;propCollection=false;propTypeKnown=false;propSize=0;propModified=0 }
                "status" -> {
                    val code=parser.nextText().trim().split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: 0
                    if(inProp) propStatus=code else directStatus=code
                }
                "resourcetype" -> if(inProp) propTypeKnown=true
                "collection" -> if(inProp) propCollection=true
                "getcontentlength" -> if(inProp) propSize=parser.nextText().trim().toLongOrNull()?.coerceAtLeast(0) ?: 0
                "getlastmodified" -> if(inProp) propModified=runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US).parse(parser.nextText().trim())?.time ?: 0 }.getOrDefault(0)
            }
        } else if(event==XmlPullParser.END_TAG) when(parser.name) {
            "propstat" -> { if(propStatus in 200..299) { ok=true;typeKnown=typeKnown || propTypeKnown;collection=collection || propCollection;size=maxOf(size,propSize);modified=maxOf(modified,propModified) };inProp=false }
            "response" -> {
                if(response && ok && typeKnown && !collection && directStatus in 200..299) cloudBackupUrl(folder,href)?.let { (url,name) ->
                    val location=when(folder.trimEnd('/').substringAfterLast('/')) { "KongyeReader"->"KongyeReader";"kongye-reader"->"旧目录";else->"根目录" }
                    items+=CloudBackup(url,name,size,modified,location)
                }
                response=false
            }
        }
    }
    if(!root) throw IOException("云端备份列表为空响应")
    return items.distinctBy { it.url }
}

/** Small DAV responses: support both Content-Length and chunked framing without private API reflection. */
internal object CloudDav {
    data class Reply(val code: Int,val body: ByteArray)
    private const val MAX_BODY=8*1024*1024
    fun request(url: String,method: String,authorization: String?,headers: Map<String,String> = emptyMap(),body: ByteArray=byteArrayOf()): Reply {
        val u=java.net.URL(davEncodedUrl(url))
        if(u.protocol !in listOf("https","http")) throw IOException("请填写 HTTP 或 HTTPS 地址")
        val https=u.protocol=="https";val port=if(u.port!=-1)u.port else if(https)443 else 80
        val plain=Socket().apply { connect(InetSocketAddress(u.host,port),15_000);soTimeout=30_000 }
        val socket=if(!https)plain else try {
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain,u.host,port,true) as SSLSocket).apply {
                startHandshake()
                if(!HttpsURLConnection.getDefaultHostnameVerifier().verify(u.host,session)) throw IOException("证书和地址对不上")
            }
        } catch(e: Exception) { plain.close();throw e }
        return socket.use { s ->
            val host=if(u.host.contains(':')) "[${u.host.trim('[',']')}]" else u.host
            val request=buildString {
                append("$method ${u.file.ifEmpty { "/" }} HTTP/1.1\r\nHost: $host:$port\r\n")
                authorization?.let { append("Authorization: $it\r\n") }
                headers.forEach { (key,value) -> append("$key: $value\r\n") }
                append("Content-Length: ${body.size}\r\nConnection: close\r\nUser-Agent: KongyeReader\r\n\r\n")
            }
            s.getOutputStream().apply { write(request.toByteArray(Charsets.UTF_8));write(body);flush() }
            readReply(s.getInputStream().buffered())
        }
    }
    internal fun readReply(input: InputStream): Reply {
        fun line(): String {
            val out=ByteArrayOutputStream()
            while(true) {
                val b=input.read()
                if(b<0) throw IOException("云端响应不完整")
                if(b==10) return out.toString("ISO-8859-1").trimEnd('\r')
                out.write(b);if(out.size()>16_384) throw IOException("云端响应头过长")
            }
        }
        var code: Int
        val headers=mutableMapOf<String,String>()
        do {
            code=line().split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("服务器回的不是 HTTP")
            headers.clear();var count=0
            while(true) {
                val value=line();if(value.isEmpty()) break
                if(++count>128) throw IOException("云端响应头过多")
                val split=value.indexOf(':');if(split>0) headers[value.take(split).lowercase(Locale.ROOT)]=value.substring(split+1).trim()
            }
        } while(code in 100..199 && code!=101)
        val out=ByteArrayOutputStream()
        fun copy(count: Long) {
            if(count<0 || count>MAX_BODY-out.size()) throw IOException("云端备份列表过大")
            var left=count;val buffer=ByteArray(8192)
            while(left>0) { val n=input.read(buffer,0,minOf(left,buffer.size.toLong()).toInt());if(n<0) throw IOException("云端响应不完整");out.write(buffer,0,n);left-=n }
        }
        when {
            code in listOf(204,304) -> Unit
            headers["transfer-encoding"]?.lowercase(Locale.ROOT)?.contains("chunked")==true -> while(true) {
                val size=line().substringBefore(';').trim().toLongOrNull(16) ?: throw IOException("云端分块响应无效")
                if(size==0L) { var trailers=0;while(line().isNotEmpty()) if(++trailers>128) throw IOException("云端响应头过多");break }
                copy(size);if(line().isNotEmpty()) throw IOException("云端分块响应不完整")
            }
            headers["content-length"]!=null -> copy(headers.getValue("content-length").toLongOrNull() ?: throw IOException("云端响应长度无效"))
            else -> { val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0)break;if(out.size()+n>MAX_BODY)throw IOException("云端备份列表过大");out.write(buffer,0,n) } }
        }
        return Reply(code,out.toByteArray())
    }
}
