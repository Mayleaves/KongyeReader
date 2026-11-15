package com.kongye.reader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.kongye.reader.data.AccArt
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Shop
import com.kongye.reader.data.ShopAccessory
import com.kongye.reader.data.ShopAnimal
import com.kongye.reader.ui.theme.K

/** H 替换原阴影，位于身体下面；正脸猫的背包和翅膀在身后，披风与肩章在身前。 */
private val frontSlots = listOf("F", "G", "E", "B", "C", "D", "A")

/**
 * 商城里的动物，戴着身上的饰品。outfit 不给就用自己装扮过的；flip 让它朝另一边。
 * faceLeft：商城、装扮里的格子统一让头朝左（素材大多朝右，小马大多朝左）。
 * 饰品锚点在动物自己的 SVG 坐标里（tools/import_shop_assets.py 换算），这里按同样的缩放画上去。
 * 墨团的身体由 App 自己画，取景和看板猫一样（同样大小的格子里一样大）。
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
    /**
     * 看板猫、墨团、鎏金看板猫头上的叶子：看板 HTML 里普通专注时的那片
     * （看板猫、墨团是墨色描边的空心叶，鎏金看板猫是自己的金叶）。商城、装扮里展示用
     */
    leaf: Boolean = false,
    /** 大图显式开启动画；商品格子、动物选择框默认静止，不创建动画时钟。 */
    animate: Boolean = false,
) {
    val animal = Shop.wearer(id) ?: return
    // 空叶的四只猫是正脸坐着的，不用“朝左”
    val native = id in Shop.kongyeCats
    val mirror = flip != (faceLeft && !animal.flip && !native)
    val pieces = outfitPieces(animal, outfit)
    val showGround = shadow && pieces.none { it.slot == "H" }
    val dark = K.c.dark
    val time = if (animate && native) assetTime() else 0f
    // 普通动物共用轻呼吸：仅在绘制时读取动画值，避免逐帧重组饰品和资源。
    // 四只原生猫仍然使用自己的原始动画。
    val breathing = if (animate && !native) {
        val transition = rememberInfiniteTransition(label = "animal breathing")
        val duration = remember(id) { 1800 + (id.hashCode() and 0x7fffffff) % 5 * 100 }
        transition.animateFloat(
            initialValue = 1f, targetValue = 1.018f,
            animationSpec = infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "body height",
        )
    } else null
    val detailClock = if (animate && !native) animalPartTime(id) else null
    val layers = if (animate && !native) animalLayers(id) else emptyList()
    val movingParts = remember(id, layers.map { it.part }) { layers.mapNotNull { it.part }.distinctBy { it.name } }
    val resource = pose?.let { animal.poses[it] } ?: animal.res
    val bodyLayer = if (native) 0 else Shop.drawable("${resource}_body")
    val body = if (native) null else painterResource(if (bodyLayer != 0) bodyLayer else Shop.drawable(resource))
    val ground = if (!native && showGround && bodyLayer != 0) painterResource(Shop.drawable("${resource}_shadow")) else null
    // 看板猫、鎏金看板猫同一套 240x260 坐标，取景也和看板猫一样
    val mascotFrame = id == Shop.GOLDCAT || id == Shop.MASCOT
    Canvas((if (size != null) modifier.size(size) else modifier).semantics { contentDescription = "动物预览：${animal.name}" }) {
        val vb = animal.vb
        val (k, ox, oy) = if (mascotFrame) {
            val s = this.size.minDimension / 210f
            Triple(s, (this.size.width - 240f * s) / 2f, -35f * s)
        } else if (body == null) minimalFrame() else {
            val k = minOf(this.size.width / vb[2], this.size.height / vb[3])
            Triple(k, (this.size.width - vb[2] * k) / 2, (this.size.height - vb[3] * k) / 2)
        }
        withTransform({ if (mirror) scale(-1f, 1f, pivot = center) }) {
            if (native && showGround) withTransform({ translate(ox, oy); scale(k, k, pivot = Offset.Zero) }) {
                drawCatGroundShadow(id, dark)
            }
            if (ground != null) translate(ox, oy) { with(ground) { draw(Size(vb[2] * k, vb[3] * k)) } }
            drawOutfit(animal, pieces, k, ox, oy, behind = true)
            // 以脚下锚点为支点，身体和身上饰品一起舒展；地面阴影及脚下饰品不动。
            val feet = animal.slots["H"]?.y ?: (vb[1] + vb[3] * 0.86f)
            withTransform({
                if (!native) scale(1f, breathing?.value ?: 1f, pivot = Offset(ox + vb[2] * k / 2f, oy + (feet - vb[1]) * k))
            }) {
                val seconds = detailClock?.value ?: 0f
                if (layers.isNotEmpty()) {
                    drawAnimalLayers(animal, layers, seconds, k, ox, oy)
                } else if (body != null) translate(ox, oy) { with(body) { draw(Size(vb[2] * k, vb[3] * k)) } }
                else withTransform({ translate(ox, oy); scale(k, k, pivot = Offset.Zero) }) {
                    when (id) {
                        Shop.MASCOT -> drawImportedFocus(dark, time, shadow = false, plainLeaf = leaf)
                        Shop.GOLDCAT -> drawGoldFocus(dark, time, leaf, shadow = false)
                        Shop.GOLDMIN -> drawGoldMinFocus(dark, time, leaf, shadow = false)
                        else -> drawMinimalFocus(dark, time, shadow = false, plainLeaf = leaf)
                    }
                }
                drawOutfit(animal, pieces, k, ox, oy, behind = false, parts = movingParts, seconds = seconds)
            }
        }
    }
}

