package com.kongye.reader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class CatWakeMemory(val context: String = "", val readingMillis: Long = 0L)

/** Night is 22:00–07:00; one night keeps the same identity across midnight. */
object CatRestPolicy {
    private const val HOUR = 60 * 60_000L
    private const val HALF_HOUR = 30 * 60_000L

    fun context(now: LocalDateTime): String {
        val date = if (now.hour < 7) now.toLocalDate().minusDays(1) else now.toLocalDate()
        return "${if (now.hour >= 22 || now.hour < 7) "night" else "day"}:$date"
    }

    fun sleeping(todayMillis: Long, totalMillis: Long, now: LocalDateTime, wake: CatWakeMemory): Boolean {
        if (wake.context == context(now) && totalMillis >= wake.readingMillis) {
            return totalMillis - wake.readingMillis >= HALF_HOUR
        }
        return now.hour >= 22 || now.hour < 7 || todayMillis >= HOUR
    }
}

/** Persist the last wake-up so reopening the home page cannot restart the half hour. */
object CatRest {
    private val _state = MutableStateFlow(CatWakeMemory())
    val state: StateFlow<CatWakeMemory> = _state
    private const val FILE = "cat-rest.json"
    private val saver = Debounced(50) { Store.write(FILE, CatWakeMemory.serializer(), _state.value) }

    fun load() { _state.value = Store.read(FILE, CatWakeMemory.serializer()) ?: CatWakeMemory() }

    fun wake(totalMillis: Long, now: LocalDateTime = LocalDateTime.now()) {
        _state.value = CatWakeMemory(CatRestPolicy.context(now), totalMillis)
        saver.poke()
    }

    fun reset() {
        _state.value = CatWakeMemory()
        saver.poke()
    }
}
