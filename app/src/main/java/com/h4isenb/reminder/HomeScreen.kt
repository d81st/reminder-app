@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.h4isenb.reminder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ru = Locale("ru")

// =====================================================================
// каркас: вкладки, редактор поверх, заготовки
// =====================================================================

@Composable
fun MainScreen(
    vm: MainViewModel,
    perms: PermState,
    soundTitle: String,
    actions: MainActions,
    pendingImport: List<Rule>?,
    onImport: (replace: Boolean) -> Unit,
    onImportCancel: () -> Unit,
) {
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    /** Ключ карточки, открытой в редакторе; null — редактор закрыт. */
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val keyboardOpen = WindowInsets.isImeVisible

    BackHandler(enabled = editing != null || tab != 0) {
        if (editing != null) editing = null else tab = 0
    }
    LaunchedEffect(tab) {
        if (tab == 1) vm.loadLog()
    }
    // редактор открыт для напоминания, которого больше нет (например, после импорта с заменой)
    val editedRule = editing?.let { k -> vm.rules.firstOrNull { it.key == k } }
    LaunchedEffect(editing, editedRule == null, vm.loaded) {
        if (editing != null && editedRule == null && vm.loaded) editing = null
    }

    val onDeleted: (Rule) -> Unit = { r ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (editing == r.key) editing = null
        val d = vm.delete(r.key)
        if (d != null) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val res = snackbar.showSnackbar("Удалено: ${r.name}", actionLabel = "Отменить", duration = SnackbarDuration.Long)
                if (res == SnackbarResult.ActionPerformed) vm.restore(d)
            }
        }
    }

    if (showExport) ExportDialog(vm.rules, actions) { showExport = false }
    if (showPaste) PasteDialog(onDismiss = { showPaste = false }) { text ->
        showPaste = false
        actions.importText(text)
    }
    pendingImport?.let { ImportDialog(it, vm.rules.size, onImport, onImportCancel) }
    if (showTemplates) {
        TemplateSheet(onDismiss = { showTemplates = false }) { t ->
            showTemplates = false
            val r = vm.add(t)
            if (r == null) actions.toast("Достигнут предел: ${MainViewModel.MAX_RULES} напоминаний")
            else editing = r.key
        }
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            when (tab) {
                0 -> HomePage(
                    rules = vm.rules,
                    loaded = vm.loaded,
                    nextByKey = vm.nextByKey,
                    today = vm.today,
                    now = vm.now,
                    perms = perms,
                    listState = listState,
                    onToggleTheme = actions.toggleTheme,
                    onOpen = { editing = it.key },
                    onEnabled = { r, on -> vm.update(r.key) { it.copy(enabled = on) } },
                    onDelete = onDeleted,
                )
                1 -> LogPage(vm)
                else -> SettingsPage(perms, soundTitle, actions, onExport = { showExport = true }, onPaste = { showPaste = true })
            }
        }

        if (!keyboardOpen) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                if (tab == 0) {
                    AddButton(Modifier.align(Alignment.End).padding(end = 20.dp, bottom = 16.dp)) { showTemplates = true }
                }
                BottomBar(tab) { tab = it }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (editing != null) 16.dp else 96.dp),
        )

        AnimatedVisibility(
            visible = editedRule != null,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
        ) {
            // последнее открытое напоминание, чтобы при закрытии редактор не пропадал раньше анимации
            val shown = remember { mutableStateOf<Rule?>(null) }
            if (editedRule != null) shown.value = editedRule
            shown.value?.let { r ->
                RuleEditor(
                    r = r,
                    allRules = vm.rules,
                    now = vm.now,
                    actions = actions,
                    onChange = { change -> vm.update(r.key, change) },
                    onDelete = { onDeleted(r) },
                    onClose = { editing = null },
                )
            }
        }
    }
}

