package com.h4isenb.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Кнопки «Через 10 мин» и «Через час» в уведомлении: показать его снова позже, повторы серии остановить. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra("rule", 0L)
        val baseAt = intent.getLongExtra("base", 0L)
        val nid = intent.getIntExtra("nid", 0)
        val min = intent.getIntExtra("min", 10).coerceIn(1, 24 * 60)
        val title = intent.getStringExtra("title").orEmpty()
        val text = intent.getStringExtra("text").orEmpty()
        val now = System.currentTimeMillis()
        if (ruleId > 0 && baseAt > 0) Store.markDone(context, ruleId, baseAt)
        Store.addSnooze(context, ruleId, now + min * TimeMath.MIN, title, text, alarm = false)
        Store.addLog(context, now, "🔁 $title: отложено на " + if (min >= 60) "${min / 60} ч" else "$min мин", text)
        if (nid != 0) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.cancel(nid)
        Scheduler.scheduleNext(context)
    }

    companion object {
        const val ACTION = "com.h4isenb.reminder.SNOOZE_"
    }
}
