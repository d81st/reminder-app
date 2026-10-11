package com.h4isenb.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar
import java.util.TimeZone

/**
 * Снимки главных экранов в светлой и тёмной теме (PNG в app/build/screenshots).
 * Данные и время фиксированы: понедельник, 10:18.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-hdpi")
class ScreenshotTest {
    private val tz = TimeZone.getTimeZone("Europe/Moscow")
    private var now = 0L

    private val rules = listOf(
        Rule("Тетрадь и Telegram", 9 * 60, 17 * 60, 60, false, "Запиши в тетрадь и отправь сообщение",
            id = 1, color = 0, days = Rule.WORKDAYS),
        Rule("Сообщение в Telegram", 9 * 60, 17 * 60, 30, true, "Отправь сообщение в Telegram",
            id = 2, color = 1, days = Rule.WORKDAYS, repeatCount = 3, repeatEvery = 5),
        Rule("Подъём", 7 * 60, 7 * 60, 60, false, "Доброе утро!", id = 3, kind = Rule.KIND_ALARM,
            days = Rule.WORKDAYS, challenge = Rule.CHALLENGE_MATH),
        Rule("Обед", 13 * 60, 13 * 60, 60, false, "Пора поесть", id = 4, color = 2, enabled = false),
    )

    private val actions = MainActions(
        toggleTheme = {}, askNotifications = {}, askBattery = {}, askExact = {}, askFullScreen = {},
        pickNotificationSound = {}, pickAlarmSound = {}, alarmSoundTitle = { "Стандартный будильник" },
        sendTest = {}, testAlarm = {}, openAppSettings = {}, toast = {}, scanQr = {}, importImage = {},
        importText = {}, shareImage = { _, _ -> }, shareText = {}, version = "2.4",
    )

    @Before
    fun setUp() {
        TimeZone.setDefault(tz)
        now = Calendar.getInstance(tz).apply {
            clear()
            set(2026, Calendar.OCTOBER, 12, 10, 18, 0)
        }.timeInMillis
    }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/$name.png") {
            ReminderTheme(dark) {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) { content() }
            }
        }
    }

    @Composable
    private fun Home(pausedUntil: Long = 0L) {
        // как в приложении: на паузе «следующее» — первое после неё
        val next = TimeMath.nextByRule(rules, maxOf(now, pausedUntil)).entries.associate { (i, e) -> rules[i].key to e }
        HomePage(
            rules = rules, loaded = true, nextByKey = next, today = TimeMath.today(rules, now), now = now,
            perms = PermState(), onToggleTheme = {}, onOpen = {}, onEnabled = { _, _ -> }, onDelete = {},
            showTip = pausedUntil == 0L, pausedUntil = pausedUntil,
        )
    }

    @Composable
    private fun Editor(i: Int) {
        RuleEditor(rules[i], rules, now, actions, onChange = {}, onDelete = {}, onClose = {})
    }

    @Test fun homeLight() = shot("1_home_light", false) { Home() }
    @Test fun homeDark() = shot("2_home_dark", true) { Home() }
    @Test fun homePausedLight() = shot("7_home_paused_light", false) { Home(pausedUntil = now + 72 * 60_000L) }
    // редактор длинный: снимаем высоким экраном, чтобы был виден пример «Как это будет»
    @Config(qualifiers = "w390dp-h1900dp-hdpi")
    @Test fun editorQuietLight() = shot("3_editor_quiet_light", false) { Editor(0) }
    @Config(qualifiers = "w390dp-h1900dp-hdpi")
    @Test fun editorPersistentLight() = shot("4_editor_persistent_light", false) { Editor(1) }
    @Config(qualifiers = "w390dp-h1900dp-hdpi")
    @Test fun editorAlarmDark() = shot("6_editor_alarm_dark", true) { Editor(2) }
    @Test fun settingsLight() = shot("5_settings_light", false) {
        SettingsPage(PermState(notifications = false), "По умолчанию", actions, onExport = {}, onPaste = {})
    }
}
