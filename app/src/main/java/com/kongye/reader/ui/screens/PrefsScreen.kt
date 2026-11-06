package com.kongye.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Achievements
import com.kongye.reader.data.Library
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Settings
import com.kongye.reader.data.Stats
import com.kongye.reader.data.Updates
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.ConfirmDialog
import com.kongye.reader.ui.components.Group
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.Segmented
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.components.SwitchRow
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.ValueRow
import com.kongye.reader.ui.theme.K

@Composable
fun PrefsScreen() {
    val c = K.c
    val s by Settings.state.collectAsState()
    val profile by Profiles.state.collectAsState()
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    val up by Updates.state.collectAsState()

    Box(Modifier.fillMaxSize().background(c.bg2)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("设置", onBack = { Nav.pop() })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                LeafCard(profile.leaves, Achievements.all.count { it.id in profile.achievements }, Achievements.all.size)
                Spacer(Modifier.height(20.dp))
                Group("外观") {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        T("深色模式", 16.sp, c.ink)
                        Spacer(Modifier.height(10.dp))
                        Segmented(listOf("跟随系统", "浅色", "深色"), s.theme, Modifier.fillMaxWidth()) { i -> Settings.update { it.copy(theme = i) } }
                    }
                    SwitchRow("夜间阅读跟随系统", s.nightFollowSystem, "系统切到深色时，阅读页自动用夜间纸色") { v ->
                        Settings.update { it.copy(nightFollowSystem = v) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Group("阅读") {
                    SwitchRow("启动时继续上次阅读", s.resumeLast, "打开应用直接回到上次读的那一页") { v -> Settings.update { it.copy(resumeLast = v) } }
                    SwitchRow("音量键翻页", s.volumeKeys) { v -> Settings.update { it.copy(volumeKeys = v) } }
                    SwitchRow("屏幕常亮", s.keepScreenOn, "阅读时不自动锁屏") { v -> Settings.update { it.copy(keepScreenOn = v) } }
                    SwitchRow("点左边也翻下一页", s.tapLeftNext, "单手拿手机时更顺手") { v -> Settings.update { it.copy(tapLeftNext = v) } }
                    SwitchRow("显示时间和电量", s.showStatus) { v -> Settings.update { it.copy(showStatus = v) } }
                }
                Spacer(Modifier.height(20.dp))
                Group("其他") {
                    ValueRow("备份与恢复", "本地 / WebDAV", chevron = true) { Nav.push(Screen.Backup) }
                    // 有新版本时在这一行提一句
                    val hint = when (up.phase) {
                        Updates.Phase.FOUND -> "有新版本"
                        Updates.Phase.READY -> "新版本已下好"
                        else -> version
                    }
                    ValueRow("关于空叶阅读", hint, chevron = true) { Nav.push(Screen.About) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** 设置页顶上：叶子数 + 商城、装扮、成就三个入口 */
@Composable
private fun LeafCard(leaves: Int, done: Int, total: Int) {
    val c = K.c
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, SmoothShape(22.dp))
            .padding(16.dp),
    ) {
        Row(Modifier.padding(start = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ico.LEAF, size = 20.dp, color = c.accent)
            Spacer(Modifier.width(6.dp))
            T("$leaves", 22.sp, c.ink, FontWeight.Black)
            Spacer(Modifier.width(4.dp))
            T("片叶子", 13.sp, c.text2)
            Spacer(Modifier.weight(1f))
            T("读一分钟得一片", 12.sp, c.text3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Ico.BAG, "商城", "动物 · 背景", Modifier.weight(1f)) { Nav.push(Screen.Shop) }
            Tile(Ico.HANGER, "装扮", "首页 · 猫园", Modifier.weight(1f)) { Nav.dress(0) }
            Tile(Ico.TROPHY, "成就", "$done / $total", Modifier.weight(1f)) { Nav.push(Screen.Achievements) }
        }
    }
}

@Composable
private fun Tile(ico: Ico, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val c = K.c
    Column(
        modifier
            .press(scaleTo = 0.95f, onClick = onClick)
            .background(c.chip, SmoothShape(18.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(ico, size = 26.dp, color = c.ink)
        Spacer(Modifier.height(6.dp))
        T(title, 14.sp, c.ink, FontWeight.Bold)
        T(sub, 11.sp, c.text2, maxLines = 1)
    }
}
