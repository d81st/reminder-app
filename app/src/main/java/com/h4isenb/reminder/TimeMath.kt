package com.h4isenb.reminder

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Расчёт времён уведомлений. Без Android-классов, поэтому проверяется обычными тестами.
 *
 * Модель: у каждого времени T есть основное уведомление в момент B = T - lead («за N минут»).
 * Если включены повторы, вокруг B строится серия из repeats = effectiveRepeats() дополнительных уведомлений
 * с шагом repeatEvery минут: ДО B (серия заканчивается в B) или ПОСЛЕ B (серия начинается в B).
 * Дни недели относятся к дню самого времени T, а не к моменту уведомления (будильник «за 30 минут» до 00:10
 * в понедельник придёт в воскресенье в 23:40).
 */
object TimeMath {
    const val MIN = 60_000L

    /**
     * Насколько раньше начала суток может оказаться уведомление этих суток: основное до 3 часов
     * («за N минут») плюс серия до 12 часов (она всегда короче интервала, а интервал не больше 720 минут).
     */
    private const val REACH = 16 * 60 * MIN

    class Event(
        val rule: Rule,
        val index: Int,
        val minute: Int,  // время T (минуты от полуночи), без учёта «за N минут»
        val at: Long,     // момент, когда нужно показать уведомление
        val baseAt: Long, // момент основного уведомления B (общий для всей серии)
        val repeat: Int,  // 0 = основное уведомление, k = на k шагов от основного
        val seq: Int,     // номер в серии по времени, с 1
        val total: Int,   // сколько уведомлений в серии (основное + повторы)
    ) {
        /** Идентификатор серии: основное уведомление вместе с его повторами. */
        fun key(): String = "${rule.id}:$baseAt"
    }

    fun fmt(m: Int): String {
        val w = Math.floorMod(m, 1440)
        return String.format(Locale.US, "%02d:%02d", w / 60, w % 60)
    }

    /** Заголовок уведомления: название, время, «через N мин» и номер в серии. */
    fun title(e: Event): String {
        val r = e.rule
        val early = r.lead > 0 || e.total > 1
        var t = r.name + " · " + (if (early) "в " else "") + fmt(e.minute)
        if (early) {
            val left = Math.round((e.baseAt + r.lead.toLong() * MIN - e.at) / MIN.toDouble()) // минут до самого времени
            t += when {
                left > 0 -> " (через $left мин)"
                left == 0L -> " (сейчас)"
                else -> " (${-left} мин назад)"
            }
        }
        if (e.total > 1) t += " · ${e.seq}/${e.total}"
        return t
    }

    /** Минуты от полуночи всех уведомлений серии для времени t, по порядку (могут выходить за границы суток). */
    fun seriesMinutes(r: Rule, t: Int): List<Int> {
        val eff = r.effectiveRepeats()
        val base = t - r.lead // момент основного уведомления
        val before = r.repeatMode == Rule.REPEAT_BEFORE
        return (0..eff).map { i -> if (before) base - (eff - i) * r.repeatEvery else base + i * r.repeatEvery }
    }

    /** Времена серии для подписи в карточке: «09:55 · 09:56 · … · 10:00» (длинные серии сокращаются). */
    fun seriesText(r: Rule, t: Int): String {
        val items = seriesMinutes(r, t)
        val n = items.size
        val sb = StringBuilder()
        var i = 0
        while (i < n) {
            if (n > 8 && i == 3) {
                sb.append(" · …")
                i = n - 2 // середину пропускаем, дальше выводим два последних
                continue
            }
            if (sb.isNotEmpty()) sb.append(" · ")
            sb.append(fmt(items[i]))
            i++
        }
        return sb.toString()
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val a = n % 100
        val b = n % 10
        return when {
            a in 11..14 -> many
            b == 1 -> one
            b in 2..4 -> few
            else -> many
        }
    }

    /**
     * Времена напоминания с учётом «не повторять время другого напоминания».
     * weekday: день недели (0 = понедельник), тогда учитываются только напоминания, работающие в этот день,
     * и для выключенного в этот день напоминания времён нет. null = без учёта дней (для подписей в карточке).
     * ymd: дата этого дня (ГГГГММДД) для разовых напоминаний; null = разовые считаются работающими.
     */
    fun times(rules: List<Rule>, idx: Int, weekday: Int? = null, ymd: Int? = null): List<Int> {
        val r = rules[idx]
        if (weekday != null && !r.activeOn(weekday, ymd)) return emptyList()
        val t = r.rawTimes()
        if (!r.skipOverlap) return t
        val other = HashSet<Int>()
        for (i in rules.indices) {
            val o = rules[i]
            if (i != idx && o.enabled && !o.skipOverlap && (weekday == null || o.activeOn(weekday, ymd))) other.addAll(o.rawTimes())
        }
        return t.filter { it !in other }
    }

