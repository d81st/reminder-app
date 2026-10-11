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
import androidx.compose.material3.AlertDialog
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

private const val MAX_CHIPS = 24
private val ru = Locale("ru")

// =====================================================================
// каркас: шапка, вкладки
// =====================================================================

@Composable
fun MainScreen(
    vm: MainViewModel,
    perms: PermState,
    keyboardOpen: Boolean,
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
    BackHandler(enabled = tab != 0) { tab = 0 }
    LaunchedEffect(tab) {
        if (tab == 1) vm.loadLog()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    if (showExport) ExportDialog(vm.rules, actions) { showExport = false }
    if (showPaste) PasteDialog(onDismiss = { showPaste = false }) { text ->
        showPaste = false
        actions.importText(text)
    }
    pendingImport?.let { ImportDialog(it, vm.rules.size, onImport, onImportCancel) }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Header(vm, perms, actions)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> RemindersPage(vm, listState, expanded, actions)
                1 -> LogPage(vm)
                else -> SettingsPage(perms, soundTitle, actions, onExport = { showExport = true }, onPaste = { showPaste = true })
            }
            if (tab == 0 && !keyboardOpen) {
                FloatingActionButton(
                    onClick = {
                        val r = vm.add()
                        if (r == null) {
                            actions.toast("Достигнут предел: ${MainViewModel.MAX_RULES} напоминаний")
                        } else {
                            expanded[r.key] = true
                            scope.launch { listState.animateScrollToItem(vm.rules.size - 1) }
                        }
                    },
                    containerColor = Indigo,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                ) { Icon(Icons.Filled.Add, contentDescription = "Добавить напоминание") }
            }
        }
        if (!keyboardOpen) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            NavigationBar(containerColor = p.card, tonalElevation = 0.dp) {
                val items = listOf(
                    Triple("Напоминания", Icons.Filled.Notifications, 0),
                    Triple("Журнал", Icons.AutoMirrored.Filled.List, 1),
                    Triple("Настройки", Icons.Filled.Settings, 2),
                )
                for ((label, icon, i) in items) {
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = p.tintText, selectedTextColor = p.tintText,
                            indicatorColor = p.tint, unselectedIconColor = p.muted, unselectedTextColor = p.muted,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(vm: MainViewModel, perms: PermState, actions: MainActions) {
    val p = LocalPalette.current
    val colors = if (p.dark) listOf(Color(0xFF1E1B4B), IndigoDark) else listOf(IndigoDark, Indigo)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Brush.linearGradient(colors))
            .padding(start = 20.dp, top = 18.dp, end = 14.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Напоминалка", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (p.dark) "☀" else "☾", color = Color.White, fontSize = 24.sp,
                modifier = Modifier.clip(CircleShape).clickable { actions.toggleTheme() }.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        val next = vm.nextAt
        Text(
            if (next != null) "Следующее: " + SimpleDateFormat("EE, d MMM, HH:mm", ru).format(Date(next))
            else "Запланированных уведомлений нет",
            color = Color(0xDDFFFFFF), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
        )
        if (!perms.notifications) {
            Text(
                "Уведомления выключены. Разрешите их во вкладке «Настройки».",
                color = Color(0xFFFFD5DC), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (!perms.fullScreen && vm.rules.any { it.isAlarm && it.enabled }) {
            Text(
                "Будильник не сможет открыться на весь экран. Разрешите во вкладке «Настройки».",
                color = Color(0xFFFFD5DC), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// =====================================================================
// страница «Напоминания»
// =====================================================================

@Composable
private fun RemindersPage(
    vm: MainViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    expanded: MutableMap<Long, Boolean>,
    actions: MainActions,
) {
    val p = LocalPalette.current
    val rules = vm.rules
    val now = vm.now
    val next = remember(rules, now) { TimeMath.nextByRule(rules, now) }
    var toDelete by remember { mutableStateOf<Rule?>(null) }

    if (rules.isEmpty()) {
        Text(
            "Пока нет напоминаний. Нажмите «+», чтобы создать.", color = p.muted, fontSize = 15.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp),
        )
    }
    LazyColumn(
        state = listState,
        // запас снизу, чтобы кнопка «+» не закрывала последнюю карточку
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(rules, key = { it.key }) { r ->
            val idx = rules.indexOf(r)
            RuleCard(
                r = r,
                idx = idx,
                rules = rules,
                next = next[idx],
                now = now,
                expanded = expanded[r.key] == true,
                onToggle = { expanded[r.key] = expanded[r.key] != true },
                onChange = { change -> vm.update(r.key, change) },
                onDelete = { toDelete = r },
                actions = actions,
            )
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить напоминание?") },
            text = { Text(r.name) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(r.key)
                    toDelete = null
                }) { Text("Удалить", color = Rose) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun RuleCard(
    r: Rule,
    idx: Int,
    rules: List<Rule>,
    next: TimeMath.Event?,
    now: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: ((Rule) -> Rule) -> Unit,
    onDelete: () -> Unit,
    actions: MainActions,
) {
    val p = LocalPalette.current
    val acc = Accents[r.color.coerceIn(0, Accents.size - 1)]
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (r.enabled) 1f else 0.6f)
            .clip(RoundedCornerShape(16.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp)
    ) {
        // Свёрнутая часть: цвет, название, краткая сводка, переключатель
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (r.isAlarm) {
                Text("⏰", fontSize = 15.sp, modifier = Modifier.padding(end = 8.dp))
            } else {
                Box(Modifier.padding(end = 10.dp).size(10.dp).clip(CircleShape).background(acc))
            }
            Column(Modifier.weight(1f)) {
                Text(r.name, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(r, rules, idx, next, now), color = p.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Switch(
                checked = r.enabled,
                onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
                modifier = Modifier.padding(start = 8.dp),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null, tint = p.muted, modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (expanded) key(r.key) { RuleDetail(r, idx, rules, next, now, acc, onChange, onDelete, actions) }
    }
}

@Composable
private fun RuleDetail(
    r: Rule,
    idx: Int,
    rules: List<Rule>,
    next: TimeMath.Event?,
    now: Long,
    acc: Color,
    onChange: ((Rule) -> Rule) -> Unit,
    onDelete: () -> Unit,
    actions: MainActions,
) {
    val p = LocalPalette.current

    Label("Название", top = 10)
    AppField(r.name, { v -> onChange { it.copy(name = v) } }, "Название", singleLine = true)

    Label("Вид", top = 12)
    Segmented(
        listOf("Уведомление", "⏰ Будильник"),
        if (r.isAlarm) 1 else 0,
    ) { i -> onChange { it.copy(kind = if (i == 1) Rule.KIND_ALARM else Rule.KIND_NOTIFY) } }
    if (r.isAlarm) {
        Hint("Громкий звук даже в беззвучном режиме, экран поверх блокировки, «Отложить» и «Выключить». " +
            "Для одного будильника в день поставьте одинаковое время «С» и «до».")
    }

    Label("Дни недели", top = 12)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in 0..6) {
            val on = r.onDay(d)
            DayChip(Rule.DAY_SHORT[d], on, acc, weekend = d >= 5) {
                onChange { it.copy(days = it.days xor (1 shl d)) }
            }
        }
    }
    Row(Modifier.padding(top = 4.dp)) {
        for ((label, mask) in listOf("Каждый день" to Rule.ALL_DAYS, "Будни" to Rule.WORKDAYS, "Выходные" to Rule.WEEKEND)) {
            TextButton(onClick = { onChange { it.copy(days = mask) } }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(label, fontSize = 13.sp, color = if (r.days == mask) p.tintText else p.muted,
                    fontWeight = if (r.days == mask) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }

    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("С", color = p.muted, fontSize = 14.sp)
        TimeButton(r.start, Modifier.padding(start = 8.dp)) { m ->
            onChange { it.copy(start = m, end = if (it.isAlarm && it.end == it.start) m else it.end) }
        }
        Text("до", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp))
        TimeButton(r.end, Modifier.padding(start = 8.dp)) { m -> onChange { it.copy(end = m) } }
    }

    Row(Modifier.padding(top = 12.dp)) {
        NumberField("Каждые, мин", r.interval, 1..720, Modifier.weight(1f).padding(end = 6.dp)) { v -> onChange { it.copy(interval = v) } }
        NumberField(if (r.isAlarm) "Звонить раньше на, мин" else "Предупредить за, мин", r.lead, 0..180,
            Modifier.weight(1f).padding(start = 6.dp)) { v -> onChange { it.copy(lead = v) } }
    }

    if (r.isAlarm) {
        AlarmOptions(r, onChange, actions)
    } else {
        // Повторы: «каждые M минут», «сколько раз ещё» и когда повторять (до времени / после времени)
        Row(Modifier.padding(top = 12.dp)) {
            NumberField("Повторять каждые, мин", r.repeatEvery, 1..120, Modifier.weight(1f).padding(end = 6.dp)) { v ->
                onChange { it.copy(repeatEvery = v) }
            }
            NumberField("Сколько раз ещё (0 = выкл.)", r.repeatCount, 0..20, Modifier.weight(1f).padding(start = 6.dp)) { v ->
                onChange { it.copy(repeatCount = v) }
            }
        }
        Label("Когда повторять", top = 10)
        Segmented(listOf("До времени", "После времени"), if (r.repeatMode == Rule.REPEAT_AFTER) 1 else 0) { i ->
            onChange { it.copy(repeatMode = if (i == 1) Rule.REPEAT_AFTER else Rule.REPEAT_BEFORE) }
        }
        Hint(repeatNote(r, TimeMath.times(rules, idx)))
    }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clickable { onChange { it.copy(skipOverlap = !it.skipOverlap) } },
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = r.skipOverlap, onCheckedChange = { v -> onChange { it.copy(skipOverlap = v) } })
        Text("Пропускать время, когда срабатывает другое напоминание", color = p.muted, fontSize = 13.sp)
    }

    Label(if (r.isAlarm) "Текст на экране будильника" else "Текст уведомления", top = 8)
    AppField(r.text, { v -> onChange { it.copy(text = v) } }, "Текст", singleLine = false)

    Label("Цвет", top = 12)
    Row(Modifier.padding(top = 4.dp)) {
        Accents.forEachIndexed { i, c ->
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(if (i == r.color) Modifier.border(3.dp, p.ink, CircleShape) else Modifier)
                    .clickable { onChange { it.copy(color = i) } }
            )
        }
    }

    TimesChips(r, idx, rules, next, now, acc)

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(leadNote(r), color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            "Удалить", color = Rose, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        )
    }
}

@Composable
private fun AlarmOptions(r: Rule, onChange: ((Rule) -> Rule) -> Unit, actions: MainActions) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Мелодия", color = p.ink, fontSize = 15.sp)
            Text(actions.alarmSoundTitle(r.alarmSound), color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TonalButton("Выбрать") { actions.pickAlarmSound(r.key) }
    }
    Row(Modifier.padding(top = 12.dp)) {
        NumberField("Отложить на, мин", r.snoozeMin, 1..60, Modifier.weight(1f).padding(end = 6.dp)) { v -> onChange { it.copy(snoozeMin = v) } }
        Spacer(Modifier.weight(1f).padding(start = 6.dp))
    }
    SwitchRow("Нарастающая громкость", "Начинает тихо и за полминуты доходит до полной громкости", r.gradual) { v ->
        onChange { it.copy(gradual = v) }
    }
    SwitchRow("Выключать решением примера", "Чтобы точно проснуться: «Выключить» сработает только после ответа", r.challenge == Rule.CHALLENGE_MATH) { v ->
        onChange { it.copy(challenge = if (v) Rule.CHALLENGE_MATH else Rule.CHALLENGE_NONE) }
    }
}

@Composable
private fun TimesChips(r: Rule, idx: Int, rules: List<Rule>, next: TimeMath.Event?, now: Long, acc: Color) {
    val p = LocalPalette.current
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = Rule.weekdayOf(cal.get(Calendar.DAY_OF_WEEK))
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val onToday = r.onDay(today)
    val t = TimeMath.times(rules, idx, if (onToday) today else null)

    Label(if (onToday) "Времена сегодня" else "Времена (сегодня не работает)", top = 12)
    FlowRow(
        Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (t.isEmpty()) {
            Chip("Нет времён: проверьте начало и конец", p.tint, p.muted)
        } else {
            val nextMin = if (next != null && sameDay(next.baseAt + r.lead * TimeMath.MIN, now)) next.minute else -1
            for (m in t.take(MAX_CHIPS)) {
                val past = !onToday || m - r.lead <= nowMin
                val strong = r.enabled && m == nextMin
                val bg = if (strong) acc else acc.copy(alpha = 0.16f)
                val fg = when {
                    strong -> if (acc.luminance() > 0.5f) Color(0xFF1B1F3B) else Color.White
                    p.dark -> lerp(acc, Color.White, 0.3f)
                    else -> lerp(acc, Color.Black, 0.35f)
                }
                Chip(TimeMath.fmt(m), bg, fg, Modifier.alpha(if (past && !strong) 0.45f else 1f))
            }
            if (t.size > MAX_CHIPS) Chip("ещё ${t.size - MAX_CHIPS}", p.tint, p.muted)
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

private fun subtitle(r: Rule, rules: List<Rule>, idx: Int, next: TimeMath.Event?, now: Long): String {
    if (TimeMath.times(rules, idx).isEmpty()) return "Нет времён: проверьте начало и конец"
    val range = if (r.start == r.end) "в " + TimeMath.fmt(r.start)
    else TimeMath.fmt(r.start) + "–" + TimeMath.fmt(r.end) + " · каждые " + r.interval + " мин"
    val base = range + " · " + Rule.daysText(r.days) + (if (r.effectiveRepeats() > 0) " · ↻" + r.effectiveRepeats() else "")
    if (!r.enabled) return "$base · выключено"
    if (next == null) return "$base · не запланировано"
    val t = next.baseAt + r.lead * TimeMath.MIN // само время, а не момент предупреждения
    val tomorrow = now + 24 * 60 * TimeMath.MIN
    val whenText = when {
        sameDay(t, now) -> TimeMath.fmt(next.minute)
        sameDay(t, tomorrow) -> "завтра " + TimeMath.fmt(next.minute)
        else -> SimpleDateFormat("EE", ru).format(Date(t)) + " " + TimeMath.fmt(next.minute)
    }
    return "$base · след. $whenText"
}

private fun leadNote(r: Rule): String = when {
    r.lead <= 0 -> ""
    r.isAlarm -> "Зазвонит за ${r.lead} мин до каждого времени"
    r.effectiveRepeats() > 0 -> "Основное уведомление за ${r.lead} мин до времени, серия считается от него"
    else -> "Придёт за ${r.lead} мин до каждого времени"
}

/** Пояснение под полями повторов: сколько уведомлений в серии, их точные времена и как остановить. */
private fun repeatNote(r: Rule, t: List<Int>): String {
    if (r.repeatCount <= 0) return "Повторы выключены. Поставьте в «Сколько раз ещё» число больше 0."
    val eff = r.effectiveRepeats()
    if (eff == 0) {
        return "Повторы не помещаются: серия должна закончиться раньше соседнего времени (интервал ${r.interval} мин). Уменьшите шаг."
    }
    val total = eff + 1
    val s = StringBuilder()
    s.append(if (r.repeatMode == Rule.REPEAT_BEFORE) "Серия идёт ДО времени. " else "Серия идёт ПОСЛЕ времени. ")
    s.append(total).append(' ').append(TimeMath.plural(total, "уведомление", "уведомления", "уведомлений"))
        .append(" с шагом ").append(r.repeatEvery).append(" мин.")
    if (t.isNotEmpty()) s.append("\nДля ").append(TimeMath.fmt(t[0])).append(": ").append(TimeMath.seriesText(r, t[0]))
    if (eff < r.repeatCount) s.append("\nОстальные повторы не помещаются до соседнего времени.")
    s.append("\nОстановить серию: «Готово» в уведомлении, тап по нему или смахивание.")
    return s.toString()
}

// =====================================================================
// страница «Журнал»
// =====================================================================

@Composable
private fun LogPage(vm: MainViewModel) {
    val p = LocalPalette.current
    val f = remember { SimpleDateFormat("d MMM, HH:mm", ru) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Последние уведомления", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card)
                    .border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
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
private fun SettingsPage(perms: PermState, soundTitle: String, actions: MainActions, onExport: () -> Unit, onPaste: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)
    ) {
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
                TonalButton("Вставить текст", Modifier.weight(1f).padding(start = 4.dp), onPaste)
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(title, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun PermRow(label: String, hint: String, ok: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (ok) "Разрешено" else "Нужно разрешить", color = if (ok) Green else Rose, fontSize = 13.sp)
            }
            if (!ok) PrimaryButton("Разрешить", onClick = onClick)
        }
        Text(hint, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

// =====================================================================
// мелкие элементы
// =====================================================================

@Composable
private fun Label(s: String, top: Int = 0) {
    Text(s, color = LocalPalette.current.muted, fontSize = 12.sp, modifier = Modifier.padding(top = top.dp, bottom = 3.dp))
}

@Composable
private fun Hint(s: String) {
    Text(s, color = LocalPalette.current.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Chip(s: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        s, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

@Composable
private fun DayChip(label: String, on: Boolean, acc: Color, weekend: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (on) acc else p.tint)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when {
                on -> if (acc.luminance() > 0.5f) Color(0xFF1B1F3B) else Color.White
                weekend -> Rose.copy(alpha = 0.8f)
                else -> p.muted
            },
        )
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Indigo, activeContentColor = Color.White,
                    inactiveContainerColor = p.tint, inactiveContentColor = p.tintText,
                    activeBorderColor = Indigo, inactiveBorderColor = p.line,
                ),
                icon = {},
            ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp)
            if (hint != null) Text(hint, color = p.muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun appFieldColors() = LocalPalette.current.let { p ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = p.bg, unfocusedContainerColor = p.bg,
        focusedBorderColor = Indigo, unfocusedBorderColor = p.line,
        focusedTextColor = p.ink, unfocusedTextColor = p.ink,
        focusedPlaceholderColor = p.muted, unfocusedPlaceholderColor = p.muted,
    )
}

@Composable
private fun AppField(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(12.dp),
        colors = appFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Числовое поле: пока вводят, значение применяется в допустимых пределах; при уходе фокуса текст выравнивается. */
@Composable
private fun NumberField(label: String, value: Int, range: IntRange, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    // значение могло измениться не отсюда (например, загрузка из базы)
    LaunchedEffect(value) {
        if (!focused && text != value.toString()) text = value.toString()
    }
    Column(modifier) {
        Label(label)
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(4)
                text.toIntOrNull()?.let { onChange(it.coerceIn(range)) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                if (focused && !f.isFocused) {
                    val n = (text.toIntOrNull() ?: range.first).coerceIn(range)
                    text = n.toString()
                    if (n != value) onChange(n)
                }
                focused = f.isFocused
            },
        )
    }
}

@Composable
private fun TimeButton(minutes: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    TonalButton(TimeMath.fmt(minutes), modifier.width(96.dp)) {
        TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
    }
}

@Composable
private fun TonalButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = p.tint, contentColor = p.tintText),
        modifier = modifier.heightIn(min = 44.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Color.White),
        border = BorderStroke(0.dp, Color.Transparent),
        modifier = modifier.heightIn(min = 44.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

// =====================================================================
// обмен: окна экспорта и импорта
// =====================================================================

/** Краткая строка о напоминании для списков обмена. */
private fun shortInfo(r: Rule): String {
    val time = if (r.start == r.end) TimeMath.fmt(r.start)
    else TimeMath.fmt(r.start) + "–" + TimeMath.fmt(r.end) + ", каждые " + r.interval + " мин"
    return (if (r.isAlarm) "⏰ " else "") + time + " · " + Rule.daysText(r.days)
}

@Composable
private fun DialogCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(p.card)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) { content() }
    }
}

@Composable
private fun ExportDialog(rules: List<Rule>, actions: MainActions, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val picked = remember { mutableStateMapOf<Long, Boolean>() }
    val chosen = rules.filter { picked[it.key] != false }
    val code = remember(chosen) { if (chosen.isEmpty()) "" else RuleCodec.encode(chosen) }
    val bmp = remember(code) { if (code.isEmpty()) null else QrCodes.bitmap(code) }

    DialogCard(onDismiss) {
        Text("Поделиться напоминаниями", color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Hint("Коллега открывает «Настройки» → «Сканировать QR-код» и наводит камеру на экран. Мелодии будильников не передаются.")
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            when {
                chosen.isEmpty() -> Text("Выберите хотя бы одно напоминание", color = p.muted, fontSize = 14.sp)
                bmp == null -> Text("Слишком много для одного QR-кода. Уберите часть напоминаний или отправьте текстом.",
                    color = Rose, fontSize = 14.sp, textAlign = TextAlign.Center)
                else -> Image(
                    bmp.asImageBitmap(), contentDescription = "QR-код с напоминаниями",
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
        }
        for (r in rules) {
            Row(
                Modifier.fillMaxWidth().clickable { picked[r.key] = picked[r.key] == false },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = picked[r.key] != false, onCheckedChange = { picked[r.key] = it })
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = p.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(shortInfo(r), color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (bmp != null) {
            PrimaryButton("Отправить картинкой", Modifier.fillMaxWidth()) {
                val caption = "QR-код с напоминаниями для «Напоминалки». В приложении: «Настройки» → «Из картинки»" +
                    if (code.length < 900) ".\n\n$code" else "."
                actions.shareImage(bmp, caption)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (chosen.isNotEmpty()) {
            TonalButton("Отправить текстом", Modifier.fillMaxWidth()) {
                actions.shareText(
                    "Напоминания для приложения «Напоминалка». Чтобы добавить: «Поделиться» → «Напоминалка», " +
                        "или скопируйте сообщение и в приложении нажмите «Вставить текст».\n\n$code"
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть", color = p.muted) }
    }
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    var text by remember {
        mutableStateOf(clipboard.getText()?.text?.takeIf { RuleCodec.find(it) != null } ?: "")
    }
    DialogCard(onDismiss) {
        Text("Вставить текст", color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Hint("Вставьте присланное сообщение целиком: код начинается с «${RuleCodec.PREFIX}».")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(RuleCodec.PREFIX + "…") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row {
            TonalButton("Из буфера", Modifier.weight(1f).padding(end = 4.dp)) { text = clipboard.getText()?.text ?: "" }
            PrimaryButton("Прочитать", Modifier.weight(1f).padding(start = 4.dp)) { onImport(text) }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена", color = p.muted) }
    }
}

@Composable
private fun ImportDialog(list: List<Rule>, mine: Int, onImport: (replace: Boolean) -> Unit, onCancel: () -> Unit) {
    val p = LocalPalette.current
    var confirmReplace by remember { mutableStateOf(false) }
    DialogCard(onCancel) {
        Text(
            "Получено: ${list.size} ${TimeMath.plural(list.size, "напоминание", "напоминания", "напоминаний")}",
            color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        for (r in list) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 10.dp).size(10.dp).clip(CircleShape).background(Accents[r.color.coerceIn(0, Accents.size - 1)]))
                Column {
                    Text(r.name, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(shortInfo(r) + if (r.enabled) "" else " · выключено", color = p.muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Добавить к моим", Modifier.fillMaxWidth()) { onImport(false) }
        if (mine > 0) {
            Spacer(Modifier.height(8.dp))
            if (!confirmReplace) {
                TonalButton("Заменить все мои", Modifier.fillMaxWidth()) { confirmReplace = true }
            } else {
                Hint("Ваши напоминания ($mine) будут удалены и заменены полученными.")
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { onImport(true) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) { Text("Да, заменить", fontWeight = FontWeight.Bold) }
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена", color = p.muted) }
    }
}
