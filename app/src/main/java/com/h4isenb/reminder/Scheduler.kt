package com.h4isenb.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import java.util.TimeZone

/**
 * Планирование. Один системный будильник всегда стоит на ближайшее событие: уведомление (основное или повтор),
 * будильник или отложенный будильник. Если ближайшее — будильник, ставится через setAlarmClock: так система
 * будит телефон наверняка и показывает значок будильника в строке состояния.
 */
object Scheduler {
    const val ACTION = "com.h4isenb.reminder.ALARM"
    const val ACTION_DONE = "com.h4isenb.reminder.DONE"
    private const val MIN = TimeMath.MIN

    private fun pending(c: Context): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Ближайшее событие: момент и будильник ли это. */
    private class Next(val at: Long, val alarm: Boolean)

    private fun findNext(c: Context, from: Long, now: Long): Next? {
        val rules = Store.load(c)
        // на паузе ничего не приходит: ищем первое событие после неё
        val start = maxOf(from, Pause.until(c))
        // дальше 36 часов тоже смотрим: иначе будильник «по будням» с вечера пятницы не был бы поставлен на понедельник
        val ev = TimeMath.firstEvent(rules, start, TimeZone.getDefault(), Store.doneMap(c))
        val sn = Store.snoozes(c).firstOrNull()
        return when {
            ev == null && sn == null -> null
            sn != null && (ev == null || sn.at <= ev.at) -> Next(sn.at, sn.alarm)
            else -> Next(ev!!.at, ev.rule.isAlarm)
        }
    }

    /** Ближайший момент срабатывания для подписи «Следующее» (с отложенными будильниками). */
    fun nextAt(c: Context): Long? = findNext(c, System.currentTimeMillis(), System.currentTimeMillis())?.at

    /** Ставит будильник на ближайшее событие и обновляет виджет и плитку в шторке. */
    fun scheduleNext(c: Context) {
        try {
            arm(c)
        } finally {
            ReminderWidget.refresh(c)
            PauseTileService.refresh(c)
        }
    }

