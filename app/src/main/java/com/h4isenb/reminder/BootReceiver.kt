package com.h4isenb.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** После перезагрузки, обновления приложения или смены времени заново ставит будильник. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // приёмник открыт системе, поэтому отвечаем только на свои системные события
        if (intent.action !in ACTIONS) return
        Scheduler.ensureChannel(context)
        Scheduler.scheduleNext(context)
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
