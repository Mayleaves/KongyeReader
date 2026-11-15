package com.kongye.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.*
import com.kongye.reader.ui.theme.K
import org.json.JSONArray

private data class HistoryEntry(val version: String, val title: String, val changes: List<String>) {
    fun matches(query: String) = version.contains(query, true) || title.contains(query, true) || changes.any { it.contains(query, true) }
}

@Composable
fun HistoryScreen() {
    val c = K.c
    val context = LocalContext.current
    val entries = remember {
        val array = JSONArray(context.assets.open("update_history.json").bufferedReader().use { it.readText() })
        List(array.length()) { i ->
            val entry = array.getJSONObject(i)
            val changes = entry.getJSONArray("changes")
            HistoryEntry(entry.getString("version"), entry.getString("title"), List(changes.length()) { changes.getString(it) })
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val results = remember(query, entries) { entries.filter { it.matches(query.trim()) } }
    val pageCount = ((results.size + 4) / 5).coerceAtLeast(1)
    val current = page.coerceIn(0, pageCount - 1)
    val listState = rememberLazyListState()
    LaunchedEffect(query, current) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().background(c.bg2).navigationBarsPadding()) {
        TopBar("更新历史", onBack = { Nav.pop() })
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(46.dp)
                .background(c.chip, SmoothShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ico.SEARCH, size = 18.dp, color = c.text2)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) T("搜索版本号或更新内容", 14.sp, c.text3)
                BasicTextField(
                    value = query, onValueChange = { query = it; page = 0 }, singleLine = true,
                    textStyle = TextStyle(color = c.ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.ink), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) IconBtn(Ico.CLOSE, size = 16.dp) { query = ""; page = 0 }
        }
        T("共 ${results.size} 个版本", 12.sp, c.text2, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (results.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    T("没有找到相关更新", 14.sp, c.text2)
                }
            }
            items(results.drop(current * 5).take(5), key = { it.version }) { entry ->
                Group(null) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(entry.version, 20.sp, c.ink, FontWeight.Bold)
                        T(entry.title, 14.sp, c.ink, FontWeight.SemiBold)
                        for (change in entry.changes) T("• $change", 13.sp, c.text2, lineHeight = 20.sp)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton("上一页", color = if (current > 0) c.ink else c.text3) { if (current > 0) page = current - 1 }
            T("${current + 1} / $pageCount", 13.sp, c.text2)
            GhostButton("下一页", color = if (current + 1 < pageCount) c.ink else c.text3) { if (current + 1 < pageCount) page = current + 1 }
        }
    }
}
