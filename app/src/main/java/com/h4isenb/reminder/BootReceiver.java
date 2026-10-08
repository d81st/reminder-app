package com.h4isenb.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** После перезагрузки, обновления приложения или смены времени заново ставит будильник. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.ensureChannel(context);
        Scheduler.scheduleNext(context);
    }
}
