package com.h4isenb.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/** Планирование будильников. Один будильник всегда стоит на ближайшее уведомление (основное или повтор). */
class Scheduler {
    static final String ACTION = "com.h4isenb.reminder.ALARM";
    static final String ACTION_DONE = "com.h4isenb.reminder.DONE";
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
            Set<String> done = Store.doneKeys(c);
            long now = System.currentTimeMillis();
            long from = Math.max(Store.getLastFired(c), now - 10 * MIN);

            long next = -1;
            for (TimeMath.Event e : TimeMath.events(rules, from, now + 36 * 60 * MIN, TimeZone.getDefault(), done)) {
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

    /** Заголовок уведомления: название, время, «через N мин» и номер повтора. */
    static String titleFor(TimeMath.Event e) {
        Rule r = e.rule;
        String t = r.lead > 0
                ? r.name + " · в " + TimeMath.fmt(e.minute)
                : r.name + " · " + TimeMath.fmt(e.minute);
        int left = r.lead - e.repeat * r.repeatEvery; // сколько минут осталось до самого времени
        if (r.lead > 0 || e.repeat > 0) {
            if (left > 0) t += " (через " + left + " мин)";
            else if (left == 0) t += " (сейчас)";
            else t += " (" + (-left) + " мин назад)";
        }
        if (e.repeat > 0 && e.groupFirstRepeat > 0) {
            t = "Повтор " + e.repeat + "/" + r.effectiveRepeats() + " · " + t;
        }
        return t;
    }

    /** Вызывается при срабатывании будильника: показывает всё, что подошло, и ставит следующий. */
    static void fireDue(Context c) {
        try {
            List<Rule> rules = Store.load(c);
            Set<String> done = Store.doneKeys(c);
            long now = System.currentTimeMillis();
            long last = Store.getLastFired(c);
            long from = Math.max(last, now - 10 * MIN);
            long to = now + 30_000L;

            ensureChannel(c);
            boolean shown = Perms.notificationsOk(c);
            // Накопившиеся повторы одного уведомления заменяют друг друга, показываем только последний
            List<TimeMath.Event> due = TimeMath.collapse(
                    TimeMath.events(rules, from, to, TimeZone.getDefault(), done));
            for (TimeMath.Event e : due) {
                String title = titleFor(e);
                String body = e.rule.text == null || e.rule.text.trim().isEmpty() ? e.rule.name : e.rule.text;
                int id = (int) ((e.baseAt / MIN) % 100000L) * 100 + (int) (e.rule.id % 100);
                boolean canStop = e.rule.effectiveRepeats() > 0 && e.rule.id > 0;
                post(c, id, title, body, canStop ? e.rule.id : 0, e.baseAt, canStop && e.repeat < e.rule.effectiveRepeats());
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

    /**
     * Показывает уведомление. ruleId > 0: уведомление относится к напоминанию с повторами, тап и смахивание
     * помечают его как «Готово». withDoneButton: добавить кнопку «Готово» (пока ещё есть повторы).
     */
    static void post(Context c, int id, String title, String text, long ruleId, long baseAt, boolean withDoneButton) {
        if (text == null || text.trim().isEmpty()) text = title;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent openIntent = new Intent(c, MainActivity.class);
        if (ruleId > 0) {
            openIntent.putExtra("done_rule", ruleId).putExtra("done_base", baseAt);
        }
        PendingIntent open = PendingIntent.getActivity(c, id, openIntent,
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

        if (ruleId > 0) {
            Intent doneIntent = new Intent(c, DoneReceiver.class).setAction(ACTION_DONE)
                    .putExtra("rule", ruleId).putExtra("base", baseAt).putExtra("nid", id);
            PendingIntent done = PendingIntent.getBroadcast(c, id, doneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setDeleteIntent(done);
            if (withDoneButton) {
                b.addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_notification), "Готово", done).build());
            }
        }

        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH);
            b.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);
            Uri s = soundUri(c);
            if (s != null) b.setSound(s);
        }
        nm.notify(id, b.build());
    }

    /** Простое уведомление без повторов (проверка). */
    static void post(Context c, int id, String title, String text) {
        post(c, id, title, text, 0, 0, false);
    }
}