    /**
     * Все уведомления (основные и повторы) в промежутке (fromExcl, toIncl], по возрастанию времени.
     * done: серии, на которые пользователь ответил «Готово» (ключ серии -> момент ответа).
     * Всё, что в такой серии должно прийти позже этого момента, пропускается.
     */
    fun events(
        rules: List<Rule>,
        fromExcl: Long,
        toIncl: Long,
        tz: TimeZone = TimeZone.getDefault(),
        done: Map<String, Long> = emptyMap(),
    ): List<Event> {
        val out = ArrayList<Event>()
        if (toIncl <= fromExcl) return out

        // времена каждого напоминания по дням: [напоминание]. Обычные дни недели повторяются, поэтому считаются один раз;
        // день, на который выпадает разовое напоминание, считается отдельно
        val dated = rules.indices.filter { rules[it].enabled && rules[it].isDated }
        val byDay = HashMap<String, List<List<Int>>>()
        fun timesOn(wd: Int, ymd: Int): List<List<Int>> {
            // ключ: день недели и какие из привязанных к дате напоминаний работают в этот день
            val k = wd.toString() + dated.filter { rules[it].activeOn(wd, ymd) }.joinToString(",", ":")
            return byDay.getOrPut(k) { rules.indices.map { i -> if (rules[i].enabled) times(rules, i, wd, ymd) else emptyList() } }
        }

        val day = Calendar.getInstance(tz)
        day.timeInMillis = fromExcl
        day.set(Calendar.HOUR_OF_DAY, 0)
        day.set(Calendar.MINUTE, 0)
        day.set(Calendar.SECOND, 0)
        day.set(Calendar.MILLISECOND, 0)
        // Серия ПОСЛЕ времени может тянуться из вчерашнего дня (до 12 ч), поэтому начинаем со вчера
        day.add(Calendar.DAY_OF_YEAR, -1)

        var guard = 0
        // Серия ДО времени может начаться раньше своих суток, поэтому идём дальше конца окна на REACH
        while (day.timeInMillis - REACH <= toIncl && guard++ < 60) {
            val all = timesOn(Rule.weekdayOf(day.get(Calendar.DAY_OF_WEEK)), Rule.ymd(day))
            for (i in rules.indices) {
                val r = rules[i]
                val reps = r.effectiveRepeats()
                val before = r.repeatMode == Rule.REPEAT_BEFORE
                val step = r.repeatEvery.toLong() * MIN
                for (t in all[i]) {
                    // Время считается по часам на стене (а не прибавлением минут к полуночи),
                    // чтобы в день перевода часов 09:00 оставалось 09:00.
                    val c = Calendar.getInstance(tz)
                    c.clear()
                    c.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), 0, t - r.lead, 0)
                    val base = c.timeInMillis
                    val doneAt = if (reps > 0 && r.id > 0) done["${r.id}:$base"] else null
                    for (k in 0..reps) {
                        val at = if (before) base - k * step else base + k * step
                        if (at <= fromExcl || at > toIncl) continue
                        if (doneAt != null && at > doneAt) continue
                        out.add(Event(r, i, t, at, base, k, if (before) reps - k + 1 else k + 1, reps + 1))
                    }
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        out.sortWith(compareBy<Event>({ it.at }, { it.index }))
        return out
    }

    /**
     * Если будильник опоздал и накопилось несколько уведомлений одной серии, показывать нужно только
     * последнее по времени (все они заменяют друг друга). Возвращает по одному событию на серию.
     */
    fun collapse(events: List<Event>): List<Event> {
        val latest = LinkedHashMap<String, Event>()
        for (e in events) latest[e.key()] = e // события идут по времени, остаётся самое позднее
        return latest.values.toList()
    }

    /** Ближайшее основное уведомление каждого напоминания (индекс -> событие) в течение недели после now. */
    fun nextByRule(rules: List<Rule>, now: Long, tz: TimeZone = TimeZone.getDefault()): Map<Int, Event> {
        val out = HashMap<Int, Event>()
        for (e in events(rules, now, now + 8L * 24 * 60 * MIN, tz)) {
            if (e.repeat == 0 && e.index !in out) out[e.index] = e
        }
        // привязанные к дате могут прийти и позже недели: считаем только их ближайшие дни
        for (i in rules.indices) {
            val r = rules[i]
            if (i in out || !r.enabled || !r.isDated) continue
            for (d in datedDays(r, now, tz)) {
                val (from, to) = dayWindow(d, now, tz) ?: continue
                val e = events(rules, from, to, tz).firstOrNull { it.index == i && it.repeat == 0 } ?: continue
                out[i] = e
                break
            }
        }
        return out
    }

    /**
     * Ближайшее уведомление после fromExcl (с повторами и с учётом «Готово»). Сначала смотрим ближайшие 36 часов,
     * потом неделю (обычные напоминания повторяются каждую неделю), потом дни разовых напоминаний.
     */
    fun firstEvent(rules: List<Rule>, fromExcl: Long, tz: TimeZone = TimeZone.getDefault(), done: Map<String, Long> = emptyMap()): Event? {
        events(rules, fromExcl, fromExcl + 36 * 60 * MIN, tz, done).firstOrNull()?.let { return it }
        events(rules, fromExcl, fromExcl + 8L * 24 * 60 * MIN, tz, done).firstOrNull()?.let { return it }
        return rules.filter { it.enabled && it.isDated }.flatMap { datedDays(it, fromExcl, tz) }.distinct().sorted().firstNotNullOfOrNull { d ->
            dayWindow(d, fromExcl, tz)?.let { (from, to) -> events(rules, from, to, tz, done).firstOrNull() }
        }
    }

    /** Два ближайших дня привязанного к дате напоминания, начиная со вчерашнего (серия могла начаться накануне). */
    private fun datedDays(r: Rule, from: Long, tz: TimeZone): List<Int> {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = from
        c.add(Calendar.DAY_OF_YEAR, -1)
        val a = r.nextDate(Rule.ymd(c)) ?: return emptyList()
        val b = r.nextDate(Rule.fromEpochDay(Rule.epochDay(a) + 1))
        return listOfNotNull(a, b)
    }

    /**
     * Промежуток, в котором лежат все уведомления дня ГГГГММДД (серии могут начаться раньше полуночи
     * или закончиться после неё), но не раньше from. null — день уже целиком прошёл.
     */
    private fun dayWindow(ymd: Int, from: Long, tz: TimeZone): Pair<Long, Long>? {
        val start = Rule.midnightOf(ymd, tz)
        val end = start + 24 * 60 * MIN + REACH
        if (end <= from) return null
        return maxOf(from, start - REACH) to end
    }

    /**
     * Все времена сегодняшнего дня (основные уведомления, по одному на каждое время T), по порядку.
     * Для ленты дня: время T берётся без «за N минут», день — по самому времени T.
     */
    fun today(rules: List<Rule>, now: Long, tz: TimeZone = TimeZone.getDefault()): List<Event> {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        val end = c.timeInMillis
        // уведомление может прийти до 3 часов раньше самого времени («за N минут»)
        return events(rules, start - 3 * 60 * MIN - 1, end, tz)
            .filter { it.repeat == 0 && (it.baseAt + it.rule.lead * MIN).let { t -> t >= start && t < end } }
            .sortedWith(compareBy<Event>({ it.minute }, { it.index }))
    }

    /** Ближайшая полночь после now: «пауза до конца дня». */
    fun endOfDay(now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Сколько времени осталось до момента: «через 12 мин», «через 1 ч 05 мин», «сейчас». */
    fun countdown(at: Long, now: Long): String {
        val m = Math.ceil((at - now) / MIN.toDouble()).toLong()
        return when {
            m <= 0 -> "сейчас"
            m < 60 -> "через $m мин"
            m < 24 * 60 -> "через ${m / 60} ч" + if (m % 60 != 0L) String.format(Locale.US, " %02d мин", m % 60) else ""
            else -> "через ${m / (24 * 60)} " + plural((m / (24 * 60)).toInt(), "день", "дня", "дней")
        }
    }

    /**
     * Номер уведомления в системе: свой для каждой серии каждого напоминания (уведомления серии заменяют друг друга).
     * Всегда не меньше 65536, так что не пересекается со служебными номерами.
     */
    fun notificationId(ruleId: Long, baseAt: Long): Int =
        ((ruleId % 32767 + 1) * 65536 + Math.floorMod(baseAt / MIN, 65536L)).toInt()
}
