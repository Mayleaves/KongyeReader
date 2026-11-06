package com.kongye.reader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kongye.reader.data.Profiles
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.screens.ShopScreen
import com.kongye.reader.ui.screens.DressScreen
import com.kongye.reader.ui.screens.AchievementsScreen
import com.kongye.reader.ui.screens.AboutScreen
import com.kongye.reader.ui.screens.BackupScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Importer
import com.kongye.reader.data.Settings
import com.kongye.reader.ui.components.Chip
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.HangingCat
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.ReadingSplash
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.ToastHost
import com.kongye.reader.ui.components.blockTouches
import com.kongye.reader.ui.reader.ReaderScreen
import com.kongye.reader.ui.screens.GardenScreen
import com.kongye.reader.ui.screens.HomeScreen
import com.kongye.reader.ui.screens.PrefsScreen
import com.kongye.reader.ui.screens.ShelfScreen
import com.kongye.reader.ui.screens.StatsScreen
import com.kongye.reader.ui.screens.TimelineScreen
import com.kongye.reader.ui.theme.AppTheme
import com.kongye.reader.ui.theme.DarkPalette
import com.kongye.reader.ui.theme.K
import com.kongye.reader.ui.theme.LightPalette
import com.kongye.reader.ui.theme.LocalPalette

@Composable
fun App(showSplash: Boolean = false) {
    var splashVisible by rememberSaveable { mutableStateOf(showSplash) }
    var splashFinishing by remember { mutableStateOf(false) }
    val splashExit = remember { Animatable(if (splashVisible) 0f else 1f) }
    LaunchedEffect(splashFinishing, splashVisible) {
        if (splashFinishing && splashVisible) {
            splashExit.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            splashVisible = false
        }
    }
    val settings by Settings.state.collectAsState()
    val dark = when (settings.theme) {
        1 -> false
        2 -> true
        else -> AppTheme.systemDark
    }
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            Importer.start(uris, openAfter = false)
        }
        LaunchedEffect(launcher) {
            Nav.pickFiles = { launcher.launch(arrayOf("text/plain", "application/epub+zip", "application/octet-stream")) }
        }
        // 新达成的成就：底部弹一句，奖励的叶子已经到账
        LaunchedEffect(Unit) {
            Profiles.unlocked.collect { a ->
                Toast.show(
                    when {
                        a.item != null -> "解锁成就「${a.title}」，获得「${a.item}」"
                        a.reward > 0 -> "解锁成就「${a.title}」，叶子 +${a.reward}"
                        else -> "解锁成就「${a.title}」"
                    },
                )
            }
        }
        // 最先注册，优先级最低：页面里的弹层、阅读菜单会先拦截返回键
        BackHandler(Nav.stack.size > 1) { Nav.pop() }
        Box(Modifier.fillMaxSize().background(K.c.bg)) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val progress = if (splashVisible) splashExit.value else 1f
                alpha = progress
                translationY = 12.dp.toPx() * (1f - progress)
                scaleX = 0.985f + 0.015f * progress
                scaleY = scaleX
            }) {
                AnimatedContent(
                    targetState = Nav.top,
                    transitionSpec = {
                        val fwd = Nav.forward
                        val enter = fadeIn(tween(220)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { if (fwd) it / 5 else -it / 5 }
                        val exit = fadeOut(tween(180)) + slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { if (fwd) -it / 8 else it / 5 }
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    contentKey = { it.key },
                    label = "nav",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen()
                        Screen.Shelf -> ShelfScreen()
                        is Screen.Reader -> ReaderScreen(s.bookId)
                        Screen.Stats -> StatsScreen()
                        Screen.Timeline -> TimelineScreen()
                        Screen.Garden -> GardenScreen()
                        is Screen.GardenPreview -> GardenScreen(preview = s.item)
                        Screen.Prefs -> PrefsScreen()
                        Screen.Shop -> ShopScreen()
                        Screen.Dress -> DressScreen()
                        Screen.Achievements -> AchievementsScreen()
                        Screen.Backup -> BackupScreen()
                        Screen.About -> AboutScreen()
                    }
                }
                ImportBusy()
                ImportFailure()
                ToastHost()
            }
            if (splashVisible) {
                BackHandler { }
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val progress = splashExit.value
                    alpha = 1f - progress
                    scaleX = 1f + 0.025f * progress
                    scaleY = scaleX
                }) {
                    ReadingSplash(
                        onFinished = { splashFinishing = true },
                        onSkip = { splashVisible = false },
                        // 桌面图标选了极简款，开屏也换成极简猫
                        minimal = Profiles.value.icon in setOf("minimal", "minlight"),
                    )
                }
            }
        }
    }
}

/** 导入中：底部黑色小胶囊 + 转圈 */
@Composable
private fun ImportBusy() {
    val busy = Importer.busy
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(busy != null, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
            val c = K.c
            val angle by rememberInfiniteTransition(label = "spin").animateFloat(
                0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin",
            )
            Row(
                Modifier
                    .background(c.ink, SmoothShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    drawArc(c.onInk, angle, 270f, false, Offset.Zero, Size(size.width, size.height), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                Spacer(Modifier.width(10.dp))
                T(busy ?: "正在导入…", 14.sp, c.onInk, FontWeight.Medium)
            }
        }
    }
}

/** 导入失败：参考“完蛋！你的鸡挂了”，换成挂在绳上的猫 */
@Composable
private fun ImportFailure() {
    val reason = Importer.failure
    BackHandler(reason != null) { Importer.failure = null }
    AnimatedVisibility(reason != null, enter = fadeIn(), exit = fadeOut()) {
        val c = K.c
        val last = remember { arrayOf("") }
        if (reason != null) last[0] = reason
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .blockTouches()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.6f))
            HangingCat(width = 170.dp)
            Spacer(Modifier.height(18.dp))
            T("完蛋！这本书打不开", 20.sp, c.ink, FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Chip(last[0], Modifier.widthIn(max = 300.dp), ico = Ico.BAN, icoColor = c.accent)
            Spacer(Modifier.height(10.dp))
            T("支持 TXT 和 EPUB 格式", 13.sp, c.text2, align = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            InkButton("返回", Modifier.padding(horizontal = 48.dp).size(width = 280.dp, height = 60.dp)) { Importer.failure = null }
            Spacer(Modifier.height(12.dp))
            GhostButton("重新选择", Ico.REFRESH) {
                Importer.failure = null
                Nav.pickFiles()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
