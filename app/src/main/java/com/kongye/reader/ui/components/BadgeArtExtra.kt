package com.kongye.reader.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 成就页另给的徽章中心图形（88x88，外框由成就页按阵营配色画）。BadgeArt.kt 由 tools/import_achievements.py 生成，
 * 设计 HTML 里没有的图形放这里。
 *   ink-cat：墨团猫猫（E:/Chrome/gemini-svg (5).svg）
 *   mascot-cat：看板猫猫（E:/Chrome/gemini-svg (6).svg）
 * 画稿按康陶配色画的，换成阵营的角色色：青 #00B8D4 = 外框上半的颜色 c2，橙 #FF6B00 = c3，
 * 黄 #FFD166 = acc，白 = c1（paper），深底 #101820 = dark（plate）。
 */
private object ExtraBadgePaths {
    val inkLeaf by lazy { svgPath("M44 26 L45 16 L55 14 L52 23 Z") }
    val inkLeafLit by lazy { svgPath("M44 26 L55 14 L52 23 Z") }
    val inkLeafStem by lazy { svgPath("M44 26 L39 19 L35 22 Z") }
    val inkTail by lazy { svgPath("M56 60 L65 55 L65 40") }
    val inkRing1 by lazy { svgPath("M61.5 45h7v2.8h-7Z") }
    val inkRing2 by lazy { svgPath("M61.5 50h7v2.5h-7Z") }
    val inkTailStar by lazy { svgPath("M65 32 L67 35 L70 36.5 L67 38 L65 41 L63 38 L60 36.5 L63 35 Z") }
    val inkHead by lazy { svgPath("M27 37 L25 22 L36 30 L52 30 L63 22 L61 37 L64 46 L57 57 L31 57 L24 46 Z") }
    val inkHeadRight by lazy { svgPath("M44 30 L52 30 L63 22 L61 37 L64 46 L57 57 L44 57 Z") }
    val inkBody by lazy { svgPath("M31 58 L57 58 L60 67 L28 67 Z") }
    val inkBodyRight by lazy { svgPath("M44 58 L57 58 L60 67 L44 67 Z") }
    val inkChestStar by lazy { svgPath("M44 53 L47 59 L53 61.5 L47 64 L44 70 L41 64 L35 61.5 L41 59 Z") }
    val inkChestDot by lazy { svgPath("M42.5 60h3v3h-3Z") }

    val mascotLeaf by lazy { svgPath("M44 31 L46 19 L55 17 L51 28 Z") }
    val mascotHead by lazy { svgPath("M26 23 L36 33 L52 33 L62 23 L62 48 L52 59 L36 59 L26 48 Z") }
    val mascotHeadRight by lazy { svgPath("M44 33 L52 33 L62 23 L62 48 L52 59 L44 59 Z") }
    val mascotCollar by lazy { svgPath("M33 61h22v3.2h-22Z") }
    val mascotStar by lazy { svgPath("M44 59 L46.5 64 L51.5 66.5 L46.5 69 L44 74 L41.5 69 L36.5 66.5 L41.5 64 Z") }
}

/** 有这个图形就画了返回 true；没有就交给 drawBadgeIcon */
internal fun DrawScope.drawExtraBadgeIcon(key: String, c1: Color, c2: Color, c3: Color, dark: Color, acc: Color): Boolean {
    with(ExtraBadgePaths) {
        when (key) {
            "ink-cat" -> {
                drawPath(inkLeaf, c3)
                drawPath(inkLeafLit, acc)
                drawPath(inkLeafStem, c2)
                drawPath(inkTail, c1, style = Stroke(4.5f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
                drawPath(inkRing1, c3)
                drawPath(inkRing2, acc)
                drawPath(inkTailStar, acc)
                drawPath(inkHead, c2)
                drawPath(inkHeadRight, c1)
                drawCircle(dark, 6f, Offset(35.5f, 44f))
                drawCircle(acc, 2.6f, Offset(35.5f, 44f))
                drawCircle(dark, 6f, Offset(52.5f, 44f))
                drawCircle(c3, 2.6f, Offset(52.5f, 44f))
                drawPath(inkBody, c3)
                drawPath(inkBodyRight, c2)
                drawPath(inkChestStar, acc)
                drawPath(inkChestDot, dark)
            }
            "mascot-cat" -> {
                drawPath(mascotLeaf, c3)
                drawPath(mascotHead, c2)
                drawPath(mascotHeadRight, c1)
                drawCircle(dark, 6.2f, Offset(35.5f, 43f))
                drawCircle(c1, 2.8f, Offset(35.5f, 43f))
                drawCircle(dark, 6.2f, Offset(52.5f, 43f))
                drawCircle(c3, 2.8f, Offset(52.5f, 43f))
                drawPath(mascotCollar, c3)
                drawPath(mascotStar, c2)
            }
            else -> return false
        }
    }
    return true
}
