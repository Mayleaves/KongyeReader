package com.kongye.reader.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.kongye.reader.ui.components.GardenBackdrop
import com.kongye.reader.ui.components.OrnamentArt
import com.kongye.reader.ui.components.ornamentWindow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.R
import androidx.compose.ui.platform.LocalContext
import com.kongye.reader.data.Achievements
import com.kongye.reader.data.Profile
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Shop
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.AccessoryArt
import com.kongye.reader.ui.components.Creature
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.Icon
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.Segmented
import com.kongye.reader.ui.components.Sheet
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.grayscale
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.launch

private val tabs = listOf("动物", "背景", "饰品", "挂件", "图标")

/** 离开商城时保留分类和滚动位置，所有入口共用。 */
private object ShopResume {
    var state: Triple<Int, Int, Int>? = null
}

/** 饰品分组标题旁的小字 */
private val slotHint = mapOf(
    "A" to "帽子、皇冠、光环",
    "B" to "花、蝴蝶结、耳机",
    "C" to "各种眼镜",
    "D" to "叼着的、鼻涕泡",
    "E" to "项圈、领结、围巾",
    "F" to "翅膀、背包",
    "G" to "气球、尾饰",
    "H" to "脚下的垫子和法阵",
)

/** 商城：读书攒叶子，换动物、猫园背景、饰品和桌面图标 */
@Composable
fun ShopScreen() {
    val c = K.c
    val profile by Profiles.state.collectAsState()
    val resume = remember { ShopResume.state }
    val pager = rememberPagerState(initialPage = resume?.first ?: 0, pageCount = { tabs.size })
    val tab = pager.currentPage
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<String?>(null) }
    val grids = tabs.indices.map { page ->
        rememberLazyGridState(if (page == resume?.first) resume.second else 0, if (page == resume?.first) resume.third else 0)
    }
    val grid = grids[tab]
    LaunchedEffect(pager) {
        snapshotFlow { grids[pager.currentPage].let { Triple(pager.currentPage, it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) } }
            .collect { ShopResume.state = it }
    }
    val tryOn: (String) -> Unit = { id ->
        ShopResume.state = Triple(tab, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
        picked = null
        Nav.push(Screen.GardenPreview(id))
    }
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cat = Shop.catalog

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("商城", onBack = { Nav.pop() }) {
                IconBtn(Ico.TROPHY) { Nav.push(Screen.Achievements) }
                // 衣架：按正在看的分类进装扮的那一页（动物→首页、背景和挂件→猫园、饰品→饰品、图标→图标）
                IconBtn(Ico.HANGER) { Nav.dress(listOf(0, 1, 2, 1, 3)[tab]) }
            }
            // 叶子数和分类条五页都一样，固定在上面不跟着左右滑；只有下面的格子换页
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
                Leaves(profile.leaves)
                Spacer(Modifier.height(14.dp))
                Segmented(tabs, tab, Modifier.fillMaxWidth()) { scope.launch { pager.animateScrollToPage(it) } }
            }
            // 左右两页提前排好，滑过去不卡
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    state = grids[page],
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = navPad + 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (page) {
                        // 动物按来历分组：空叶自己的猫、小动物、小马宝莉、表情
                        0 -> for ((group, title) in Shop.animalGroups) {
                            val list = cat.animals.filter { it.group == group }
                            if (list.isEmpty()) continue
                            item(key = "group-$group", span = { GridItemSpan(maxLineSpan) }) { GroupTitle(title.first, title.second) }
                            // 空叶组：看板猫打头（本来就有），极简猫和它一样大；三只猫头上都顶着叶子
                            if (group == "kongye") item(key = Shop.MASCOT) {
                                ItemCard("看板猫", Shop.MASCOT, 0, profile, onClick = { picked = Shop.MASCOT }) { Creature(Shop.MASCOT, Modifier.fillMaxSize(0.72f), leaf = true) }
                            }
                            items(list, key = { it.id }) { a ->
                                ItemCard(a.name, a.id, a.price, profile, onClick = { picked = a.id }) {
                                    if (a.id == Shop.MINCAT || a.id == Shop.GOLDCAT) Creature(a.id, Modifier.fillMaxSize(0.72f), leaf = true)
                                    else Creature(a.id, Modifier.fillMaxSize().padding(6.dp), faceLeft = true)
                                }
                            }
                        }
                        1 -> items(cat.backgrounds, key = { it.id }) { b ->
                            ItemCard(b.name, b.id, b.price, profile, ratio = 0.62f, onClick = { picked = b.id }) {
                                Image(painterResource(Shop.drawable(b.res)), b.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                            }
                        }
                        // 饰品按部位分组，每组上面一个小标题
                        2 -> for ((slot, slotName) in Shop.slotNames) {
                            val group = cat.accessories.filter { it.slot == slot }
                            if (group.isEmpty()) continue
                            item(key = "slot-$slot", span = { GridItemSpan(maxLineSpan) }) { GroupTitle(slotName, slotHint[slot].orEmpty()) }
                            items(group, key = { it.id }) { a ->
                                ItemCard(a.name, a.id, a.price, profile, onClick = { picked = a.id }) {
                                    AccessoryArt(a, Modifier.fillMaxSize().padding(14.dp))
                                }
                            }
                        }
                        // 挂件按节日分组，一个节日六件：路牌、小屋、天空、三块空地各一件
                        3 -> for (fest in cat.festivals) {
                            val list = cat.ornaments.filter { it.fest == fest.id }
                            if (list.isEmpty()) continue
                            item(key = "fest-${fest.id}", span = { GridItemSpan(maxLineSpan) }) {
                                GroupTitle(fest.name, "${list.count { profile.has(it.id) }} / ${list.size}", dot = parseColor(fest.color))
                            }
                            items(list, key = { it.id }) { o ->
                                ItemCard(o.name, o.id, o.price, profile, onClick = { picked = o.id }) {
                                    OrnamentArt(o, Modifier.fillMaxSize().padding(16.dp))
                                }
                            }
                        }
                        else -> items(cat.icons, key = { it.id }) { i ->
                            ItemCard(i.name, "icon_${i.id}", i.price, profile, onClick = { picked = "icon_${i.id}" }) {
                                IconPreview(i.id, Modifier.fillMaxSize().padding(12.dp))
                            }
                        }
                    }
                }
            }
        }
        ItemSheet(picked, profile, tryOn) { picked = null }
    }
}

