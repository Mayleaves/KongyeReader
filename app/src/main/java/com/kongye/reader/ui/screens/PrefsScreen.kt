package com.kongye.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Library
import com.kongye.reader.data.Settings
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.ConfirmDialog
import com.kongye.reader.ui.components.Group
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Segmented
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
    val books by Library.books.collectAsState()
    val sessions by Stats.sessions.collectAsState()
    var clearing by remember { mutableStateOf(false) }

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
                Group("数据") {
                    ValueRow("书架", "${books.size} 本")
                    ValueRow("累计阅读", Fmt.cn(sessions.sumOf { it.millis }))
                    ValueRow("清空阅读记录", "", color = c.accent, chevron = true) { clearing = true }
                }
                Spacer(Modifier.height(20.dp))
                Group("关于") {
                    ValueRow("版本", "1.1.0")
                    ValueRow("隐私", "不联网，书和记录只在本机")
                }
                Spacer(Modifier.height(36.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CatMascot(size = 72.dp, shadow = false, blink = true)
                    Spacer(Modifier.height(6.dp))
                    T("空叶阅读", 14.sp, c.ink, FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    T("安安静静读一本书", 12.sp, c.text2)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
        ConfirmDialog(
            visible = clearing,
            title = "清空阅读记录？",
            message = "统计、时间轴和猫园里的猫都会消失，书架和阅读进度不受影响",
            confirm = "清空",
            ico = Ico.TRASH,
            onConfirm = {
                Stats.clear()
                clearing = false
                Toast.show("阅读记录已清空")
            },
            onDismiss = { clearing = false },
        )
    }
}
