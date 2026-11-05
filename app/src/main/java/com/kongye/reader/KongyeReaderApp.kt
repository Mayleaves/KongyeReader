package com.kongye.reader

import android.app.Application
import com.kongye.reader.data.Dict
import com.kongye.reader.data.CatRest
import com.kongye.reader.data.Library
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
        Store.io.launch { Dict.prepare() }
    }

    companion object {
        lateinit var app: KongyeReaderApp
            private set
    }
}
