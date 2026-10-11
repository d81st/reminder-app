@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.h4isenb.reminder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private const val MAX_CHIPS = 24

/**
 * Редактор напоминания на весь экран, по шагам: «Когда» → «Дни» → «Что показать» (→ «Будильник»).
 * Повторы, «за N минут», цвет и прочее спрятаны в «Дополнительно». Правки сохраняются сразу.
 */
@Composable
fun RuleEditor(
    r: Rule,
    allRules: List<Rule>,
    now: Long,
    actions: MainActions,
    onChange: ((Rule) -> Rule) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val acc = if (r.isAlarm) (if (p.dark) Amber else Color(0xFFB45309)) else accent(r.color, p.dark)
    val idx = allRules.indexOfFirst { it.key == r.key }
    var more by rememberSaveable(r.key) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = p.ink) }
            Text(if (r.id == 0L) "Новое напоминание" else "Напоминание", color = p.ink, fontFamily = Unbounded,
                fontWeight = FontWeight.Medium, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = p.muted) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Label("Название")
                AppField(r.name, { v -> onChange { it.copy(name = v) } }, "Например, «Перерыв»", singleLine = true)
            }

            KindToggle(r.isAlarm) { alarm ->
                onChange {
                    // будильник обычно один раз в день: из окна «с–до» делаем одно время
                    val kind = if (alarm) Rule.KIND_ALARM else Rule.KIND_NOTIFY
                    if (alarm && !it.isAlarm && it.start != it.end) it.copy(kind = kind, end = it.start) else it.copy(kind = kind)
                }
            }

            WhenStep(r, idx, allRules, acc, onChange)
            DaysStep(r, acc, onChange)

            Block {
                StepTitle(3, if (r.isAlarm) "Что показать на экране" else "Что показать", acc)
                AppField(r.text, { v -> onChange { it.copy(text = v) } }, "Текст уведомления", singleLine = false)
            }

            if (r.isAlarm) AlarmStep(r, acc, actions, onChange)

            // «Дополнительно»: всё, что нужно не каждому
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { more = !more }.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Дополнительно", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(moreSummary(r), color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp))
                Icon(if (more) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = p.muted)
            }
            AnimatedVisibility(more) { MoreBlock(r, idx, allRules, now, acc, onChange) }
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = onClose,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (p.dark) p.primary else p.ink, contentColor = if (p.dark) p.onPrimary else Color.White),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp).fillMaxWidth().height(56.dp),
        ) { Text("Готово", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun KindToggle(alarm: Boolean, onSelect: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tint).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KindOption("Уведомление", Icons.Filled.Notifications, !alarm, Modifier.weight(1f)) { onSelect(false) }
        KindOption("Будильник", AlarmIcon, alarm, Modifier.weight(1f)) { onSelect(true) }
    }
}

@Composable
private fun KindOption(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) p.card else Color.Transparent)
            .semantics { selected = on }.clickable(role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (on) p.ink else p.muted, modifier = Modifier.size(18.dp))
        Text(label, color = if (on) p.ink else p.muted, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp))
    }
}

/** Шаг 1: один раз или окно «с–до» с шагом; под ним — какие получатся времена. */
@Composable
private fun WhenStep(r: Rule, idx: Int, allRules: List<Rule>, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    val once = r.start == r.end
    val preset = when {
        once -> 0
        r.interval == 60 -> 1
        r.interval == 30 -> 2
        else -> 3
    }
    var custom by rememberSaveable(r.key) { mutableStateOf(preset == 3) }
    Block {
        StepTitle(1, "Когда", acc)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Один раз", preset == 0 && !custom, acc) { custom = false; onChange { it.copy(end = it.start) } }
            Pill("Каждый час", preset == 1 && !custom, acc) { custom = false; onChange { widen(it, 60) } }
            Pill("Каждые 30 мин", preset == 2 && !custom, acc) { custom = false; onChange { widen(it, 30) } }
            Pill("Своё", custom || preset == 3, acc) { custom = true; if (once) onChange { widen(it, it.interval) } }
        }
        if (once) {
            TimeBox("в", r.start, Modifier.fillMaxWidth()) { m -> onChange { it.copy(start = m, end = m) } }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeBox("с", r.start, Modifier.weight(1f)) { m -> onChange { it.copy(start = m) } }
                Text("→", color = p.muted, fontSize = 18.sp)
                TimeBox("до", r.end, Modifier.weight(1f)) { m -> onChange { it.copy(end = m) } }
            }
            if (custom || preset == 3) {
                NumberField("Шаг, минут", r.interval, 1..720, Modifier.fillMaxWidth()) { v -> onChange { it.copy(interval = v) } }
            }
        }
        val t = if (idx >= 0) TimeMath.times(allRules, idx) else r.rawTimes()
        Text(timesSummary(t), color = if (t.isEmpty()) Rose else p.muted, fontSize = 12.sp)
    }
}

