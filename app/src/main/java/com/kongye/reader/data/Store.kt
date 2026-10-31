package com.kongye.reader.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** 应用私有目录里的 JSON 文件读写。写入先落临时文件再改名，进程被杀也不会留下半个文件 */
object Store {
    lateinit var root: File
        private set
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(dir: File) {
        root = dir
    }

    fun <T> read(name: String, ser: KSerializer<T>): T? = runCatching {
        val f = File(root, name)
        if (f.exists()) json.decodeFromString(ser, f.readText()) else null
    }.getOrNull()

    @Synchronized
    fun <T> write(name: String, ser: KSerializer<T>, value: T) {
        val f = File(root, name)
        val tmp = File(root, "$name.tmp")
        tmp.writeText(json.encodeToString(ser, value))
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}

/** 合并短时间内的多次保存：翻页时进度一直在变，只在停下来后写一次盘 */
class Debounced(private val delayMs: Long, private val action: () -> Unit) {
    private var job: Job? = null

    @Synchronized
    fun poke() {
        job?.cancel()
        job = Store.io.launch {
            delay(delayMs)
            action()
        }
    }

    @Synchronized
    fun flush() {
        job?.cancel()
        job = Store.io.launch { action() }
    }
}
