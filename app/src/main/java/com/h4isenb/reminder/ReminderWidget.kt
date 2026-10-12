package com.h4isenb.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Виджет на рабочем столе: ближайшее напоминание и кнопка «Пауза». */
class ReminderWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context))
    }

    companion object {
        private val ru = Locale("ru")

        /** Перерисовать все виджеты (после любого изменения расписания или паузы). */
        fun refresh(c: Context) {
            try {
                val m = AppWidgetManager.getInstance(c) ?: return
                val ids = m.getAppWidgetIds(ComponentName(c, ReminderWidget::class.java))
                if (ids.isNotEmpty()) m.updateAppWidget(ids, views(c))
            } catch (ignored: Exception) {
            }
        }

        private fun views(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget_next)
            val now = System.currentTimeMillis()
            val pausedUntil = Pause.until(c)
            val paused = pausedUntil > now
            val rules = Store.load(c)
            val e = TimeMath.firstEvent(rules, maxOf(now, pausedUntil), TimeZone.getDefault(), Store.doneMap(c))
            if (e == null) {
                v.setTextViewText(R.id.w_caption, if (paused) "На паузе " + Phrases.pauseUntil(pausedUntil, now) else "Ближайших напоминаний нет")
                v.setTextViewText(R.id.w_time, "—")
                v.setTextViewText(R.id.w_name, if (rules.isEmpty()) "Добавьте первое в приложении" else "Всё спокойно")
            } else {
                val t = e.baseAt + e.rule.lead * TimeMath.MIN
                val day = when {
                    sameDay(t, now) -> ""
                    sameDay(t, now + 24 * 60 * TimeMath.MIN) -> "завтра "
                    t - now < 6 * 24 * 60 * TimeMath.MIN -> SimpleDateFormat("EE", ru).format(Date(t)) + " "
                    else -> SimpleDateFormat("d MMM", ru).format(Date(t)).trimEnd('.') + " "
                }
                v.setTextViewText(R.id.w_caption,
                    if (paused) "На паузе " + Phrases.pauseUntil(pausedUntil, now) + " · потом" else (if (e.rule.isAlarm) "Будильник" else "Следующее"))
                v.setTextViewText(R.id.w_time, day + TimeMath.fmt(e.minute))
                v.setTextViewText(R.id.w_name, e.rule.name)
            }
            v.setTextViewText(R.id.w_pause, if (paused) "Продолжить" else "Пауза 1 ч")

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = PendingIntent.getActivity(c, 10, Intent(c, MainActivity::class.java), flags)
            v.setOnClickPendingIntent(R.id.w_root, open)
            val toggle = Intent(c, PauseReceiver::class.java).setAction(PauseReceiver.ACTION_TOGGLE)
            v.setOnClickPendingIntent(R.id.w_pause, PendingIntent.getBroadcast(c, 11, toggle, flags))
            return v
        }
    }
}