/** Из «один раз» в окно с шагом: конец ставится на 8 часов позже, если окна ещё нет. */
private fun widen(r: Rule, interval: Int): Rule {
    val end = if (r.end > r.start) r.end else minOf(r.start + 8 * 60, 23 * 60 + 59)
    return r.copy(interval = interval, end = end)
}

private fun timesSummary(t: List<Int>): String = when {
    t.isEmpty() -> "Нет времён: «до» раньше, чем «с»"
    t.size == 1 -> "Один раз в день: " + TimeMath.fmt(t[0])
    t.size <= 4 -> "${t.size} " + TimeMath.plural(t.size, "раз", "раза", "раз") + " в день: " + t.joinToString(", ") { TimeMath.fmt(it) }
    else -> "${t.size} " + TimeMath.plural(t.size, "раз", "раза", "раз") + " в день: " +
        TimeMath.fmt(t[0]) + ", " + TimeMath.fmt(t[1]) + " … " + TimeMath.fmt(t.last())
}

/** Шаг 2: дни недели. */
@Composable
private fun DaysStep(r: Rule, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    Block {
        StepTitle(2, "Дни", acc, Rule.daysText(r.days))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (d in 0..6) {
                DayCircle(Rule.DAY_SHORT[d], r.onDay(d), acc, weekend = d >= 5) { onChange { it.copy(days = it.days xor (1 shl d)) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, mask) in listOf("Будни" to Rule.WORKDAYS, "Каждый день" to Rule.ALL_DAYS, "Выходные" to Rule.WEEKEND)) {
                Text(
                    label, fontSize = 13.sp,
                    color = if (r.days == mask) p.tintText else p.muted,
                    fontWeight = if (r.days == mask) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (r.days == mask) p.tint else Color.Transparent)
                        .clickable(role = Role.Button) { onChange { it.copy(days = mask) } }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Шаг 4 для будильника: мелодия, «Отложить», громкость, пример. */
@Composable
private fun AlarmStep(r: Rule, acc: Color, actions: MainActions, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    Block {
        StepTitle(4, "Будильник", acc)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Мелодия", color = p.ink, fontSize = 15.sp)
                Text(actions.alarmSoundTitle(r.alarmSound), color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TonalButton("Выбрать") { actions.pickAlarmSound(r.key) }
        }
        Column {
            Label("Отложить на")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(5, 10, 15, 30)) Pill("$m мин", r.snoozeMin == m, acc) { onChange { it.copy(snoozeMin = m) } }
            }
        }
        SwitchRow("Нарастающая громкость", "Начинает тихо и за полминуты доходит до полной", r.gradual) { v -> onChange { it.copy(gradual = v) } }
        SwitchRow("Выключать решением примера", "«Выключить» сработает только после ответа", r.challenge == Rule.CHALLENGE_MATH) { v ->
            onChange { it.copy(challenge = if (v) Rule.CHALLENGE_MATH else Rule.CHALLENGE_NONE) }
        }
    }
}

private fun moreSummary(r: Rule): String = buildList {
    if (r.lead > 0) add("за ${r.lead} мин")
    if (!r.isAlarm && r.effectiveRepeats() > 0) add("повторы ×${r.effectiveRepeats()}")
    if (r.skipOverlap) add("без совпадений")
    if (isEmpty()) add("повторы, цвет")
}.joinToString(", ")

@Composable
private fun MoreBlock(r: Rule, idx: Int, allRules: List<Rule>, now: Long, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    Block {
        NumberField(if (r.isAlarm) "Звонить раньше на, минут" else "Предупредить заранее, минут", r.lead, 0..180, Modifier.fillMaxWidth()) { v ->
            onChange { it.copy(lead = v) }
        }
        if (!r.isAlarm) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Повторять каждые, мин", r.repeatEvery, 1..120, Modifier.weight(1f)) { v -> onChange { it.copy(repeatEvery = v) } }
                NumberField("Сколько раз ещё", r.repeatCount, 0..20, Modifier.weight(1f)) { v -> onChange { it.copy(repeatCount = v) } }
            }
            if (r.repeatCount > 0) {
                Segmented(listOf("До времени", "После времени"), if (r.repeatMode == Rule.REPEAT_AFTER) 1 else 0) { i ->
                    onChange { it.copy(repeatMode = if (i == 1) Rule.REPEAT_AFTER else Rule.REPEAT_BEFORE) }
                }
            }
            Text(repeatNote(r, if (idx >= 0) TimeMath.times(allRules, idx) else r.rawTimes()), color = p.muted, fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange { it.copy(skipOverlap = !it.skipOverlap) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = r.skipOverlap, onCheckedChange = { v -> onChange { it.copy(skipOverlap = v) } })
            Text("Пропускать время, когда срабатывает другое напоминание", color = p.ink, fontSize = 13.sp)
        }
        if (!r.isAlarm) {
            Column {
                Label("Цвет")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in Accents.indices) {
                        val c = accent(i, p.dark)
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c)
                                .then(if (i == r.color) Modifier.border(3.dp, p.ink, CircleShape) else Modifier)
                                .semantics { selected = i == r.color }
                                .clickable(role = Role.RadioButton) { onChange { it.copy(color = i) } }
                        )
                    }
                }
            }
        }
        if (idx >= 0) TimesChips(r, idx, allRules, now, acc)
    }
}

