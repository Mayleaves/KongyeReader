package com.kongye.reader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object Settings {
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state
    val value: AppSettings get() = _state.value

    private val saver = Debounced(300) { Store.write(FILE, AppSettings.serializer(), _state.value) }

    fun load() {
        _state.value = Store.read(FILE, AppSettings.serializer()) ?: AppSettings()
    }

    fun update(f: (AppSettings) -> AppSettings) {
        _state.update(f)
        saver.poke()
    }

    private const val FILE = "settings.json"
}
