@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.h4isenb.reminder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar


/**
 * Редактор напоминания на весь экран. Сверху коротко «Когда» и «Дни» (раскрываются по нажатию),
 * дальше главный выбор — «Как напоминать»: тихо, настойчиво (с повторами) или будильником,
 * и живой пример «Как это будет», который можно проиграть. Правки сохраняются сразу.
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
    val mode = Phrases.mode(r)
    val acc = if (r.isAlarm) (if (p.dark) Amber else Color(0xFFB45309)) else accent(r.color, p.dark)
    val idx = allRules.indexOfFirst { it.key == r.key }

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

            WhenAndDays(r, idx, allRules, now, acc, onChange)

            SectionTitle("Как напоминать")
            // карточки одной высоты: по самой высокой
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeCard("Тихо", "одно уведомление", Icons.Filled.Notifications, mode == Phrases.MODE_QUIET, accent(r.color, p.dark), Modifier.weight(1f)) {
                    onChange { it.copy(kind = Rule.KIND_NOTIFY, repeatCount = 0) }
                }
                ModeCard("Настойчиво", "повторит до «Готово»", Icons.Filled.Refresh, mode == Phrases.MODE_PERSISTENT,
                    accent(r.color, p.dark), Modifier.weight(1f)) {
                    onChange {
                        it.copy(kind = Rule.KIND_NOTIFY, repeatCount = if (it.repeatCount > 0) it.repeatCount else 3,
                            repeatEvery = it.repeatEvery.coerceIn(1, 120))
                    }
                }
                ModeCard("Будильник", "громкий звонок", AlarmIcon, mode == Phrases.MODE_ALARM, if (p.dark) Amber else Color(0xFFB45309), Modifier.weight(1f)) {
                    onChange {
                        // будильник обычно один раз в день: из окна «с–до» делаем одно время
                        if (!it.isAlarm && it.start != it.end) it.copy(kind = Rule.KIND_ALARM, end = it.start) else it.copy(kind = Rule.KIND_ALARM)
                    }
                }
            }
            when (mode) {
                Phrases.MODE_PERSISTENT -> PersistentBlock(r, acc, onChange)
                Phrases.MODE_ALARM -> AlarmBlock(r, acc, actions, onChange)
            }
            LeadBlock(r, acc, onChange)

            Block {
                Text(if (r.isAlarm) "Текст на экране будильника" else "Текст уведомления", color = p.muted, fontSize = 13.sp)
                AppField(r.text, { v -> onChange { it.copy(text = v) } }, "Что напомнить", singleLine = false)
            }

            Preview(r, idx, allRules, now, acc)

            MoreBlock(r, onChange)
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
private fun SectionTitle(s: String, trailing: String? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(s, color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = p.muted, fontSize = 13.sp)
    }
}

// =====================================================================
// «Когда» и «Дни»: коротко одной строкой, по нажатию — настройки
// =====================================================================

@Composable
private fun WhenAndDays(r: Rule, idx: Int, allRules: List<Rule>, now: Long, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    // у нового напоминания сразу открыто «Когда»
    var open by rememberSaveable(r.key) { mutableIntStateOf(if (r.id == 0L) 1 else 0) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 4.dp).animateContentSize()) {
        SummaryRow(Icons.Filled.Notifications, "Когда", Phrases.schedule(r), open == 1, acc) { open = if (open == 1) 0 else 1 }
        if (open == 1) Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { WhenControls(r, idx, allRules, acc, onChange) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        SummaryRow(Icons.Filled.DateRange, if (r.isOneDay) "Дата" else "Дни", Phrases.days(r, Rule.ymd(now)).replaceFirstChar { it.uppercase() },
            open == 2, acc, dots = if (r.isDated) null else r.days) {
            open = if (open == 2) 0 else 2
        }
        if (open == 2) Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { DaysControls(r, now, acc, onChange) }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, caption: String, value: String, open: Boolean, acc: Color, dots: Int? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClickLabel = if (open) "Свернуть" else "Изменить", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(acc.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = acc, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(caption, color = p.muted, fontSize = 12.sp)
            Text(value, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (dots != null && !open) DayDots(dots, acc, p.off)
        else Text(if (open) "Свернуть" else "Изменить", color = acc, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WhenControls(r: Rule, idx: Int, allRules: List<Rule>, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    val once = r.start == r.end
    val preset = when {
        once -> 0
        r.interval == 60 -> 1
        r.interval == 30 -> 2
        else -> 3
    }
    var custom by rememberSaveable(r.key) { mutableStateOf(preset == 3) }
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

@Composable
private fun DaysControls(r: Rule, now: Long, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    val today = Rule.ymd(now)
    val tomorrow = Rule.ymd(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, 1) })
    val mode = when {
        r.isCycle -> 1
        r.isDated -> 2
        else -> 0
    }
    Segmented(listOf("Дни недели", "Раз в N дней", "Одна дата"), mode) { i ->
        val start = if (laterToday(r, now)) today else tomorrow
        when (i) {
            0 -> onChange { it.copy(date = 0, everyDays = 0) }
            1 -> onChange { it.copy(date = if (it.isDated) it.date else start, everyDays = if (it.isCycle) it.everyDays else 2) }
            else -> onChange { it.copy(date = if (it.isDated) it.date else start, everyDays = 0) }
        }
    }
    if (r.isCycle) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in listOf(2, 3, 7)) Pill(Phrases.every(n).replaceFirstChar { it.uppercase() }, r.everyDays == n, acc) { onChange { it.copy(everyDays = n) } }
        }
        NumberField("Каждые, дней", r.everyDays, 2..Rule.MAX_EVERY_DAYS, Modifier.fillMaxWidth()) { v -> onChange { it.copy(everyDays = v) } }
        Label("Начиная с")
        DatePills(r, today, tomorrow, acc, onChange)
        // ближайшие дни, чтобы было видно, как ляжет расписание
        val days = generateSequence(r.nextDate(today)) { r.nextDate(Rule.fromEpochDay(Rule.epochDay(it) + 1)) }.take(3).toList()
        Text("Ближайшие дни: " + days.joinToString(" · ") { Phrases.date(it, today) },
            color = p.muted, fontSize = 12.sp)
        return
    }
    if (r.isDated) {
        DatePills(r, today, tomorrow, acc, onChange)
        val passed = r.date < today || (r.date == today && !laterToday(r, now))
        Text(
            if (passed) "Этот день уже прошёл: выберите другую дату, иначе напоминание не придёт."
            else "Придёт только в этот день. Потом карточка останется: её можно перенести на другую дату или удалить.",
            color = if (!passed) p.muted else if (p.dark) Color(0xFFFDA4AF) else Rose, fontSize = 12.sp,
        )
        return
    }
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

/** «Сегодня», «Завтра», «Другой день…» (календарь). */
@Composable
private fun DatePills(r: Rule, today: Int, tomorrow: Int, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    var pick by remember { mutableStateOf(false) }
    val other = r.date != today && r.date != tomorrow
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Сегодня", r.date == today, acc) { onChange { it.copy(date = today) } }
        Pill("Завтра", r.date == tomorrow, acc) { onChange { it.copy(date = tomorrow) } }
        Pill(if (other) Phrases.date(r.date, today) else "Другой день…", other, acc) { pick = true }
    }
    if (pick) DatePickDialog(r.date, today, onDismiss = { pick = false }) { d -> pick = false; onChange { it.copy(date = d) } }
}

