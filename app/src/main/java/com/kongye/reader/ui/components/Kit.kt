package com.kongye.reader.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** 连续曲率圆角（类似 iOS 的 squircle），按钮和卡片都用它，比普通圆角更“软” */
class SmoothShape(private val radius: Dp, private val topOnly: Boolean = false) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }
        val e = min(r * 1.28f, min(w, h) / 2f)
        val c = e * 0.34f
        val p = Path().apply {
            moveTo(e, 0f)
            lineTo(w - e, 0f)
            cubicTo(w - c, 0f, w, c, w, e)
            if (topOnly) {
                lineTo(w, h)
                lineTo(0f, h)
            } else {
                lineTo(w, h - e)
                cubicTo(w, h - c, w - c, h, w - e, h)
                lineTo(e, h)
                cubicTo(c, h, 0f, h - c, 0f, h - e)
            }
            lineTo(0f, e)
            cubicTo(0f, c, c, 0f, e, 0f)
            close()
        }
        return Outline.Generic(p)
    }
}

/** 按下时轻微缩小，不用水波纹 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(
    enabled: Boolean = true,
    scaleTo: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, spring(stiffness = 900f), label = "press")
    this
        .graphicsLayer {
            scaleX = s
            scaleY = s
            alpha = if (pressed) 0.86f else 1f
        }
        .combinedClickable(interactionSource = src, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)
}

/** 吃掉点击，防止点穿到下层 */
fun Modifier.blockTouches(): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
}

@Composable
fun T(
    text: String,
    size: TextUnit = 15.sp,
    color: Color = K.c.ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    spacing: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align, lineHeight = lineHeight, letterSpacing = spacing),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun T(
    text: AnnotatedString,
    size: TextUnit = 15.sp,
    color: Color = K.c.ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** “58h25m”：数字大、单位小，参考图统计页的写法 */
fun bigDuration(ms: Long, big: TextUnit, small: TextUnit): AnnotatedString {
    val total = ms / 60_000
    val h = total / 60
    val m = total % 60
    return buildAnnotatedString {
        if (h > 0) {
            withStyle(SpanStyle(fontSize = big)) { append("$h") }
            withStyle(SpanStyle(fontSize = small)) { append("h") }
        }
        if (m > 0 || h == 0L) {
            withStyle(SpanStyle(fontSize = big)) { append("$m") }
            withStyle(SpanStyle(fontSize = small)) { append("m") }
        }
    }
}

@Composable
fun IconBtn(ico: Ico, modifier: Modifier = Modifier, color: Color = K.c.ink, size: Dp = 24.dp, onClick: () -> Unit) {
    Box(modifier.size(44.dp).press(scaleTo = 0.88f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(ico, size = size, color = color)
    }
}

/** 主按钮：粗黑圆角大按钮 */
@Composable
fun InkButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
    color: Color = K.c.ink,
    textColor: Color = K.c.onInk,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .press(onClick = onClick)
            .background(color, SmoothShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 17.sp, textColor, FontWeight.SemiBold)
    }
}

/** 次要操作：灰色图标 + 文字，比如“↻ 重新开始” */
@Composable
fun GhostButton(text: String, ico: Ico? = null, modifier: Modifier = Modifier, color: Color = K.c.text2, onClick: () -> Unit) {
    Row(
        modifier
            .press(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ico != null) {
            Icon(ico, size = 18.dp, color = color)
            Spacer(Modifier.width(6.dp))
        }
        T(text, 15.sp, color, FontWeight.Medium)
    }
}

/** 灰色小胶囊，比如“❤ 好感 -5” */
@Composable
fun Chip(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    ico: Ico? = null,
    icoColor: Color = K.c.ink,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)
            .background(K.c.chip, SmoothShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ico != null) {
            Icon(ico, size = 17.dp, color = icoColor)
            Spacer(Modifier.width(6.dp))
        }
        T(text, 14.sp, K.c.ink, FontWeight.Medium)
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(Ico.CHEVRON_RIGHT, size = 14.dp, color = K.c.text2)
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, ico: Ico? = null, icoColor: Color = K.c.ink, chevron: Boolean = false, onClick: (() -> Unit)? = null) =
    Chip(AnnotatedString(text), modifier, ico, icoColor, chevron, onClick)

