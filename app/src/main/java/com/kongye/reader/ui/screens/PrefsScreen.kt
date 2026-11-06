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
    val books by Library.books.collectAsState()
    val sessions by Stats.sessions.collectAsState()
    var clearing by remember { mutableStateOf(false) }
    val profile by Profiles.state.collectAsState()
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    // 彩蛋：连点版本号七下
    var taps by remember { mutableIntStateOf(0) }
    var egg by remember { mutableStateOf(false) }
    // 检查更新：状态在 Updates 里（下载在后台接着跑），这里只管什么时候弹出来
    val up by Updates.state.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(up.phase) {
        if (!asked) return@LaunchedEffect
        when (up.phase) {
            Updates.Phase.LATEST -> Toast.show("已经是最新版（$version）")
            Updates.Phase.FOUND, Updates.Phase.READY -> sheet = true
            Updates.Phase.FAILED -> if (up.release == null) Toast.show(up.error ?: "没连上 GitHub") else sheet = true
            else -> return@LaunchedEffect
        }
        asked = false
    }

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
                Group("数据") {
                    ValueRow("书架", "${books.size} 本")
                    ValueRow("累计阅读", Fmt.cn(sessions.sumOf { it.millis }))
                    ValueRow("备份与恢复", "本地 / WebDAV", chevron = true) { Nav.push(Screen.Backup) }
                    ValueRow("清空阅读记录", "", color = c.accent, chevron = true) { clearing = true }
                }
                Spacer(Modifier.height(20.dp))
                Group("关于") {
                    ValueRow("版本", version) {
                        taps++
                        when {
                            "egg" in profile.achievements -> if (taps >= 3) Toast.show("彩蛋已经找到啦，下面可以开关")
                            taps >= 7 -> {
                                taps = 0
                                egg = true
                            }
                            taps >= 3 -> Toast.show("再点 ${7 - taps} 下")
                        }
                    }
                    val upValue = when (up.phase) {
                        Updates.Phase.CHECKING -> "正在检查…"
                        Updates.Phase.LATEST -> "已是最新版"
                        Updates.Phase.FOUND -> "有新版本 ${up.release?.version}"
                        Updates.Phase.DOWNLOADING -> "下载中 ${if (up.total > 0) up.done * 100 / up.total else 0}%"
                        Updates.Phase.READY -> "下好了，点这里安装"
                        Updates.Phase.FAILED -> if (up.release != null) "下载没成功" else ""
                        Updates.Phase.IDLE -> ""
                    }
                    ValueRow("检查更新", upValue, color = c.ink, chevron = true) {
                        val hasRelease = up.release != null && up.phase != Updates.Phase.LATEST
                        if (hasRelease) sheet = true
                        else {
                            asked = true
                            Updates.check()
                        }
                    }
                    ValueRow("GitHub", "Mayleaves/KongyeReader", chevron = true) { Updates.open(context, Updates.GITHUB) }
                    // 找到过彩蛋才出现：关掉后只剩自己用叶子换到的，正在用的没买的换回默认
                    if ("egg" in profile.achievements) {
                        SwitchRow("彩蛋", profile.egg == true, "开着时商城里的东西全部能用；关掉只剩自己换到的") { v ->
                            Profiles.setEgg(v)
                            Toast.show(if (v) "彩蛋打开了，全部能用" else "彩蛋关掉了，只剩自己换到的")
                        }
                    }
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
            visible = egg,

            title = "发现一片空叶",
            message = "商城里所有的动物、背景、饰品和图标都送给你",
            confirm = "全部收下",
            ico = Ico.LEAF,
            onConfirm = {
                Profiles.unlockEverything()
                egg = false
                Toast.show("全部解锁了，去装扮看看吧")
            },
            onDismiss = { egg = false },
        )
        UpdateSheet(
            visible = sheet && up.release != null && up.phase != Updates.Phase.LATEST && up.phase != Updates.Phase.CHECKING,
            state = up,
        ) {
            sheet = false
            Updates.dismiss()
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