/** Осталось ли у напоминания сегодня хоть одно время впереди (с учётом «за N минут»). */
private fun laterToday(r: Rule, now: Long): Boolean {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    return r.rawTimes().any { it - r.lead > nowMin }
}

// =====================================================================
// «Как напоминать»
// =====================================================================

@Composable
private fun ModeCard(title: String, hint: String, icon: ImageVector, on: Boolean, acc: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxHeight()
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .border(2.dp, if (on) acc else Color.Transparent, RoundedCornerShape(18.dp))
            .semantics { selected = on }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (on) acc else p.muted, modifier = Modifier.size(22.dp))
        // при крупном системном шрифте заголовок уменьшается, пока не поместится в карточку целиком
        var titleSize by remember { mutableStateOf(13.sp) }
        var fits by remember { mutableStateOf(false) }
        Text(title, color = if (on) p.ink else p.muted, fontSize = titleSize, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            modifier = Modifier.drawWithContent { if (fits) drawContent() },
            onTextLayout = { res ->
                if (res.hasVisualOverflow && titleSize.value > 8f) titleSize = (titleSize.value * 0.92f).sp else fits = true
            })
        Text(hint, color = p.muted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

/** «Если не нажать „Готово“, напомнить ещё [− 3 +] раза с промежутком …». */
@Composable
private fun PersistentBlock(r: Rule, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    Block {
        Text("Если не нажать «Готово», напомнить ещё", color = p.ink, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton("−", "Меньше", acc) { onChange { it.copy(repeatCount = (it.repeatCount - 1).coerceAtLeast(1)) } }
            Text(r.repeatCount.toString(), color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepButton("+", "Больше", acc) { onChange { it.copy(repeatCount = (it.repeatCount + 1).coerceAtMost(20)) } }
            Text(TimeMath.plural(r.repeatCount, "раз", "раза", "раз"), color = p.muted, fontSize = 15.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("с промежутком", color = p.muted, fontSize = 13.sp)
            PresetPills(r.key, listOf(1, 2, 5, 10), r.repeatEvery, 1..120, "Промежуток, минут", acc,
                label = { "$it мин" }) { v -> onChange { it.copy(repeatEvery = v) } }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("серия", color = p.muted, fontSize = 13.sp)
            // обе половины одной ширины и высоты, подложка выбранной закрывает её целиком
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(p.bg).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DirOption("до времени", "последнее — в само время", r.repeatMode == Rule.REPEAT_BEFORE, Modifier.weight(1f)) {
                    onChange { it.copy(repeatMode = Rule.REPEAT_BEFORE) }
                }
                DirOption("после времени", "первое — в само время", r.repeatMode == Rule.REPEAT_AFTER, Modifier.weight(1f)) {
                    onChange { it.copy(repeatMode = Rule.REPEAT_AFTER) }
                }
            }
        }
        val eff = r.effectiveRepeats()
        if (eff < r.repeatCount) {
            Text(
                if (eff == 0) "Повторы не помещаются: следующее время уже через ${r.interval} мин. Уменьшите промежуток."
                else "Поместится только $eff: следующее время уже через ${r.interval} мин.",
                color = if (p.dark) Color(0xFFFDA4AF) else Rose, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun StepButton(label: String, description: String, acc: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.tint)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = acc, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun DirOption(title: String, hint: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(if (on) p.card else Color.Transparent)
            .semantics { selected = on }.clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = if (on) p.ink else p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(hint, color = p.muted, fontSize = 11.sp, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AlarmBlock(r: Rule, acc: Color, actions: MainActions, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    Block {
        Text("Звонит громко, пока не выключите. Экран поверх блокировки, слышно и в беззвучном режиме.", color = p.muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Мелодия", color = p.ink, fontSize = 15.sp)
                Text(actions.alarmSoundTitle(r.alarmSound), color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TonalButton("Выбрать") { actions.pickAlarmSound(r.key) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("«Отложить» на", color = p.muted, fontSize = 13.sp)
            PresetPills(r.key, listOf(5, 10, 15, 30), r.snoozeMin, 1..60, "Отложить на, минут", acc,
                label = { "$it мин" }) { v -> onChange { it.copy(snoozeMin = v) } }
        }
        SwitchRow("Нарастающая громкость", "Начинает тихо и за полминуты доходит до полной", r.gradual) { v -> onChange { it.copy(gradual = v) } }
        SwitchRow("Выключать решением примера", "«Выключить» сработает только после ответа", r.challenge == Rule.CHALLENGE_MATH) { v ->
            onChange { it.copy(challenge = if (v) Rule.CHALLENGE_MATH else Rule.CHALLENGE_NONE) }
        }
    }
}

@Composable
private fun LeadBlock(r: Rule, acc: Color, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    Block {
        Text(if (r.isAlarm) "Зазвонит" else "Первое уведомление", color = p.muted, fontSize = 13.sp)
        PresetPills(r.key, listOf(0, 5, 10, 15), r.lead, 0..180, if (r.isAlarm) "Раньше на, минут" else "Заранее, минут", acc,
            label = { if (it == 0) "вовремя" else "за $it мин" }) { v -> onChange { it.copy(lead = v) } }
    }
}

// =====================================================================
// «Как это будет»: живой пример серии
// =====================================================================

/** Время, на котором показывать пример: ближайшее сегодняшнее, иначе первое. */
private fun sampleTime(r: Rule, idx: Int, allRules: List<Rule>, now: Long): Int? {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val t = if (idx >= 0) TimeMath.times(allRules, idx) else r.rawTimes()
    return t.firstOrNull { it - r.lead > nowMin } ?: t.firstOrNull()
}

@Composable
private fun Preview(r: Rule, idx: Int, allRules: List<Rule>, now: Long, acc: Color) {
    val p = LocalPalette.current
    val t = sampleTime(r, idx, allRules, now)
    SectionTitle("Как это будет", t?.let { "для " + TimeMath.fmt(it) })
    if (t == null) {
        Block { Text("Нет ни одного времени: проверьте «Когда».", color = Rose, fontSize = 13.sp) }
        return
    }
    val series = Phrases.series(r, t)
    val n = series.size
    // показ серии по шагам; при любом изменении настроек начинается заново
    var step by remember(series, r.kind) { mutableIntStateOf(-1) }
    var doneAt by remember(series, r.kind) { mutableIntStateOf(-1) }
    var playing by remember(series, r.kind) { mutableStateOf(false) }
    LaunchedEffect(playing, series) {
        if (!playing) return@LaunchedEffect
        for (i in 0 until n) {
            step = i
            delay(1100)
        }
        playing = false
    }
    val k = step.coerceAtLeast(0)

    Block {
        val track = p.line
        val ink = p.ink
        val off = p.off
        val lo = minOf(series.first(), t) - 8
        val hi = maxOf(series.last(), t) + 8
        BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
            val tx = maxWidth * ((t - lo).toFloat() / (hi - lo))
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val w = size.width
                val mid = 20.dp.toPx()
                fun x(m: Int) = (m - lo).toFloat() / (hi - lo) * w
                drawRect(track, Offset(0f, mid - 1.dp.toPx()), Size(w, 2.dp.toPx()))
                drawRect(ink, Offset(x(t) - 1.dp.toPx(), 4.dp.toPx()), Size(2.dp.toPx(), 32.dp.toPx()))
                for ((i, m) in series.withIndex()) {
                    val cancelled = doneAt >= 0 && i > doneAt
                    val current = i == step && !cancelled
                    val dim = step >= 0 && i > step && !cancelled
                    val c = if (cancelled) off else acc
                    if (current) drawCircle(acc.copy(alpha = 0.2f), 11.dp.toPx(), Offset(x(m), mid))
                    drawCircle(c.copy(alpha = if (dim) 0.35f else 1f), (if (current) 8 else 5).dp.toPx(), Offset(x(m), mid))
                }
            }
            // подпись самого времени под его чертой
            Text(TimeMath.fmt(t), color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = (tx - 16.dp).coerceIn(0.dp, maxWidth - 34.dp)))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((i, m) in series.withIndex()) {
                val cancelled = doneAt >= 0 && i > doneAt
                val current = i == step && !cancelled
                Text(
                    TimeMath.fmt(m), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = when {
                        cancelled -> p.muted
                        current -> onAccent(p.dark)
                        else -> acc
                    },
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (current) acc else if (cancelled) p.bg else acc.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        // как уведомление будет выглядеть на телефоне
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.alarmCard).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (r.isAlarm) Amber else acc), contentAlignment = Alignment.Center) {
                    Icon(if (r.isAlarm) AlarmIcon else Icons.Filled.Notifications, contentDescription = null,
                        tint = if (r.isAlarm) p.alarmCard else Color.White, modifier = Modifier.size(12.dp))
                }
                Text("Напоминалка · " + TimeMath.fmt(series[k]), color = p.alarmMuted, fontSize = 11.sp)
            }
            Text(if (r.isAlarm) "Будильник · " + TimeMath.fmt(series[k]) else Phrases.title(r, t, k),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(r.text.ifBlank { r.name }, color = p.alarmMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!r.isAlarm && n > 1 && doneAt < 0 && k < n - 1) {
                Text(
                    "Готово", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(p.alarmTile)
                        .clickable(role = Role.Button) {
                            doneAt = k
                            step = k
                            playing = false
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Text(Phrases.explain(r, t, doneAt), color = p.muted, fontSize = 13.sp, lineHeight = 19.sp)
        if (n > 1) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(p.tint)
                    .clickable(enabled = !playing, role = Role.Button) {
                        doneAt = -1
                        step = 0
                        playing = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (playing) "Идёт показ…" else "Показать, как придут уведомления", color = p.tintText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// =====================================================================
// «Дополнительно»
// =====================================================================

/** Цвет карточки и «пропускать чужие времена» — то, чего нет в блоках выше. */
@Composable
private fun MoreBlock(r: Rule, onChange: ((Rule) -> Rule) -> Unit) {
    val p = LocalPalette.current
    Block {
        Text("Дополнительно", color = p.muted, fontSize = 13.sp)
        if (!r.isAlarm) {
            Column {
                Label("Цвет")
                ColorPicker(r.color) { i -> onChange { it.copy(color = i) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange { it.copy(skipOverlap = !it.skipOverlap) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = r.skipOverlap, onCheckedChange = { v -> onChange { it.copy(skipOverlap = v) } })
            Column {
                Text("Пропускать время, когда срабатывает другое напоминание", color = p.ink, fontSize = 13.sp)
                if (r.skipOverlap) Text("Если галочка стоит у нескольких, в общее время придёт то, что срабатывает реже.",
                    color = p.muted, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Готовые значения кнопками и «своё…» для точного числа: поле ввода появляется под кнопками.
 * Одна и та же штука для «Первое уведомление», промежутка повторов и «Отложить на».
 */
@Composable
private fun PresetPills(
    key: Long, options: List<Int>, value: Int, range: IntRange, fieldLabel: String, acc: Color,
    label: (Int) -> String, onPick: (Int) -> Unit,
) {
    var custom by rememberSaveable(key, fieldLabel) { mutableStateOf(value !in options) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in options) Pill(label(m), !custom && value == m, acc) { custom = false; onPick(m) }
        Pill(if (custom) label(value) else "своё…", custom, acc) { custom = true }
    }
    if (custom) NumberField(fieldLabel, value, range, Modifier.fillMaxWidth()) { v -> onPick(v) }
}

