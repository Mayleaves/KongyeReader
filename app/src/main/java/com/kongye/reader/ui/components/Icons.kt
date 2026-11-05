package com.kongye.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kongye.reader.ui.theme.K

/** 线条图标，24×24 画布，圆头圆角，和参考图里那种细线图标一致 */
enum class Ico(val stroke: String? = null, val fill: String? = null, val width: Float = 2f) {
    BACK("M15 5L8 12L15 19", width = 2.2f),
    CHEVRON_RIGHT("M10 6L16 12L10 18"),
    CHEVRON_LEFT("M14 6L8 12L14 18"),
    LIST("M10 6H20M10 12H20M10 18H20", "M3.6 6a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0ZM3.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0ZM3.6 18a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z", 2.2f),
    RING("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0ZM8.5 12a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0Z", width = 2.2f),
    PLUS("M12 5V19M5 12H19", width = 2.2f),
    SEARCH("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0ZM16 16L20 20"),
    BOOKMARK("M7 4H17V20L12 16L7 20Z"),
    BOOKMARK_ON(fill = "M7 4H17V20L12 16L7 20Z", stroke = "M7 4H17V20L12 16L7 20Z"),
    TOC("M4 6H20M4 12H14M4 18H17"),
    MOON("M20 14.5A8 8 0 1 1 9.5 4A6.5 6.5 0 0 0 20 14.5Z"),
    SUN("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0ZM12 2.5V4.5M12 19.5V21.5M2.5 12H4.5M19.5 12H21.5M5.3 5.3L6.7 6.7M17.3 17.3L18.7 18.7M5.3 18.7L6.7 17.3M17.3 6.7L18.7 5.3"),
    MORE(fill = "M4 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0ZM10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0ZM16 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
    SWAP("M5 9H19L15.5 5.5M19 15H5L8.5 18.5"),
    TARGET("M6 12a6 6 0 1 0 12 0a6 6 0 1 0 -12 0ZM12 2.5V6M12 18V21.5M2.5 12H6M18 12H21.5", "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z"),
    REFRESH("M19 12a7 7 0 1 1 -2.05 -4.95M19 4.5V8H15.5", width = 2.2f),
    TRASH("M4.5 7H19.5M9.5 7V4.5H14.5V7M6.5 7L7.5 20H16.5L17.5 7M10 11V16M14 11V16"),
    BOOK("M4 5.5C6.5 4.5 9.5 4.5 12 6C14.5 4.5 17.5 4.5 20 5.5V19C17.5 18 14.5 18 12 19.5C9.5 18 6.5 18 4 19ZM12 6V19.5"),
    CLOCK("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0ZM12 7.5V12L15 14"),
    CLOSE("M6 6L18 18M18 6L6 18", width = 2.2f),
    TIMELINE("M6 4V20M10 7H20M10 12H17M10 17H20"),
    CHART("M5 20V11M12 20V5M19 20V14", width = 2.6f),
    CHECK("M5 12.5L10 17.5L19 7", width = 2.4f),
    BAN("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0ZM6 6L18 18", width = 2.4f),
    PAW(fill = "M12 21c-3 0-6-1.6-6-4.2C6 14 9 12 12 12s6 2 6 4.8C18 19.4 15 21 12 21ZM3.5 11a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0ZM7.5 7.5a2 2.6 0 1 0 4 0a2 2.6 0 1 0 -4 0ZM12.5 7.5a2 2.6 0 1 0 4 0a2 2.6 0 1 0 -4 0ZM16.5 11a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0Z"),
    LEAF(fill = "M4 19C4 10 11 4 20 4C20 13 14 20 5 20Z"),
    EDIT("M4.5 19.5H8.5L19 9L15 5L4.5 15.5ZM13 7L17 11"),
    UNDO("M9 13.5L4.5 9L9 4.5M4.5 9H14a5.5 5.5 0 0 1 0 11H10"),
    REDO("M15 13.5L19.5 9L15 4.5M19.5 9H10a5.5 5.5 0 0 0 0 11H14"),
    CHEVRON_UP("M6 15L12 9L18 15"),
    CHEVRON_DOWN("M6 9L12 15L18 9"),
    BAG("M5.5 8.5H18.5L17.5 20H6.5ZM9 8.5V7a3 3 0 0 1 6 0V8.5"),
    TROPHY("M8 4.5H16V10a4 4 0 0 1 -8 0ZM8 6.5H5a3 3 0 0 0 3 4M16 6.5H19a3 3 0 0 1 -3 4M12 14V17.5M8.5 19.5H15.5"),
    CLOUD("M7 18.5H17a4 4 0 0 0 0.5 -8A5.5 5.5 0 0 0 6.8 9.6A4.5 4.5 0 0 0 7 18.5Z"),
    HANGER("M12 8.5V7.5a2 2 0 1 0 -2 -2M12 8.5L3.5 15a1.5 1.5 0 0 0 1 2.7H19.5a1.5 1.5 0 0 0 1 -2.7Z"),
    EYE("M2.5 12C5 7.5 8.3 5.5 12 5.5S19 7.5 21.5 12C19 16.5 15.7 18.5 12 18.5S5 16.5 2.5 12ZM9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
    LOCK("M6 10.5H18V20H6ZM8.5 10.5V8a3.5 3.5 0 0 1 7 0V10.5", width = 2.2f),
    FLAME("M12 21c-3.6 0-6-2.5-6-5.8C6 11 10 9.5 10 4.5c3 1.6 4.6 4 4.6 6.5c0.9-0.6 1.4-1.6 1.5-2.7C17.6 9.8 18 12.6 18 15.2C18 18.5 15.6 21 12 21Z"),
    STAR("M12 3.5L14.6 9L20.5 9.6L16 13.6L17.3 19.5L12 16.5L6.7 19.5L8 13.6L3.5 9.6L9.4 9Z"),
    CROWN("M4 8L8 12L12 5.5L16 12L20 8L18.5 18H5.5Z"),
    KEY("M4 15a4 4 0 1 0 8 0a4 4 0 1 0 -8 0ZM11 12L20 3M17 6L19.5 8.5M15 8L17 10"),
    GIFT("M4 9H20V12.5H4ZM5.5 12.5H18.5V20.5H5.5ZM12 9V20.5M12 9C10 9 7.5 8.5 7.5 6.5a2 2 0 0 1 4.5 0V9M12 9C14 9 16.5 8.5 16.5 6.5a2 2 0 0 0 -4.5 0"),
    SPROUT("M12 20V11M12 11C12 7.5 9.5 5 5.5 5C5.5 8.8 8 11 12 11ZM12 13C12 9.5 14.5 7 18.5 7C18.5 10.8 16 13 12 13Z"),
    SUN_SMALL("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0ZM12 5.5V6.5M12 17.5V18.5M5.5 12H6.5M17.5 12H18.5M7.4 7.4L8.1 8.1M15.9 15.9L16.6 16.6M7.4 16.6L8.1 15.9M15.9 8.1L16.6 7.4"),
}

private val cache = HashMap<String, Path>()

fun svgPath(d: String): Path = cache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

@Composable
fun Icon(ico: Ico, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = K.c.ink) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, pivot = Offset.Zero) {
            ico.fill?.let { drawPath(svgPath(it), color) }
            ico.stroke?.let {
                drawPath(svgPath(it), color, style = Stroke(ico.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