    private fun arm(c: Context) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(c)
            val now = System.currentTimeMillis()
            val next = findNext(c, maxOf(Store.getLastFired(c), now - 10 * MIN), now)
            if (next == null) {
                am.cancel(pi)
                return
            }
            val at = maxOf(next.at, now + 1000)
            try {
                when {
                    next.alarm && Perms.exactAlarmOk(c) -> {
                        val show = PendingIntent.getActivity(
                            c, 1, Intent(c, MainActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                    }
                    Perms.exactAlarmOk(c) -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } catch (ex: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (ignored: Exception) {
            // планирование не должно валить приложение; при следующем запуске будильник поставится снова
        }
    }

    private fun bodyOf(r: Rule): String = r.text.trim().ifEmpty { r.name }

    /** Вызывается при срабатывании будильника: показывает всё, что подошло, и ставит следующий. */
    fun fireDue(c: Context) {
        try {
            val rules = Store.load(c)
            val now = System.currentTimeMillis()
            val last = Store.getLastFired(c)
            // то, что выпало на паузу, не показываем и потом не «догоняем»
            val from = maxOf(last, now - 10 * MIN, Pause.until(c))
            val to = now + 30_000L

            ensureChannel(c)
            val shown = Perms.notificationsOk(c)
            // Накопившиеся уведомления одной серии заменяют друг друга, показываем только последнее по времени
            val due = TimeMath.collapse(TimeMath.events(rules, from, to, TimeZone.getDefault(), Store.doneMap(c)))
            var ring: Triple<Long, String, String>? = null
            for (e in due) {
                val title = TimeMath.title(e)
                val body = bodyOf(e.rule)
                if (e.rule.isAlarm) {
                    ring = Triple(e.rule.id, title, body) // если подошло несколько будильников, звонит последний
                    Store.addLog(c, now, "⏰ $title", body)
                    continue
                }
                val series = e.total > 1 && e.rule.id > 0
                // кнопка «Готово» нужна, пока в серии есть что-то после этого уведомления
                post(c, TimeMath.notificationId(e.rule.id, e.baseAt), title, body,
                    if (series) e.rule.id else 0, e.baseAt, series && e.seq < e.total,
                    snooze = Snoozed(e.rule.id, e.rule.name + " · было в " + TimeMath.fmt(e.minute), body))
                Store.addLog(c, now, if (shown) title else "$title (не показано: уведомления выключены)", body)
            }
            val ids = rules.map { it.id }.toSet()
            for (s in Store.snoozes(c)) {
                if (s.at > to) break
                Store.deleteSnooze(c, s.id)
                // напоминание удалили, пока будильник был отложен (0 — проверочный будильник из настроек)
                if (s.ruleId > 0 && s.ruleId !in ids) continue
                if (!s.alarm) {
                    post(c, TimeMath.notificationId(s.ruleId, s.at), s.title, s.text, snooze = Snoozed(s.ruleId, s.title, s.text))
                    Store.addLog(c, now, "🔁 ${s.title} (отложенное)", s.text)
                    continue
                }
                ring = Triple(s.ruleId, s.title, s.text)
                Store.addLog(c, now, "⏰ ${s.title} (отложенный)", s.text)
            }
            ring?.let { AlarmService.start(c, it.first, it.second, it.third) }
            Store.setLastFired(c, maxOf(last, to))
        } finally {
            scheduleNext(c)
        }
    }

    /** Выбранный звук; null = без звука. */
    fun soundUri(c: Context): Uri? {
        val s = Store.getSound(c)
        if (s == "silent") return null
        val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (s.isEmpty() || s == "default") return def
        return try {
            Uri.parse(s)
        } catch (e: Exception) {
            def
        }
    }

    /** Звук канала нельзя поменять после создания, поэтому у каждого звука свой канал. */
    fun channelId(c: Context): String = "reminders_" + Integer.toHexString(Store.getSound(c).hashCode())

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = channelId(c)
        if (nm.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, "Напоминания", NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(true)
            val s = soundUri(c)
            if (s == null) {
                ch.setSound(null, null)
            } else {
                ch.setSound(
                    s, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            nm.createNotificationChannel(ch)
        }
        // каналы со старыми звуками убираем, чтобы не копились в настройках телефона
        for (old in nm.notificationChannels) {
            if (old.id.startsWith("reminders") && old.id != id) nm.deleteNotificationChannel(old.id)
        }
    }

    /**
     * Показывает уведомление. ruleId > 0: уведомление относится к напоминанию с повторами, тап и смахивание
     * помечают его как «Готово». withDoneButton: добавить кнопку «Готово» (пока ещё есть повторы).
     */
    /** Что показать снова, если уведомление отложат. */
    class Snoozed(val ruleId: Long, val title: String, val text: String)

    /** Варианты «Отложить» в уведомлении, минут. */
    val SNOOZE_OPTIONS = listOf(10 to "Через 10 мин", 60 to "Через час")

    fun post(
        c: Context, id: Int, title: String, text: String?, ruleId: Long = 0, baseAt: Long = 0, withDoneButton: Boolean = false,
        snooze: Snoozed? = null,
    ) {
        val body = if (text.isNullOrBlank()) title else text
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(c, MainActivity::class.java)
        if (ruleId > 0) {
            openIntent.putExtra("done_rule", ruleId).putExtra("done_base", baseAt).putExtra("done_token", Store.intentToken(c))
        }
        val open = PendingIntent.getActivity(c, id, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, channelId(c)) else Notification.Builder(c)
        b.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)

        if (ruleId > 0) {
            val doneIntent = Intent(c, DoneReceiver::class.java).setAction(ACTION_DONE)
                .putExtra("rule", ruleId).putExtra("base", baseAt).putExtra("nid", id)
            val done = PendingIntent.getBroadcast(c, id, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            b.setDeleteIntent(done)
            if (withDoneButton) {
                b.addAction(Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notification), "Готово", done).build())
            }
        }

        if (snooze != null) {
            // «Отложить»: уведомление придёт снова через столько минут, а серия повторов остановится
            for ((min, label) in SNOOZE_OPTIONS) {
                val si = Intent(c, SnoozeReceiver::class.java).setAction(SnoozeReceiver.ACTION + min)
                    .putExtra("rule", snooze.ruleId).putExtra("base", if (ruleId > 0) baseAt else 0L).putExtra("nid", id)
                    .putExtra("title", snooze.title).putExtra("text", snooze.text).putExtra("min", min)
                val pi = PendingIntent.getBroadcast(c, id, si, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                b.addAction(Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notification), label, pi).build())
            }
        }

        if (Build.VERSION.SDK_INT < 26) {
            @Suppress("DEPRECATION")
            b.setPriority(Notification.PRIORITY_HIGH)
            @Suppress("DEPRECATION")
            b.setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
            @Suppress("DEPRECATION")
            soundUri(c)?.let { b.setSound(it) }
        }
        nm.notify(id, b.build())
    }
}
