package com.h4isenb.reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;

/** Проверки разрешений, от которых зависит работа уведомлений в фоне. */
class Perms {
    private Perms() {
    }

    static boolean notificationsOk(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 24) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        }
        return true;
    }

    static boolean exactAlarmOk(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am == null || am.canScheduleExactAlarms();
    }

    static boolean batteryOk(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** Ограничивает автоматические запросы: не больше max раз за всё время, чтобы не надоедать. */
    static boolean canNag(Context c, String key, int max) {
        SharedPreferences p = c.getSharedPreferences("reminder", Context.MODE_PRIVATE);
        int n = p.getInt(key, 0);
        if (n >= max) return false;
        p.edit().putInt(key, n + 1).apply();
        return true;
    }
}
