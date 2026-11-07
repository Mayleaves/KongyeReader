package com.kongye.reader.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Profile
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Shop
import com.kongye.reader.data.ShopAnimal
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.AccessoryArt
import com.kongye.reader.ui.components.Creature
import com.kongye.reader.ui.components.GardenBackdrop
import com.kongye.reader.ui.components.OrnamentArt
import com.kongye.reader.ui.components.GhostButton
import com.kongye.reader.ui.components.Group
import com.kongye.reader.ui.components.Ico
import com.kongye.reader.ui.components.IconBtn
import com.kongye.reader.ui.components.InkButton
import com.kongye.reader.ui.components.Segmented
import com.kongye.reader.ui.components.SmoothShape
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.Toast
import com.kongye.reader.ui.components.TopBar
import com.kongye.reader.ui.components.grayscale
import com.kongye.reader.ui.components.press
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.launch

private val tabs = listOf("首页", "猫园", "饰品", "图标")

/** 装扮：分四页——首页放谁和路牌、猫园的背景和来客、动物戴什么、桌面图标 */
@Composable
fun DressScreen() {
    val c = K.c
    val profile by Profiles.state.collectAsState()
    val animals = Shop.catalog.animals.filter { profile.has(it.id) }
    val pager = rememberPagerState(initialPage = Nav.dressTab, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(Nav.dressRequest) { pager.scrollToPage(Nav.dressTab) }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { Nav.dressTab = it } }

    Column(Modifier.fillMaxSize().background(c.bg2)) {
        TopBar("装扮", onBack = { Nav.pop() }) {
            IconBtn(Ico.BAG) { Nav.push(Screen.Shop) }
        }
        Segmented(tabs, pager.currentPage, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            scope.launch { pager.animateScrollToPage(it) }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { tab ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                when (tab) {
                    0 -> HomeTab(profile, animals)
                    1 -> GardenTab(profile, animals)
                    2 -> OutfitTab(profile, animals)
                    else -> IconTab(profile)
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun HomeTab(profile: Profile, animals: List<ShopAnimal>) {
    Group("首页上的动物") {
        Pad {
            AnimalGrid(animals, mascot = true, leaf = true, selected = { it == profile.homeAnimal }) { id ->
                Profiles.update { it.copy(homeAnimal = id) }
            }
            if (animals.isEmpty()) Hint("在商城领养动物后，可以换到首页")
        }
    }
    Spacer(Modifier.height(20.dp))
    Group("路牌") { Pad { SignEditor(profile.signText) } }
}

@Composable
private fun GardenTab(profile: Profile, animals: List<ShopAnimal>) {
    val c = K.c
    Group("背景") {
        Pad {
            val bgs = Shop.catalog.backgrounds.filter { profile.has(it.id) }
            Grid(bgs, cols = 3, collapsedRows = 2, selected = { it.id == profile.gardenBg }) { b, m ->
                Pick(
                    on = profile.gardenBg == b.id,
                    label = b.name,
                    modifier = m.semantics { contentDescription = "猫园背景：${b.name}" },
                    ratio = 0.68f,
                    onClick = {
                        Profiles.update { it.copy(gardenBg = b.id) }
                    },
                ) {
                    Image(painterResource(Shop.drawable(b.res)), b.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    SceneGroup(profile)
    Spacer(Modifier.height(20.dp))
    Group("会来猫园的动物") {
        Pad {
            T("每读一次来一只，按选中的顺序轮流；朝向随机", 12.sp, c.text2)
            Spacer(Modifier.height(10.dp))
            AnimalGrid(animals, mascot = true, leaf = true, selected = { it in profile.gardenAnimals }) { id ->
                Profiles.update { p ->
                    val next = if (id in p.gardenAnimals) p.gardenAnimals - id else p.gardenAnimals + id
                    p.copy(gardenAnimals = next.ifEmpty { listOf(Shop.MASCOT) })
                }
            }
        }
    }
}

/** 节日挂件：上面一张缩小的猫园，下面每个位置一排换到的挂件 */
@Composable
private fun SceneGroup(profile: Profile) {
    val c = K.c
    val scene = profile.scene.filterValues { profile.has(it) }
    val bg = Shop.background(profile.gardenBg)?.takeIf { profile.has(it.id) } ?: Shop.background(Shop.DEFAULT_BG)
    Group("节日挂件") {
        if (bg != null) {
            Box(Modifier.fillMaxWidth().height(250.dp).padding(top = 12.dp), contentAlignment = Alignment.Center) {
                GardenBackdrop(
                    bg, profile.signText, scene,
                    Modifier.width(119.dp).height(238.dp).clip(SmoothShape(14.dp)),
                    focus = Rect(0f, 0f, 400f, 800f),
                )
                if (scene.isNotEmpty()) {
                    GhostButton("全部摘下", modifier = Modifier.align(Alignment.BottomEnd)) {
                        for (slot in scene.keys) Profiles.hang(slot, null)
                    }
                }
            }
        }
        if (Shop.catalog.ornaments.none { profile.has(it.id) }) {
            Pad { Hint("在商城的「挂件」里换到节日挂件，就能挂到猫园的路牌、小屋、天空和空地上") }
        } else for ((slot, spot) in Shop.catalog.ornamentSlots) {
            val owned = Shop.catalog.ornaments.filter { it.slot == slot && profile.has(it.id) }
            Pad(vertical = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(spot.name, 14.sp, c.ink, FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    scene[slot]?.let { T(Shop.nameOf(it), 12.sp, c.text2, maxLines = 1) }
                }
                Spacer(Modifier.height(6.dp))
                if (owned.isEmpty()) {
                    T("商城的「挂件」里能换到", 12.sp, c.text3)
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pick(scene[slot] == null, "不挂", modifier = Modifier.width(56.dp), onClick = { Profiles.hang(slot, null) }) {
                            T("—", 18.sp, c.text3)
                        }
                        for (o in owned) {
                            Pick(scene[slot] == o.id, o.name, modifier = Modifier.width(56.dp), onClick = { Profiles.hang(slot, o.id) }) {
                                OrnamentArt(o, Modifier.fillMaxSize().padding(7.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun OutfitTab(profile: Profile, animals: List<ShopAnimal>) {
    val c = K.c
    // 看板猫打头（本来就有），后面是领养的动物
    var dressing by rememberSaveable { mutableStateOf(Shop.MASCOT) }
    val who = animals.firstOrNull { it.id == dressing } ?: Shop.wearer(Shop.MASCOT) ?: animals.firstOrNull() ?: return
    val wear = profile.outfits[who.id].orEmpty()
    Group(null) {
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            Creature(who.id, size = 210.dp, faceLeft = true, leaf = who.id in Shop.kongyeCats, animate = true)
            if (wear.isNotEmpty()) {
                GhostButton("全部摘下", modifier = Modifier.align(Alignment.BottomEnd)) {
                    for (slot in wear.keys) Profiles.equip(who.id, slot, null)
                }
            }
        }
        Pad(vertical = 4.dp) {
            T("给谁戴", 13.sp, c.text2)
            Spacer(Modifier.height(8.dp))
            AnimalGrid(animals, mascot = true, leaf = true, selected = { it == who.id }) { dressing = it }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(20.dp))
    Group("饰品") {
        for ((slot, slotName) in Shop.slotNames) {
            val owned = Shop.catalog.accessories.filter { it.slot == slot && profile.has(it.id) }
            Pad(vertical = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(slotName, 14.sp, c.ink, FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    wear[slot]?.let { T(Shop.nameOf(it), 12.sp, c.text2) }
                }
                Spacer(Modifier.height(6.dp))
                if (owned.isEmpty()) {
                    T("商城里能换到这个部位的饰品", 12.sp, c.text3)
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pick(wear[slot] == null, "不戴", modifier = Modifier.width(56.dp), onClick = { Profiles.equip(who.id, slot, null) }) {
                            T("—", 18.sp, c.text3)
                        }
                        for (acc in owned) {
                            Pick(wear[slot] == acc.id, acc.name, modifier = Modifier.width(56.dp), onClick = { Profiles.equip(who.id, slot, acc.id) }) {
                                AccessoryArt(acc, Modifier.fillMaxSize().padding(7.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun IconTab(profile: Profile) {
    val c = K.c
    Group("桌面图标") {
        Pad {
            val icons = Shop.catalog.icons.filter { profile.has("icon_${it.id}") }
            val focus = Nav.dressItem?.removePrefix("icon_")
            Grid(icons, cols = 3, collapsedRows = 9, selected = { it.id == focus || it.id == profile.icon }) { i, m ->
                val requester = remember(i.id) { BringIntoViewRequester() }
                LaunchedEffect(Nav.dressRequest, focus) {
                    if (i.id == focus) {
                        androidx.compose.runtime.withFrameNanos { }
                        requester.bringIntoView()
                    }
                }
                Pick(
                    on = profile.icon == i.id,
                    label = i.name,
                    modifier = m.bringIntoViewRequester(requester)
                        .then(if (i.id == focus) Modifier.border(1.5.dp, c.accent, SmoothShape(16.dp)) else Modifier)
                        .semantics { contentDescription = "桌面图标：${i.name}" },
                    onClick = {
                        if (profile.icon != i.id) {
                            Profiles.update { it.copy(icon = i.id) }
                            Toast.show("离开空叶阅读后，桌面图标就会换好")
                        }
                    },
                ) {
                    IconPreview(i.id, Modifier.fillMaxSize().padding(8.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            T("换图标会让系统关掉 App，所以等你离开空叶阅读（回桌面或切走）时才换；有的桌面要过几秒才刷新", 12.sp, c.text3, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SignEditor(current: String) {
    val c = K.c
    val state = rememberTextFieldState(current)
    T("首页左下角和猫园的路牌一起改，最多 6 个字", 12.sp, c.text2)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .background(c.chip, SmoothShape(14.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                state,
                Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = c.ink, fontSize = 16.sp),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(6),
                cursorBrush = SolidColor(c.ink),
            )
        }
        Spacer(Modifier.width(10.dp))
        InkButton("保存", Modifier.width(76.dp), height = 44.dp) {
            val t = state.text.toString().trim()
            Profiles.update { it.copy(signText = t.ifEmpty { Profile.DEFAULT_SIGN }) }
            Toast.show("路牌改好了")
            Nav.home()
        }
    }
}

/** 动物格子：看板猫（可选）+ 已领养的动物，一律头朝左 */
@Composable
private fun AnimalGrid(animals: List<ShopAnimal>, mascot: Boolean, leaf: Boolean = false, selected: (String) -> Boolean, onPick: (String) -> Unit) {
    val ids = (if (mascot) listOf(Shop.MASCOT) else emptyList()) + animals.map { it.id }
    Grid(ids, cols = 4, collapsedRows = 2, selected = selected) { id, m ->
        if (id in Shop.kongyeCats) {
            // 看板猫、极简猫、鎏金看板猫一样大；leaf：头上顶着普通专注时的叶子
            Pick(selected(id), Shop.nameOf(id), modifier = m, onClick = { onPick(id) }) { Creature(id, Modifier.fillMaxSize(0.78f), shadow = false, leaf = leaf) }
        } else {
            Pick(selected(id), Shop.nameOf(id), modifier = m, onClick = { onPick(id) }) {
                Creature(id, Modifier.fillMaxSize().padding(4.dp), faceLeft = true)
            }
        }
    }
}

/**
 * 换行的格子，超过 collapsedRows 行先收起来，底下一个“展开全部”。
 * 选中的那个在收起的部分里时，也会自动展开，免得看不见。
 */
@Composable
private fun <T> Grid(
    items: List<T>,
    cols: Int,
    collapsedRows: Int,
    selected: (T) -> Boolean = { false },
    cell: @Composable (T, Modifier) -> Unit,
) {
    val c = K.c
    val limit = cols * collapsedRows
    var expanded by remember { mutableStateOf(items.indexOfFirst(selected) >= limit) }
    val shown = if (expanded || items.size <= limit) items else items.take(limit)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in shown.chunked(cols)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (item in row) cell(item, Modifier.weight(1f))
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (items.size > limit) {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
            GhostButton(if (expanded) "收起" else "展开全部（${items.size}）", if (expanded) Ico.CHEVRON_UP else Ico.CHEVRON_DOWN, color = c.ink) {
                expanded = !expanded
            }
        }
    }
}

@Composable
private fun Pad(vertical: Dp = 12.dp, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = vertical)) { content() }
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(6.dp))
    T(text, 12.sp, K.c.text3)
}

/** 可选的一格：选中时黑框，没解锁的灰掉 */
@Composable
private fun Pick(
    on: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    ratio: Float = 1f,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
) {
    val c = K.c
    Column(modifier.press(scaleTo = 0.94f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(SmoothShape(14.dp))
                .background(c.chip)
                .border(if (on) 2.5.dp else 0.dp, if (on) c.ink else c.chip, SmoothShape(14.dp)),
        ) {
            Box(Modifier.fillMaxSize().grayscale(locked), contentAlignment = Alignment.Center, content = preview)
            if (locked) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                        .background(c.card, SmoothShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) { com.kongye.reader.ui.components.Icon(Ico.LOCK, size = 12.dp, color = c.text2) }
            }
        }
        Spacer(Modifier.height(4.dp))
        T(label, 11.sp, if (on) c.ink else c.text2, if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}
