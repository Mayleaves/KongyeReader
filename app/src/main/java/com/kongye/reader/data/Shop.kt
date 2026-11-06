package com.kongye.reader.data

import android.annotation.SuppressLint
import com.kongye.reader.KongyeReaderApp
import kotlinx.serialization.Serializable

/** 商城目录：tools/import_shop_assets.py 生成的 assets/shop.json */
@Serializable
data class ShopCatalog(
    val animals: List<ShopAnimal> = emptyList(),
    val backgrounds: List<ShopBackground> = emptyList(),
    val accessories: List<ShopAccessory> = emptyList(),
    val ornaments: List<ShopOrnament> = emptyList(),
    val festivals: List<Festival> = emptyList(),
    /** 挂件在背景上的位置（背景 400x800 坐标），A~F */
    val ornamentSlots: Map<String, OrnamentSlot> = emptyMap(),
    val icons: List<ShopIcon> = emptyList(),
)

/** 饰品挂在动物身上的位置（动物自己的 SVG 坐标）：先平移到 (x, y)，再缩放 s（朝左的动物水平翻转），再转 r 度 */
@Serializable
data class Anchor(val x: Float, val y: Float, val s: Float, val r: Float = 0f, val items: Map<String, Anchor> = emptyMap())

@Serializable
data class ShopAnimal(
    val id: String,
    val name: String,
    val price: Int,
    /** 商城里的分组：kongye 空叶自己的猫和镇店之宝、animal 小动物、pony 小马宝莉、emoji 表情 */
    val group: String = "animal",
    val res: String,
    val private: Boolean = false,
    /** 成就奖励：点亮这个成就才能用 */
    val lock: String? = null,
    /** 首页换姿势用的图（happy / sleep / loaf），目前只有鎏金看板猫 */
    val poses: Map<String, String> = emptyMap(),
    /** SVG 的 viewBox：x, y, 宽, 高 */
    val vb: List<Float>,
    /** 正脸两只眼睛：眼镜用双镜片款 */
    val dual: Boolean = false,
    /** 默认朝左：饰品水平翻转 */
    val flip: Boolean = false,
    /** 饰品翻不翻，不给就跟 flip 一样（至尊金猪皇朝左，但神装是照着它不翻转调好的） */
    val accFlip: Boolean? = null,
    val slots: Map<String, Anchor> = emptyMap(),
)

@Serializable
data class SignText(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: String,
    val bold: Boolean = true,
    val spacing: Float = 0f,
    val kai: Boolean = false,
    val text: String = "",
)

@Serializable
data class ShopBackground(
    val id: String, val name: String, val price: Int, val res: String, val vb: List<Float>, val sign: SignText? = null,
    val lock: String? = null,
    /** 挂件位置，不给就用 ornamentSlots（400x800 的背景）；成就奖励背景是 260x540，另有一套 */
    val slots: Map<String, OrnamentSlot>? = null,
)

@Serializable
data class AccArt(val res: String, val box: List<Float>)

@Serializable
data class ShopAccessory(val id: String, val name: String, val slot: String, val price: Int, val main: AccArt, val dual: AccArt? = null, val lock: String? = null)

/** 节日挂件：挂在猫园背景的路牌、小屋、天空和三块空地上。box 是挂件自己的外框，原点就是挂的位置 */
@Serializable
data class ShopOrnament(val id: String, val name: String, val fest: String, val slot: String, val price: Int, val res: String, val box: List<Float>, val lock: String? = null)

@Serializable
data class Festival(val id: String, val name: String, val color: String)

@Serializable
data class OrnamentSlot(val x: Float, val y: Float, val s: Float, val name: String)

@Serializable
data class ShopIcon(val id: String, val name: String, val price: Int, val lock: String? = null)

object Shop {
    /** 首页和猫园默认那只“看板猫”（不在目录里，App 自己画的） */
    const val MASCOT = "mascot"
    /** 极简猫：目录里有（饰品锚点、价格），身体由 App 自己画（和看板猫一样有三种状态、眼睛跟手指） */
    const val MINCAT = "mincat"
    /** 鎏金看板猫：成就「看板猫猫」的奖励（连着摸 20 下，页面上写成“ ? ”下） */
    const val GOLDCAT = "goldcat"
    const val DEFAULT_BG = "farm"
    /** 镇店之宝：至尊金猪皇，9999 片叶子；买下点亮成就 c0「镇店之宝」，送 8 件神装 */
    const val TREASURE = "goldpig"

    /** 商城动物分组的标题和小字 */
    val animalGroups = linkedMapOf(
        "kongye" to ("空叶" to "空叶阅读自己的猫，还有镇店之宝"),
        "animal" to ("小动物" to "原本就有的农场伙伴"),
        "emoji" to ("表情" to "从表情包里跑出来的"),
        "pony" to ("小马宝莉" to "小马国的朋友"),
    )

