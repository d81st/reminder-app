package com.h4isenb.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** Сколько дней показывать в полосе: несколько прошедших и месяц вперёд. */
private const val STRIP_BEFORE = 3
private const val STRIP_AFTER = 30

/** Полоса дней: день недели, число и точки напоминаний; выбранный день залит, сегодня — с рамкой. */
@Composable
fun WeekStrip(rules: List<Rule>, now: Long, selectedDay: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val today = Rule.ymd(now)
    val days = remember(today) {
        val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -STRIP_BEFORE) }
        (0..STRIP_BEFORE + STRIP_AFTER).map {
            val ymd = Rule.ymd(c)
            val wd = Rule.weekdayOf(c.get(Calendar.DAY_OF_WEEK))
            c.add(Calendar.DAY_OF_YEAR, 1)
            ymd to wd
        }
    }
    val dots = remember(rules, days) { days.associate { (ymd, _) -> ymd to TimeMath.onDate(rules, ymd).take(3).map { it.first } } }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (STRIP_BEFORE - 1).coerceAtLeast(0))
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 0.dp)) {
        items(days, key = { it.first }) { (ymd, wd) ->
            val on = ymd == selectedDay
            val isToday = ymd == today
            Column(
                Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) p.primary else p.card)
                    .then(if (isToday && !on) Modifier.border(1.5.dp, p.primary, RoundedCornerShape(16.dp)) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(ymd) }
                    .semantics { selected = on }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(Rule.DAY_SHORT[wd], color = if (on) p.onPrimary else p.muted, fontSize = 11.sp)
                Text((ymd % 100).toString(), color = if (on) p.onPrimary else p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in dots[ymd].orEmpty()) {
                        val r = rules[i]
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) p.onPrimary else colorOf(r, p.dark)))
                    }
                }
            }
        }
    }
}

private fun colorOf(r: Rule, dark: Boolean): Color = if (r.isAlarm) (if (dark) Amber else Color(0xFFB45309)) else accent(r.color, dark)

/** Что показать в ленте: время, пустой промежуток или линия «Сейчас». */
private sealed class TlRow {
    class Entry(val e: TimeMath.DayEntry, val next: Boolean) : TlRow()
    class Gap(val minutes: Int) : TlRow()
    class Now(val minute: Int) : TlRow()
}

/**
 * Лента дня в общем списке главного экрана. Слева время, посередине линия с кружками, справа карточка.
 * Сегодня: прошедшие бледнее, следующее выделено, линия «Сейчас», кружок справа — «Сделано».
 */
