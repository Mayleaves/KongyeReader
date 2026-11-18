package com.kongye.reader

import android.app.Instrumentation
import com.kongye.reader.data.*
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

/** All network mutations target the local fixture; all restores target the isolated debug package. */
internal class CloudBackupChecks(private val test: Instrumentation) {
    fun run() {
        check(test.targetContext.packageName.endsWith(".debug"))
        MockDav().use { dav ->
            val c=dav.config
            val listing=Backup.listCloud(c)
            check(!listing.incomplete && listing.backups.size==5) { "Expected five recognized backup files: ${listing.backups.map { it.name }}" }
            check(listing.backups.first().title=="最新备份")
            val dated=listing.backups.filter { it.title!="最新备份" }
            check(dated.map { it.title }==listOf("2026.11.18","2026.11.17","2026.11.16","2026.11.15"))
            check(dated.map { it.location }.containsAll(listOf("KongyeReader","旧目录","根目录")))
            check(dated.all { it.size>0 && it.modified>0 })
            check(dav.requests.filter { it.first=="PROPFIND" }.size==3)
            check(runCatching { Backup.listCloud(c.copy(password="wrong")) }.isFailure)
            dav.failLegacy=true
            val partial=Backup.listCloud(c)
            check(partial.incomplete && partial.backups.size==4)
            dav.failLegacy=false

            val probe=File(test.targetContext.filesDir,"cloud-restore-probe.txt")
            val old=dated.first { it.title=="2026.11.16" }
            probe.writeText("local")
            Backup.download(c,old)
            check(probe.readText()=="day16") { "Selected historical backup was not restored" }
            check(dav.requests.last { it.first=="GET" }.second.endsWith("/kongye-reader/kongye-reader-backup-20261116.zip"))
            probe.writeText("keep")
            val missing=old.copy(name="kongye-reader-backup-20261114.zip",url=dav.base+"/kongye-reader/kongye-reader-backup-20261114.zip")
            check(runCatching { Backup.download(c,missing) }.isFailure)
            check(probe.readText()=="keep") { "Missing selected backup must not fall back to another day" }

            val day17=dated.first { it.title=="2026.11.17" }
            dav.rejectDeletes+=old.url.removePrefix(dav.origin)
            val before=dav.files.keys.toSet()
            val deleted=Backup.deleteCloud(c,listOf(day17,old))
            check(deleted.deleted==setOf(day17.url) && deleted.failed.keys==setOf(old.url))
            check(dav.files.keys==before-setOf(day17.url.removePrefix(dav.origin))) { "An unselected cloud file changed" }
            check(dav.requests.filter { it.first=="DELETE" }.map { it.second }.toSet()==setOf(day17.url.removePrefix(dav.origin),old.url.removePrefix(dav.origin)))
            val requestCount=dav.requests.size
            check(runCatching { Backup.deleteCloud(c,listOf(old.copy(url=dav.origin+"/dav/private-notes.txt",name="private-notes.txt"))) }.isFailure)
            check(dav.requests.size==requestCount)
            dav.rejectDeletes.clear()
            check(Backup.deleteCloud(c,listOf(old)).failed.isEmpty())
            check(Backup.deleteCloud(c,listOf(old)).deleted==setOf(old.url)) // already gone is harmless
            check(Backup.listCloud(c).backups.size==3)

            Backup.upload(c)
            val writes=dav.requests.filter { it.first=="PUT" }.map { it.second }
            check(writes.size==2 && writes.all { it.startsWith("/dav/KongyeReader/") })
            check(writes.any { it.endsWith("-latest.zip") })
            check(dav.files.containsKey("/dav/private-notes.txt"))
            probe.delete()

            val unicode=dav.origin+"/dav/阅读 空间/KongyeReader"
            check(cloudBackupUrl(unicode,"kongye-reader-backup-20261118.zip")!=null)
            check(cloudBackupUrl(unicode,"https://other.invalid/dav/kongye-reader-backup-20261118.zip")==null)
            check(cloudBackupUrl(unicode,"../kongye-reader-backup-20261118.zip")==null)
            check(cloudBackupUrl(unicode,"nested/kongye-reader-backup-20261118.zip")==null)
            check(cloudBackupUrl(unicode,"%2e%2e%2fkongye-reader-backup-20261118.zip")==null)
            check(!isCloudBackupName("kongye-reader-backup-20260230.zip"))
            check(!isCloudBackupName("kongye-reader-backup-20261118.zip.bak"))
            val badChunk="HTTP/1.1 207 Multi-Status\r\nTransfer-Encoding: chunked\r\n\r\nZ\r\n"
            check(runCatching { CloudDav.readReply(ByteArrayInputStream(badChunk.toByteArray())) }.isFailure)
            val truncated="HTTP/1.1 207 Multi-Status\r\nContent-Length: 10\r\n\r\nabc"
            check(runCatching { CloudDav.readReply(ByteArrayInputStream(truncated.toByteArray())) }.isFailure)
            val dtd="""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///data/local/tmp/none">]><d:multistatus xmlns:d="DAV:"/>"""
            check(runCatching { parseCloudBackups(dav.base,dtd.toByteArray()) }.isFailure)
            File(test.targetContext.filesDir,"cloud-backup-test-result.txt").writeText("PASS: listing new/legacy/root folders; chunked and fixed responses; metadata and sorting; historical restore; missing-day isolation; selected deletion and partial failures; unrelated-file preservation; new-directory upload; invalid href/date/XML rejection")
        }
    }
}

