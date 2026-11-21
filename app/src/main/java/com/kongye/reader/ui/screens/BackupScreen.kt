package com.kongye.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.KongyeReaderApp
import com.kongye.reader.data.Backup
import com.kongye.reader.data.CloudConfig
import com.kongye.reader.data.Profiles
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.ConfirmDialog
import com.kongye.reader.ui.components.Group
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.ValueRow
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.SwitchRow
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.theme.K
import com.kongye.reader.ui.theme.scrollContentWidth
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
    var browser by remember { mutableStateOf<CloudConfig?>(null) }
    var browserDeleting by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
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

    fun browseCloud(deleting: Boolean) {
        if(busy.isNotEmpty()) return
        val cfg=current()
        if(cfg.url.isBlank()) { Toast.show("先填 WebDAV 地址");return }
        keyboard?.hide()
        Backup.saveCloud(cfg)
        cloud=cfg
        browserDeleting=deleting
        browser=cfg
    }

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
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = scrollContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Group("本地备份") {
                    Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        BackupAction("备份到文件",Ico.UPLOAD,Modifier.weight(1f),enabled=busy.isEmpty()) {
                            save.launch(Backup.fileName())
                        }
                        BackupAction("从文件恢复",Ico.DOWNLOAD,Modifier.weight(1f),enabled=busy.isEmpty()) {
                            open.launch(arrayOf("application/zip","application/octet-stream"))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Group("云端备份") {
                    Column(Modifier.padding(horizontal=18.dp,vertical=12.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            T("WebDAV",14.sp,c.ink,FontWeight.SemiBold,Modifier.weight(1f))
                            T("测试连接",12.sp,c.text2,modifier=Modifier.press(enabled=busy.isEmpty()) {
                                keyboard?.hide()
                                val cfg=current()
                                Backup.saveCloud(cfg)
                                run("正在连接…","连接成功") { Backup.test(cfg) }
                            }.padding(vertical=10.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Field("地址",url)
                        Spacer(Modifier.height(10.dp))
                        Field("账号",user)
                        Spacer(Modifier.height(10.dp))
                        Field("密码",pass,secret=true)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            BackupAction("备份到云端",Ico.UPLOAD,Modifier.weight(1f),primary=true,enabled=busy.isEmpty()) {
                                keyboard?.hide()
                                val cfg=current()
                                Backup.saveCloud(cfg)
                                run("正在上传…","已备份到云端") { Backup.upload(cfg) }
                            }
                            BackupAction("从云端恢复",Ico.DOWNLOAD,Modifier.weight(1f),enabled=busy.isEmpty()) {
                                browseCloud(deleting=false)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        val fmt=remember { SimpleDateFormat("yyyy.MM.dd HH:mm",Locale.US) }
                        val status=when {
                            cloud.lastError.isNotEmpty() -> "上次自动备份失败：${cloud.lastError}"
                            cloud.lastBackup>0 -> "上次备份 ${fmt.format(Date(cloud.lastBackup))}"
                            else -> "还没有云端备份"
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(Ico.CLOCK,size=13.dp,color=if(cloud.lastError.isNotEmpty())c.accent else c.text2)
                            Spacer(Modifier.width(6.dp))
                            T(status,11.sp,if(cloud.lastError.isNotEmpty())c.accent else c.text2,lineHeight=16.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Group(null) {
                    Layout(
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            ValueRow("管理云备份", "历史与删除", chevron = true) { browseCloud(deleting = false) }
                            Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(c.line))
                            SwitchRow("每天自动备份", cloud.auto, "离开应用时，距上次超过一天就传一份") { enabled ->
                                cloud = current().copy(auto = enabled)
                                Backup.saveCloud(cloud)
                            }
                        },
                    ) { (manage, divider, auto), constraints ->
                        val width = constraints.maxWidth
                        // 按较高一行测量，系统字号放大时两行仍等高。
                        val rowHeight = maxOf(manage.minIntrinsicHeight(width), auto.minIntrinsicHeight(width))
                        val rowConstraints = Constraints.fixed(width, rowHeight)
                        val manageRow = manage.measure(rowConstraints)
                        val line = divider.measure(Constraints(minWidth = width, maxWidth = width))
                        val autoRow = auto.measure(rowConstraints)
                        layout(width, rowHeight * 2 + line.height) {
                            manageRow.placeRelative(0, 0)
                            line.placeRelative(0, rowHeight)
                            autoRow.placeRelative(0, rowHeight + line.height)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                T("备份包含书籍、书架与进度、阅读记录、设置、叶子和装扮；词典和字体无需备份。",11.sp,c.text2,
                    modifier=Modifier.padding(horizontal=8.dp),lineHeight=17.sp)
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
            visible = restoreFile != null,
            title = "用备份覆盖现在的数据？",
            message = "书籍、书架、进度、阅读记录、设置、叶子和装扮都会换成备份里的内容，现在手机上的会被替换",
            confirm = "恢复",
            ico = Ico.REFRESH,
            onConfirm = {
                val uri = restoreFile
                restoreFile = null
                if (uri != null) run("正在恢复…", "恢复好了") { Backup.restore(uri) }
            },
            onDismiss = {
                restoreFile = null
            },
        )
        browser?.let { config ->
            CloudBackupsSheet(config,browserDeleting,onDismiss={ browser=null },onRestored={ cloud=Backup.cloud();browser=null })
        }
    }
}

@Composable
private fun BackupAction(title: String,icon: Ico,modifier: Modifier=Modifier,primary: Boolean=false,enabled: Boolean=true,onClick: () -> Unit) {
    val c=K.c
    val foreground=if(primary)c.onInk else c.ink
    Column(
        modifier.background(if(primary)c.ink else c.chip,SmoothShape(16.dp))
            .press(enabled=enabled,onClick=onClick).padding(horizontal=8.dp,vertical=16.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon,size=23.dp,color=foreground)
        T(title,14.sp,foreground,FontWeight.SemiBold,maxLines=1)
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
