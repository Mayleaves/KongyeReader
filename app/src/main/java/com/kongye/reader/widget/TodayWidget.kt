package com.kongye.reader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import com.kongye.reader.MainActivity
import com.kongye.reader.R
import com.kongye.reader.data.Stats

/** 桌面小组件：今天读了多久，大字数字 + 探头的黑猫 */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context))
    }

    companion object {
        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context) ?: return
            val ids = m.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isNotEmpty()) m.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val list = Stats.sessions.value
            val ms = Stats.todayMillis(list)
            val total = ms / 60_000
            val h = total / 60
            val m = total % 60
            val sb = SpannableStringBuilder()
            fun num(s: String) {
                val st = sb.length
                sb.append(s)
                sb.setSpan(AbsoluteSizeSpan(30, true), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            fun unit(s: String) {
                val st = sb.length
                sb.append(s)
                sb.setSpan(AbsoluteSizeSpan(14, true), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (h > 0) {
                num("$h"); unit("时")
            }
            num("$m"); unit(if (h > 0) "分" else "分钟")
            val streak = Stats.streak(list)
            val rv = RemoteViews(context.packageName, R.layout.widget_today)
            rv.setTextViewText(R.id.widget_value, sb)
            rv.setTextViewText(R.id.widget_sub, if (streak > 0) "连续 $streak 天" else "今天还没翻书")
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(android.R.id.background, pi)
            return rv
        }
    }
}
