package com.kongye.reader.ui

import java.time.LocalDate
import java.util.Locale

object Fmt {
    private val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 1时5分 / 25分钟 / 2小时 */
    fun cn(ms: Long): String {
        val total = ms / 60_000
        val h = total / 60
        val m = total % 60
        return when {
            h > 0 && m > 0 -> "${h}时${m}分"
            h > 0 -> "${h}小时"
            else -> "${m}分钟"
        }
    }

    /** 2h30m */
    fun hm(ms: Long): String {
        val total = ms / 60_000
        val h = total / 60
        val m = total % 60
        return when {
            h > 0 && m > 0 -> "${h}h${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /** 首页“今日阅读 0:25” */
    fun clock(ms: Long): String {
        val total = ms / 60_000
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    fun time(ms: Long): String {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return String.format(Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }

    fun words(chars: Long): String = when {
        chars >= 10_000 -> String.format(Locale.US, "%.1f万字", chars / 10_000.0)
        else -> "${chars}字"
    }

    fun percent(p: Float): String = String.format(Locale.US, "%.1f%%", p * 100)

    fun weekday(d: LocalDate): String = weekdays[d.dayOfWeek.value - 1]

    fun md(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日"
}
