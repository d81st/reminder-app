package com.h4isenb.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Расчёт времён и планирование будильников. Один будильник всегда стоит на ближайшее уведомление. */
class Scheduler {
    static final String CHANNEL = "reminders";
    static final String ACTION = "com.h4isenb.reminder.ALARM";
    static final long MIN = 60_000L;

    static class Event {
        Rule rule;
        int index;
        int minute; // время события (минуты от полуночи), без учёта «за N минут»
        long at;    // момент, когда нужно показать уведомление
    }

    static String fmt(int m) {
        return String.format(Locale.US, "%02d:%02d", (m / 60) % 24, m % 60);
    }

    /** Времена расписания с учётом «не повторять время другого расписания». */
    static List<Integer> times(List<Rule> rules, int idx) {
        Rule r = rules.get(idx);
        List<Integer> t = r.rawTimes();
        if (r.skipOverlap) {
            Set<Integer> other = new HashSet<>();
            for (int i = 0; i < rules.size(); i++) {
                Rule o = rules.get(i);
                if (i != idx && o.enabled && !o.skipOverlap) other.addAll(o.rawTimes());
            }
            t.removeAll(other);
        }
        return t;
    }

    /** Все уведомления в промежутке (fromExcl, toIncl]. */
    static List<Event> events(List<Rule> rules, long fromExcl, long toIncl) {
        List<Event> out = new ArrayList<>();
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(fromExcl);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        day.add(Calendar.DAY_OF_YEAR, -1);

        while (day.getTimeInMillis() - 3 * 60 * MIN <= toIncl) {
            for (int i = 0; i < rules.size(); i++) {
                Rule r = rules.get(i);
                if (!r.enabled) continue;
                for (int t : times(rules, i)) {
                    Calendar c = (Calendar) day.clone();
                    c.add(Calendar.MINUTE, t - r.lead);
                    long at = c.getTimeInMillis();
                    if (at > fromExcl && at <= toIncl) {
                        Event e = new Event();
                        e.rule = r;
                        e.index = i;
                        e.minute = t;
                        e.at = at;
                        out.add(e);
                    }
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        Collections.sort(out, (a, b) -> Long.compare(a.at, b.at));
        return out;
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Ставит будильник на ближайшее уведомление. */
    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(c);
        List<Rule> rules = Store.load(c);
        long now = System.currentTimeMillis();
        long from = Math.max(Store.getLastFired(c), now - 10 * MIN);

        long next = -1;
        for (Event e : events(rules, from, now + 36 * 60 * MIN)) {
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
    }

    /** Вызывается при срабатывании будильника: показывает всё, что подошло, и ставит следующий. */
    static void fireDue(Context c) {
        List<Rule> rules = Store.load(c);
        long now = System.currentTimeMillis();
        long last = Store.getLastFired(c);
        long from = Math.max(last, now - 10 * MIN);
        long to = now + 30_000L;

        ensureChannel(c);
        for (Event e : events(rules, from, to)) {
            String title = e.rule.lead > 0
                    ? e.rule.name + " · в " + fmt(e.minute) + " (через " + e.rule.lead + " мин)"
                    : e.rule.name + " · " + fmt(e.minute);
            int id = (int) ((e.at / MIN) % 1000000L) * 10 + (int) (e.rule.id % 10);
            post(c, id, title, e.rule.text);
        }
        Store.setLastFired(c, Math.max(last, to));
        scheduleNext(c);
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
