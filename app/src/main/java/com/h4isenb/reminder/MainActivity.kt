package com.h4isenb.reminder

import android.Manifest
import android.content.Intent
import android.graphics.Rect
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb

/** Состояние разрешений для вкладки «Настройки». */
data class PermState(
    val notifications: Boolean = true,
    val battery: Boolean = true,
    val exact: Boolean = true,
    val fullScreen: Boolean = true,
)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    private var dark by mutableStateOf(false)
    private var perms by mutableStateOf(PermState())
    private var keyboardOpen by mutableStateOf(false)
    private var soundTitle by mutableStateOf("")

    private var preview: Ringtone? = null
    private var permStep = 0
    private var chainActive = false
    private var fromUser = false
    /** Для какого напоминания выбирается звук будильника; null = общий звук уведомлений. */
    private var soundFor: Long? = null

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (Build.VERSION.SDK_INT >= 33 && !Perms.notificationsOk(this) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
            (fromUser || Perms.canNag(this, "nagNotif", 2))
        ) {
            // Система больше не показывает диалог (отказано насовсем): ведём в настройки уведомлений
            openNotificationSettings()
        } else {
            afterAsk()
        }
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { afterAsk() }
    private val soundLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK && res.data != null) handleSound(res.data!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = Store.isDark(this)
        super.onCreate(savedInstanceState)
        handleDoneIntent(intent)
        soundTitle = notificationSoundTitle()
        applyBars()

        setContent {
            ReminderTheme(dark) {
                MainScreen(
                    vm = vm,
                    perms = perms,
                    keyboardOpen = keyboardOpen,
                    soundTitle = soundTitle,
                    actions = actions,
                )
            }
        }

        // Когда открыта клавиатура, нижнюю панель прячем, чтобы не съедала место
        val root = window.decorView
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            root.getWindowVisibleDisplayFrame(r)
            val h = root.rootView.height
            keyboardOpen = h > 0 && (h - r.bottom) > h * 0.15f
        }

        Scheduler.ensureChannel(this)
        AlarmService.ensureChannel(this)
        Scheduler.scheduleNext(this)
        refreshPerms()
        // При первом открытии (и при каждом новом запуске) сразу просим недостающие разрешения
        if (savedInstanceState == null) root.post { startPermissionChain() }
    }

    private fun applyBars() {
        val p = palette(dark)
        window.statusBarColor = (if (dark) androidx.compose.ui.graphics.Color(0xFF1E1B4B) else IndigoDark).toArgb()
        window.navigationBarColor = p.card.toArgb()
        if (Build.VERSION.SDK_INT >= 26) {
            val dv = window.decorView
            @Suppress("DEPRECATION")
            dv.systemUiVisibility = if (dark) {
                dv.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                dv.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDoneIntent(intent)
    }

    /** Тап по уведомлению с повторами означает «увидел»: остальные повторы отменяются. */
    private fun handleDoneIntent(intent: Intent?) {
        if (intent == null) return
        val rule = intent.getLongExtra("done_rule", 0L)
        val base = intent.getLongExtra("done_base", 0L)
        if (rule > 0 && base > 0) {
            Store.markDone(this, rule, base)
            Scheduler.scheduleNext(this)
            intent.removeExtra("done_rule")
            intent.removeExtra("done_base")
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
        vm.loadLog()
        refreshPerms()
    }

    override fun onPause() {
        super.onPause()
        stopPreview()
        vm.flush()
    }

    // =====================================================================
    // действия интерфейса
    // =====================================================================

    private val actions by lazy {
        MainActions(
        toggleTheme = {
            dark = !dark
            Store.setDark(this, dark)
            applyBars()
        },
        askNotifications = { fromUser = true; chainActive = false; askNotifications() },
        askBattery = { fromUser = true; chainActive = false; askBattery() },
        askExact = { fromUser = true; chainActive = false; askExactAlarm() },
        askFullScreen = { fromUser = true; chainActive = false; askFullScreen() },
        pickNotificationSound = { pickSound(null) },
        pickAlarmSound = { key -> pickSound(key) },
        alarmSoundTitle = { s -> alarmSoundTitle(s) },
        sendTest = { sendTest() },
        testAlarm = { AlarmService.start(this, 0, "Проверка будильника", "Так будет звонить будильник") },
        openAppSettings = {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                toast("Не удалось открыть настройки")
            }
        },
        toast = { toast(it) },
        version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "",
        )
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // =====================================================================
    // разрешения
    // =====================================================================

    private fun refreshPerms() {
        perms = PermState(
            notifications = Perms.notificationsOk(this),
            battery = Perms.batteryOk(this),
            exact = Perms.exactAlarmOk(this),
            fullScreen = Perms.fullScreenOk(this),
        )
    }

    private fun startPermissionChain() {
        permStep = 0
        advancePermissions()
    }

    /** По очереди просит: уведомления, точные будильники, экран будильника, работу в фоне. Каждый шаг раз за запуск. */
    private fun advancePermissions() {
        while (permStep < 4) {
            val s = permStep++
            if (s == 0 && !Perms.notificationsOk(this) && (Build.VERSION.SDK_INT >= 33 || Perms.canNag(this, "nagNotifOld", 3))) {
                chainActive = true
                fromUser = false
                askNotifications()
                return
            }
            if (s == 1 && !Perms.exactAlarmOk(this)) {
                chainActive = true
                askExactAlarm()
                return
            }
            if (s == 2 && !Perms.fullScreenOk(this) && vm.rules.any { it.isAlarm } && Perms.canNag(this, "nagFullScreen", 2)) {
                chainActive = true
                askFullScreen()
                return
            }
            if (s == 3 && !Perms.batteryOk(this) && Perms.canNag(this, "nagBattery", 3)) {
                chainActive = true
                askBattery()
                return
            }
        }
        chainActive = false
        refreshPerms()
        vm.refreshStatus()
        Scheduler.scheduleNext(this)
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openNotificationSettings()
    }

    private fun launchSettings(vararg intents: Intent) {
        for (i in intents) {
            try {
                settingsLauncher.launch(i)
                return
            } catch (ignored: Exception) {
            }
        }
        afterAsk()
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 26) {
            launchSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } else {
            launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun askExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) return afterAsk()
        launchSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }

    private fun askFullScreen() {
        if (Build.VERSION.SDK_INT < 34) return afterAsk()
        launchSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
    }

    @Suppress("BatteryLife")
    private fun askBattery() {
        launchSettings(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    private fun afterAsk() {
        refreshPerms()
        vm.refreshStatus()
        Scheduler.scheduleNext(this)
        if (chainActive) {
            chainActive = false
            advancePermissions()
        }
    }

    // =====================================================================
    // звук
    // =====================================================================

    private fun notificationSoundTitle(): String {
        val s = Store.getSound(this)
        if (s == "silent") return "Без звука"
        if (s.isEmpty() || s == "default") return "По умолчанию"
        return ringtoneTitle(Uri.parse(s)) ?: "Выбранный звук"
    }

    private fun alarmSoundTitle(s: String): String {
        if (s.isEmpty()) return "Стандартный будильник"
        return ringtoneTitle(Uri.parse(s)) ?: "Выбранная мелодия"
    }

    private fun ringtoneTitle(u: Uri): String? = try {
        RingtoneManager.getRingtone(this, u)?.getTitle(this)
    } catch (e: Exception) {
        null
    }

    private fun pickSound(ruleKey: Long?) {
        soundFor = ruleKey
        val alarm = ruleKey != null
        val type = if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        val current: Uri? = if (alarm) {
            vm.rules.firstOrNull { it.key == ruleKey }?.alarmSound?.takeIf { it.isNotEmpty() }?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(type)
        } else {
            Scheduler.soundUri(this)
        }
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (alarm) RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE else type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, if (alarm) "Мелодия будильника" else "Звук уведомлений")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, !alarm)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(type))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        try {
            soundLauncher.launch(i)
        } catch (e: Exception) {
            toast("Не удалось открыть выбор звука")
        }
    }

    private fun handleSound(data: Intent) {
        @Suppress("DEPRECATION")
        val picked: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val key = soundFor
        if (key != null) {
            val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val value = if (picked == null || picked == def) "" else picked.toString()
            vm.update(key) { it.copy(alarmSound = value) }
            vm.persist()
            return
        }
        val value = when (picked) {
            null -> "silent"
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) -> "default"
            else -> picked.toString()
        }
        Store.setSound(this, value)
        Scheduler.ensureChannel(this)
        soundTitle = notificationSoundTitle()
        playPreview(Scheduler.soundUri(this))
    }

    private fun playPreview(u: Uri?) {
        stopPreview()
        if (u == null) return
        try {
            preview = RingtoneManager.getRingtone(this, u)?.also {
                it.play()
                handler.postDelayed(::stopPreview, 3000)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun stopPreview() {
        preview?.let {
            try {
                it.stop()
            } catch (ignored: Exception) {
            }
        }
        preview = null
    }

    private fun sendTest() {
        if (!Perms.notificationsOk(this)) {
            toast("Сначала разрешите уведомления")
            return
        }
        Scheduler.ensureChannel(this)
        val body = vm.rules.firstOrNull()?.text?.takeIf { it.isNotBlank() } ?: "Тестовое уведомление"
        Scheduler.post(this, 1, "Проверка", body)
        Store.addLog(this, System.currentTimeMillis(), "Проверка", body)
        vm.loadLog()
    }
}

/** Всё, что экран может попросить у активности (разрешения, выбор звука и т. п.). */
class MainActions(
    val toggleTheme: () -> Unit,
    val askNotifications: () -> Unit,
    val askBattery: () -> Unit,
    val askExact: () -> Unit,
    val askFullScreen: () -> Unit,
    val pickNotificationSound: () -> Unit,
    val pickAlarmSound: (Long) -> Unit,
    val alarmSoundTitle: (String) -> String,
    val sendTest: () -> Unit,
    val testAlarm: () -> Unit,
    val openAppSettings: () -> Unit,
    val toast: (String) -> Unit,
    val version: String,
)
