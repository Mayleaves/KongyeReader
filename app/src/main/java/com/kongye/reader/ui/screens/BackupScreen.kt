package com.kongye.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.KongyeReaderApp
import com.kongye.reader.data.Backup
import com.kongye.reader.data.Profiles
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.ConfirmDialog
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.Group
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.SwitchRow
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备份与恢复：本地 zip（系统文件选择器，存哪都行）和 WebDAV 云备份 */
@Composable
fun BackupScreen() {
    val c = K.c
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf("") }
    var cloud by remember { mutableStateOf(Backup.cloud()) }
    var restoreFile by remember { mutableStateOf<Uri?>(null) }
    var restoreCloud by remember { mutableStateOf(false) }
    val url = rememberTextFieldState(cloud.url)
    val user = rememberTextFieldState(cloud.user)
    val pass = rememberTextFieldState(cloud.password)

    fun run(label: String, done: String, work: () -> Unit) {
        if (busy.isNotEmpty()) return
        busy = label
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { work() }.exceptionOrNull() }
            busy = ""
            cloud = Backup.cloud()
            Toast.show(err?.message ?: done)
        }
    }

    fun current() = cloud.copy(url = url.text.toString().trim(), user = user.text.toString().trim(), password = pass.text.toString())

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        run("正在备份…", "备份好了") {
            KongyeReaderApp.app.contentResolver.openOutputStream(uri)?.use { Backup.write(it) } ?: error("写不进这个位置")
            Profiles.count("backup")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreFile = uri }

    Box(Modifier.fillMaxSize().background(c.bg2)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("备份与恢复", onBack = { Nav.pop() })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                T("备份包含书（正文、目录、封面）、书架和进度、阅读记录、设置、叶子和装扮；词典和字体不用备份", 12.sp, c.text2, modifier = Modifier.padding(horizontal = 8.dp), lineHeight = 18.sp)
                Spacer(Modifier.height(16.dp))
                Group("本地备份") {
                    Column(Modifier.padding(18.dp)) {
                        Row {
                            InkButton("备份到文件", Modifier.weight(1f), height = 50.dp) { save.launch(Backup.fileName()) }
                            Spacer(Modifier.width(10.dp))
                            InkButton("从文件恢复", Modifier.weight(1f), height = 50.dp, color = c.chip, textColor = c.ink) {
                                open.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Group("云备份（WebDAV）") {
                    Column(Modifier.padding(18.dp)) {
                        Field("地址", url)
                        Spacer(Modifier.height(10.dp))
                        Field("账号", user)
                        Spacer(Modifier.height(10.dp))
                        Field("密码", pass, secret = true)
                        Spacer(Modifier.height(14.dp))
                        Row {
                            InkButton("立即备份", Modifier.weight(1f), height = 50.dp) {
                                val cfg = current()
                                Backup.saveCloud(cfg)
                                run("正在上传…", "已备份到云端") { Backup.upload(cfg) }
                            }
                            Spacer(Modifier.width(10.dp))
                            InkButton("测试连接", Modifier.weight(1f), height = 50.dp, color = c.chip, textColor = c.ink) {
                                val cfg = current()
                                Backup.saveCloud(cfg)
                                run("正在连接…", "连接成功") { Backup.test(cfg) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        GhostButton("从云端恢复", Ico.CLOUD, Modifier.align(Alignment.CenterHorizontally)) { restoreCloud = true }
                        val fmt = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US) }
                        val status = when {
                            cloud.lastError.isNotEmpty() -> "上次自动备份没成功：${cloud.lastError}"
                            cloud.lastBackup > 0 -> "上次备份：${fmt.format(Date(cloud.lastBackup))}"
                            else -> "还没有备份过"
                        }
                        T(status, 12.sp, if (cloud.lastError.isNotEmpty()) c.accent else c.text3, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    SwitchRow("每天自动备份", cloud.auto, "离开应用时，距上次超过一天就传一份") { v ->
                        cloud = current().copy(auto = v)
                        Backup.saveCloud(cloud)
                    }
                }
                Spacer(Modifier.height(36.dp))
            }
        }
        if (busy.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 30.dp)
                    .background(c.ink, SmoothShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { T(busy, 14.sp, c.onInk) }
        }
        ConfirmDialog(
            visible = restoreFile != null || restoreCloud,
            title = "用备份覆盖现在的数据？",
            message = "书架、进度、阅读记录、叶子和装扮都会换成备份里的样子，现在手机上的会被替换",
            confirm = "恢复",
            ico = Ico.REFRESH,
            onConfirm = {
                val uri = restoreFile
                val fromCloud = restoreCloud
                restoreFile = null
                restoreCloud = false
                if (uri != null) run("正在恢复…", "恢复好了") { Backup.restore(uri) }
                else if (fromCloud) {
                    val cfg = current()
                    run("正在下载…", "已从云端恢复") { Backup.download(cfg) }
                }
            },
            onDismiss = {
                restoreFile = null
                restoreCloud = false
            },
        )
    }
}

@Composable
private fun Field(label: String, state: TextFieldState, secret: Boolean = false) {
    val c = K.c
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(label, 13.sp, c.text2, modifier = Modifier.width(40.dp))
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .background(c.chip, SmoothShape(14.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val style = TextStyle(color = c.ink, fontSize = 15.sp)
            if (secret) {
                BasicSecureTextField(state, Modifier.fillMaxWidth(), textStyle = style, cursorBrush = SolidColor(c.ink))
            } else {
                BasicTextField(state, Modifier.fillMaxWidth(), textStyle = style, lineLimits = TextFieldLineLimits.SingleLine, cursorBrush = SolidColor(c.ink))
            }
        }
    }
}
