package com.kongye.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.kongye.reader.data.Importer
import com.kongye.reader.data.Library
import com.kongye.reader.data.Settings
import com.kongye.reader.ui.App
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var appliedDark: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.systemDark = systemDark()
        applyBars()
        super.onCreate(savedInstanceState)
        // 启动时继续上次阅读：在第一帧之前把阅读页放进页面栈，不会先闪一下首页
        val importing = savedInstanceState == null && handleIntent(intent)
        if (!importing && Settings.value.resumeLast && Nav.stack.size == 1) {
            Library.books.value.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt }?.let { Nav.openBook(it.id) }
        }
        setContent { App(showSplash = savedInstanceState == null) }
        lifecycleScope.launch { Settings.state.collect { applyBars() } }
    }

    private fun systemDark() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun appDark(): Boolean = when (Settings.value.theme) {
        1 -> false
        2 -> true
        else -> AppTheme.systemDark
    }

    /** 状态栏、导航栏图标颜色跟随应用主题；阅读页离开时也调用它恢复 */
    fun applyBars(force: Boolean = false) {
        val dark = appDark()
        if (!force && dark == appliedDark) return
        appliedDark = dark
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF101011.toInt() else Color.WHITE))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppTheme.systemDark = systemDark()
        applyBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        Library.flush()
    }

    /** 文件管理器“打开方式”、其他应用“分享到”；返回是否开始了导入 */
    private fun handleIntent(intent: Intent?): Boolean {
        intent ?: return false
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { Importer.start(listOf(it), openAfter = true) } ?: return false
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> Importer.start(listOf(stream), openAfter = true)
                    !text.isNullOrBlank() -> Importer.startText(text, intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
                    else -> return false
                }
            }
            else -> return false
        }
        intent.action = null
        return true
    }

    /** 阅读页注册后，音量键用来翻页 */
    // Activity 的公开按键入口；忽略 androidx.core 父类实现上的库内限制标记。
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = volumeHandler
        if (handler != null && Settings.value.volumeKeys &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) handler(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        var volumeHandler: ((Int) -> Unit)? = null
    }
}
