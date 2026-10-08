package com.h4isenb.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.List;

/** Планирование будильников. Один будильник всегда стоит на ближайшее уведомление. */
class Scheduler {
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

    /** Выбранный звук; null = без звука. */
    static Uri soundUri(Context c) {
        String s = Store.getSound(c);
        if ("silent".equals(s)) return null;
        Uri def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (s == null || s.isEmpty() || "default".equals(s)) return def;
        try {
            return Uri.parse(s);
        } catch (Exception e) {
            return def;
        }
    }

    /** Звук канала нельзя поменять после создания, поэтому у каждого звука свой канал. */
    static String channelId(Context c) {
        return "reminders_" + Integer.toHexString(String.valueOf(Store.getSound(c)).hashCode());
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String id = channelId(c);
        if (nm.getNotificationChannel(id) == null) {
            NotificationChannel ch = new NotificationChannel(id, "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            Uri s = soundUri(c);
            if (s == null) {
                ch.setSound(null, null);
            } else {
                ch.setSound(s, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            nm.createNotificationChannel(ch);
        }
        // каналы со старыми звуками убираем, чтобы не копились в настройках телефона
        for (NotificationChannel old : nm.getNotificationChannels()) {
            String oid = old.getId();
            if (oid.startsWith("reminders") && !oid.equals(id)) nm.deleteNotificationChannel(oid);
        }
    }

    static void post(Context c, int id, String title, String text) {
        if (text == null || text.trim().isEmpty()) text = title;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, channelId(c))
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
            b.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);
            Uri s = soundUri(c);
            if (s != null) b.setSound(s);
        }
        nm.notify(id, b.build());
    }
}