/**
 * 墨团 200x220 坐标在画布上的位置：和看板猫（240x260，CatMascot/DashboardCat 的取景）对齐，
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

/** 披风和肩章搭在肩前，避免被正脸猫的身体全部遮住。 */
private val frontDrapes = setOf("ach_c13", "acc-F-ingot-saddle")

/** 正脸猫的翅膀以背部锚点镜像一份。 */
private val pairedWings = setOf("acc-F-wings", "acc-F-batwings")

/**
 * behind：脚下（H）和正脸猫的背包、翅膀；披风、肩章在肩前。
 * 否则其余部位按次序盖在身体上
 */
internal fun DrawScope.drawOutfit(animal: ShopAnimal, pieces: List<Piece>, k: Float, ox: Float, oy: Float, behind: Boolean, parts: List<AnimalPart> = emptyList(), seconds: Float = 0f) {
    val front = animal.id in Shop.kongyeCats
    val back = if (front) listOf("H", "F") else listOf("H")
    val slots = if (behind) back else frontSlots - back.toSet()
    if (front && !behind) for (p in pieces.filter { it.slot == "F" && it.acc.id in frontDrapes }) {
        drawPiece(animal, p, k, ox, oy)
    }
    for (slot in slots) for (p in pieces.filter { it.slot == slot }) {
        if (front && slot == "F" && p.acc.id in frontDrapes) continue
        // 背包、披风和发条固定在躯干，只有翅膀类饰品跟着翅膀活动。
        val part = parts.firstOrNull { slot in it.attachments && (slot != "F" || p.acc.id in pairedWings) }
        withAnimalPart(animal, part, seconds, k, ox, oy) {
            drawPiece(animal, p, k, ox, oy)
            if (front && p.acc.id in pairedWings) drawPiece(animal, p, k, ox, oy, mirror = true)
        }
    }
}

private fun DrawScope.drawPiece(animal: ShopAnimal, p: Piece, k: Float, ox: Float, oy: Float, mirror: Boolean = false) {
    val base = animal.slots[p.slot] ?: return
    val a = base.items[p.acc.id] ?: base
    val vb = animal.vb
    val dir = (if (animal.accFlip ?: animal.flip) -1f else 1f) * (if (mirror) -1f else 1f)
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
