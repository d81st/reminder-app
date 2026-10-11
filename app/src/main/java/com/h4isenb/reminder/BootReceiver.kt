package com.h4isenb.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** После перезагрузки, обновления приложения или смены времени заново ставит будильник. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.ensureChannel(context)
        Scheduler.scheduleNext(context)
    }
}
