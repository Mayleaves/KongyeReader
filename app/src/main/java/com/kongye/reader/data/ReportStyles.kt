package com.kongye.reader.data

data class ReportStyle(val id: String, val name: String, val icon: String, val variants: Set<String>, val color: Long, val blurb: String) {
    val item get() = "report_$id"
}

/** 报告与同色桌面图标共用拥有状态；经典和极简任一款已拥有就可使用。 */
object ReportStyles {
    val all = listOf(
        ReportStyle("light", "浅色", "default", setOf("default"), 0xFFF6F6F8, "留白、细线与一枚朱红闲章，把阅读留在干净的纸面上。"),
        ReportStyle("sunset", "落日", "sunset", setOf("sunset", "minimal"), 0xFFFAF7F0, "暖纸色与落日橙，把翻过的故事收进傍晚的余晖。"),
        ReportStyle("gold", "暗金浮雕", "gold", setOf("gold", "mingold"), 0xFF131217, "墨黑底色托起暗金细节，像为这段阅读压上一道金印。"),
        ReportStyle("neon", "霓虹幻彩", "neon", setOf("neon", "minneon"), 0xFF0F0C1B, "霓虹粉与青蓝交错，让每一段阅读在夜色里发光。"),
        ReportStyle("pixel", "像素复古", "pixel", setOf("pixel", "minpixel"), 0xFFF5F0E6, "方正线条与复古纸色，把阅读时光存成一张像素存档。"),
    )
    fun get(id: String) = all.firstOrNull { it.id == id }
    fun item(id: String) = if (id.startsWith("report_")) get(id.removePrefix("report_")) else null
    fun available(profile: Profile, id: String): Boolean = get(id)?.let { style ->
        style.variants.any { profile.has("icon_$it") }
    } ?: false
    fun selected(profile: Profile) = get(profile.reportStyle)?.id?.takeIf { available(profile, it) } ?: "light"
}
