@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.h4isenb.reminder

import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle


private val ru = Locale("ru")

// =====================================================================
// страница «Журнал»
// =====================================================================

@Composable
internal fun LogPage(vm: MainViewModel) {
    val p = LocalPalette.current
    val f = remember { SimpleDateFormat("d MMM, HH:mm", ru) }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageTitle("Журнал", Modifier.weight(1f))
                TonalButton("Очистить") { vm.clearLog() }
            }
        }
        if (vm.log.isEmpty()) {
            item {
                Text("Пока пусто. Здесь появятся сработавшие уведомления.", color = p.muted, fontSize = 15.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
            }
        }
        items(vm.log) { it ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(f.format(Date(it.at)), color = p.muted, fontSize = 12.sp)
                Text(it.title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (it.text.isNotEmpty()) Text(it.text, color = p.muted, fontSize = 13.sp)
            }
        }
    }
}

// =====================================================================
// страница «Настройки»
// =====================================================================

@Composable
internal fun SettingsPage(perms: PermState, soundTitle: String, actions: MainActions, onExport: () -> Unit, onPaste: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp)
    ) {
        PageTitle("Настройки")
        Section("Оформление") {
            SwitchRow("Тёмная тема", null, p.dark) { actions.toggleTheme() }
        }
        Section("Обмен с коллегами") {
            Hint("Настройте расписание один раз и передайте его другим: QR-кодом с экрана, картинкой или текстом в мессенджере.")
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Поделиться напоминаниями", Modifier.fillMaxWidth(), onExport)
            Spacer(Modifier.height(8.dp))
            TonalButton("Сканировать QR-код", Modifier.fillMaxWidth(), actions.scanQr)
            Spacer(Modifier.height(8.dp))
            Row {
                TonalButton("Из картинки", Modifier.weight(1f).padding(end = 4.dp), actions.importImage)
                TonalButton("Из текста", Modifier.weight(1f).padding(start = 4.dp), onPaste)
            }
        }
        Section("Звук") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Звук уведомлений", color = p.ink, fontSize = 15.sp)
                    Text(soundTitle, color = p.muted, fontSize = 13.sp)
                }
                TonalButton("Выбрать") { actions.pickNotificationSound() }
            }
            Hint("Список мелодий вашего телефона. После выбора звук один раз проигрывается. " +
                "Мелодия будильника выбирается в карточке будильника.")
        }
        Section("Разрешения") {
            PermRow("Уведомления", "Без этого напоминания не появятся.", perms.notifications, actions.askNotifications)
            PermRow("Работа в фоне", "Не ограничивать батарею, чтобы уведомления приходили вовремя при выключенном экране.",
                perms.battery, actions.askBattery)
            if (Build.VERSION.SDK_INT >= 31) {
                PermRow("Точные будильники", "Нужны, чтобы уведомление пришло минута в минуту.", perms.exact, actions.askExact)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                PermRow("Будильник на весь экран", "Чтобы будильник открывался поверх экрана блокировки.",
                    perms.fullScreen, actions.askFullScreen)
            }
        }
        Section("Проверка") {
            Hint("Если на телефоне есть «Автозапуск» (Xiaomi, Huawei, Oppo и др.), включите его для этого приложения в настройках телефона.")
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Отправить тестовое уведомление", Modifier.fillMaxWidth(), actions.sendTest)
            Spacer(Modifier.height(8.dp))
            TonalButton("Проверить будильник", Modifier.fillMaxWidth(), actions.testAlarm)
            Spacer(Modifier.height(8.dp))
            TonalButton("Открыть настройки приложения", Modifier.fillMaxWidth(), actions.openAppSettings)
        }
        Text("Напоминалка ${actions.version}", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

