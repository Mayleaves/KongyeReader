package com.kongye.reader

import android.app.Application
import com.kongye.reader.data.Dict
import com.kongye.reader.data.CatRest
import com.kongye.reader.data.Library
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Settings
import com.kongye.reader.data.Stats
import com.kongye.reader.data.Store
import kotlinx.coroutines.launch

class KongyeReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        Store.init(filesDir)
        Settings.load()
        Library.load()
        Stats.load()
        CatRest.load()
        Profiles.load()
        Stats.bindAchievementHistory(Profiles.value)
        Store.io.launch { Dict.prepare() }
        // 读书换叶子、书架变化时看看有没有新成就
        Store.io.launch { Stats.sessions.collect { Profiles.syncReading(it) } }
        Store.io.launch { Library.books.collect { Profiles.check() } }
    }

    companion object {
        lateinit var app: KongyeReaderApp
            private set
    }
}
