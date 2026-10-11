package com.h4isenb.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Кнопка «Готово» в уведомлении или смахивание уведомления: останавливает его повторы. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra("rule", 0L)
        val baseAt = intent.getLongExtra("base", 0L)
        val nid = intent.getIntExtra("nid", 0)
        Store.markDone(context, ruleId, baseAt)
        if (nid != 0) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.cancel(nid)
        Scheduler.scheduleNext(context)
    }
}
