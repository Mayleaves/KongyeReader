package com.kongye.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.kongye.reader.data.AccArt
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Shop
import com.kongye.reader.data.ShopAccessory
import com.kongye.reader.data.ShopAnimal
import com.kongye.reader.ui.theme.K

/** 和配饰 HTML 舞台一样的前后次序：H（脚下）在动物身后，其余按 F G E B C D A 依次盖在上面 */
private val frontSlots = listOf("F", "G", "E", "B", "C", "D", "A")

/**
 * 商城里的动物，戴着身上的饰品。outfit 不给就用自己装扮过的；flip 让它朝另一边。
 * faceLeft：商城、装扮里的格子统一让头朝左（素材大多朝右，小马大多朝左）。
 * 饰品锚点在动物自己的 SVG 坐标里（tools/import_shop_assets.py 换算），这里按同样的缩放画上去。
 * 极简猫的身体由 App 自己画，取景和看板猫一样（同样大小的格子里一样大）。
 */
@Composable
fun Creature(
    id: String,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    outfit: Map<String, String>? = null,
    flip: Boolean = false,
    faceLeft: Boolean = false,
    shadow: Boolean = true,
    /** 鎏金看板猫换姿势：happy / sleep / loaf，不给就是坐着 */
    pose: String? = null,
) {
    val animal = Shop.animal(id) ?: return
    // 极简猫、鎏金看板猫是正脸坐着的，不用“朝左”
    val front = id == Shop.MINCAT || id == Shop.GOLDCAT
    val mirror = flip != (faceLeft && !animal.flip && !front)
    val pieces = outfitPieces(animal, outfit)
    val dark = K.c.dark
    val native = id == Shop.MINCAT || id == Shop.GOLDCAT
    val body = if (native) null else painterResource(Shop.drawable(pose?.let { animal.poses[it] } ?: animal.res))
    // 鎏金看板猫和看板猫同一套 240x260 坐标，取景也和看板猫一样
    val mascotFrame = id == Shop.GOLDCAT
    Canvas(if (size != null) modifier.size(size) else modifier) {
        val vb = animal.vb
        val (k, ox, oy) = if (mascotFrame) {
            val s = this.size.minDimension / 210f
            Triple(s, (this.size.width - 240f * s) / 2f, -35f * s)
        } else if (body == null) minimalFrame() else {
            val k = minOf(this.size.width / vb[2], this.size.height / vb[3])
            Triple(k, (this.size.width - vb[2] * k) / 2, (this.size.height - vb[3] * k) / 2)
        }
        withTransform({ if (mirror) scale(-1f, 1f, pivot = center) }) {
            drawOutfit(animal, pieces, k, ox, oy, behind = true)
            if (body != null) translate(ox, oy) { with(body) { draw(Size(vb[2] * k, vb[3] * k)) } }
            else withTransform({ translate(ox, oy); scale(k, k, pivot = Offset.Zero) }) {
                if (mascotFrame) drawGoldFocus(dark, 0f, shadow = shadow) else drawMinimalFocus(dark, 0f, shadow = shadow)
            }
            drawOutfit(animal, pieces, k, ox, oy, behind = false)
        }
    }
}

/**
 * 极简猫 200x220 坐标在画布上的位置：和看板猫（240x260，CatMascot/DashboardCat 的取景）对齐，
 * 一样大、脚踩在同一条线上。返回 (缩放, 左, 上)
 */
internal fun DrawScope.minimalFrame(): Triple<Float, Float, Float> {
    val s = size.minDimension / 210f
    return Triple(s, (size.width - 240f * s) / 2f + 20f * s, s)
}

internal class Piece(val slot: String, val acc: ShopAccessory, val art: AccArt, val painter: Painter)

/** 动物身上的饰品（outfit 不给就用自己装扮过的） */
@Composable
internal fun outfitPieces(animal: ShopAnimal, outfit: Map<String, String>? = null): List<Piece> {
    val profile by Profiles.state.collectAsState()
    val wear = outfit ?: profile.outfits[animal.id].orEmpty()
    return wear.mapNotNull { (slot, accId) ->
        val acc = Shop.accessory(accId) ?: return@mapNotNull null
        val art = if (slot == "C" && animal.dual && acc.dual != null) acc.dual else acc.main
        val res = Shop.drawable(art.res)
        if (res == 0) null else Piece(slot, acc, art, painterResource(res))
    }
}

/** behind：脚下（H）那层，画在身体前面；否则其余部位按次序盖在身体上 */
internal fun DrawScope.drawOutfit(animal: ShopAnimal, pieces: List<Piece>, k: Float, ox: Float, oy: Float, behind: Boolean) {
    if (behind) for (p in pieces.filter { it.slot == "H" }) drawPiece(animal, p, k, ox, oy)
    else for (slot in frontSlots) for (p in pieces.filter { it.slot == slot }) drawPiece(animal, p, k, ox, oy)
}

private fun DrawScope.drawPiece(animal: ShopAnimal, p: Piece, k: Float, ox: Float, oy: Float) {
    val base = animal.slots[p.slot] ?: return
    val a = base.items[p.acc.id] ?: base
    val vb = animal.vb
    val dir = if (animal.flip) -1f else 1f
    // SVG 里是 translate · scale(±s) · rotate；等比缩放和旋转可以交换，缩放直接放进画的尺寸里，
    // 矢量图按最终像素栅格化，不会先画小再放大发糊
    val f = a.s * k
    withTransform({
        translate(ox + (a.x - vb[0]) * k, oy + (a.y - vb[1]) * k)
        scale(dir, 1f, pivot = Offset.Zero)
        rotate(a.r, pivot = Offset.Zero)
    }) {
        translate(p.art.box[0] * f, p.art.box[1] * f) { with(p.painter) { draw(Size(p.art.box[2] * f, p.art.box[3] * f)) } }
    }
}

/** 单件饰品的小图（商城、装扮页） */
@Composable
fun AccessoryArt(acc: ShopAccessory, modifier: Modifier = Modifier) {
    val res = Shop.drawable(acc.main.res)
    if (res != 0) Image(painterResource(res), acc.name, modifier, contentScale = ContentScale.Fit)
}