/** Все времена сегодня: прошедшие бледнее. */
@Composable
private fun TimesChips(r: Rule, idx: Int, rules: List<Rule>, now: Long, acc: Color) {
    val p = LocalPalette.current
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = Rule.weekdayOf(cal.get(Calendar.DAY_OF_WEEK))
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val onToday = r.onDay(today)
    val t = TimeMath.times(rules, idx, if (onToday) today else null)
    Column {
        Label(if (onToday) "Времена сегодня" else "Времена (сегодня не работает)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val fg = if (p.dark) lerp(acc, Color.White, 0.3f) else lerp(acc, Color.Black, 0.35f)
            for (m in t.take(MAX_CHIPS)) {
                val past = !onToday || m - r.lead <= nowMin
                Chip(TimeMath.fmt(m), acc.copy(alpha = 0.16f), fg, Modifier.alpha(if (past) 0.45f else 1f))
            }
            if (t.size > MAX_CHIPS) Chip("ещё ${t.size - MAX_CHIPS}", p.tint, p.muted)
        }
    }
}

/** Пояснение к повторам: сколько уведомлений в серии, их точные времена и как остановить. */
internal fun repeatNote(r: Rule, t: List<Int>): String {
    if (r.repeatCount <= 0) return "Повторы выключены: поставьте в «Сколько раз ещё» число больше 0."
    val eff = r.effectiveRepeats()
    if (eff == 0) {
        return "Повторы не помещаются: серия должна закончиться раньше соседнего времени (шаг ${r.interval} мин). Уменьшите шаг повторов."
    }
    val total = eff + 1
    val s = StringBuilder()
    s.append(if (r.repeatMode == Rule.REPEAT_BEFORE) "Серия идёт до времени. " else "Серия идёт после времени. ")
    s.append(total).append(' ').append(TimeMath.plural(total, "уведомление", "уведомления", "уведомлений"))
        .append(" с шагом ").append(r.repeatEvery).append(" мин.")
    if (t.isNotEmpty()) s.append("\nДля ").append(TimeMath.fmt(t[0])).append(": ").append(TimeMath.seriesText(r, t[0]))
    if (eff < r.repeatCount) s.append("\nОстальные повторы не помещаются до соседнего времени.")
    s.append("\nОстановить серию: «Готово» в уведомлении, тап по нему или смахивание.")
    return s.toString()
}
