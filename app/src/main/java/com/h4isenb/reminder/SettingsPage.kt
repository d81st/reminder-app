package com.h4isenb.reminder

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Настройки: группы строк со значком, подписью, значением справа и стрелкой, как в системных настройках. */
@Composable
internal fun SettingsPage(
    perms: PermState, soundTitle: String, actions: MainActions, onExport: () -> Unit, onPaste: () -> Unit, onLog: () -> Unit = {},
) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp)
    ) {
        PageTitle("Настройки")

        SettingsGroup("Оформление") {
            SettingsRow(Icons.Outlined.DarkMode, "Тёмная тема", onClick = actions.toggleTheme, trailing = {
                AccentSwitch(p.dark, p.primary, { actions.toggleTheme() }, thumbOn = p.onPrimary)
            })
        }

        SettingsGroup("Обмен с коллегами", "Настройте расписание один раз и передайте его другим: QR-кодом с экрана, картинкой или текстом в мессенджере.") {
            SettingsRow(Icons.Outlined.Share, "Поделиться напоминаниями", subtitle = "QR-код, картинка или текст", onClick = onExport)
            SettingsDivider()
            SettingsRow(Icons.Outlined.QrCodeScanner, "Сканировать QR-код", onClick = actions.scanQr)
            SettingsDivider()
            SettingsRow(Icons.Outlined.Image, "Из картинки", subtitle = "снимок экрана с QR-кодом", onClick = actions.importImage)
            SettingsDivider()
            SettingsRow(Icons.Outlined.ContentPaste, "Из текста", subtitle = "код из сообщения", onClick = onPaste)
        }

        SettingsGroup("Звук", "Мелодия будильника выбирается в карточке будильника.") {
            SettingsRow(Icons.Outlined.MusicNote, "Звук уведомлений", value = soundTitle, onClick = actions.pickNotificationSound)
        }

        SettingsGroup("Разрешения", "Без них напоминания могут не прийти или прийти с опозданием.") {
            PermissionRow(Icons.Outlined.Notifications, "Уведомления", "без этого напоминания не появятся", perms.notifications, actions.askNotifications)
            SettingsDivider()
            PermissionRow(Icons.Outlined.BatteryChargingFull, "Работа в фоне", "не ограничивать батарею", perms.battery, actions.askBattery)
            if (Build.VERSION.SDK_INT >= 31) {
                SettingsDivider()
                PermissionRow(Icons.Outlined.Alarm, "Точные будильники", "минута в минуту", perms.exact, actions.askExact)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                SettingsDivider()
                PermissionRow(Icons.Outlined.ScreenLockPortrait, "Будильник на весь экран", "поверх экрана блокировки",
                    perms.fullScreen, actions.askFullScreen)
            }
        }

        SettingsGroup("Журнал") {
            SettingsRow(Icons.Outlined.History, "Журнал уведомлений", subtitle = "что и когда приходило", onClick = onLog)
        }

        SettingsGroup("Проверка", "Если на телефоне есть «Автозапуск» (Xiaomi, Huawei, Oppo и др.), включите его для этого приложения.") {
            SettingsRow(Icons.AutoMirrored.Outlined.Send, "Тестовое уведомление", onClick = actions.sendTest)
            SettingsDivider()
            SettingsRow(Icons.Outlined.AlarmOn, "Проверить будильник", onClick = actions.testAlarm)
            SettingsDivider()
            SettingsRow(Icons.Outlined.Settings, "Настройки приложения", subtitle = "в настройках телефона", onClick = actions.openAppSettings)
        }

        Text("Напоминалка ${actions.version}", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

/** Группа строк: заголовок над ней и пояснение под ней. */
@Composable
private fun SettingsGroup(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 20.dp)) {
        Text(title, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).padding(vertical = 4.dp), content = content)
        if (footer != null) Text(footer, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp))
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(LocalPalette.current.line))
}

/** Строка: значок, название (и подпись), значение справа и стрелка либо свой элемент справа. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color? = null,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(p.tint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint ?: p.tintText, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = p.muted, fontSize = 12.sp, maxLines = 2)
        }
        if (value != null) {
            Text(value, color = valueColor ?: p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (valueColor != null) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(0.7f, fill = false))
        }
        if (trailing != null) trailing()
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = p.muted, modifier = Modifier.size(22.dp))
    }
}

/** Разрешение: справа «Включено» зелёным или «Включить» красным. */
@Composable
private fun PermissionRow(icon: ImageVector, title: String, hint: String, ok: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val good = if (p.dark) Color(0xFF4ADE80) else Green
    val bad = if (p.dark) Color(0xFFFDA4AF) else Rose
    // разрешено — только зелёная галочка, чтобы название не сжималось; иначе — «Включить»
    SettingsRow(icon, title, subtitle = hint, value = if (ok) null else "Включить", valueColor = bad,
        iconTint = if (ok) null else bad, onClick = onClick,
        trailing = if (ok) ({ Icon(Icons.Outlined.CheckCircle, contentDescription = "Включено", tint = good, modifier = Modifier.size(22.dp)) }) else null)
}
