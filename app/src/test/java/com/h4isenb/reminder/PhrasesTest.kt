package com.h4isenb.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasesTest {
    private val hourly = Rule("Час", 9 * 60, 17 * 60, 60, false, "", id = 1, days = Rule.WORKDAYS)

    @Test
    fun scheduleSentences() {
        assertEquals("Каждый час с 9 до 17", Phrases.schedule(hourly))
        assertEquals("Каждые 30 мин с 9:30 до 16:30", Phrases.schedule(Rule("x", 9 * 60 + 30, 16 * 60 + 30, 30)))
        assertEquals("Каждые 2 ч с 9 до 17", Phrases.schedule(Rule("x", 9 * 60, 18 * 60, 120)))
        assertEquals("Раз в день в 07:00", Phrases.schedule(Rule("x", 7 * 60, 7 * 60, 60)))
        // последнее время, а не «до»: окно 9–17:10 каждые 60 мин заканчивается в 17
        assertEquals("Каждый час с 9 до 17", Phrases.schedule(Rule("x", 9 * 60, 17 * 60 + 10, 60)))
    }

    @Test
    fun daysPhrases() {
        assertEquals("по будням", Phrases.days(Rule.WORKDAYS))
        assertEquals("каждый день", Phrases.days(Rule.ALL_DAYS))
        assertEquals("по выходным", Phrases.days(Rule.WEEKEND))
        assertEquals("по пн, ср, пт", Phrases.days(0b10101))
    }

    @Test
    fun modes() {
        assertEquals(Phrases.MODE_QUIET, Phrases.mode(hourly))
        assertEquals("Тихо · одно уведомление", Phrases.modeLabel(hourly))
        val p = hourly.copy(repeatCount = 3, repeatEvery = 5)
        assertEquals(Phrases.MODE_PERSISTENT, Phrases.mode(p))
        assertEquals("Настойчиво · ещё 3 раза", Phrases.modeLabel(p))
        assertEquals("Настойчиво · повторы не помещаются", Phrases.modeLabel(hourly.copy(repeatCount = 3, repeatEvery = 60)))
        assertEquals("Будильник", Phrases.modeLabel(hourly.copy(kind = Rule.KIND_ALARM)))
    }

    @Test
    fun previewMatchesRealNotifications() {
        val p = hourly.copy(repeatCount = 3, repeatEvery = 5)
        assertEquals(listOf(585, 590, 595, 600), Phrases.series(p, 600))
        assertEquals("Час · в 10:00 (через 15 мин) · 1/4", Phrases.title(p, 600, 0))
        assertEquals("Час · в 10:00 (сейчас) · 4/4", Phrases.title(p, 600, 3))
        assertEquals("Час · 10:00", Phrases.title(hourly, 600, 0))
    }

    @Test
    fun explanations() {
        val p = hourly.copy(repeatCount = 3, repeatEvery = 5)
        assertTrue(Phrases.explain(p, 600).startsWith("Придёт 4 уведомления с 09:45 до 10:00"))
        assertEquals("Вы нажали «Готово» в 09:50: остальные 2 уведомления не придут.", Phrases.explain(p, 600, 1))
        assertEquals("Придёт одно уведомление в 10:00. Повторов не будет.", Phrases.explain(hourly, 600))
    }

    @Test
    fun pauseLabels() {
        val tz = java.util.TimeZone.getTimeZone("Europe/Moscow")
        fun at(d: Int, h: Int, m: Int) = java.util.Calendar.getInstance(tz).apply { clear(); set(2026, 9, d, h, m, 0) }.timeInMillis
        val now = at(12, 14, 30)
        assertEquals("до 15:30", Phrases.pauseUntil(now + 3600_000, now, tz))
        assertEquals("до конца дня", Phrases.pauseUntil(TimeMath.endOfDay(now, tz), now, tz))
        assertEquals(at(13, 0, 0), TimeMath.endOfDay(now, tz))
        assertEquals("до завтра 09:00", Phrases.pauseUntil(at(13, 9, 0), now, tz))
        assertEquals("до чт 09:00", Phrases.pauseUntil(at(15, 9, 0), now, tz))
    }

    @Test
    fun dates() {
        assertEquals("чт, 15 октября", Phrases.date(20261015, 20261012))
        assertEquals("пт, 1 января 2027", Phrases.date(20270101, 20261012))
        val r = Rule("Отчёт", 14 * 60, 14 * 60, 60, false, date = 20261015)
        assertEquals("чт, 15 октября", Phrases.days(r, 20261012))
        assertEquals("В 14:00", Phrases.schedule(r))
        assertEquals("по будням", Phrases.days(r.copy(date = 0, days = Rule.WORKDAYS), 20261012))
    }
}
