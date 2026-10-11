package com.h4isenb.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager

/** Проверки разрешений, от которых зависит работа уведомлений и будильников в фоне. */
object Perms {
    fun notificationsOk(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        return nm == null || nm.areNotificationsEnabled()
    }

    fun exactAlarmOk(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        return am == null || am.canScheduleExactAlarms()
    }

    fun batteryOk(c: Context): Boolean {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager?
        return pm == null || pm.isIgnoringBatteryOptimizations(c.packageName)
    }

    /** Будильник на весь экран поверх блокировки (на Android 14 это отдельное разрешение). */
    fun fullScreenOk(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        return nm == null || nm.canUseFullScreenIntent()
    }

    /** Ограничивает автоматические запросы: не больше max раз за всё время, чтобы не надоедать. */
    fun canNag(c: Context, key: String, max: Int): Boolean {
        val p = Store.prefs(c)
        val n = p.getInt(key, 0)
        if (n >= max) return false
        p.edit().putInt(key, n + 1).apply()
        return true
    }
}
