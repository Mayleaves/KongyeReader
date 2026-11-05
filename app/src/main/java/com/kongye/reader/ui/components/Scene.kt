package com.kongye.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.kongye.reader.data.OrnamentSlot
import com.kongye.reader.data.Shop
import com.kongye.reader.data.ShopBackground
import com.kongye.reader.data.ShopOrnament
import com.kongye.reader.ui.reader.ReaderFonts
import kotlin.math.max

private class Hung(val slot: OrnamentSlot, val orn: ShopOrnament, val painter: Painter)

/**
 * 猫园背景：铺满、顶端对齐；背景自带的路牌字去掉了，这里写上自己起的名字；scene（位置 → 挂件）挂在上面。
 * focus 给了就只看背景里这一块（背景 400x800 坐标），商城、装扮里看挂件用。
 */
@Composable
fun GardenBackdrop(
    bg: ShopBackground,
    sign: String,
    scene: Map<String, String>,
    modifier: Modifier = Modifier,
    focus: Rect? = null,
) {
    val painter = painterResource(Shop.drawable(bg.res))
    val measurer = rememberTextMeasurer()
    // 只有水墨背景的路牌用楷体；第一次要从安装包里解出字体文件，别的背景用不着
    val wenkai = remember(bg.sign?.kai) { if (bg.sign?.kai == true) ReaderFonts.family(1) else null }
    val hung = scene.mapNotNull { (slot, id) ->
        val s = Shop.slotsOf(bg)[slot] ?: return@mapNotNull null
        val o = Shop.ornament(id) ?: return@mapNotNull null
        val res = Shop.drawable(o.res)
        if (res == 0) null else Hung(s, o, painterResource(res))
    }
    Canvas(modifier) {
        val (vx, vy, vw, vh) = bg.vb
        val win = focus ?: Rect(vx, vy, vx + vw, vy + vh)
        val k = max(size.width / win.width, size.height / win.height)
        // 整张背景：横向居中、顶端对齐；看局部：那一块居中
        val ox = if (focus == null) (size.width - vw * k) / 2 else (size.width - win.width * k) / 2 - (win.left - vx) * k
        val oy = if (focus == null) 0f else (size.height - win.height * k) / 2 - (win.top - vy) * k
        clipRect {
            translate(ox, oy) { with(painter) { draw(Size(vw * k, vh * k)) } }
            bg.sign?.let { st ->
                val color = runCatching { Color(android.graphics.Color.parseColor(st.color)) }.getOrDefault(Color.White)
                // 字多了缩小一点，别冲出路牌
                val shrink = if (sign.length <= 4) 1f else 4f / sign.length
                val style = TextStyle(
                    color = color,
                    fontSize = (st.size * k * shrink).toSp(),
                    fontWeight = if (st.bold) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = (st.spacing * k * shrink).toSp(),
                    fontFamily = if (st.kai) wenkai else null,
                )
                val r = measurer.measure(sign, style)
                drawText(r, topLeft = Offset(ox + (st.x - vx) * k - r.size.width / 2f, oy + (st.y - vy) * k - r.firstBaseline))
            }
            for (h in hung) drawHung(h, ox + (h.slot.x - vx) * k, oy + (h.slot.y - vy) * k, h.slot.s * k)
        }
    }
}

private fun DrawScope.drawHung(h: Hung, x: Float, y: Float, f: Float) {
    val b = h.orn.box
    translate(x + b[0] * f, y + b[1] * f) { with(h.painter) { draw(Size(b[2] * f, b[3] * f)) } }
}

/** 挂件在背景上那一块（看挂件时取景用）：以挂的位置为中心，宽取背景的 55%、按 ratio 定高 */
fun ornamentWindow(o: ShopOrnament, ratio: Float, bg: ShopBackground): Rect {
    val (vx, vy, vw, vh) = bg.vb
    val s = Shop.slotsOf(bg)[o.slot] ?: return Rect(vx, vy, vx + vw, vy + vh)
    val w = vw * 0.55f
    // 挂件自己的外框中心（有的挂件画在锚点旁边，比如小屋左边的礼盒）
    val cx = (s.x + (o.box[0] + o.box[2] / 2) * s.s).coerceIn(vx + w / 2, vx + vw - w / 2)
    val cy = s.y + (o.box[1] + o.box[3] / 2) * s.s
    val h = w / ratio
    val top = (cy - h / 2).coerceIn(vy, vy + vh - h)
    return Rect(cx - w / 2, top, cx + w / 2, top + h)
}

/** 单件挂件的小图（商城格子） */
@Composable
fun OrnamentArt(o: ShopOrnament, modifier: Modifier = Modifier) {
    val res = Shop.drawable(o.res)
    if (res != 0) Image(painterResource(res), o.name, modifier, contentScale = ContentScale.Fit)
}
