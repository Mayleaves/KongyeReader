package com.kongye.reader.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Shop
import com.kongye.reader.data.ReportStyles
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.components.*
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ReadingReportSheet(report: ReadingReportData, initialTheme: String? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by Profiles.state.collectAsState()
    val animal = profile.homeAnimal.takeIf { profile.has(it) && Shop.wearer(it) != null } ?: Shop.MASCOT
    var trialTheme by rememberSaveable { mutableStateOf(initialTheme) }
    val theme = trialTheme?.takeIf { ReportStyles.get(it) != null } ?: ReportStyles.selected(profile)
    val canUse = ReportStyles.available(profile, theme)
    var showBooks by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val bitmap by produceState<Bitmap?>(null, report, animal, showBooks, theme, profile.icon) {
        value = null
        error = null
        try {
            value = ReadingReport.render(context.applicationContext, report, animal, showBooks, theme, profile.icon in Shop.minimalIcons)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "图片生成失败，请关闭后重试"
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val image = saveBitmap
        saveBitmap = null
        if (uri != null && image != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("无法打开保存位置")
                    output.use { ReadingReport.write(image, it) }
                }
                Toast.show("阅读报告已保存")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.show("保存失败，请重试")
            } finally {
                busy = false
            }
        }
    }
    val previewHeight = (LocalConfiguration.current.screenHeightDp * 0.47f).dp.coerceIn(140.dp, 450.dp)
    Sheet(true, onDismiss, scrollContent = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("分享阅读报告", 20.sp, K.c.ink, FontWeight.Bold, Modifier.weight(1f))
            IconBtn(Ico.BAG, Modifier.semantics { contentDescription = "主题商城" }) { onDismiss(); Nav.shop(4) }
            IconBtn(Ico.CLOSE, onClick = onDismiss)
        }
        T("${report.title} · ${report.period}", 12.sp, K.c.text2)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image != null) Image(image.asImageBitmap(), "阅读报告预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else T(error ?: "正在生成…", 14.sp, K.c.text2)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().background(K.c.chip, SmoothShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T("主题", 14.sp, K.c.ink, modifier = Modifier.weight(1f))
                if (!canUse) T("试看 · ${ReportStyles.get(theme)?.name}", 12.sp, K.c.accent, FontWeight.Medium)
            }
            ReportThemeStrip(profile, theme) { style ->
                if (ReportStyles.available(profile, style.id)) {
                    trialTheme = null
                    Profiles.update { it.copy(reportStyle = style.id) }
                } else trialTheme = style.id
            }
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(K.c.line))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T("显示在读书名", 14.sp, K.c.ink, modifier = Modifier.weight(1f))
                InkSwitch(showBooks) { showBooks = it }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InkButton("保存图片", Modifier.weight(1f), height = 48.dp, color = K.c.chip, textColor = K.c.ink) {
                val image = bitmap
                if (!canUse) Toast.show("解锁后即可保存或分享该主题")
                else if (image != null && !busy) {
                    saveBitmap = image
                    save.launch("空叶阅读-${report.title}.png")
                }
            }
            InkButton(if (busy) "处理中…" else "分享海报", Modifier.weight(1f), height = 48.dp) {
                val image = bitmap
                if (!canUse) Toast.show("解锁后即可保存或分享该主题")
                else if (image != null && !busy) scope.launch {
                    busy = true
                    try {
                        val intent = withContext(Dispatchers.IO) { ReadingReport.shareIntent(context, image) }
                        context.startActivity(Intent.createChooser(intent, "分享阅读报告"))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Toast.show("无法分享，请重试或保存图片")
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}