/** 分段选择：灰色轨道 + 白色滑块，“日 周 月 年 总” */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, height: Dp = 40.dp, onSelect: (Int) -> Unit) {
    val c = K.c
    BoxWithConstraints(
        modifier
            .height(height)
            .background(c.chip, SmoothShape(14.dp))
            .padding(3.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, tween(220), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .background(c.seg, SmoothShape(11.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, s ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    T(s, 14.sp, if (i == selected) c.ink else c.text2, if (i == selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 滑块：灰色轨道 + 黑色进度 + 圆形把手。step > 0 时吸附到整步，只在数值真的变了时回调
 */
@Composable
fun InkSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    step: Float = 0f,
    dimmed: Boolean = false,
    onChange: (Float) -> Unit,
) {
    val c = K.c
    val span = range.endInclusive - range.start
    fun snap(f: Float): Float {
        var v = range.start + f.coerceIn(0f, 1f) * span
        if (step > 0f) v = range.start + kotlin.math.round((v - range.start) / step) * step
        return v.coerceIn(range.start, range.endInclusive)
    }
    var drag by remember { mutableStateOf<Float?>(null) }
    val latest by androidx.compose.runtime.rememberUpdatedState(value)
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    val frac = drag ?: if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
    Canvas(
        modifier
            .height(34.dp)
            .pointerInput(range, step) {
                detectTapGestures { o ->
                    val v = snap(o.x / size.width)
                    if (v != latest) change(v)
                }
            }
            .pointerInput(range, step) {
                var f = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        f = (o.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        val v = snap(f)
                        if (v != latest) change(v)
                    },
                    onDragEnd = { drag = null },
                    onDragCancel = { drag = null },
                ) { ch, dx ->
                    ch.consume()
                    f = (f + dx / size.width).coerceIn(0f, 1f)
                    drag = f
                    val v = snap(f)
                    if (v != latest) change(v)
                }
            },
    ) {
        val y = size.height / 2
        val r = 9.dp.toPx()
        val x = r + (size.width - 2 * r) * frac
        val ink = if (dimmed) c.text3 else c.ink
        drawLine(c.track, Offset(r, y), Offset(size.width - r, y), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(ink, Offset(r, y), Offset(x, y), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(ink, r, Offset(x, y))
        drawCircle(c.card, 3.5.dp.toPx(), Offset(x, y))
    }
}

@Composable
fun InkSwitch(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val c = K.c
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "switch")
    Box(
        modifier
            .size(48.dp, 28.dp)
            .background(lerp(c.track, c.ink, t), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = 20.dp * t)
                .size(22.dp)
                .background(if (c.dark) lerp(c.text2, c.onInk, t) else Color.White, CircleShape),
        )
    }
}

/** 居中标题的顶栏 */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, right: @Composable RowScope.() -> Unit = {}) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp),
    ) {
        if (onBack != null) IconBtn(Ico.BACK, Modifier.align(Alignment.CenterStart).padding(start = 6.dp), onClick = onBack)
        T(title, 18.sp, K.c.ink, FontWeight.Bold, Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically, content = right)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(K.c.card, SmoothShape(24.dp))
            .padding(padding),
        content = content,
    )
}

object Toast {
    var message by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    private val main = MainScope()

    fun show(msg: String) {
        main.launch {
            message = msg
            job?.cancel()
            job = main.launch {
                delay(2200)
                message = null
            }
        }
    }
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val msg = Toast.message
    var last by remember { mutableStateOf("") }
    if (msg != null) last = msg
    Box(modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(msg != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            Box(
                Modifier
                    .padding(horizontal = 32.dp)
                    .background(K.c.ink, SmoothShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                T(last, 14.sp, K.c.onInk, FontWeight.Medium, align = TextAlign.Center)
            }
        }
    }
}

/** 底部弹层 */
@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(visible) { onDismiss() }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        )
    }
    AnimatedVisibility(visible, enter = slideInVertically(tween(260)) { it }, exit = slideOutVertically(tween(220)) { it }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(K.c.card, SmoothShape(28.dp, topOnly = true))
                    .blockTouches()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(36.dp, 4.dp)
                        .background(K.c.line, CircleShape),
                )
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

