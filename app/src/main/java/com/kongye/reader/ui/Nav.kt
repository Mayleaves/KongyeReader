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
        Library.update(id) { it.copy(lastReadAt = System.currentTimeMillis()) }
        stack.removeAll { it is Screen.Reader }
        forward = true
        stack.add(Screen.Reader(id))
    }

    /** 装扮停在哪一页（0 首页、1 猫园、2 饰品、3 图标）。放在这里，去猫园试看背景再回来还是那一页 */
    var dressTab by mutableIntStateOf(0)

    /** 打开装扮的某一页；装扮已经在栈里（比如 装扮→商城→去装扮）就退回到它，不叠两层 */
    fun dress(tab: Int) {
        dressTab = tab
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
