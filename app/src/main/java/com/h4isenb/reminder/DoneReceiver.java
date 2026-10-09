package com.h4isenb.reminder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Кнопка «Готово» в уведомлении или смахивание уведомления: останавливает его повторы. */
public class DoneReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long ruleId = intent.getLongExtra("rule", 0L);
        long baseAt = intent.getLongExtra("base", 0L);
        int nid = intent.getIntExtra("nid", 0);
        Store.markDone(context, ruleId, baseAt);
        if (nid != 0) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(nid);
        }
        Scheduler.scheduleNext(context);
    }
}
