package com.kongye.reader.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标：清单里三个 activity-alias 各带一个图标，同一时刻只开一个。
 * 关掉启动这次的那个 alias，系统会连带结束 App，所以装扮页只记下选择，
 * 等离开 App（按 Home、切到别的应用、退出）时才真的换，见 [sync]。
 * 换图标后有的桌面会把图标挪到最后一页，或者要过几秒才刷新。
 */
object AppIcon {
    /** 把桌面图标换成装扮里选的那个；已经一样就什么都不做 */
    fun sync(context: Context) {
        runCatching { apply(context, Profiles.state.value.icon) }
    }

    private val aliases = mapOf(
        "default" to ".LauncherDefault",
        "sunset" to ".LauncherSunset",
        "minimal" to ".LauncherMinimal",
        "minlight" to ".LauncherMinLight",
        "gold" to ".LauncherGold",
        "neon" to ".LauncherNeon",
        "pixel" to ".LauncherPixel",
    )

    fun apply(context: Context, id: String) {
        val pm = context.packageManager
        val target = aliases[id] ?: aliases.getValue("default")
        // 先打开新的再关旧的，桌面上不会有一刻一个图标都没有
        val ordered = aliases.values.sortedBy { if (it == target) 0 else 1 }
        for (alias in ordered) {
            val state = if (alias == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val name = ComponentName(context.packageName, "com.kongye.reader$alias")
            if (pm.getComponentEnabledSetting(name) != state) pm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
        }
    }
}
