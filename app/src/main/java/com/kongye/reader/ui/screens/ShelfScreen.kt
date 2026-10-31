package com.kongye.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Book
import com.kongye.reader.data.Library
import com.kongye.reader.data.Settings
import com.kongye.reader.ui.Fmt
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.BookCover
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.CatPose
import com.kongye.reader.ui.components.ConfirmDialog
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.Sheet
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val sortNames = listOf("最近阅读", "导入时间", "书名")

@Composable
fun ShelfScreen() {
    val c = K.c
    val books by Library.books.collectAsState()
    val settings by Settings.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    val shown = remember(books, settings.shelfSort, query) {
        val q = query.trim()
        val list = if (q.isEmpty()) books else books.filter { it.title.contains(q, true) || it.author.contains(q, true) }
        when (settings.shelfSort) {
            1 -> list.sortedByDescending { it.addedAt }
            2 -> Collator.getInstance(Locale.CHINA).let { col -> list.sortedWith { a, b -> col.compare(a.title, b.title) } }
            else -> list.sortedByDescending { if (it.lastReadAt > 0) it.lastReadAt else it.addedAt }
        }
    }
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("书架", onBack = { Nav.pop() }) {
                IconBtn(Ico.PLUS) { Nav.pickFiles() }
            }
            if (books.isEmpty()) {
                EmptyShelf(Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = navPad + 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            SearchField(query) { query = it }
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.press { Settings.update { it.copy(shelfSort = (it.shelfSort + 1) % 3) } },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    T(sortNames[settings.shelfSort], 14.sp, c.ink, FontWeight.SemiBold)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Ico.SWAP, size = 16.dp)
                                }
                                Spacer(Modifier.weight(1f))
                                T("共 ${books.size} 本", 13.sp, c.text2)
                            }
                        }
                    }
                    items(shown, key = { it.id }) { b ->
                        BookItem(b, onClick = { Nav.openBook(b.id) }, onLong = { selected = b.id })
                    }
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            T("没有找到相关的书", 14.sp, c.text2, modifier = Modifier.padding(top = 40.dp), align = TextAlign.Center)
                        }
                    }
                }
            }
        }
        BookSheet(selected, onDismiss = { selected = null }, onEdit = {
            editing = it
            selected = null
        }, onDelete = {
            deleting = it
            selected = null
        })
        BookInfoSheet(editing) { editing = null }
        val del = deleting?.let { Library.get(it) }
        ConfirmDialog(
            visible = deleting != null,
            title = "从书架删除？",
            message = "《${del?.title.orEmpty()}》的进度和书签会一起删除，读过的时长仍保留在统计里",
            confirm = "删除",
            ico = Ico.TRASH,
            onConfirm = {
                deleting?.let { Library.remove(it) }
                deleting = null
                Toast.show("已删除")
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = K.c
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(c.chip, SmoothShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ico.SEARCH, size = 18.dp, color = c.text2)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) T("搜索书名或作者", 15.sp, c.text3)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = c.ink, fontSize = 15.sp),
                cursorBrush = SolidColor(c.ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) IconBtn(Ico.CLOSE, size = 16.dp, color = c.text2) { onChange("") }
    }
}