/** 居中确认框：灰色大图标 + 粗标题 + 灰色说明 + 黑按钮，参考“专注中禁止使用”那一页 */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirm: String,
    ico: Ico = Ico.BAN,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(visible) { onDismiss() }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        )
    }
    AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut() + scaleOut(targetScale = 0.96f)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(K.c.card, SmoothShape(28.dp))
                    .blockTouches()
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(ico, size = 56.dp, color = K.c.text3)
                Spacer(Modifier.height(18.dp))
                T(title, 22.sp, K.c.ink, FontWeight.Bold, align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                T(message, 14.sp, K.c.text2, align = TextAlign.Center, lineHeight = 21.sp)
                Spacer(Modifier.height(28.dp))
                InkButton(confirm, Modifier.fillMaxWidth(), height = 52.dp, onClick = onConfirm)
                Spacer(Modifier.height(6.dp))
                GhostButton("取消", onClick = onDismiss)
            }
        }
    }
}

/** 手绘感路牌，参考首页左下角“鸡场”和猫园里的“一周农场” */
@Composable
fun Signpost(text: String, modifier: Modifier = Modifier, dark: Boolean = false, pointLeft: Boolean = true, onClick: (() -> Unit)? = null) {
    val c = K.c
    val board = if (dark) Color(0xFF48484A) else if (c.dark) Color(0xFF2A2A2C) else Color(0xFFEEEEEE)
    val post = if (dark) Color(0xFF6A6A6C) else if (c.dark) Color(0xFF232325) else Color(0xFFE2E2E2)
    val tc = if (dark) Color.White else c.text2
    Box(
        modifier
            .size(width = 100.dp, height = 66.dp)
            .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRoundRect(post, topLeft = Offset(w * 0.45f, h * 0.4f), size = Size(w * 0.09f, h * 0.6f), cornerRadius = CornerRadius(3.dp.toPx()))
            val path = Path().apply {
                if (pointLeft) {
                    moveTo(w * 0.03f, h * 0.36f)
                    lineTo(w * 0.17f, h * 0.10f)
                    lineTo(w * 0.95f, h * 0.07f)
                    lineTo(w * 0.96f, h * 0.60f)
                    lineTo(w * 0.16f, h * 0.63f)
                } else {
                    moveTo(w * 0.97f, h * 0.36f)
                    lineTo(w * 0.83f, h * 0.10f)
                    lineTo(w * 0.05f, h * 0.07f)
                    lineTo(w * 0.04f, h * 0.60f)
                    lineTo(w * 0.84f, h * 0.63f)
                }
                close()
            }
            drawPath(path, board)
            drawPath(path, board, style = Stroke(6.dp.toPx(), join = StrokeJoin.Round))
        }
        T(
            text, 15.sp, tc, FontWeight.Bold,
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 13.dp, start = if (pointLeft) 10.dp else 0.dp, end = if (pointLeft) 0.dp else 10.dp),
            maxLines = 1,
        )
    }
}

/** 标题字后面的两道小“闪光” */
@Composable
fun Logo(text: String = "空叶阅读") {
    val ink = K.c.ink
    Row(verticalAlignment = Alignment.Top) {
        T(text, 25.sp, ink, FontWeight.Black, spacing = 1.sp)
        Canvas(Modifier.padding(start = 2.dp, top = 2.dp).size(12.dp, 14.dp)) {
            val sw = 2.4.dp.toPx()
            drawLine(ink, Offset(size.width * 0.15f, size.height * 0.55f), Offset(size.width * 0.5f, size.height * 0.1f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.45f, size.height * 0.8f), Offset(size.width * 0.85f, size.height * 0.4f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** 设置页的一组 */
@Composable
fun Group(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            T(title, 13.sp, K.c.text2, FontWeight.Medium, Modifier.padding(start = 8.dp, bottom = 8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(K.c.card, SmoothShape(22.dp))
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, desc: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(title, 16.sp, K.c.ink)
            if (desc != null) {
                Spacer(Modifier.height(2.dp))
                T(desc, 12.sp, K.c.text2, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        InkSwitch(checked, onChange = onChange)
    }
}

@Composable
fun ValueRow(title: String, value: String, color: Color = K.c.ink, chevron: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        T(title, 16.sp, color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(value, 14.sp, K.c.text2)
            if (chevron) Icon(Ico.CHEVRON_RIGHT, size = 16.dp, color = K.c.text3)
        }
    }
}
