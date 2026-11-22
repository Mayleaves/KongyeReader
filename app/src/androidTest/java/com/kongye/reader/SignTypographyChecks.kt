package com.kongye.reader

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kongye.reader.data.Profile
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Settings
import com.kongye.reader.ui.components.Signpost
import com.kongye.reader.ui.screens.GardenScreen
import com.kongye.reader.ui.screens.HomeScreen
import com.kongye.reader.ui.theme.DarkPalette
import com.kongye.reader.ui.theme.LightPalette
import com.kongye.reader.ui.theme.LocalPalette
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Xiaomi visual captures use an isolated debug profile, including a six-character custom sign. */
internal class SignTypographyChecks(private val test: Instrumentation) {
    fun run() {
        check(test.targetContext.packageName.endsWith(".debug"))
        val output = File(test.targetContext.filesDir, "sign-typography").apply { mkdirs() }
        val previousProfile = Profiles.value
        val previousSettings = Settings.value
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }) as MainActivity
        fun capture(name: String) {
            SystemClock.sleep(1000)
            repeat(12) {
                val ready = CountDownLatch(1)
                var bitmap: Bitmap? = null
                var result = -1
                test.runOnMainSync {
                    val view = activity.window.decorView
                    if (!activity.hasWindowFocus() || view.width == 0 || view.height == 0) {
                        ready.countDown()
                    } else {
                        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                        try {
                            PixelCopy.request(activity.window, bitmap!!, { result = it; ready.countDown() }, Handler(Looper.getMainLooper()))
                        } catch (_: IllegalArgumentException) {
                            ready.countDown()
                        }
                    }
                }
                check(ready.await(8, TimeUnit.SECONDS))
                if (result == PixelCopy.SUCCESS) {
                    File(output, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap!!.recycle()
                    return
                }
                bitmap?.recycle()
                SystemClock.sleep(500)
            }
            error("Reader preview window not available for $name")
        }
        test.runOnMainSync { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        try {
            for (dark in listOf(false, true)) {
                for (label in listOf("猫园", "空叶阅读花园")) {
                    test.runOnMainSync {
                        Settings.update { it.copy(theme = if (dark) 2 else 1, resumeLast = false) }
                        Profiles.update { Profile(signText = label) }
                        activity.setContent { CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) { HomeScreen() } }
                    }
                    val suffix = (if (dark) "dark" else "light") + if (label.length > 2) "-six" else ""
                    capture("home-$suffix")
                    test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) { GardenScreen() } } }
                    capture("garden-$suffix")
                }
            }
            test.runOnMainSync { activity.setContent {
                CompositionLocalProvider(LocalPalette provides LightPalette) {
                    Column(Modifier.fillMaxSize().background(LightPalette.bg).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        for (label in listOf("猫园", "空叶阅读", "空叶阅读花园", "Cat123", "", "龘的猫园")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Signpost(label)
                                Signpost(label, dark = true, pointLeft = false)
                            }
                        }
                    }
                }
            } }
            capture("sign-labels")
            File(output, "result.txt").writeText("PASS captures: home and garden in light/dark themes, default and six-character names, left/right signs, Latin, blank and rare-character fallback")
        } finally {
            test.runOnMainSync {
                Profiles.update { previousProfile }
                Settings.update { previousSettings }
                activity.finish()
            }
        }
    }
}
