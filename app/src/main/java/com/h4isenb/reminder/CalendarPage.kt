package com.h4isenb.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь",
    "Октябрь", "Ноябрь", "Декабрь")

/** Один день в сетке месяца. */
private class CalDay(val ymd: Int, val inMonth: Boolean, val items: List<Pair<Int, List<Int>>>)

/**
 * Календарь: месяц с отметками, в какие дни что придёт, и список дня. Отсюда же можно добавить напоминание на день.
 */
@Composable
fun CalendarPage(rules: List<Rule>, now: Long, onOpen: (Rule) -> Unit, onAddOn: (Int) -> Unit) {
    val p = LocalPalette.current
    val today = Rule.ymd(now)
    // месяц как число «год*12 + месяц», выбранный день — ГГГГММДД
    var month by rememberSaveable { mutableIntStateOf(today / 10000 * 12 + today / 100 % 100 - 1) }
    var selected by rememberSaveable { mutableIntStateOf(today) }
    val weeks = remember(rules, month) { monthGrid(rules, month) }
    val dayItems = remember(rules, selected) { TimeMath.onDate(rules, selected) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title") { PageTitle("Календарь") }
        item(key = "month") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Прошлый месяц") { month-- }
                    Text("${MONTHS[month % 12]} ${month / 12}", color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium,
                        fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                            .clickable(onClickLabel = "К сегодняшнему дню") {
                                month = today / 10000 * 12 + today / 100 % 100 - 1
                                selected = today
                            })
                    ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий месяц") { month++ }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                    for (d in 0..6) {
                        Text(Rule.DAY_SHORT[d], color = if (d >= 5) p.muted.copy(alpha = 0.7f) else p.muted, fontSize = 12.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                for (week in weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (d in week) {
                            DayCell(d, rules, d.ymd == today, d.ymd == selected, Modifier.weight(1f)) {
                                selected = d.ymd
                                if (!d.inMonth) month = d.ymd / 10000 * 12 + d.ymd / 100 % 100 - 1
                            }
                        }
                    }
                }
            }
        }
        item(key = "day") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val title = when (selected) {
                    today -> "Сегодня"
                    else -> Phrases.date(selected, today).replaceFirstChar { it.uppercase() }
                }
                Text(title, color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (selected >= today) TonalButton("+ На этот день") { onAddOn(selected) }
            }
        }
        if (dayItems.isEmpty()) {
            item(key = "empty") {
                Text(if (selected < today) "В этот день ничего не было." else "В этот день ничего не придёт.",
                    color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        items(dayItems, key = { rules[it.first].key }) { (i, times) ->
            DayItem(rules[i], times) { onOpen(rules[i]) }
        }
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = p.ink) }
}

@Composable
private fun DayCell(d: CalDay, rules: List<Rule>, isToday: Boolean, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val count = d.items.size
    Column(
        modifier
            .aspectRatio(0.9f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) p.primary else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, p.primary, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = "${d.ymd % 100}, " + if (count == 0) "ничего" else "напоминаний: $count"
            }
            .alpha(if (d.inMonth) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text((d.ymd % 100).toString(), color = if (isSelected) p.onPrimary else p.ink, fontSize = 14.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((i, _) in d.items.take(3)) {
                val r = rules[i]
                val c = if (r.isAlarm) (if (p.dark) Amber else Color(0xFFB45309)) else accent(r.color, p.dark)
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) p.onPrimary else c))
            }
        }
    }
}

@Composable
private fun DayItem(r: Rule, times: List<Int>, onClick: () -> Unit) {
    val p = LocalPalette.current
    val acc = if (r.isAlarm) (if (p.dark) Amber else Color(0xFFB45309)) else accent(r.color, p.dark)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).clickable(onClickLabel = "Изменить", onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(acc))
        Column(Modifier.weight(1f)) {
            Text(r.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val when_ = when {
                times.size == 1 -> TimeMath.fmt(times[0])
                times.size <= 4 -> times.joinToString(", ") { TimeMath.fmt(it) }
                else -> "${times.size} " + TimeMath.plural(times.size, "раз", "раза", "раз") +
                    ": ${TimeMath.fmt(times.first())} – ${TimeMath.fmt(times.last())}"
            }
            Text((if (r.isAlarm) "Будильник · " else "") + when_, color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(TimeMath.fmt(times.first()), color = acc, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

/** Недели месяца (с понедельника), включая дни соседних месяцев по краям. */
private fun monthGrid(rules: List<Rule>, month: Int): List<List<CalDay>> {
    val utc = java.util.TimeZone.getTimeZone("UTC")
    val c = Calendar.getInstance(utc)
    c.clear()
    c.set(month / 12, month % 12, 1)
    val first = Rule.weekdayOf(c.get(Calendar.DAY_OF_WEEK))
    c.add(Calendar.DAY_OF_YEAR, -first)
    val weeks = ArrayList<List<CalDay>>()
    while (weeks.size < 6) {
        val week = (0..6).map {
            val ymd = Rule.ymd(c)
            val d = CalDay(ymd, c.get(Calendar.MONTH) == month % 12, TimeMath.onDate(rules, ymd))
            c.add(Calendar.DAY_OF_YEAR, 1)
            d
        }
        weeks.add(week)
        // следующая неделя уже целиком в другом месяце
        if (c.get(Calendar.MONTH) != month % 12) break
    }
    return weeks
}
