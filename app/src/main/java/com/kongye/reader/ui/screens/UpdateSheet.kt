package com.kongye.reader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Updates
import com.kongye.reader.data.Updates.Phase
import com.kongye.reader.ui.components.CatMascot
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.Sheet
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.theme.K
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 发现新版本：版本号、大小、日期，Release 说明（标题、条目），然后在 App 里下载、交给系统安装。
 * 下载在后台接着跑，收起来也不停；设置里「检查更新」那一行显示进度，点一下再打开。
 */
@Composable
fun UpdateSheet(visible: Boolean, state: Updates.State, onDismiss: () -> Unit) {
    val c = K.c
    val context = LocalContext.current
    // 收起的动画里还要画上一次的内容
    val last = remember { arrayOfNulls<Updates.State>(1) }
    if (visible) last[0] = state
    Sheet(visible = visible, onDismiss = onDismiss) {
        val s = (if (visible) state else last[0]) ?: return@Sheet
        val rel = s.release ?: return@Sheet
        val apk = rel.apk
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).background(c.chip, SmoothShape(20.dp)), contentAlignment = Alignment.Center) {
                CatMascot(size = 52.dp, shadow = false, accent = true)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                T("发现新版本", 20.sp, c.ink, FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(Updates.current, c.chip, c.text2)
                    Icon(Ico.CHEVRON_RIGHT, size = 14.dp, color = c.text3, modifier = Modifier.padding(horizontal = 4.dp))
                    Chip(rel.version, c.ink, c.onInk)
                    val meta = listOfNotNull(apk?.let { mb(it.size) }, date(rel.published)).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        T(meta, 12.sp, c.text3, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .background(c.chip, SmoothShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) { Notes(rel.body) }
        if (Updates.personalBuild()) {
            Spacer(Modifier.height(10.dp))
            T("这台手机装的是自用版。发行包是公开版，不带两款楷体字体和小马宝莉，装上后这两样会没有", 12.sp, c.accent, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(18.dp))
        when (s.phase) {
            Phase.DOWNLOADING -> {
                val frac by animateFloatAsState(if (s.total > 0) (s.done.toFloat() / s.total).coerceIn(0f, 1f) else 0f, tween(200), label = "download")
                Box(Modifier.fillMaxWidth().height(8.dp).background(c.chip, CircleShape)) {
                    Box(Modifier.fillMaxWidth(frac).height(8.dp).background(c.ink, CircleShape))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    T("正在下载 ${(frac * 100).toInt()}%", 13.sp, c.text2)
                    Spacer(Modifier.weight(1f))
                    T("${mb(s.done)} / ${mb(s.total)}", 13.sp, c.text2)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.align(Alignment.CenterHorizontally)) {
                    GhostButton("取消下载") { Updates.cancel() }
                    GhostButton("收起") { onDismiss() }
                }
            }
            Phase.READY -> {
                InkButton("安装 ${rel.version}", Modifier.fillMaxWidth(), height = 52.dp) {
                    if (!Updates.install(context)) Toast.show("在系统设置里允许空叶阅读安装应用，回来再点安装")
                }
                Spacer(Modifier.height(4.dp))
                GhostButton("以后再说", modifier = Modifier.align(Alignment.CenterHorizontally)) { onDismiss() }
            }
            else -> {
                if (s.phase == Phase.FAILED && s.error != null) {
                    T(s.error, 13.sp, c.accent, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                }
                if (apk != null) {
                    InkButton(if (s.phase == Phase.FAILED) "重新下载" else "下载并安装 · ${mb(apk.size)}", Modifier.fillMaxWidth(), height = 52.dp) {
                        Updates.download()
                    }
                } else {
                    InkButton("这一版没有附安装包，去 GitHub 看看", Modifier.fillMaxWidth(), height = 52.dp) {
                        Updates.open(context, rel.url.ifEmpty { Updates.GITHUB })
                    }
                }
                Spacer(Modifier.height(4.dp))
                GhostButton("以后再说", modifier = Modifier.align(Alignment.CenterHorizontally)) { onDismiss() }
            }
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Box(Modifier.background(bg, SmoothShape(9.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        T(text, 13.sp, fg, FontWeight.SemiBold)
    }
}

/** Release 说明：### 是小标题，- 是条目，别的是一段灰字；大标题（## 空叶阅读 x）上面已经有了，不再写 */
@Composable
private fun Notes(body: String) {
    val c = K.c
    var first = true
    for (raw in body.replace("\r", "").lines()) {
        val line = raw.trim().replace("**", "")
        when {
            line.isEmpty() || line.startsWith("# ") || line.startsWith("## ") -> continue
            line.startsWith("###") -> {
                if (!first) Spacer(Modifier.height(12.dp))
                T(line.trimStart('#').trim(), 14.sp, c.ink, FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
            }
            line.startsWith("- ") || line.startsWith("* ") -> Row(Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.padding(top = 8.dp).size(4.dp).background(c.text3, CircleShape))
                Spacer(Modifier.width(8.dp))
                T(line.drop(2), 13.sp, c.text2, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
            else -> {
                if (!first) Spacer(Modifier.height(12.dp))
                T(line, 12.sp, c.text3, lineHeight = 18.sp)
            }
        }
        first = false
    }
}

private fun mb(bytes: Long) = String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)

private fun date(iso: String): String? = runCatching {
    val d = Instant.parse(iso).atZone(ZoneId.systemDefault())
    "${d.monthValue} 月 ${d.dayOfMonth} 日"
}.getOrNull()
