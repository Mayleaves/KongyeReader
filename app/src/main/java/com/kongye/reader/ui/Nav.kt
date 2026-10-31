package com.kongye.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kongye.reader.data.Library

sealed class Screen(val key: String) {
    data object Home : Screen("home")
    data object Shelf : Screen("shelf")
    data class Reader(val bookId: String) : Screen("reader/$bookId")
    data object Stats : Screen("stats")
    data object Timeline : Screen("timeline")
    data object Garden : Screen("garden")
    data object Prefs : Screen("prefs")
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

    fun openBook(id: String) {
        Library.update(id) { it.copy(lastReadAt = System.currentTimeMillis()) }
        stack.removeAll { it is Screen.Reader }
        forward = true
        stack.add(Screen.Reader(id))
    }

    /** 选择导入文件，由根界面注册 */
    var pickFiles: () -> Unit = {}
}
