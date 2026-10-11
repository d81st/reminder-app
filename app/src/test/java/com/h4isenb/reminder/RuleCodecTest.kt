package com.h4isenb.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuleCodecTest {
    private fun sample() = listOf(
        Rule("Каждый час", 9 * 60, 17 * 60, 60, false, "Запиши в тетрадь", id = 5, color = 2,
            repeatEvery = 2, repeatCount = 3, repeatMode = Rule.REPEAT_AFTER, days = Rule.WORKDAYS, lead = 5),
        Rule("Подъём", 7 * 60, 7 * 60, 60, false, "Вставай!", id = 6, kind = Rule.KIND_ALARM,
            alarmSound = "content://media/internal/audio/1", snoozeMin = 9, challenge = Rule.CHALLENGE_MATH,
            gradual = false, enabled = false, days = 0b1000001),
    )

    /** Сравнение без полей, которые не передаются (id, ключ карточки, мелодия). */
    private fun portable(r: Rule) = r.copy(id = 0, key = 0, alarmSound = "")

    @Test
    fun roundTripKeepsEverythingPortable() {
        val code = RuleCodec.encode(sample())
        assertTrue(code.startsWith(RuleCodec.PREFIX))
        val back = RuleCodec.decode(code)
        assertEquals(sample().map(::portable), back.map(::portable))
        assertTrue(back.all { it.id == 0L && it.alarmSound.isEmpty() })
    }

    @Test
    fun codeIsCompactEnoughForQr() {
        val many = (1..30).map { Rule("Напоминание номер $it", 9 * 60, 17 * 60, 30, it % 2 == 0, "Отправь сообщение в Telegram и запиши в тетрадь") }
        val code = RuleCodec.encode(many)
        // QR (уровень коррекции M) вмещает 2331 байт
        assertTrue("длина ${code.length}", code.length < 2331)
        assertEquals(30, RuleCodec.decode(code).size)
    }

    @Test
    fun findsCodeInsideMessage() {
        val code = RuleCodec.encode(sample())
        val msg = "Привет! Вот наше расписание:\n$code\nСпасибо"
        assertEquals(code, RuleCodec.find(msg))
        assertEquals(2, RuleCodec.decode(msg).size)
        assertNull(RuleCodec.find("просто текст"))
    }

    @Test
    fun rejectsGarbage() {
        for (s in listOf("hello", "RMD1.", "RMD1.!!!!", "RMD1.AAAA", RuleCodec.encode(sample()).dropLast(10))) {
            try {
                RuleCodec.decode(s)
                fail("должно было не разобраться: $s")
            } catch (e: RuleCodec.BadCode) {
                assertNotNull(e.message)
            }
        }
    }

    @Test
    fun clampsOutOfRangeValues() {
        val bad = Rule("x", start = 5000, end = -3, interval = 9999, lead = 999, color = 40, repeatCount = 99, snoozeMin = 0)
        val r = RuleCodec.decode(RuleCodec.encode(listOf(bad)))[0]
        assertEquals(1439, r.start)
        assertEquals(0, r.end)
        assertEquals(720, r.interval)
        assertEquals(180, r.lead)
        assertEquals(5, r.color)
        assertEquals(20, r.repeatCount)
        assertEquals(1, r.snoozeMin)
    }

    @Test
    fun importedAlarmsAreDisabled() {
        val got = RuleCodec.forImport(RuleCodec.decode(RuleCodec.encode(sample().map { it.copy(enabled = true) })))
        assertEquals(listOf(true, false), got.map { it.enabled })
        assertTrue(got[1].isAlarm)
    }
}