    val slotNames = linkedMapOf(
        "A" to "头顶", "B" to "耳边", "C" to "眼睛", "D" to "嘴边",
        "E" to "脖子", "F" to "背上", "G" to "尾巴", "H" to "脚下",
    )

    val catalog: ShopCatalog by lazy {
        val app = KongyeReaderApp.app
        val raw = runCatching { app.assets.open("shop.json").bufferedReader().use { it.readText() } }.getOrNull()
        val all = raw?.let { runCatching { Store.json.decodeFromString(ShopCatalog.serializer(), it) }.getOrNull() } ?: ShopCatalog()
        // 小马宝莉的图不进仓库，本机没放进来时这些动物也不出现
        all.copy(animals = all.animals.filter { drawable(it.res) != 0 })
    }

    fun animal(id: String) = catalog.animals.firstOrNull { it.id == id }

    /** 空叶自己的三只猫：头上有叶子，身体由 App 自己画 */
    val kongyeCats = setOf(MASCOT, MINCAT, GOLDCAT)

    /** 看板猫不在目录里，但和鎏金看板猫是同一套 240x260 骨架：借它的饰品位置，也能戴饰品 */
    private val mascotWearer: ShopAnimal? by lazy {
        animal(GOLDCAT)?.copy(id = MASCOT, name = "看板猫", price = 0, lock = null, poses = emptyMap())
    }

    /** 能戴饰品的：目录里的动物，加上看板猫 */
    fun wearer(id: String): ShopAnimal? = if (id == MASCOT) mascotWearer else animal(id)
    fun background(id: String) = catalog.backgrounds.firstOrNull { it.id == id }
    fun accessory(id: String) = catalog.accessories.firstOrNull { it.id == id }
    fun ornament(id: String) = catalog.ornaments.firstOrNull { it.id == id }

    /**
     * 成就页配色方案：第一套免费，其余要用叶子换（编号 style_xxx）。
     * 和 BadgeArt.badgeStyles 同一个顺序：原版黑金，然后 2077、政府、企业、帮派，后三类按 2077 势力从高到低
     */
    val styles = listOf(
        "classic", "nightcity",
        "ncpd",
        "arasaka", "militech", "kangtao", "kiroshi", "trauma",
        "tyger", "valentinos", "maelstrom", "sixthstreet", "voodooboys", "wraiths", "mox", "scavengers",
    )
    const val STYLE_PRICE = 500

    /** 成就奖励对应的成就编号；不是奖励就是 null */
    fun lockOf(id: String): String? =
        animal(id)?.lock ?: background(id)?.lock ?: accessory(id)?.lock ?: ornament(id)?.lock
            ?: catalog.icons.firstOrNull { "icon_${it.id}" == id }?.lock

    /** 成就的奖励是商城里的哪件（lock 是这个成就编号的那件），图标加 icon_ 前缀 */
    fun rewardOf(achievement: String): String? =
        catalog.animals.firstOrNull { it.lock == achievement }?.id
            ?: catalog.backgrounds.firstOrNull { it.lock == achievement }?.id
            ?: catalog.accessories.firstOrNull { it.lock == achievement }?.id
            ?: catalog.ornaments.firstOrNull { it.lock == achievement }?.id
            ?: catalog.icons.firstOrNull { it.lock == achievement }?.let { "icon_${it.id}" }

    /** 挂件在这张背景上的位置 */
    fun slotsOf(bg: ShopBackground) = bg.slots ?: catalog.ornamentSlots
    fun festival(id: String) = catalog.festivals.firstOrNull { it.id == id }

    /** 素材按名字查资源（目录是生成的 JSON）；res/raw/shop_keep.xml 让资源压缩保留它们 */
    @SuppressLint("DiscouragedApi")
    fun drawable(name: String): Int {
        val app = KongyeReaderApp.app
        return app.resources.getIdentifier(name, "drawable", app.packageName)
    }

    /** 所有可以买的东西的编号：动物、背景、饰品按自己的编号，图标加前缀 */
    fun allItemIds(): List<String> =
        catalog.animals.map { it.id } + catalog.backgrounds.map { it.id } + catalog.accessories.map { it.id } +
            catalog.ornaments.map { it.id } + catalog.icons.map { "icon_${it.id}" } + styles.map { "style_$it" }

    fun priceOf(id: String): Int? =
        animal(id)?.price ?: background(id)?.price ?: accessory(id)?.price ?: ornament(id)?.price
            ?: catalog.icons.firstOrNull { "icon_${it.id}" == id }?.price
            ?: id.removePrefix("style_").takeIf { id.startsWith("style_") && it in styles }?.let { if (it == styles.first()) 0 else STYLE_PRICE }

    fun nameOf(id: String): String = if (id == MASCOT) "看板猫" else
        animal(id)?.name ?: background(id)?.name ?: accessory(id)?.name ?: ornament(id)?.name
            ?: catalog.icons.firstOrNull { "icon_${it.id}" == id }?.name?.let { "$it 图标" } ?: id
}