@Composable
private fun AddButton(modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(60.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = p.primary, spotColor = p.primary)
            .clip(RoundedCornerShape(20.dp))
            .background(p.primary)
            .clickable(onClickLabel = "Новое напоминание", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Новое напоминание" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Add, contentDescription = null, tint = p.onPrimary, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val items = listOf<Triple<String, ImageVector, Int>>(
            Triple("Сегодня", Icons.Filled.DateRange, 0),
            Triple("Журнал", Icons.AutoMirrored.Filled.List, 1),
            Triple("Настройки", Icons.Filled.Settings, 2),
        )
        for ((label, icon, i) in items) {
            val on = tab == i
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) p.tint else Color.Transparent)
                    .clickable(role = Role.Tab) { onTab(i) }
                    .padding(horizontal = if (on) 16.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, contentDescription = null, tint = if (on) p.tintText else p.muted, modifier = Modifier.size(20.dp))
                Text(label, color = if (on) p.tintText else p.muted, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

// =====================================================================
// страница «Сегодня»
// =====================================================================

/** Главная страница без ViewModel: всё приходит параметрами, поэтому её можно снимать скриншот-тестами. */
@Composable
fun HomePage(
    rules: List<Rule>,
    loaded: Boolean,
    nextByKey: Map<Long, TimeMath.Event>,
    today: List<TimeMath.Event>,
    now: Long,
    perms: PermState,
    listState: LazyListState = rememberLazyListState(),
    onToggleTheme: () -> Unit,
    onOpen: (Rule) -> Unit,
    onEnabled: (Rule, Boolean) -> Unit,
    onDelete: (Rule) -> Unit,
) {
    val p = LocalPalette.current
    if (!loaded) return
    LazyColumn(
        state = listState,
        // запас снизу под плавающую панель и кнопку «+»
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { TodayHeader(rules, nextByKey, now, onToggleTheme) }
        item(key = "timeline") { DayTimeline(today, now, Modifier.padding(top = 10.dp)) }
        val warn = when {
            !perms.notifications -> "Уведомления выключены. Разрешите их во вкладке «Настройки»."
            !perms.fullScreen && rules.any { it.isAlarm && it.enabled } ->
                "Будильник не сможет открыться на весь экран. Разрешите во вкладке «Настройки»."
            else -> null
        }
        if (warn != null) {
            item(key = "warn") {
                Text(warn, color = if (p.dark) Color(0xFFFDA4AF) else Rose, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(14.dp))
            }
        }
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text("Напоминания", color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                    modifier = Modifier.weight(1f))
                if (rules.isNotEmpty()) Text(rules.size.toString(), color = p.muted, fontSize = 13.sp)
            }
        }
        if (rules.isEmpty()) {
            item(key = "empty") {
                Text("Пока нет напоминаний. Нажмите «+»: можно начать с готовой заготовки.", color = p.muted, fontSize = 15.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            }
        }
        items(rules, key = { it.key }) { r ->
            SwipeToDelete(onDelete = { onDelete(r) }, modifier = Modifier.animateItem()) {
                RuleCard(r, nextByKey[r.key], now, onOpen = { onOpen(r) }, onEnabled = { onEnabled(r, it) })
            }
        }
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            true
        } else {
            false
        }
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(if (p.dark) Color(0xFF4C1D2B) else Color(0xFFFFE4E8))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("Удалить", color = if (p.dark) Color(0xFFFDA4AF) else Rose, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        },
    ) { content() }
}

