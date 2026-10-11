package com.h4isenb.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Звонящий будильник: громкий звук по каналу будильника (слышно и в беззвучном режиме), вибрация,
 * экран поверх блокировки, «Отложить» и «Выключить». Работает как служба переднего плана, чтобы система
 * не остановила звук. Если никто не ответил за RING_LIMIT, будильник замолкает и остаётся уведомление о пропуске.
 */
class AlarmService : Service() {

    /** Что сейчас звонит; null = ничего. Экран будильника следит за этим и закрывается сам. */
    data class Ringing(
        val ruleId: Long,
        val title: String,
        val text: String,
        val snoozeMin: Int,
        val challenge: Int,
        val startedAt: Long,
    )

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private var volume = 1f
    /** Будильники, сработавшие, пока звонил другой. */
    private val queue = ArrayDeque<Intent>()

    private val timeout = Runnable {
        val r = state.value
        if (r != null) {
            Store.addLog(this, System.currentTimeMillis(), "⏰ ${r.title} (пропущен)", r.text)
            Scheduler.ensureChannel(this)
            Scheduler.post(this, MISSED_ID, "Пропущенный будильник", r.title)
        }
        stopRinging()
    }

    private val ramp = object : Runnable {
        override fun run() {
            val p = player ?: return
            volume = minOf(1f, volume + 0.04f) // от тихого до полной громкости примерно за 25 секунд
            try {
                p.setVolume(volume, volume)
            } catch (ignored: Exception) {
            }
            if (volume < 1f) handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            ACTION_START -> begin(intent)
            else -> if (state.value == null) stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        val current = state.value
        if (current != null) {
            // уже звонит другой будильник: этот зазвонит сразу после него, а не потеряется
            queue.addLast(Intent(intent))
            startForegroundWith(buildNotification(current))
            releaseStartLock()
            return
        }
        val ruleId = intent.getLongExtra(EXTRA_RULE, 0L)
        val rule = Store.rule(this, ruleId)
        val r = Ringing(
            ruleId = ruleId,
            title = intent.getStringExtra(EXTRA_TITLE) ?: rule?.name ?: "Будильник",
            text = intent.getStringExtra(EXTRA_TEXT) ?: "",
            snoozeMin = (rule?.snoozeMin ?: 5).coerceIn(1, 60),
            challenge = rule?.challenge ?: Rule.CHALLENGE_NONE,
            startedAt = System.currentTimeMillis(),
        )
        state.value = r

        ensureChannel(this)
        startForegroundWith(buildNotification(r))

        if (wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reminder:ringing").apply {
                setReferenceCounted(false)
                acquire(RING_LIMIT + 60_000L)
            }
        }
        releaseStartLock()

        playSound(rule)
        vibrate()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RING_LIMIT)

        // экран будильника; на заблокированном телефоне его откроет полноэкранное уведомление
        try {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (ignored: Exception) {
        }
    }

    private fun soundFor(rule: Rule?): List<Uri> {
        val out = ArrayList<Uri>()
        rule?.alarmSound?.takeIf { it.isNotEmpty() }?.let { s -> runCatching { Uri.parse(s) }.getOrNull()?.let(out::add) }
        for (type in intArrayOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)) {
            RingtoneManager.getDefaultUri(type)?.let(out::add)
        }
        return out
    }

    private fun playSound(rule: Rule?) {
        val gradual = rule?.gradual ?: true
        for (uri in soundFor(rule)) {
            try {
                val p = MediaPlayer()
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                p.setDataSource(this, uri)
                p.isLooping = true
                p.prepare()
                volume = if (gradual) 0.08f else 1f
                p.setVolume(volume, volume)
                p.start()
                player = p
                if (gradual) handler.postDelayed(ramp, 1000)
                return
            } catch (ignored: Exception) {
                // мелодия могла быть удалена: пробуем следующую
            }
        }
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val pattern = longArrayOf(0, 700, 500)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun snooze() {
        val r = state.value
        if (r != null) {
            val at = System.currentTimeMillis() + r.snoozeMin * TimeMath.MIN
            Store.addSnooze(this, r.ruleId, at, r.title, r.text)
            Store.addLog(this, System.currentTimeMillis(), "⏰ ${r.title}: отложен на ${r.snoozeMin} мин", r.text)
        }
        stopRinging()
        Scheduler.scheduleNext(this)
    }

    private fun dismiss() {
        stopRinging()
        Scheduler.scheduleNext(this)
    }

    private fun stopSound() {
        handler.removeCallbacks(ramp)
        player?.let {
            try {
                it.stop()
            } catch (ignored: Exception) {
            }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun startForegroundWith(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(RING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(RING_ID, n)
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        stopSound()
        val next = queue.removeFirstOrNull()
        if (next != null) {
            state.value = null
            begin(next)
            return
        }
        state.value = null
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        releaseStartLock()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        state.value = null
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun buildNotification(r: Ringing): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val screen = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags,
        )
        val snooze = PendingIntent.getService(this, 1, intent(this, ACTION_SNOOZE), flags)
        val dismiss = PendingIntent.getService(this, 2, intent(this, ACTION_DISMISS), flags)

        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.title)
            .setContentText(r.text.ifEmpty { "Будильник" })
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notification),
                "Отложить на ${r.snoozeMin} мин", snooze).build())
        // если для выключения нужно решить пример, выключить можно только на экране будильника
        if (r.challenge == Rule.CHALLENGE_NONE) {
            b.addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notification),
                "Выключить", dismiss).build())
        }
        if (Build.VERSION.SDK_INT < 26) {
            @Suppress("DEPRECATION")
            b.setPriority(Notification.PRIORITY_MAX)
        }
        return b.build()
    }

    companion object {
        const val ACTION_START = "com.h4isenb.reminder.RING"
        const val ACTION_SNOOZE = "com.h4isenb.reminder.SNOOZE"
        const val ACTION_DISMISS = "com.h4isenb.reminder.DISMISS"
        private const val EXTRA_RULE = "rule"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val CHANNEL = "alarm_ringing"
        private const val RING_ID = 7
        private const val MISSED_ID = 8
        /** Сколько звонить без ответа. */
        private const val RING_LIMIT = 10 * 60_000L

        private val state = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> get() = state

        /** Не даёт телефону уснуть между срабатыванием системного будильника и запуском службы. */
        private var startLock: PowerManager.WakeLock? = null

        private fun intent(c: Context, action: String) = Intent(c, AlarmService::class.java).setAction(action)

        @Synchronized
        private fun releaseStartLock() {
            startLock?.let { if (it.isHeld) it.release() }
            startLock = null
        }

        fun start(c: Context, ruleId: Long, title: String, text: String) {
            synchronized(this) {
                if (startLock == null) {
                    val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
                    startLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reminder:start").apply {
                        setReferenceCounted(false)
                        acquire(30_000L)
                    }
                }
            }
            val i = intent(c, ACTION_START).putExtra(EXTRA_RULE, ruleId).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {
                // система не дала запустить службу (бывает на некоторых прошивках): хотя бы обычное уведомление
                releaseStartLock()
                Scheduler.ensureChannel(c)
                Scheduler.post(c, MISSED_ID, "⏰ $title", text)
            }
        }

        fun snooze(c: Context) = c.startService(intent(c, ACTION_SNOOZE))

        fun dismiss(c: Context) = c.startService(intent(c, ACTION_DISMISS))

        fun ensureChannel(c: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) != null) return
            val ch = NotificationChannel(CHANNEL, "Будильник", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null) // звук играет сама служба по каналу будильника
            ch.enableVibration(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }
}
