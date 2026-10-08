package com.h4isenb.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

/** Планирование будильников. Один будильник всегда стоит на ближайшее уведомление. */
class Scheduler {
    static final String CHANNEL = "reminders";
    static final String ACTION = "com.h4isenb.reminder.ALARM";
    private static final long MIN = TimeMath.MIN;

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Ставит будильник на ближайшее уведомление. */
    static void scheduleNext(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = pending(c);
            List<Rule> rules = Store.load(c);
            long now = System.currentTimeMillis();
            long from = Math.max(Store.getLastFired(c), now - 10 * MIN);

            long next = -1;
            for (TimeMath.Event e : TimeMath.events(rules, from, now + 36 * 60 * MIN)) {
                if (next < 0 || e.at < next) next = e.at;
            }
            if (next < 0) {
                am.cancel(pi);
                return;
            }
            long at = Math.max(next, now + 1000);
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
            } catch (SecurityException ex) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception ignored) {
            // планирование не должно валить приложение; при следующем запуске будильник поставится снова
        }
    }

    /** Вызывается при срабатывании будильника: показывает всё, что подошло, и ставит следующий. */
    static void fireDue(Context c) {
        try {
            List<Rule> rules = Store.load(c);
            long now = System.currentTimeMillis();
            long last = Store.getLastFired(c);
            long from = Math.max(last, now - 10 * MIN);
            long to = now + 30_000L;

            ensureChannel(c);
            for (TimeMath.Event e : TimeMath.events(rules, from, to)) {
                String title = e.rule.lead > 0
                        ? e.rule.name + " · в " + TimeMath.fmt(e.minute) + " (через " + e.rule.lead + " мин)"
                        : e.rule.name + " · " + TimeMath.fmt(e.minute);
                String body = e.rule.text == null || e.rule.text.trim().isEmpty() ? e.rule.name : e.rule.text;
                int id = (int) ((e.at / MIN) % 100000L) * 100 + (int) (e.rule.id % 100);
                boolean shown = Perms.notificationsOk(c);
                post(c, id, title, body);
                Store.addLog(c, now, shown ? title : title + " (не показано: уведомления выключены)", body);
            }
            Store.setLastFired(c, Math.max(last, to));
        } finally {
            scheduleNext(c);
        }
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }

    static void post(Context c, int id, String title, String text) {
        if (text == null || text.trim().isEmpty()) text = title;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH);
            b.setDefaults(Notification.DEFAULT_ALL);
        }
        nm.notify(id, b.build());
    }
}
