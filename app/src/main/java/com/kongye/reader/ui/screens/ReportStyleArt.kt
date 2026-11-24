package com.kongye.reader.ui.screens

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Profile
import com.kongye.reader.data.ReportStyle
import com.kongye.reader.data.ReportStyles
import com.kongye.reader.data.Shop
import com.kongye.reader.ui.components.*
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.CancellationException

@Composable
internal fun ReportStylePreview(id: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(id) { context.assets.open("reports/$id.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }
    Image(image, "${ReportStyles.get(id)?.name}阅读报告示例", modifier, contentScale = ContentScale.Fit)
}

/** 商城详情只放大展示报告的时长与首页动物区域。 */
@Composable
internal fun ReportSummaryPreview(id: String, profile: Profile, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val animal = profile.homeAnimal.takeIf { profile.has(it) && Shop.wearer(it) != null } ?: Shop.MASCOT
    val image by produceState<Bitmap?>(null, id, animal) {
        value = null
        try {
            val full = ReadingReport.render(context, ReadingReportData(
                title = "我的阅读周报", period = "阅读报告示例", millis = 141 * 60_000L,
                days = 2, finished = 0, books = emptyList(), values = listOf(72 * 60_000L, 69 * 60_000L), streak = 5,
            ), animal, false, id)
            try { value = Bitmap.createBitmap(full, 100, 290, 880, 396) }
            finally { full.recycle() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { value = null }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it.asImageBitmap(), "${ReportStyles.get(id)?.name}阅读报告局部预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: T("正在生成预览…", 13.sp, K.c.text2)
    }
}

@Composable
internal fun ReportThemeStrip(profile: Profile, selected: String, onSelect: (ReportStyle) -> Unit) {
    Row(Modifier.fillMaxWidth().background(K.c.chip, SmoothShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (style in ReportStyles.all) {
            val owned = ReportStyles.available(profile, style.id)
            val active = selected == style.id
            Column(
                Modifier.weight(1f)
                    .then(if (active) Modifier.shadow(2.dp, SmoothShape(14.dp), ambientColor = Color.Black.copy(alpha = .035f), spotColor = Color.Black.copy(alpha = .035f)).background(K.c.card, SmoothShape(14.dp)) else Modifier)
                    .press { onSelect(style) }.padding(horizontal = 4.dp, vertical = 8.dp)
                    .semantics { contentDescription = "报告主题：${style.name}，${if (owned) "已解锁" else "未解锁"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(22.dp).clip(SmoothShape(5.dp))) {
                    val brush = when (style.id) {
                        "light" -> Brush.linearGradient(0f to Color.White, .52f to Color.White, .52f to Color(0xFF1E1E22), 1f to Color(0xFF1E1E22), start = Offset.Zero, end = Offset(size.width, size.height))
                        "sunset" -> Brush.radialGradient(0f to Color(0xFFFF8A7A), .68f to Color(0xFFF9F8F4), 1f to Color(0xFFF9F8F4), center = Offset(size.width * .32f, size.height * .32f), radius = size.width * .96f)
                        "gold" -> Brush.linearGradient(0f to Color(0xFF131217), .5f to Color(0xFF131217), .5f to Color(0xFFF6B525), 1f to Color(0xFFF6B525), start = Offset.Zero, end = Offset(size.width, size.height))
                        "neon" -> Brush.linearGradient(.3f to Color(0xFF121020), .65f to Color(0xFF36BCC7), 1f to Color(0xFFFA2C7E), start = Offset.Zero, end = Offset(size.width, size.height))
                        else -> Brush.linearGradient(0f to Color(0xFFF3EFE6), .52f to Color(0xFFF3EFE6), .52f to Color(0xFFE09224), 1f to Color(0xFFE09224), start = Offset.Zero, end = Offset(size.width, size.height))
                    }
                    drawRect(brush)
                }
                Spacer(Modifier.height(5.dp))
                T(style.name, 10.sp, if (active) K.c.ink else K.c.text2, if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                if (!owned) Icon(Ico.LOCK, size = 8.dp, color = K.c.text3)
            }
        }
    }
}
