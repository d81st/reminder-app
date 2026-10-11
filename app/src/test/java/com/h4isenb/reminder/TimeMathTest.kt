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
}
