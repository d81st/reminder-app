package com.h4isenb.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeMathTest {
    private val tz: TimeZone = TimeZone.getTimeZone("Europe/Moscow")
    private val berlin: TimeZone = TimeZone.getTimeZone("Europe/Berlin")

    /** Момент по часам на стене в зоне z. */
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, z: TimeZone = tz): Long {
        val c = Calendar.getInstance(z)
        c.clear()
        c.set(y, mo - 1, d, h, mi, 0)
        return c.timeInMillis
    }

    private fun hm(ms: Long, z: TimeZone = tz): String {
        val c = Calendar.getInstance(z)
        c.timeInMillis = ms
        return TimeMath.fmt(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
    }

    private fun hourly(id: Long = 1) = Rule("Час", start = 9 * 60, end = 17 * 60, interval = 60, id = id)

    // 2026-10-12 — понедельник
    private val mon = intArrayOf(2026, 10, 12)

    @Test
    fun rawTimesCoverWindow() {
        assertEquals((9..17).map { it * 60 }, hourly().rawTimes())
        assertTrue(Rule("x", start = 10 * 60, end = 9 * 60).rawTimes().isEmpty())
    }

    @Test
    fun skipOverlapRemovesOtherRuleTimes() {
        val rules = listOf(hourly(1), Rule("Полчаса", interval = 30, skipOverlap = true, id = 2))
        val t = TimeMath.times(rules, 1)
        assertEquals(8, t.size)
        assertTrue(t.all { it % 60 == 30 })
    }

    @Test
    fun eventsForOneDay() {
        val from = at(mon[0], mon[1], mon[2], 0, 0)
        val ev = TimeMath.events(listOf(hourly()), from, from + 24 * 60 * TimeMath.MIN, tz)
        assertEquals(9, ev.size)
        assertEquals("09:00", hm(ev.first().at))
        assertEquals("17:00", hm(ev.last().at))
    }

    @Test
    fun workdaysSkipWeekend() {
        val r = hourly().copy(days = Rule.WORKDAYS)
        val from = at(mon[0], mon[1], mon[2], 0, 0)
        val ev = TimeMath.events(listOf(r), from, from + 7L * 24 * 60 * TimeMath.MIN, tz)
        assertEquals(5 * 9, ev.size)
        val cal = Calendar.getInstance(tz)
        for (e in ev) {
            cal.timeInMillis = e.at
            val wd = Rule.weekdayOf(cal.get(Calendar.DAY_OF_WEEK))
            assertTrue("уведомление в выходной: $wd", wd < 5)
        }
    }

    @Test
    fun weekdayIndexMapping() {
        assertEquals(0, Rule.weekdayOf(Calendar.MONDAY))
        assertEquals(5, Rule.weekdayOf(Calendar.SATURDAY))
        assertEquals(6, Rule.weekdayOf(Calendar.SUNDAY))
    }

    @Test
    fun dayBelongsToTimeNotToNotification() {
        // Только понедельник, 00:10, «за 30 минут»: уведомление в воскресенье в 23:40
        val r = Rule("Ночь", start = 10, end = 10, interval = 60, lead = 30, days = 1, id = 1)
        val sun = at(2026, 10, 11, 0, 0)
        val ev = TimeMath.events(listOf(r), sun, sun + 2L * 24 * 60 * TimeMath.MIN, tz)
        assertEquals(1, ev.size)
        assertEquals(at(2026, 10, 11, 23, 40), ev[0].at)
    }

    @Test
    fun skipOverlapOnlyAgainstRulesOfThatDay() {
        // Часовое только по будням: в субботу получасовое не должно терять целые часы
        val rules = listOf(hourly(1).copy(days = Rule.WORKDAYS), Rule("Полчаса", interval = 30, skipOverlap = true, id = 2))
        assertEquals(8, TimeMath.times(rules, 1, 0).size)
        assertEquals(17, TimeMath.times(rules, 1, 5).size)
        assertTrue(TimeMath.times(rules, 0, 5).isEmpty())
    }

    @Test
    fun repeatsBeforeEndAtTime() {
        val r = hourly().copy(repeatEvery = 1, repeatCount = 5, lead = 5)
        assertEquals("09:50 · 09:51 · 09:52 · 09:53 · 09:54 · 09:55", TimeMath.seriesText(r, 10 * 60))
    }

    @Test
    fun repeatsAfterStartAtTime() {
        val r = hourly().copy(repeatEvery = 1, repeatCount = 5, repeatMode = Rule.REPEAT_AFTER)
        assertEquals("10:00 · 10:01 · 10:02 · 10:03 · 10:04 · 10:05", TimeMath.seriesText(r, 10 * 60))
    }

    @Test
    fun longSeriesShortened() {
        val r = hourly().copy(repeatEvery = 1, repeatCount = 20)
        assertEquals("09:40 · 09:41 · 09:42 · … · 09:59 · 10:00", TimeMath.seriesText(r, 10 * 60))
    }

    @Test
    fun repeatsLimitedByInterval() {
        assertEquals(5, Rule("x", interval = 30, repeatEvery = 5, repeatCount = 20).effectiveRepeats())
        assertEquals(0, Rule("x", interval = 30, repeatEvery = 5, repeatCount = 0).effectiveRepeats())
    }

    @Test
    fun alarmHasNoSeries() {
        val r = hourly().copy(kind = Rule.KIND_ALARM, repeatEvery = 1, repeatCount = 5)
        assertEquals(0, r.effectiveRepeats())
        val from = at(mon[0], mon[1], mon[2], 0, 0)
        assertEquals(9, TimeMath.events(listOf(r), from, from + 24 * 60 * TimeMath.MIN, tz).size)
    }

    @Test
    fun doneStopsRestOfSeries() {
        val r = hourly().copy(repeatEvery = 1, repeatCount = 5)
        val base = at(mon[0], mon[1], mon[2], 10, 0)
        val done = mapOf("1:$base" to at(mon[0], mon[1], mon[2], 9, 57))
        val from = at(mon[0], mon[1], mon[2], 9, 50)
        val ev = TimeMath.events(listOf(r), from, at(mon[0], mon[1], mon[2], 10, 30), tz, done)
        assertEquals(listOf("09:55", "09:56", "09:57"), ev.map { hm(it.at) })
    }

    @Test
    fun collapseKeepsLatestOfSeries() {
        val r = hourly().copy(repeatEvery = 1, repeatCount = 5)
        val from = at(mon[0], mon[1], mon[2], 9, 50)
        val ev = TimeMath.collapse(TimeMath.events(listOf(r), from, at(mon[0], mon[1], mon[2], 9, 58), tz))
        assertEquals(1, ev.size)
        assertEquals("09:58", hm(ev[0].at))
        assertEquals(4, ev[0].seq)
    }

    @Test
    fun wallClockKeptOverDstChange() {
        // В Берлине 25.10.2026 часы переводятся назад: 09:00 должно остаться 09:00
        val r = Rule("x", start = 9 * 60, end = 9 * 60, id = 1)
        val from = at(2026, 10, 24, 8, 0, berlin)
        val ev = TimeMath.events(listOf(r), from, from + 2L * 24 * 60 * TimeMath.MIN, berlin)
        assertEquals(2, ev.size)
        assertTrue(ev.all { hm(it.at, berlin) == "09:00" })
        assertEquals(25 * 60 * TimeMath.MIN, ev[1].at - ev[0].at)
    }

    @Test
    fun titleShowsCountdownAndSeq() {
        val r = hourly().copy(repeatEvery = 1, repeatCount = 2, lead = 5)
        val from = at(mon[0], mon[1], mon[2], 9, 0)
        val first = TimeMath.events(listOf(r), from, at(mon[0], mon[1], mon[2], 9, 53), tz).first()
        assertEquals("Час · в 10:00 (через 7 мин) · 1/3", TimeMath.title(first))
    }

    @Test
    fun nextByRuleSkipsDaysOff() {
        val r = hourly().copy(days = Rule.WORKDAYS)
        val sat = at(2026, 10, 17, 12, 0)
        val e = TimeMath.nextByRule(listOf(r), sat, tz)[0]!!
        assertEquals(at(2026, 10, 19, 9, 0), e.at)
    }

    @Test
    fun daysText() {
        assertEquals("каждый день", Rule.daysText(Rule.ALL_DAYS))
        assertEquals("будни", Rule.daysText(Rule.WORKDAYS))
        assertEquals("выходные", Rule.daysText(Rule.WEEKEND))
        assertEquals("Пн, Ср, Пт", Rule.daysText(0b10101))
    }

    @Test
    fun notificationIdsDoNotCollideAcrossRules() {
        val base = at(mon[0], mon[1], mon[2], 10, 0)
        // раньше у напоминаний 1 и 101 номера совпадали
        assertNotEquals(TimeMath.notificationId(1, base), TimeMath.notificationId(101, base))
        assertNotEquals(TimeMath.notificationId(1, base), TimeMath.notificationId(1, base + 60 * TimeMath.MIN))
        assertTrue(TimeMath.notificationId(32766, base) >= 65536)
        assertTrue(TimeMath.notificationId(1, base) >= 65536)
    }

    @Test
    fun plural() {
        assertEquals("уведомление", TimeMath.plural(1, "уведомление", "уведомления", "уведомлений"))
        assertEquals("уведомления", TimeMath.plural(3, "уведомление", "уведомления", "уведомлений"))
        assertEquals("уведомлений", TimeMath.plural(12, "уведомление", "уведомления", "уведомлений"))
    }

    @Test
    fun todayListsEachTimeOnceInOrder() {
        val rules = listOf(
            hourly(1).copy(repeatEvery = 1, repeatCount = 3, lead = 5),
            Rule("Полчаса", interval = 30, skipOverlap = true, id = 2),
        )
        val ev = TimeMath.today(rules, at(mon[0], mon[1], mon[2], 12, 0), tz)
        assertEquals(17, ev.size)
        assertEquals(9 * 60, ev.first().minute)
        assertEquals(17 * 60, ev.last().minute)
        assertEquals(ev.map { it.minute }.sorted(), ev.map { it.minute })
    }

    @Test
    fun todayRespectsDaysAndKeepsEarlyWarningsOfToday() {
        // только понедельник, 00:10 «за 30 минут»: в понедельник одно время, в воскресенье — ни одного
        val r = Rule("Ночь", start = 10, end = 10, lead = 30, days = 1, id = 1)
        assertEquals(1, TimeMath.today(listOf(r), at(mon[0], mon[1], mon[2], 8, 0), tz).size)
        assertTrue(TimeMath.today(listOf(r), at(2026, 10, 11, 8, 0), tz).isEmpty())
    }

    @Test
    fun countdownText() {
        val now = at(mon[0], mon[1], mon[2], 10, 0)
        assertEquals("через 12 мин", TimeMath.countdown(now + 12 * TimeMath.MIN, now))
        assertEquals("через 1 ч 05 мин", TimeMath.countdown(now + 65 * TimeMath.MIN, now))
        assertEquals("через 2 ч", TimeMath.countdown(now + 120 * TimeMath.MIN, now))
        assertEquals("сейчас", TimeMath.countdown(now, now))
        assertEquals("через 2 дня", TimeMath.countdown(now + 2 * 24 * 60 * TimeMath.MIN, now))
    }

    // ----- разовые напоминания -----

    private fun once(date: Int, id: Long = 7) = Rule("Отчёт", start = 14 * 60, end = 14 * 60, interval = 60, id = id, date = date)

    @Test
    fun datedRuleFiresOnlyOnItsDate() {
        val r = once(20261015)
        val ev = TimeMath.events(listOf(r), at(2026, 10, 12, 0, 0), at(2026, 10, 20, 0, 0), tz)
        assertEquals(listOf(at(2026, 10, 15, 14, 0)), ev.map { it.at })
    }

    @Test
    fun datedRuleIgnoresWeekdays() {
        // 17 октября 2026 — суббота, а дни недели «будни»: разовое всё равно приходит
        val r = once(20261017).copy(days = Rule.WORKDAYS)
        val ev = TimeMath.events(listOf(r), at(2026, 10, 16, 0, 0), at(2026, 10, 18, 0, 0), tz)
        assertEquals(1, ev.size)
    }

    @Test
    fun farDatedRuleIsFound() {
        val r = once(20261201)
        val now = at(2026, 10, 12, 10, 0)
        assertEquals(at(2026, 12, 1, 14, 0), TimeMath.nextByRule(listOf(r), now, tz)[0]!!.at)
        assertEquals(at(2026, 12, 1, 14, 0), TimeMath.firstEvent(listOf(r), now, tz)!!.at)
    }

    @Test
    fun passedDatedRuleHasNoNext() {
        val r = once(20261010)
        val now = at(2026, 10, 12, 10, 0)
        assertTrue(TimeMath.nextByRule(listOf(r), now, tz).isEmpty())
        assertEquals(null, TimeMath.firstEvent(listOf(r), now, tz))
    }

    @Test
    fun firstEventLooksPastWeekend() {
        // будильник по будням: с вечера пятницы следующее — утро понедельника, через 60 с лишним часов
        val r = Rule("Подъём", 7 * 60, 7 * 60, 60, false, id = 3, kind = Rule.KIND_ALARM, days = Rule.WORKDAYS)
        val fri = at(2026, 10, 16, 20, 0)
        assertEquals(at(2026, 10, 19, 7, 0), TimeMath.firstEvent(listOf(r), fri, tz)!!.at)
    }

    @Test
    fun datedRuleSkipOverlapOnlyOnItsDate() {
        // разовое в 10:00 убирает 10:00 у «каждый час без совпадений» только в свой день
        val hour = hourly(1).copy(skipOverlap = true)
        val r = Rule("Звонок", 10 * 60, 10 * 60, 60, false, id = 2, date = 20261013)
        val rules = listOf(hour, r)
        assertTrue(10 * 60 !in TimeMath.times(rules, 0, 1, 20261013))
        assertTrue(10 * 60 in TimeMath.times(rules, 0, 2, 20261014))
    }

    @Test
    fun ymdHelpers() {
        assertEquals(20261012, Rule.ymd(at(2026, 10, 12, 23, 59), tz))
        assertEquals(at(2026, 10, 12, 0, 0), Rule.midnightOf(20261012, tz))
        assertTrue(Rule.validYmd(20280229))
        assertTrue(!Rule.validYmd(20270229))
        assertTrue(!Rule.validYmd(20261301))
        assertTrue(!Rule.validYmd(0))
    }

    // ----- каждые N дней -----

    @Test
    fun everyTwoDaysFromDate() {
        val r = Rule("Цветы", 19 * 60, 19 * 60, 60, false, id = 8, date = 20261012, everyDays = 2)
        val ev = TimeMath.events(listOf(r), at(2026, 10, 11, 0, 0), at(2026, 10, 19, 0, 0), tz)
        assertEquals(listOf(12, 14, 16, 18).map { at(2026, 10, it, 19, 0) }, ev.map { it.at })
    }

    @Test
    fun nextDateOfCycle() {
        val r = Rule("x", date = 20261012, everyDays = 3)
        assertEquals(20261012, r.nextDate(20261001))
        assertEquals(20261012, r.nextDate(20261012))
        assertEquals(20261015, r.nextDate(20261013))
        assertEquals(20261102, r.nextDate(20261031)) // через конец месяца
        assertEquals(null, Rule("x", date = 20261012).nextDate(20261013))
    }

    @Test
    fun longCycleFoundBeyondWeek() {
        val r = Rule("x", 19 * 60, 19 * 60, 60, false, id = 9, date = 20261012, everyDays = 20)
        val now = at(2026, 10, 13, 10, 0)
        assertEquals(at(2026, 11, 1, 19, 0), TimeMath.firstEvent(listOf(r), now, tz)!!.at)
        assertEquals(at(2026, 11, 1, 19, 0), TimeMath.nextByRule(listOf(r), now, tz)[0]!!.at)
    }

    @Test
    fun oneTimeADaySeriesIsNotLimitedByHiddenInterval() {
        // одно время в день: 12 повторов каждые 10 минут (2 часа до времени) не урезаются «интервалом» в 60 минут
        val r = Rule("x", 19 * 60, 19 * 60, 60, false, id = 10, repeatCount = 12, repeatEvery = 10)
        assertEquals(12, r.effectiveRepeats())
        val ev = TimeMath.events(listOf(r), at(2026, 10, 13, 0, 0), at(2026, 10, 13, 23, 0), tz)
        assertEquals("17:00", hm(ev.first().at))
        assertEquals("19:00", hm(ev.last().at))
        assertEquals(13, ev.size)
    }

    @Test
    fun onDateForCalendar() {
        val rules = listOf(
            hourly(1).copy(days = Rule.WORKDAYS),
            Rule("Отчёт", 14 * 60, 14 * 60, 60, false, id = 2, date = 20261015),
            Rule("Выкл", 10 * 60, 10 * 60, 60, false, id = 3, enabled = false),
        )
        // чт 15 октября: «каждый час» по будням и разовое
        assertEquals(listOf(0, 1), TimeMath.onDate(rules, 20261015).map { it.first })
        assertEquals(listOf(14 * 60), TimeMath.onDate(rules, 20261015)[1].second)
        // сб 17 октября: ничего
        assertTrue(TimeMath.onDate(rules, 20261017).isEmpty())
    }

    @Test
    fun dayEntriesSortedWithBase() {
        val rules = listOf(
            Rule("Обед", 13 * 60, 13 * 60, 60, false, id = 1),
            Rule("Утро", 9 * 60, 10 * 60, 60, false, id = 2, lead = 5),
        )
        val e = TimeMath.dayEntries(rules, 20261012, tz)
        assertEquals(listOf(9 * 60, 10 * 60, 13 * 60), e.map { it.minute })
        // «за 5 минут»: момент основного уведомления 08:55
        assertEquals(at(2026, 10, 12, 8, 55), e[0].baseAt)
        assertEquals("2:" + at(2026, 10, 12, 8, 55), e[0].doneKey())
    }

    @Test
    fun doneInAdvanceSkipsQuietReminder() {
        val r = Rule("Обед", 13 * 60, 13 * 60, 60, false, id = 1)
        val base = at(2026, 10, 12, 13, 0)
        val done = mapOf("1:$base" to at(2026, 10, 12, 10, 0))
        assertTrue(TimeMath.events(listOf(r), at(2026, 10, 12, 11, 0), at(2026, 10, 12, 14, 0), tz, done).isEmpty())
    }
}