@Composable
private fun BookItem(b: Book, onClick: () -> Unit, onLong: () -> Unit) {
    val c = K.c
    Column(Modifier.press(scaleTo = 0.97f, onLongClick = onLong, onClick = onClick)) {
        BookCover(b, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        T(b.title, 14.sp, c.ink, FontWeight.Medium, maxLines = 2, lineHeight = 19.sp)
        Spacer(Modifier.height(3.dp))
        T(progressLabel(b), 12.sp, if (b.finished) c.accent else c.text2)
    }
}

private fun progressLabel(b: Book): String = when {
    b.finished -> "已读完"
    b.progress <= 0f && b.lastReadAt == 0L -> "未读"
    else -> Fmt.percent(b.progress)
}

@Composable
private fun EmptyShelf(modifier: Modifier) {
    val c = K.c
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CatMascot(size = 170.dp, pose = CatPose.LOAF, sleep = true)
        Spacer(Modifier.height(14.dp))
        T("书架空空的", 20.sp, c.ink, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        T("导入手机里的 TXT、EPUB 小说\n也可以在文件管理器里选择“用空叶阅读打开”", 13.sp, c.text2, align = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(40.dp))
        InkButton("导入书籍", Modifier.size(width = 200.dp, height = 58.dp)) { Nav.pickFiles() }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BookSheet(id: String?, onDismiss: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit) {
    val c = K.c
    val books by Library.books.collectAsState()
    val last = remember { arrayOfNulls<Book>(1) }
    id?.let { i -> books.firstOrNull { it.id == i } }?.let { last[0] = it }
    Sheet(visible = id != null, onDismiss = onDismiss) {
        val b = last[0] ?: return@Sheet
        Row {
            BookCover(b, Modifier.width(76.dp), 11.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                T(b.title, 18.sp, c.ink, FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                T(b.author.ifEmpty { "佚名" }, 13.sp, c.text2, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                T("${b.format.uppercase()} · ${b.chapterCount} 章 · ${Fmt.words(b.chars)}", 12.sp, c.text2)
                Spacer(Modifier.height(3.dp))
                val added = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(b.addedAt))
                T("已读 ${Fmt.cn(b.readMillis)} · $added 加入", 12.sp, c.text2)
            }
        }
        if (b.tags.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Tags(b.tags)
        }
        if (b.intro.isNotEmpty()) {
            // 简介默认露三行，点一下展开
            var open by remember(b.id) { mutableStateOf(false) }
            Spacer(Modifier.height(if (b.tags.isEmpty()) 14.dp else 10.dp))
            T(b.intro, 13.sp, c.text2, modifier = Modifier.press(scaleTo = 1f) { open = !open }, maxLines = if (open) 14 else 3, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(22.dp))
        InkButton(if (b.progress > 0f) "继续阅读" else "开始阅读", Modifier.fillMaxWidth(), height = 54.dp) {
            onDismiss()
            Nav.openBook(b.id)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            GhostButton(if (b.finished) "标为在读" else "标为读完", Ico.CHECK) {
                Library.update(b.id) { it.copy(finished = !it.finished) }
                onDismiss()
            }
            GhostButton("从头读", Ico.REFRESH) {
                Library.update(b.id) { it.copy(chapter = 0, offset = 0, progress = 0f, finished = false) }
                onDismiss()
                Toast.show("进度已清零")
            }
            GhostButton("编辑", Ico.EDIT) { onEdit(b.id) }
            GhostButton("删除", Ico.TRASH, color = c.accent) { onDelete(b.id) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(tags: List<String>) {
    val c = K.c
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in tags) {
            T(t, 12.sp, c.ink, modifier = Modifier.background(c.chip, SmoothShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp), maxLines = 1)
        }
    }
}

/** 改书名、作者、简介、标签；TXT 还可以按新规则重新分章 */
@Composable
private fun BookInfoSheet(id: String?, onDismiss: () -> Unit) {
    val c = K.c
    val scope = rememberCoroutineScope()
    val last = remember { arrayOfNulls<Book>(1) }
    id?.let { Library.get(it) }?.let { last[0] = it }
    var redetecting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Sheet(visible = id != null, onDismiss = onDismiss) {
        val b = last[0] ?: return@Sheet
        val title = remember(b.id) { TextFieldState(b.title) }
        val author = remember(b.id) { TextFieldState(b.author) }
        val tags = remember(b.id) { TextFieldState(b.tags.joinToString(" ")) }
        val intro = remember(b.id) { TextFieldState(b.intro) }
        Column(Modifier.imePadding()) {
            T("编辑书籍信息", 18.sp, c.ink, FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            InfoField("书名", title)
            Spacer(Modifier.height(10.dp))
            InfoField("作者", author)
            Spacer(Modifier.height(10.dp))
            InfoField("标签", tags, hint = "用空格分开，比如：都市 日常")
            Spacer(Modifier.height(10.dp))
            InfoField("简介", intro, single = false)
            Spacer(Modifier.height(20.dp))
            InkButton("保存", Modifier.fillMaxWidth(), height = 52.dp) {
                Library.updateInfo(
                    b.id, title.text.toString(), author.text.toString(), intro.text.toString(),
                    tags.text.split(Regex("[\\s,，、]+")).filter { it.isNotEmpty() },
                )
                onDismiss()
                Toast.show("已保存")
            }
            if (b.format == "txt") {
                Spacer(Modifier.height(4.dp))
                GhostButton(if (busy) "正在识别…" else "重新识别章节", Ico.TOC, Modifier.align(Alignment.CenterHorizontally)) {
                    if (!busy) redetecting = true
                }
            }
        }
    }
    val book = last[0]
    ConfirmDialog(
        visible = redetecting,
        title = "重新识别章节？",
        message = "按新的规则把《${book?.title.orEmpty()}》重新分章，读到的位置和书签会尽量保留",
        confirm = "重新识别",
        ico = Ico.TOC,
        onConfirm = {
            redetecting = false
            val bid = book?.id ?: return@ConfirmDialog
            busy = true
            scope.launch {
                val n = withContext(Dispatchers.IO) { Library.redetect(bid) }
                busy = false
                Toast.show(if (n < 0) "识别失败" else "识别出 $n 章")
            }
        },
        onDismiss = { redetecting = false },
    )
}

@Composable
private fun InfoField(label: String, state: TextFieldState, hint: String = "", single: Boolean = true) {
    val c = K.c
    Row(verticalAlignment = if (single) Alignment.CenterVertically else Alignment.Top) {
        T(label, 13.sp, c.text2, modifier = Modifier.width(40.dp).padding(top = if (single) 0.dp else 11.dp))
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .background(c.chip, SmoothShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (state.text.isEmpty() && hint.isNotEmpty()) T(hint, 15.sp, c.text3, maxLines = 1)
            BasicTextField(
                state,
                Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = c.ink, fontSize = 15.sp, lineHeight = 22.sp),
                lineLimits = if (single) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6),
                cursorBrush = SolidColor(c.ink),
            )
        }
    }
}