private fun parseColor(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

/** 分组小标题：粗体名字 + 一行灰色小字；dot 给了就在前面放个色点（节日） */
@Composable
private fun GroupTitle(title: String, hint: String, dot: Color? = null) {
    val c = K.c
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(7.dp))
        }
        T(title, 15.sp, c.ink, FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        T(hint, 12.sp, c.text3, maxLines = 1)
    }
}

@Composable
private fun Leaves(n: Int) {
    val c = K.c
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.chip, SmoothShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ico.LEAF, size = 22.dp, color = c.accent)
        Spacer(Modifier.width(8.dp))
        T("$n", 24.sp, c.ink, FontWeight.Black)
        Spacer(Modifier.width(6.dp))
        T("片叶子", 13.sp, c.text2)
        Spacer(Modifier.weight(1f))
        T("读一分钟得一片", 12.sp, c.text2)
    }
}

@Composable
private fun ItemCard(
    name: String,
    id: String,
    price: Int,
    profile: Profile,
    ratio: Float = 1f,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
) {
    val c = K.c
    val owned = profile.has(id)
    val lock = Shop.lockOf(id)
    Column(Modifier.press(scaleTo = 0.96f, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(SmoothShape(16.dp))
                .background(c.chip),
        ) {
            // 没解锁的灰掉
            Box(Modifier.fillMaxSize().grayscale(!owned), contentAlignment = Alignment.Center, content = preview)
        }
        Spacer(Modifier.height(6.dp))
        T(name, 13.sp, c.ink, FontWeight.Medium, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (owned) {
                T(if (price == 0 && lock == null) "免费" else "已拥有", 12.sp, c.text2)
            } else if (lock != null) {
                // 成就奖励：不卖，点亮对应成就才有
                Icon(Ico.TROPHY, size = 12.dp, color = c.text3)
                Spacer(Modifier.width(3.dp))
                T("成就解锁", 12.sp, c.text3, FontWeight.SemiBold)
            } else {
                Icon(Ico.LEAF, size = 12.dp, color = c.accent)
                Spacer(Modifier.width(3.dp))
                T("$price", 12.sp, if (profile.leaves >= price) c.ink else c.text3, FontWeight.SemiBold)
            }
        }
    }
}

/** 桌面图标预览：底色 + 前景，和桌面上一样的圆角 */
@Composable
fun IconPreview(id: String, modifier: Modifier = Modifier) {
    // 前景 ic_launcher_fg_<id>、底色 ic_launcher_bg_<id>（tools/import_shop_assets.py 生成）；经典是最早那套
    val context = LocalContext.current
    val (fg, bg) = remember(id) {
        @Suppress("DiscouragedApi")
        val color = context.resources.getIdentifier("ic_launcher_bg_$id", "color", context.packageName)
        val drawable = Shop.drawable("ic_launcher_fg_$id")
        if (id == "default" || drawable == 0 || color == 0) R.drawable.ic_launcher_foreground to R.color.ic_launcher_background
        else drawable to color
    }.let { (d, c) -> d to colorResource(c) }
    Box(modifier.aspectRatio(1f).clip(SmoothShape(22.dp)).background(bg)) {
        Image(painterResource(fg), null, Modifier.fillMaxSize())
    }
}

