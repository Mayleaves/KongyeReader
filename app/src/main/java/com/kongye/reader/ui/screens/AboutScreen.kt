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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Library
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Stats
import com.kongye.reader.data.Updates
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.ConfirmDialog
import com.kongye.reader.ui.components.Group
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.SwitchRow
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.ValueRow
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K

/** 关于：猫、名字、版本（连点七下是彩蛋），读书的统计，检查更新、GitHub，找到过彩蛋后的开关 */
@Composable
fun AboutScreen() {
    val c = K.c
    val books by Library.books.collectAsState()
    val sessions by Stats.sessions.collectAsState()
    val profile by Profiles.state.collectAsState()
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    // 彩蛋：连点版本号七下
    var taps by remember { mutableIntStateOf(0) }
    var egg by remember { mutableStateOf(false) }
    fun tapVersion() {
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
            TopBar("关于", onBack = { Nav.pop() })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CatMascot(size = 112.dp, shadow = false, blink = true)
                    Spacer(Modifier.height(10.dp))
                    T("空叶阅读", 22.sp, c.ink, FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    T("安安静静读一本书", 13.sp, c.text2)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .press(scaleTo = 0.94f) { tapVersion() }
                            .background(c.chip, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) { T("版本 $version", 12.sp, c.text2, FontWeight.Medium) }
                }
                Group("读书") {
                    ValueRow("书架", "${books.size} 本")
                    ValueRow("读完", "${books.count { it.finished }} 本")
                    ValueRow("累计阅读", Fmt.cn(sessions.sumOf { it.millis }))
                }
                Spacer(Modifier.height(20.dp))
                Group("更新") {
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
    }
}
