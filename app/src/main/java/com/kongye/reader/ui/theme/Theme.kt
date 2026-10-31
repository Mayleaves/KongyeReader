package com.kongye.reader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 黑白为主、橙红点睛：大面积白（或墨黑）底，灰色胶囊，粗黑按钮 */
@Immutable
data class Palette(
    val dark: Boolean,
    /** 页面底色 */
    val bg: Color,
    /** 统计、设置这类“卡片页”的浅灰底 */
    val bg2: Color,
    val card: Color,
    /** 灰色胶囊、输入框、分段控件轨道 */
    val chip: Color,
    /** 分段控件里选中的那一格 */
    val seg: Color,
    val ink: Color,
    val onInk: Color,
    val text2: Color,
    val text3: Color,
    val line: Color,
    val track: Color,
    val accent: Color,
    /** 吉祥物眼白：浅色模式白、深色模式与底色一致 */
    val eye: Color,
    val shadow: Color,
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    bg2 = Color(0xFFF5F5F6),
    card = Color(0xFFFFFFFF),
    chip = Color(0xFFF2F2F3),
    seg = Color(0xFFFFFFFF),
    ink = Color(0xFF1F1F1F),
    onInk = Color(0xFFFFFFFF),
    text2 = Color(0xFF8E8E93),
    text3 = Color(0xFFC4C4C8),
    line = Color(0xFFECECEE),
    track = Color(0xFFE2E2E5),
    accent = Color(0xFFF0492C),
    eye = Color(0xFFFFFFFF),
    shadow = Color(0xFFECECEC),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF101011),
    bg2 = Color(0xFF101011),
    card = Color(0xFF1C1C1E),
    chip = Color(0xFF242426),
    seg = Color(0xFF3A3A3C),
    ink = Color(0xFFF2F2F2),
    onInk = Color(0xFF111111),
    text2 = Color(0xFF8E8E93),
    text3 = Color(0xFF4A4A4E),
    line = Color(0xFF2A2A2C),
    track = Color(0xFF3A3A3C),
    accent = Color(0xFFFF5A3C),
    eye = Color(0xFF101011),
    shadow = Color(0xFF232325),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object K {
    val c: Palette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current
}

/** 系统是否处于深色模式，由 MainActivity 维护 */
object AppTheme {
    var systemDark by mutableStateOf(false)
}

/** 阅读页的纸色 */
@Immutable
data class Paper(
    val name: String,
    val bg: Color,
    val text: Color,
    val sub: Color,
    val panel: Color,
    val chip: Color,
    val ink: Color,
    val dark: Boolean = false,
)

val Papers = listOf(
    Paper("白", Color(0xFFFFFFFF), Color(0xFF242424), Color(0xFFA3A3A3), Color(0xFFFFFFFF), Color(0xFFF2F2F3), Color(0xFF1F1F1F)),
    Paper("米", Color(0xFFF6F0E3), Color(0xFF2E2921), Color(0xFFA89F8E), Color(0xFFFBF7EE), Color(0xFFEDE5D4), Color(0xFF2A251E)),
    Paper("灰", Color(0xFFEFEFEF), Color(0xFF232323), Color(0xFF9B9B9B), Color(0xFFF8F8F8), Color(0xFFE4E4E5), Color(0xFF1F1F1F)),
    Paper("青", Color(0xFFE3EDE0), Color(0xFF1F2A1E), Color(0xFF8FA08B), Color(0xFFF1F6EF), Color(0xFFD5E2D1), Color(0xFF1C261B)),
)

val NightPaper = Paper("夜", Color(0xFF121212), Color(0xFFA4A4A4), Color(0xFF5C5C5C), Color(0xFF1C1C1E), Color(0xFF2A2A2C), Color(0xFFE8E8E8), dark = true)

/** 阅读页里的面板、开关、按钮跟着纸色走 */
fun Paper.palette(base: Palette): Palette = base.copy(
    dark = dark,
    bg = bg,
    bg2 = bg,
    card = panel,
    chip = chip,
    seg = if (dark) Color(0xFF3A3A3C) else Color.White,
    ink = ink,
    onInk = if (dark) Color(0xFF111111) else Color.White,
    text2 = sub,
    text3 = sub.copy(alpha = 0.6f),
    line = if (dark) Color(0xFF2A2A2C) else chip,
    track = if (dark) Color(0xFF3A3A3C) else chip,
    eye = if (dark) panel else Color.White,
    shadow = chip,
)