internal class MockDav: Closeable {
    private val server=ServerSocket(0,20,InetAddress.getByName("127.0.0.1"))
    val origin="http://127.0.0.1:${server.localPort}"
    val base="$origin/dav"
    val config=CloudConfig(base,"reader","fixture")
    val files=ConcurrentHashMap<String,ByteArray>()
    val requests=CopyOnWriteArrayList<Pair<String,String>>()
    val rejectDeletes=ConcurrentHashMap.newKeySet<String>()
    @Volatile var failLegacy=false
    private val listener=thread(isDaemon=true,name="mock-dav") {
        while(!server.isClosed) try { val socket=server.accept();thread(isDaemon=true) { socket.use { handle(it) } } } catch(_:IOException) { break }
    }
    init {
        files["/dav/KongyeReader/kongye-reader-backup-latest.zip"]=zip("latest")
        files["/dav/KongyeReader/kongye-reader-backup-20261118.zip"]=zip("day18")
        files["/dav/KongyeReader/kongye-reader-backup-20261117.zip"]=zip("day17")
        files["/dav/kongye-reader/kongye-reader-backup-20261116.zip"]=zip("day16")
        files["/dav/kongye-reader-backup-20261115.zip"]=zip("day15")
        files["/dav/KongyeReader/kongye-reader-backup-20260230.zip"]=byteArrayOf(1)
        files["/dav/private-notes.txt"]="untouched".toByteArray()
    }
    private fun zip(value: String): ByteArray = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
        for((name,text) in mapOf("kongye-reader-backup.txt" to "fixture","cloud-restore-probe.txt" to value)) {
            zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()
        }
    } }.toByteArray()
    private fun line(input: InputStream): String {
        val out=ByteArrayOutputStream()
        while(true) { val b=input.read();if(b<0 || b==10)break;out.write(b) }
        return out.toString("UTF-8").trimEnd('\r')
    }
    private fun handle(socket: Socket) {
        socket.soTimeout=10_000
        val input=socket.getInputStream().buffered()
        val first=line(input).split(' ');if(first.size<2)return
        val method=first[0];val path=android.net.Uri.decode(first[1])
        val headers=mutableMapOf<String,String>()
        while(true) { val l=line(input);if(l.isEmpty())break;headers[l.substringBefore(':').lowercase()]=l.substringAfter(':').trim() }
        val body=ByteArray(headers["content-length"]?.toIntOrNull() ?: 0)
        var at=0;while(at<body.size) { val n=input.read(body,at,body.size-at);if(n<0)return;at+=n }
        requests+=method to path
        val out=socket.getOutputStream()
        fun reply(code: Int,bytes: ByteArray=byteArrayOf(),chunked: Boolean=false) {
            out.write("HTTP/1.1 $code Fixture\r\nConnection: close\r\n".toByteArray())
            if(chunked) {
                out.write("Transfer-Encoding: chunked\r\nContent-Type: application/xml; charset=utf-8\r\n\r\n".toByteArray())
                for(chunk in bytes.asList().chunked(37)) { val b=chunk.toByteArray();out.write((b.size.toString(16)+";fixture=yes\r\n").toByteArray());out.write(b);out.write("\r\n".toByteArray()) }
                out.write("0\r\nX-Fixture: complete\r\n\r\n".toByteArray())
            } else { out.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray());out.write(bytes) }
            out.flush()
        }
        if(headers["authorization"]!="Basic cmVhZGVyOmZpeHR1cmU=") { reply(401);return }
        when(method) {
            "PROPFIND" -> {
                if(failLegacy && path=="/dav/kongye-reader/") { reply(503);return }
                if(path !in listOf("/dav/","/dav/KongyeReader/","/dav/kongye-reader/")) { reply(404);return }
                fun response(href: String,size: Int,collection: Boolean=false,status: Int=200)="""<x:response><x:href>$href</x:href><x:propstat><x:prop><x:resourcetype>${if(collection)"<x:collection/>" else ""}</x:resourcetype><x:getcontentlength>$size</x:getcontentlength><x:getlastmodified>Wed, 18 Nov 2026 09:10:00 GMT</x:getlastmodified></x:prop><x:status>HTTP/1.1 $status Fixture</x:status></x:propstat></x:response>"""
                val contents=buildString {
                    append("""<?xml version="1.0" encoding="UTF-8"?><x:multistatus xmlns:x="DAV:">""")
                    append(response(path,0,collection=true))
                    files.entries.filter { it.key.startsWith(path) && !it.key.removePrefix(path).contains('/') }.forEach { append(response(it.key,it.value.size)) }
                    append(response(path+"kongye-reader-backup-20261101.zip",0,collection=true))
                    append(response(path+"kongye-reader-backup-20261102.zip",42,status=404))
                    append(response("https://other.invalid"+path+"kongye-reader-backup-20261103.zip",42))
                    append(response(path+"nested/kongye-reader-backup-20261104.zip",42))
                    append("</x:multistatus>")
                }
                reply(207,contents.toByteArray(),chunked=path=="/dav/KongyeReader/")
            }
            "GET" -> files[path]?.let { reply(200,it) } ?: reply(404)
            "PUT" -> { files[path]=body;reply(201) }
            "MKCOL" -> reply(405)
            "DELETE" -> if(path in rejectDeletes) reply(403) else { val previous=files.remove(path);reply(if(previous==null)404 else 204) }
            else -> reply(405)
        }
    }
    override fun close() { server.close();listener.join(1000) }
}
