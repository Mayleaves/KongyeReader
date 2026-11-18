package com.kongye.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Backup
import com.kongye.reader.data.CloudBackup
import com.kongye.reader.data.CloudConfig
import com.kongye.reader.ui.components.*
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One list for choosing a restore date and explicitly selecting cloud files for deletion. */
@Composable
internal fun CloudBackupsSheet(config: CloudConfig, startDeleting: Boolean, onDismiss: () -> Unit, onRestored: () -> Unit) {
    val c=K.c
    val scope=rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<CloudBackup>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var incomplete by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(startDeleting) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var working by remember { mutableStateOf("") }
    var restore by remember { mutableStateOf<CloudBackup?>(null) }
    var remove by remember { mutableStateOf(emptyList<CloudBackup>()) }
    val blocked=loading || working.isNotEmpty()
    val height=(LocalConfiguration.current.screenHeightDp.dp-280.dp).coerceIn(100.dp,440.dp)
    val fmt=remember { SimpleDateFormat("yyyy.MM.dd HH:mm",Locale.US) }
    LaunchedEffect(config,refresh) {
        loading=true;error=null
        val result=withContext(Dispatchers.IO) { runCatching { Backup.listCloud(config) } }
        result.onSuccess { listing ->
            rows=listing.backups;incomplete=listing.incomplete;selected=selected.intersect(rows.map { it.url }.toSet())
        }.onFailure { error=it.message ?: "读取云端备份失败" }
        loading=false
    }
    Sheet(true,onDismiss={ if(working.isEmpty()) onDismiss() }) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            T("云端备份",20.sp,c.ink,FontWeight.Bold,Modifier.weight(1f))
            IconBtn(Ico.REFRESH,Modifier.semantics { contentDescription="刷新云端备份" }) { if(!blocked) refresh++ }
            if (deleting) {
                GhostButton("完成", color = c.ink) {
                    if (!blocked) {
                        deleting = false
                        selected = emptySet()
                    }
                }
            } else {
                IconBtn(Ico.TRASH, Modifier.semantics { contentDescription = "选择删除" }, color = c.accent) {
                    if (!blocked) deleting = true
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            working.isNotEmpty() -> T(working,13.sp,c.text2)
            loading -> T("正在读取云端备份…",13.sp,c.text2)
            error!=null -> Row(verticalAlignment=Alignment.CenterVertically) {
                T(error.orEmpty(),13.sp,c.accent,modifier=Modifier.weight(1f))
                GhostButton("重试") { if(!blocked) refresh++ }
            }
            else -> T("共 ${rows.size} 份备份",12.sp,c.text2)
        }
        if(incomplete && error==null && !loading) T("部分目录未能读取，可刷新重试",12.sp,c.accent,modifier=Modifier.padding(top=6.dp))
        Spacer(Modifier.height(12.dp))
        if(rows.isEmpty()) Box(Modifier.fillMaxWidth().height(120.dp),contentAlignment=Alignment.Center) {
            if(!loading && error==null) T("云端还没有备份",14.sp,c.text2)
        } else LazyColumn(Modifier.fillMaxWidth().heightIn(max=height),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(rows,key={ it.url }) { item ->
                val checked=item.url in selected
                Row(
                    Modifier.fillMaxWidth().background(c.chip,SmoothShape(14.dp))
                        .semantics { if(deleting) { this.selected=checked;contentDescription="选择备份 ${item.title} ${item.location}" } }
                        .press {
                            if(!blocked) {
                                if(deleting) selected=if(checked) selected-item.url else selected+item.url
                                else restore=item
                            }
                        }.padding(14.dp),
                    verticalAlignment=Alignment.CenterVertically,
                ) {
                    if(deleting) {
                        Box(Modifier.size(24.dp).background(if(checked)c.ink else c.card,SmoothShape(6.dp))
                            .border(1.5.dp,if(checked)c.ink else c.text3,SmoothShape(6.dp)),contentAlignment=Alignment.Center) {
                            if(checked) Icon(Ico.CHECK,size=17.dp,color=c.onInk)
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        T(item.title,15.sp,c.ink,FontWeight.SemiBold)
                        val stamp=if(item.modified>0) fmt.format(Date(item.modified)) else ""
                        val size=if(item.size>0) String.format(Locale.US,"%.1f MB",item.size/1048576.0) else ""
                        T(listOf(stamp,size,item.location).filter { it.isNotEmpty() }.joinToString(" · "),11.sp,c.text2,modifier=Modifier.padding(top=5.dp))
                    }
                    if(!deleting) Icon(Ico.CHEVRON_RIGHT,size=18.dp,color=c.text2)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if(deleting) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            GhostButton(if(rows.isNotEmpty() && selected.size==rows.size) "取消全选" else "全选",color=c.ink) {
                if(!blocked) selected=if(selected.size==rows.size) emptySet() else rows.map { it.url }.toSet()
            }
            Spacer(Modifier.weight(1f))
            InkButton("删除所选（${selected.size}）",height=46.dp,color=if(selected.isEmpty())c.chip else c.accent,textColor=if(selected.isEmpty())c.text2 else c.onInk,
                modifier=Modifier.widthIn(min=160.dp)) {
                if(!blocked && selected.isNotEmpty()) remove=rows.filter { it.url in selected }
            }
        } else T("点选日期恢复备份，或点右上角垃圾桶选择删除",12.sp,c.text2)
    }
    ConfirmDialog(
        visible=restore!=null,
        title="恢复 ${restore?.title.orEmpty()}？",
        message="书籍、书架、进度、阅读记录、设置、叶子和装扮将换成这份云端备份中的内容。",
        confirm="拉取并恢复",ico=Ico.REFRESH,
        onConfirm={
            val chosen=restore
            restore=null
            if(chosen!=null && !blocked) {
                working="正在下载并恢复…"
                scope.launch {
                    val result=withContext(Dispatchers.IO) { runCatching { Backup.download(config,chosen) } }
                    working=""
                    result.onSuccess { Toast.show("云端备份已恢复到本机");onRestored() }
                        .onFailure { error=it.message ?: "恢复失败";Toast.show(error.orEmpty()) }
                }
            }
        },onDismiss={ restore=null },
    )
    ConfirmDialog(
        visible=remove.isNotEmpty(),
        title="删除所选的 ${remove.size} 份云备份？",
        message=remove.take(5).joinToString("\n") { "${it.title} · ${it.location}" } +
            (if(remove.size>5) "\n等 ${remove.size} 份备份" else "") + "\n删除后无法从云端找回，不影响手机里的数据。",
        confirm="删除所选备份",ico=Ico.TRASH,
        onConfirm={
            val chosen=remove
            remove=emptyList()
            if(chosen.isNotEmpty() && !blocked) {
                working="正在删除 ${chosen.size} 份云备份…"
                scope.launch {
                    val result=withContext(Dispatchers.IO) { runCatching { Backup.deleteCloud(config,chosen) } }
                    working=""
                    result.onSuccess { outcome ->
                        rows=rows.filter { it.url !in outcome.deleted };selected=selected-outcome.deleted
                        error=if(outcome.failed.isEmpty()) null else "${outcome.failed.size} 份未能删除，可重新选择后重试"
                        Toast.show("已删除 ${outcome.deleted.size} 份云备份" + if(outcome.failed.isNotEmpty()) "，${outcome.failed.size} 份失败" else "")
                    }.onFailure { error=it.message ?: "删除失败" }
                }
            }
        },onDismiss={ remove=emptyList() },
    )
}