/** Дата, тема и крупно — сколько осталось до ближайшего напоминания. */
@Composable
private fun TodayHeader(rules: List<Rule>, nextByKey: Map<Long, TimeMath.Event>, now: Long, onToggleTheme: () -> Unit) {
    val p = LocalPalette.current
    val date = remember(now / 60_000 / 60) {
        SimpleDateFormat("EEEE, d MMMM", ru).format(Date(now)).replaceFirstChar { it.titlecase(ru) }
    }
    val soonest = nextByKey.values.minByOrNull { it.at }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(p.card)
                    .clickable(role = Role.Button, onClickLabel = "Сменить тему", onClick = onToggleTheme)
                    .semantics { contentDescription = if (p.dark) "Светлая тема" else "Тёмная тема" },
                contentAlignment = Alignment.Center,
            ) { Text(if (p.dark) "☀" else "☾", color = p.ink, fontSize = 20.sp) }
        }
        if (soonest == null) {
            Text(if (rules.isEmpty()) "Начнём?" else "Всё спокойно", color = p.ink, fontFamily = Unbounded,
                fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, modifier = Modifier.padding(top = 6.dp))
            Text(if (rules.isEmpty()) "Добавьте первое напоминание кнопкой «+»" else "Ближайшую неделю напоминаний нет",
                color = p.muted, fontSize = 16.sp)
        } else {
            val t = soonest.baseAt + soonest.rule.lead * TimeMath.MIN
            Text(TimeMath.countdown(t, now), color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Bold,
                fontSize = 40.sp, lineHeight = 44.sp, modifier = Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (soonest.rule.isAlarm) Amber else accent(soonest.rule.color, p.dark)))
                Text(soonest.rule.name, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false))
                Text(" · " + whenShort(t, now) + TimeMath.fmt(soonest.minute), color = p.muted, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}

/** «завтра » / «пн » перед временем, если это не сегодня. */
private fun whenShort(t: Long, now: Long): String = when {
    sameDay(t, now) -> ""
    sameDay(t, now + 24 * 60 * TimeMath.MIN) -> "завтра "
    else -> SimpleDateFormat("EE", ru).format(Date(t)) + " "
}

internal fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

/**
 * Лента дня: шкала от утра до вечера, отметки всех сегодняшних напоминаний в их цвете, черта «сейчас».
 * Прошедшие отметки бледнее, ближайшая — с ореолом.
 */
