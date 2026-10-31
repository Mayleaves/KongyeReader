package com.kongye.reader.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kongye.reader.data.Stats
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.Chip
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.Logo
import com.kongye.reader.ui.components.Signpost
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.launch
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
    val current = remember(books) {
        books.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt } ?: books.maxByOrNull { it.addedAt }
    }
    val today = remember(sessions) { Stats.todayMillis(sessions) }
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    val hopPx = with(LocalDensity.current) { 34.dp.toPx() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
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
                IconBtn(Ico.LIST) { Nav.push(Screen.Shelf) }
                IconBtn(Ico.RING) { Nav.push(Screen.Prefs) }
            }
            Spacer(Modifier.weight(1f))
            CatMascot(
                Modifier
                    .offset { IntOffset(0, hop.value.roundToInt()) }
                    .press(scaleTo = 0.97f) {
                        Toast.show(quotes.random())
                        scope.launch {
                            hop.animateTo(-hopPx, tween(140))
                            hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
                        }
                    },
                size = 210.dp,
                blink = true,
                accent = today >= 30 * 60_000,
            )
            Spacer(Modifier.height(12.dp))
            Chip(
                buildAnnotatedString {
                    append("今日阅读 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Fmt.clock(today)) }
                },
                ico = Ico.CLOCK,
                chevron = true,
            ) { Nav.push(Screen.Stats) }
            Spacer(Modifier.weight(1f))
            InkButton(
                if (current != null) "继续阅读" else "导入书籍",
                Modifier.size(width = 200.dp, height = 60.dp),
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
                .padding(start = 14.dp, bottom = 14.dp),
        ) { Nav.push(Screen.Garden) }
    }
}