@Composable
private fun ItemSheet(id: String?, profile: Profile, tryOn: (String) -> Unit, onDismiss: () -> Unit) {
    val c = K.c
    val last = remember { arrayOfNulls<String>(1) }
    id?.let { last[0] = it }
    Sheet(visible = id != null, onDismiss = onDismiss) {
        val item = last[0] ?: return@Sheet
        val price = Shop.priceOf(item) ?: 0
        val owned = profile.has(item)
        val lock = Shop.lockOf(item)?.let { Achievements.byId(it) }
        val animal = Shop.animal(item)
        val bg = Shop.background(item)
        val acc = Shop.accessory(item)
        val orn = Shop.ornament(item)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(SmoothShape(22.dp))
                .background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item == Shop.MASCOT -> Creature(Shop.MASCOT, size = 190.dp, leaf = true, animate = true)
                animal != null -> Creature(animal.id, size = 190.dp, faceLeft = true, leaf = animal.id in Shop.kongyeCats, animate = true)
                bg != null -> Image(painterResource(Shop.drawable(bg.res)), bg.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                // 挂件：挂在自己猫园的背景上，只看挂的那一块
                orn != null -> {
                    val gardenBg = Shop.background(profile.gardenBg)?.takeIf { profile.has(it.id) } ?: Shop.background(Shop.DEFAULT_BG)
                    if (gardenBg != null && Shop.slotsOf(gardenBg)[orn.slot] != null) GardenBackdrop(
                        gardenBg, profile.signText,
                        profile.scene.filterValues { profile.has(it) } + (orn.slot to orn.id),
                        Modifier.fillMaxSize(),
                        focus = ornamentWindow(orn, maxWidth / maxHeight, gardenBg),
                    ) else OrnamentArt(orn, Modifier.size(140.dp))
                }
                acc != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    // 戴在看板猫（商城里的黑猫）身上看看效果
                    val model = Shop.animal("cat")
                    if (model != null) Creature("cat", size = 180.dp, outfit = mapOf(acc.slot to acc.id), faceLeft = true)
                    else AccessoryArt(acc, Modifier.size(140.dp))
                }
                else -> IconPreview(item.removePrefix("icon_"), Modifier.size(140.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        T(Shop.nameOf(item), 20.sp, c.ink, FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        val desc = when {
            lock != null -> "成就「${lock.title}」的奖励"
            item == Shop.MASCOT -> "空叶阅读自己的猫，一开始就陪着你，首页和猫园默认都是它"
            item == "icon_minlight" -> "极简猫的浅色桌面图标，选了它开屏也换成极简猫"
            item == Shop.TREASURE -> "24K 纯金的至尊金猪皇。带回家就点亮成就「镇店之宝」，送 8 件所有动物都能戴的神装"
            animal != null -> "领养后可以放到首页，也能请它来猫园"
            bg != null -> "猫园背景，路牌上写你起的名字"
            acc != null -> "戴在动物的${Shop.slotNames[acc.slot]}，每只动物各戴各的"
            orn != null -> "${Shop.festival(orn.fest)?.name ?: "节日"}挂件，挂在猫园的${Shop.catalog.ornamentSlots[orn.slot]?.name ?: "背景"}上"
            item.startsWith("icon_") && item.removePrefix("icon_") in setOf("gold", "neon", "pixel") -> "成就奖励的桌面图标，随时可以换"
            item == "icon_minimal" -> "极简猫的落日桌面图标，选了它开屏也换成极简猫"
            else -> "桌面图标，随时可以换"
        }
        T(desc, 13.sp, c.text2)
        Spacer(Modifier.height(20.dp))
        when {
            owned && orn != null && profile.scene[orn.slot] != orn.id -> InkButton("挂到猫园", Modifier.fillMaxWidth(), height = 52.dp) {
                Profiles.hang(orn.slot, orn.id)
                Toast.show("「${orn.name}」挂上了")
            }
            owned -> InkButton("去装扮", Modifier.fillMaxWidth(), height = 52.dp) {
                onDismiss()
                Nav.dress(Nav.dressTabFor(item), item)
            }
            lock != null -> InkButton("去看成就", Modifier.fillMaxWidth(), height = 52.dp) {
                onDismiss()
                Nav.push(Screen.Achievements)
            }
            profile.leaves >= price -> InkButton("用 $price 片叶子换", Modifier.fillMaxWidth(), height = 52.dp) {
                if (Profiles.buy(item)) Toast.show("换到了「${Shop.nameOf(item)}」")
            }
            else -> {
                InkButton("叶子不够，还差 ${price - profile.leaves} 片", Modifier.fillMaxWidth(), height = 52.dp, color = c.chip, textColor = c.text2) {}
                Spacer(Modifier.height(4.dp))
                T("读一分钟书得一片叶子，完成成就还有奖励", 12.sp, c.text2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.align(Alignment.CenterHorizontally)) {
            if (bg != null || orn != null) GhostButton("在猫园里试看", Ico.EYE) { tryOn(item) }
            GhostButton("关闭") { onDismiss() }
        }
    }
}