@Composable
fun DayTimeline(today: List<TimeMath.Event>, now: Long, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    var startH = minOf(8, (today.minOfOrNull { it.minute } ?: 8 * 60) / 60)
    var endH = maxOf(18, ((today.maxOfOrNull { it.minute } ?: 18 * 60) + 59) / 60)
    if ((endH - startH) % 2 != 0) { if (endH < 24) endH++ else startH-- }
    val span = ((endH - startH) * 60).toFloat()
    val left = today.count { it.minute > nowMin }
    val nextMinute = today.firstOrNull { it.minute > nowMin }?.minute

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.card)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
            .semantics { contentDescription = "Сегодня осталось $left из ${today.size}" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Сегодня", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (today.isEmpty()) "напоминаний нет" else "осталось $left из ${today.size}", color = p.muted, fontSize = 13.sp)
        }
        val ink = p.ink
        val track = p.line
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val w = size.width
            val mid = size.height / 2
            fun x(minute: Int) = ((minute - startH * 60) / span).coerceIn(0f, 1f) * w
            val nowX = x(nowMin)
            val inRange = nowMin >= startH * 60 && nowMin <= endH * 60
            drawRoundRect(track, Offset(0f, mid - 1.dp.toPx()), Size(w, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            if (nowMin > startH * 60) {
                drawRoundRect(ink, Offset(0f, mid - 1.dp.toPx()), Size(nowX, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            }
            for (e in today) {
                val cx = x(e.minute)
                val past = e.minute <= nowMin
                val c = if (e.rule.isAlarm) Amber else accent(e.rule.color, p.dark)
                val a = if (past) 0.3f else 1f
                if (e.minute == nextMinute) drawCircle(c.copy(alpha = 0.25f), 10.dp.toPx(), Offset(cx, mid))
                when {
                    e.rule.isAlarm -> drawCircle(c.copy(alpha = a), 7.dp.toPx(), Offset(cx, mid))
                    e.rule.interval >= 60 || e.rule.start == e.rule.end ->
                        drawRoundRect(c.copy(alpha = a), Offset(cx - 2.dp.toPx(), mid - 12.dp.toPx()), Size(4.dp.toPx(), 24.dp.toPx()),
                            CornerRadius(2.dp.toPx()))
                    else -> drawCircle(c.copy(alpha = a), 5.dp.toPx(), Offset(cx, mid))
                }
            }
            if (inRange) drawRect(ink, Offset(nowX - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (h in startH..endH step 2) Text(String.format(Locale.US, "%02d", h), color = p.muted, fontSize = 11.sp)
        }
    }
}

// =====================================================================
// карточка напоминания
// =====================================================================

/** Время коротко: «09» для целого часа, иначе «09:30». */
private fun hm(m: Int) = if (m % 60 == 0) String.format(Locale.US, "%02d", m / 60) else TimeMath.fmt(m)

private fun everyText(interval: Int) = when {
    interval == 60 -> "каждый час"
    interval % 60 == 0 -> "каждые ${interval / 60} ч"
    else -> "каждые $interval мин"
}

/** Строка под названием: окно и шаг, или «раз в день». */
internal fun scheduleText(r: Rule): String = when {
    r.rawTimes().isEmpty() -> "нет времён: проверьте начало и конец"
    r.start == r.end || r.rawTimes().size == 1 -> "раз в день"
    else -> hm(r.start) + "–" + hm(r.end) + ", " + everyText(r.interval)
}

/** Подпись рядом с крупным временем: «через 12 мин», «сегодня», «завтра», «пн». */
private fun nextLabel(r: Rule, next: TimeMath.Event?, now: Long): String {
    if (!r.enabled) return "выключено"
    if (next == null) return "не запланировано"
    val t = next.baseAt + r.lead * TimeMath.MIN
    return when {
        sameDay(t, now) && t - now <= 60 * TimeMath.MIN -> TimeMath.countdown(t, now)
        sameDay(t, now) -> "сегодня"
        sameDay(t, now + 24 * 60 * TimeMath.MIN) -> "завтра"
        else -> SimpleDateFormat("EEEE", ru).format(Date(t))
    }
}

@Composable
fun RuleCard(r: Rule, next: TimeMath.Event?, now: Long, onOpen: () -> Unit, onEnabled: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val alarm = r.isAlarm
    val acc = if (alarm) Amber else accent(r.color, p.dark)
    val bg = if (alarm) p.alarmCard else p.card
    val ink = if (alarm) Color.White else p.ink
    val muted = if (alarm) p.alarmMuted else p.muted
    val time = TimeMath.fmt(next?.minute ?: r.start)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(onClickLabel = "Изменить", onClick = onOpen)
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (alarm) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.alarmTile), contentAlignment = Alignment.Center) {
                Icon(AlarmIcon, contentDescription = "Будильник", tint = Amber, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f).alpha(if (r.enabled) 1f else 0.55f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(time, color = acc, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(nextLabel(r, next, now), color = muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(r.name, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)) {
                val extra = buildString {
                    append(if (alarm && (r.start == r.end || r.rawTimes().size == 1)) Rule.daysText(r.days) else scheduleText(r))
                    if (alarm && r.challenge == Rule.CHALLENGE_MATH) append(" · с примером")
                    if (!alarm && r.effectiveRepeats() > 0) append(" · ↻" + r.effectiveRepeats())
                }
                Text(extra, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!alarm) DayDots(r.days, acc, p.off)
            }
        }
        AccentSwitch(r.enabled, acc, onEnabled, thumbOn = if (alarm) p.alarmCard else onAccent(p.dark))
    }
}

// =====================================================================
// заготовки для «+»
// =====================================================================

@Composable
private fun TemplateSheet(onDismiss: () -> Unit, onPick: (Rule) -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.bg,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Новое напоминание", color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp))
            for ((i, t) in Templates.all.withIndex()) {
                val sample = t.make()
                TemplateRow(t.title, t.hint, if (sample.isAlarm) Amber else accent(i + 1, p.dark), sample.isAlarm) { onPick(t.make()) }
            }
            TemplateRow("С чистого листа", "все настройки вручную", p.muted, false) { onPick(Templates.blank()) }
        }
    }
}

@Composable
private fun TemplateRow(title: String, hint: String, color: Color, alarm: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(if (alarm) AlarmIcon else Icons.Filled.Notifications, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = p.muted, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(0.dp))
}
