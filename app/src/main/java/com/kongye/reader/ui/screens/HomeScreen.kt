package com.kongye.reader.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Library
import com.kongye.reader.data.CatRest
import com.kongye.reader.data.CatRestPolicy
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.DashboardCat
import com.kongye.reader.ui.components.CatSpeechBubble
import com.kongye.reader.ui.components.catGazeAt
import com.kongye.reader.ui.components.Chip
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.Logo
import com.kongye.reader.ui.components.Signpost
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.math.roundToInt

private val quotes = listOf(
    "喵，今天读点什么？",
    "翻开一本书吧",
    "你读书，我在旁边打盹",
    "一页也算数",
    "书里有小鱼干吗？",
    "读够半小时，我头上会长出一片叶子",
)

/** 首页：参考小鸡专注的首页，一只猫、一个胶囊、一个大黑按钮，左下角路牌去猫园 */
@Composable
fun HomeScreen() {
    val c = K.c
    val books by Library.books.collectAsState()
    val sessions by Stats.sessions.collectAsState()
    val wakeMemory by CatRest.state.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val current = remember(books) {
        books.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt } ?: books.maxByOrNull { it.addedAt }
    }
    val today = remember(sessions, now.toLocalDate()) { Stats.todayMillis(sessions) }
    val totalRead = remember(sessions) { sessions.sumOf { it.millis } }
    val sleeping = CatRestPolicy.sleeping(today, totalRead, now, wakeMemory)
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    var petSignal by remember { mutableIntStateOf(0) }
    val hopPx = with(LocalDensity.current) { 34.dp.toPx() }
    val gazeRadius = with(LocalDensity.current) { 100.dp.toPx() }
    var fingerPosition by remember { mutableStateOf<Offset?>(null) }
    var homeOrigin by remember { mutableStateOf(Offset.Zero) }
    val buttonBounds = remember { mutableMapOf<String, Rect>() }
    fun Modifier.excludeFromGaze(key: String): Modifier = onGloballyPositioned {
        buttonBounds[key] = it.boundsInRoot()
    }
    var catEyeCenter by remember { mutableStateOf(Offset.Zero) }
    var catHeadTop by remember { mutableStateOf(0f) }
    var speech by remember { mutableStateOf("") }
    var speechSignal by remember { mutableIntStateOf(0) }
    var speechVisible by remember { mutableStateOf(false) }
    var speechHeight by remember { mutableIntStateOf(0) }
    val bubbleGap = with(LocalDensity.current) { 50.dp.toPx() }
    val headInset = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(speechSignal) {
        if (speechSignal > 0) {
            speechVisible = true
            delay(2200)
            speechVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)
        .onGloballyPositioned { homeOrigin = it.positionInRoot() }
        .pointerInput(Unit) {
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Latch for this gesture: a button press must never move the eyes,
                    // even if the finger later drags outside the button.
                    val follows = buttonBounds.values.none { it.contains(down.position + homeOrigin) }
                    fingerPosition = down.position.takeIf { follows }
                    var pressed = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val finger = event.changes.firstOrNull { it.id == down.id }
                        pressed = finger?.pressed == true
                        fingerPosition = finger?.takeIf { follows && it.pressed }?.position
                    } while (pressed)
                }
            } finally {
                fingerPosition = null
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 10.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo()
                Spacer(Modifier.weight(1f))
                IconBtn(Ico.LIST, Modifier.excludeFromGaze("shelf")) { Nav.push(Screen.Shelf) }
                IconBtn(Ico.RING, Modifier.excludeFromGaze("prefs")) { Nav.push(Screen.Prefs) }
            }
            Spacer(Modifier.weight(1f))
            // Anchor speech to the cat's resting layout, outside the animated hop.
            Box(Modifier.size(210.dp).onGloballyPositioned {
                catHeadTop = it.positionInRoot().y + headInset
            }) {
                DashboardCat(
                    Modifier
                        .offset { IntOffset(0, hop.value.roundToInt()) }
                        .onGloballyPositioned {
                            catEyeCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height * (91f / 210f))
                        }
                        .press(scaleTo = 0.97f) {
                            if (sleeping) {
                                CatRest.wake(totalRead, LocalDateTime.now())
                                speech = "醒啦，陪你继续读书"
                            } else {
                                petSignal++
                                speech = quotes.random()
                                scope.launch {
                                    hop.animateTo(-hopPx, tween(140))
                                    hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
                                }
                            }
                            speechSignal++
                        },
                    size = 210.dp,
                    petSignal = petSignal,
                    leaf = today >= 30 * 60_000,
                    gaze = catGazeAt(fingerPosition?.plus(homeOrigin), catEyeCenter, gazeRadius),
                    sleeping = sleeping,
                )
            }
            Spacer(Modifier.height(12.dp))
            Chip(
                buildAnnotatedString {
                    append("今日阅读 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Fmt.clock(today)) }
                },
                ico = Ico.CLOCK,
                modifier = Modifier.excludeFromGaze("today"),
                chevron = true,
            ) { Nav.push(Screen.Stats) }
            Spacer(Modifier.weight(1f))
            InkButton(
                if (current != null) "继续阅读" else "导入书籍",
                Modifier.size(width = 200.dp, height = 60.dp).excludeFromGaze("read"),
            ) {
                if (current != null) Nav.openBook(current.id) else Nav.pickFiles()
            }
            Spacer(Modifier.height(14.dp))
            if (current != null) {
                val label = when {
                    current.finished -> "已读完"
                    current.progress > 0f -> "读到 ${Fmt.percent(current.progress)}"
                    current.lastReadAt > 0 -> "刚翻开"
                    else -> "还没开始读"
                }
                T(
                    "《${current.title}》· $label", 13.sp, c.text2,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .excludeFromGaze("book")
                        .press { Nav.push(Screen.Shelf) },
                    maxLines = 1, align = TextAlign.Center,
                )
            } else {
                T("支持 TXT · EPUB", 13.sp, c.text3)
            }
            Spacer(Modifier.weight(0.7f))
            Spacer(Modifier.height(72.dp))
        }
        Signpost(
            "猫园",
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, bottom = 14.dp)
                .excludeFromGaze("garden"),
        ) { Nav.push(Screen.Garden) }
        AnimatedVisibility(
            speechVisible,
            modifier = Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (catHeadTop - homeOrigin.y - bubbleGap - speechHeight).roundToInt().coerceAtLeast(0)) }
                .onSizeChanged { speechHeight = it.height },
            enter = fadeIn(),
            exit = fadeOut(),
        ) { CatSpeechBubble(speech) }
    }
}