fun LazyListScope.dayTimeline(
    rules: List<Rule>,
    ymd: Int,
    now: Long,
    done: Map<String, Long>,
    pausedUntil: Long,
    onOpen: (Rule) -> Unit,
    onToggleDone: (Rule, Long) -> Unit,
) {
    val entries = TimeMath.dayEntries(rules, ymd)
    val today = Rule.ymd(now)
    if (entries.isEmpty()) {
        item(key = "tl-empty") {
            val p = LocalPalette.current
            Text(
                if (rules.isEmpty()) "Пока нет напоминаний. Нажмите «+» внизу: можно начать с готовой заготовки."
                else if (ymd < today) "В этот день ничего не было." else "В этот день ничего не придёт. Свободный день.",
                color = p.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
        return
    }
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    // время T уже наступило: уведомление было (или было бы) показано
    fun past(e: TimeMath.DayEntry) = ymd < today || (ymd == today && e.minute <= nowMin)
    val nextIdx = if (ymd == today) entries.indexOfFirst { !past(it) && it.doneKey() !in done } else -1
    val rows = ArrayList<TlRow>()
    var nowShown = ymd != today
    for ((i, e) in entries.withIndex()) {
        if (i > 0) {
            val gap = e.minute - entries[i - 1].minute
            if (gap >= 120) rows.add(TlRow.Gap(gap))
        }
        if (!nowShown && !past(e)) {
            rows.add(TlRow.Now(nowMin))
            nowShown = true
        }
        rows.add(TlRow.Entry(e, i == nextIdx))
    }
    if (!nowShown) rows.add(TlRow.Now(nowMin))

    // одним блоком, без промежутков между строками: иначе линия ленты рвётся
    item(key = "timeline-$ymd") {
        Column {
            for ((k, row) in rows.withIndex()) {
                val first = k == 0
                val last = k == rows.size - 1
                when (row) {
                    is TlRow.Entry -> EntryRow(row.e, row.next, past(row.e), row.e.doneKey() in done, ymd == today, now,
                        paused = row.e.baseAt in now..<pausedUntil, first = first, last = last,
                        onOpen = { onOpen(row.e.rule) }, onToggle = { onToggleDone(row.e.rule, row.e.baseAt) })
                    is TlRow.Gap -> GapRow(row.minutes)
                    is TlRow.Now -> NowRow(row.minute)
                }
            }
        }
    }
}

/** Вертикальная линия ленты в средней колонке. */
private fun Modifier.rail(color: Color, top: Boolean, bottom: Boolean, dashed: Boolean = false) = drawBehind {
    val x = size.width / 2
    val w = 2.dp.toPx()
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
    if (top) drawLine(color, Offset(x, 0f), Offset(x, size.height / 2), w, pathEffect = effect)
    if (bottom) drawLine(color, Offset(x, size.height / 2), Offset(x, size.height), w, pathEffect = effect)
}

private val TIME_W = 50.dp
private val RAIL_W = 34.dp

@Composable
private fun EntryRow(
    e: TimeMath.DayEntry,
    next: Boolean,
    past: Boolean,
    done: Boolean,
    isToday: Boolean,
    now: Long,
    paused: Boolean,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val p = LocalPalette.current
    val r = e.rule
    val acc = colorOf(r, p.dark)
    val faded = done || (past && isToday) || (past && !isToday)
    val icon = when (Phrases.mode(r)) {
        Phrases.MODE_PERSISTENT -> Icons.Filled.Refresh
        Phrases.MODE_ALARM -> AlarmIcon
        else -> Icons.Filled.Notifications
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(TIME_W).padding(top = 18.dp), horizontalAlignment = Alignment.End) {
            Text(TimeMath.fmt(e.minute), color = if (next) p.ink else p.muted, fontSize = 13.sp,
                fontWeight = if (next) FontWeight.Bold else FontWeight.Medium)
        }
        Box(Modifier.width(RAIL_W).fillMaxHeight().rail(p.line, !first, !last), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.padding(top = 14.dp).size(26.dp).clip(CircleShape)
                    .background(if (done) acc else if (faded) p.bg else acc.copy(alpha = 0.16f))
                    .border(2.dp, if (faded && !done) p.line else acc, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = if (r.isAlarm) Color(0xFF17163A) else onAccent(p.dark), modifier = Modifier.size(14.dp))
                else Icon(icon, contentDescription = null, tint = if (faded) p.muted else acc, modifier = Modifier.size(13.dp))
            }
        }
        Row(
            Modifier.weight(1f).padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp))
                .background(if (next) acc.copy(alpha = if (p.dark) 0.22f else 0.12f) else p.card)
                .then(if (next) Modifier.border(1.5.dp, acc, RoundedCornerShape(18.dp)) else Modifier)
                .clickable(onClickLabel = "Изменить", onClick = onOpen)
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp)
                .alpha(if (faded) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildList {
                    if (r.isAlarm) add("Будильник")
                    else if (Phrases.mode(r) == Phrases.MODE_PERSISTENT) add("Настойчиво")
                    if (r.lead > 0) add("заранее за ${r.lead} мин")
                    when {
                        done -> add("сделано")
                        paused -> add("на паузе")
                        next -> add(TimeMath.countdown(e.baseAt + r.lead * TimeMath.MIN, now))
                    }
                }.joinToString(" · ").ifEmpty { r.text.ifBlank { "напоминание" } }
                Text(sub, color = if (next && !done) acc else p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (next) FontWeight.SemiBold else FontWeight.Normal)
            }
            // «Сделано» — только для сегодняшнего дня: отметки хранятся несколько дней
            if (isToday && r.id > 0) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClick = onToggle)
                        .semantics { contentDescription = r.name + " " + TimeMath.fmt(e.minute); stateDescription = if (done) "Сделано" else "Не сделано" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (done) acc else Color.Transparent)
                            .border(2.dp, if (done) acc else p.muted.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = if (r.isAlarm) Color(0xFF17163A) else onAccent(p.dark), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/** Пустой промежуток между временами: «свободно 2 ч 30 мин». */
@Composable
private fun GapRow(minutes: Int) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(TIME_W))
        Box(Modifier.width(RAIL_W).fillMaxHeight().rail(p.line, top = true, bottom = true, dashed = true))
        val h = minutes / 60
        val m = minutes % 60
        Text("свободно $h ч" + if (m > 0) " $m мин" else "", color = p.muted, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.card.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

/** Линия «Сейчас 10:18» между прошедшим и будущим. */
@Composable
private fun NowRow(minute: Int) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Сейчас", color = p.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.width(TIME_W + 6.dp).clip(RoundedCornerShape(8.dp)).background(p.primary).padding(horizontal = 6.dp, vertical = 3.dp),
            textAlign = TextAlign.Center)
        Box(Modifier.width(RAIL_W - 6.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(8.dp).clip(CircleShape).background(p.primary)) }
        Box(Modifier.weight(1f).height(2.dp).drawBehind {
            drawLine(p.primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        })
        Text(" " + TimeMath.fmt(minute), color = p.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
