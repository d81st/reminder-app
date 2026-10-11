package com.h4isenb.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Пауза: до заданного момента напоминания и будильники не приходят (всё, что выпало на паузу, пропускается).
 * Отложенные будильники пауза не трогает: их откладывали вручную.
 */
object Pause {
    private const val KEY = "pausedUntil"
    const val HOUR = 60 * 60 * 1000L

    fun until(c: Context): Long = Store.prefs(c).getLong(KEY, 0L)

    fun active(c: Context, now: Long = System.currentTimeMillis()): Boolean = until(c) > now

    /** Поставить паузу до момента until (0 — снять) и обновить будильник, виджет и плитку в шторке. */
    fun set(c: Context, until: Long) {
        Store.prefs(c).edit().putLong(KEY, until).apply()
        val now = System.currentTimeMillis()
        // после паузы не «догоняем» пропущенное
        if (until > now) Store.setLastFired(c, maxOf(Store.getLastFired(c), now))
        Store.addLog(c, now, if (until > now) "Пауза " + Phrases.pauseUntil(until, now) else "Пауза снята", "")
        Scheduler.scheduleNext(c)
    }

    fun forHour(c: Context) = set(c, System.currentTimeMillis() + HOUR)

    fun resume(c: Context) = set(c, 0L)

    /** Нажатие в шторке или на виджете: нет паузы — пауза на час, есть — снять. */
    fun toggle(c: Context) = if (active(c)) resume(c) else forHour(c)
}

/** Кнопка «Пауза» на виджете. Не экспортируется: другие приложения поставить паузу не могут. */
class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) Pause.toggle(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.h4isenb.reminder.TOGGLE_PAUSE"
    }
}
