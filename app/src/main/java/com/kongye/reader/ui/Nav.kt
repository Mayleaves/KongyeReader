package com.kongye.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kongye.reader.data.Library
import com.kongye.reader.data.Shop

sealed class Screen(val key: String) {
    data object Home : Screen("home")
    data object Shelf : Screen("shelf")
    data class Reader(val bookId: String) : Screen("reader/$bookId")
    data object Stats : Screen("stats")
    data object Timeline : Screen("timeline")
    data object Garden : Screen("garden")
    /** 商城里看背景或挂件：在猫园里铺上、挂上试试 */
    data class GardenPreview(val item: String) : Screen("garden-preview/$item")
    data object Prefs : Screen("prefs")
    data object Shop : Screen("shop")
    data object Dress : Screen("dress")
    data object Achievements : Screen("achievements")
    data object Backup : Screen("backup")
    data object About : Screen("about")
}

/** 简单的页面栈：首页在最底下，返回键逐层退出 */
object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var forward by mutableStateOf(true)
        private set

    val top: Screen get() = stack.last()

    fun push(s: Screen) {
        if (top == s) return
        forward = true
        stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** 回到首页：首页上面的页面全部关掉 */
    fun home() {
        if (stack.size <= 1) return
        forward = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun openBook(id: String) {
        val previous=Library.get(id)?.lastReadAt ?: 0
        val now=System.currentTimeMillis()
        Library.update(id) { it.copy(lastReadAt = now) }
        com.kongye.reader.data.AchievementTracking.openedBook(id,previous,now)
        stack.removeAll { it is Screen.Reader }
        forward = true
        stack.add(Screen.Reader(id))
    }

    /** 装扮停在哪一页（0 首页、1 猫园、2 饰品、3 图标）。放在这里，去猫园试看背景再回来还是那一页 */
    var dressTab by mutableIntStateOf(0)
    var dressItem by mutableStateOf<String?>(null)
        private set
    var dressRequest by mutableIntStateOf(0)
        private set

    /** 从商城进入时保留商城，让返回仍落在刚才浏览的商品位置。 */
    fun dress(tab: Int, item: String? = null) {
        dressTab = tab
        dressItem = item
        dressRequest++
        if (top == Screen.Shop) return push(Screen.Dress)
        val i = stack.indexOfLast { it == Screen.Dress }
        if (i < 0 || i == stack.lastIndex) return push(Screen.Dress)
        forward = false
        while (stack.size > i + 1) stack.removeAt(stack.lastIndex)
    }

    /** 这件东西在装扮的哪一页用：动物→首页，背景、挂件→猫园，饰品→饰品，图标→图标 */
    fun dressTabFor(id: String): Int = when {
        id.startsWith("icon_") -> 3
        Shop.accessory(id) != null -> 2
        Shop.background(id) != null || Shop.ornament(id) != null -> 1
        else -> 0
    }

    /** 选择导入文件，由根界面注册 */
    var pickFiles: () -> Unit = {}
}
