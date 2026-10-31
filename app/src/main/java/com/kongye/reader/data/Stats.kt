package com.kongye.reader.data

import com.kongye.reader.KongyeReaderApp
import com.kongye.reader.widget.TodayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 阅读记录：每次阅读一条 Session，统计、时间轴、猫园都从这里算 */
object Stats {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    private val saver = Debounced(300) {
        Store.write(FILE, ListSerializer(Session.serializer()), _sessions.value)
        TodayWidget.refresh(KongyeReaderApp.app)
    }

    fun load() {
        _sessions.value = Store.read(FILE, ListSerializer(Session.serializer())) ?: emptyList()
    }

    fun record(s: Session) {
        if (s.millis < 15_000 && !s.finished) return
        _sessions.update { list ->
            val last = list.lastOrNull()
            // 同一本书、隔了不到 3 分钟又接着读，算作同一次
            if (last != null && last.bookId == s.bookId && s.start - last.end < 3 * 60_000 && date(last.start) == date(s.start)) {
                list.dropLast(1) + last.copy(
                    end = s.end,
                    millis = last.millis + s.millis,
                    pages = last.pages + s.pages,
                    toChapter = s.toChapter.ifEmpty { last.toChapter },
                    finished = last.finished || s.finished,
                )
            } else list + s
        }
        saver.poke()
    }

    fun clear() {
        _sessions.value = emptyList()
        saver.flush()
    }

    private const val FILE = "sessions.json"

    // —— 下面是各页面共用的统计换算 ——

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun today(): LocalDate = LocalDate.now(zone)

    fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun byDay(list: List<Session>): Map<LocalDate, Long> {
        val m = HashMap<LocalDate, Long>()
        for (s in list) {
            val d = date(s.start)
            m[d] = (m[d] ?: 0L) + s.millis
        }
        return m
    }

    fun todayMillis(list: List<Session>): Long {
        val t = today()
        return list.filter { date(it.start) == t }.sumOf { it.millis }
    }

    /** 连续阅读天数（今天没读的话从昨天往前数） */
    fun streak(list: List<Session>): Int {
        val days = list.map { date(it.start) }.toHashSet()
        var d = today()
        if (d !in days) d = d.minusDays(1)
        var n = 0
        while (d in days) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** 能在猫园里“领到一只猫”的阅读：至少 3 分钟 */
    fun isCat(s: Session) = s.millis >= 3 * 60_000 || s.finished
}
