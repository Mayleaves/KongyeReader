package com.kongye.reader.data

import kotlinx.serialization.Serializable

/** 书架上的一本书。正文和目录另存在 books/<id>/ 下，这里只放元数据和进度 */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String = "",
    val format: String = "txt",
    val chars: Long = 0,
    val chapterCount: Int = 0,
    val addedAt: Long = 0,
    val lastReadAt: Long = 0,
    /** 读到第几章，以及该章正文里的字符偏移 */
    val chapter: Int = 0,
    val offset: Int = 0,
    val progress: Float = 0f,
    val readMillis: Long = 0,
    val finished: Boolean = false,
    val hasCover: Boolean = false,
    val fingerprint: String = "",
    val bookmarks: List<Bookmark> = emptyList(),
    /** 简介和标签：EPUB 取自书里的元数据，TXT 取自开头的“简介：”“标签：” */
    val intro: String = "",
    val tags: List<String> = emptyList(),
    /** 1.2.0 之前导入的书没有简介，启动时从正文开头补一次，补过就不再找 */
    val metaScanned: Boolean = false,
    /** 原始文件名用于重新识别，独立于用户修改后的书名。 */
    val sourceName: String = "",
)

@Serializable
data class Bookmark(val chapter: Int, val offset: Int, val excerpt: String, val createdAt: Long)

/** 章节在 content.txt 里的范围（不含标题） */
@Serializable
data class Chapter(val title: String, val start: Int, val end: Int)

/** 一次连续阅读：从打开书到离开（或长时间没翻页） */
@Serializable
data class Session(
    val bookId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val millis: Long,
    val pages: Int = 0,
    val fromChapter: String = "",
    val toChapter: String = "",
    val finished: Boolean = false,
)

@Serializable
data class AppSettings(
    /** 0 跟随系统 1 浅色 2 深色 */
    val theme: Int = 0,
    val fontSize: Int = 19,
    /** 0 系统默认 1 霞鹜文楷 GB 2 仓耳今楷 */
    val font: Int = 0,
    /** 行高 = 字号 × lineHeight */
    val lineHeight: Float = 1.8f,
    /** 段与段之间多空出的高度，单位是字号 */
    val paraSpacing: Float = 0.4f,
    /** 首行缩进几个字 */
    val indent: Float = 2f,
    /** 正文离页眉、页脚的距离（dp） */
    val marginV: Int = 12,
    /** 正文左右边距（dp） */
    val marginH: Int = 24,
    /** 阅读页亮度 0~1；小于 0 跟随系统 */
    val brightness: Float = -1f,
    /** 白 / 米 / 灰 / 青 */
    val paper: Int = 0,
    val night: Boolean = false,
    val nightFollowSystem: Boolean = true,
    /** 0 平移 1 覆盖 2 上下滚动 */
    val turnMode: Int = 0,
    /** 打开应用直接回到上次读的书 */
    val resumeLast: Boolean = true,
    val volumeKeys: Boolean = true,
    val keepScreenOn: Boolean = true,
    val tapLeftNext: Boolean = false,
    val showStatus: Boolean = true,
    /** 书架排序：0 最近阅读 1 导入时间 2 书名 */
    val shelfSort: Int = 0,
)
