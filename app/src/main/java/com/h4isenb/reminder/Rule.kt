package com.h4isenb.reminder

/**
 * Одно напоминание: окно времени, интервал, «за N минут», повторы, дни недели, вид (уведомление или будильник).
 * Времена в минутах от полуночи. Неизменяемое: правка = copy(...).
 */
data class Rule(
    val name: String,
    val start: Int = 9 * 60,
    val end: Int = 17 * 60,
    val interval: Int = 60,
    val skipOverlap: Boolean = false,
    val text: String = "",
    val id: Long = 0,          // 0 = ещё не сохранено в базе
    val enabled: Boolean = true,
    val lead: Int = 0,         // основное уведомление приходит за столько минут до времени
    val color: Int = 0,        // индекс цвета карточки
    val repeatEvery: Int = 5,  // шаг между уведомлениями серии, минут
    val repeatCount: Int = 0,  // сколько раз ещё повторить, кроме основного (0 = повторов нет)
    val repeatMode: Int = REPEAT_BEFORE,
    val days: Int = ALL_DAYS,  // дни недели битами: бит 0 = понедельник ... бит 6 = воскресенье
    val kind: Int = KIND_NOTIFY,
    val alarmSound: String = "",   // "" = стандартный звук будильника
    val snoozeMin: Int = 5,        // «Отложить» на столько минут
    val challenge: Int = CHALLENGE_NONE,
    val gradual: Boolean = true,   // громкость будильника нарастает
    /** Разовое напоминание: только в этот день, ГГГГММДД (20261015). 0 = по дням недели (days). */
    val date: Int = 0,
    /** 2 и больше: не один раз, а каждые столько дней, начиная с date. */
    val everyDays: Int = 0,
    /** Ключ карточки в интерфейсе: не меняется, когда новое напоминание получает id при сохранении. */
    val key: Long = if (id > 0) id else nextKey(),
) {
    val isAlarm: Boolean get() = kind == KIND_ALARM

    /** Привязано к дате (одна дата или «каждые N дней» от неё), а не к дням недели. */
    val isDated: Boolean get() = date != 0

    /** Только в один день. */
    val isOneDay: Boolean get() = date != 0 && everyDays < 2

    /** Каждые everyDays дней, начиная с date. */
    val isCycle: Boolean get() = date != 0 && everyDays >= 2

    /** Отмечен ли день недели (0 = понедельник ... 6 = воскресенье). Для разового дни недели не действуют: см. activeOn. */
    fun onDay(weekday: Int): Boolean = days and (1 shl weekday) != 0

    /** Работает ли напоминание в этот день: weekday — день недели, ymd — дата ГГГГММДД (null = любая). */
    fun activeOn(weekday: Int, ymd: Int?): Boolean = when {
        !isDated -> onDay(weekday)
        ymd == null -> true
        isCycle -> (epochDay(ymd) - epochDay(date)).let { it >= 0 && it % everyDays == 0L }
        else -> ymd == date
    }

    /** Первый день не раньше fromYmd, в который работает привязанное к дате напоминание; null — таких дней больше нет. */
    fun nextDate(fromYmd: Int): Int? = when {
        !isDated -> null
        isCycle -> {
            val d = epochDay(fromYmd) - epochDay(date)
            if (d <= 0) date else fromEpochDay(epochDay(fromYmd) + Math.floorMod(-d, everyDays.toLong()))
        }
        date >= fromYmd -> date
        else -> null
    }

    /** Все времена окна без учёта других напоминаний. */
    fun rawTimes(): List<Int> {
        if (interval < 1 || end < start) return emptyList()
        return (start..end step interval).toList()
    }

    /** Сколько повторов реально будет: серия должна уложиться раньше соседнего времени этого же напоминания. */
    fun effectiveRepeats(): Int {
        if (isAlarm) return 0 // будильник звонит, пока его не выключат: серия уведомлений не нужна
        if (repeatCount <= 0 || repeatEvery < 1) return 0
        // одно время в день: соседнего времени нет, серия ограничена только 12 часами
        if (end - start < interval) return minOf(repeatCount, 720 / repeatEvery)
        if (interval < 2) return 0
        return minOf(repeatCount, (interval - 1) / repeatEvery)
    }

    companion object {
        /** Повторы идут ДО времени: серия заканчивается в самое время (для 10:00 это 09:55 ... 10:00). */
        const val REPEAT_BEFORE = 0
        /** Повторы идут ПОСЛЕ времени: серия начинается в самое время (10:00 ... 10:05). */
        const val REPEAT_AFTER = 1

        const val KIND_NOTIFY = 0
        const val KIND_ALARM = 1

        const val CHALLENGE_NONE = 0
        /** Чтобы выключить будильник, нужно решить пример. */
        const val CHALLENGE_MATH = 1

        /** «Каждые N дней»: не больше месяца. */
        const val MAX_EVERY_DAYS = 30

        const val ALL_DAYS = 0b1111111
        const val WORKDAYS = 0b0011111
        const val WEEKEND = 0b1100000

        val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        private var keys = 0L

        @Synchronized
        private fun nextKey(): Long = --keys

        /** Новый ключ карточки для напоминания, которого ещё нет в базе. */
        fun newKey(): Long = nextKey()

        /** День недели из Calendar.DAY_OF_WEEK (1 = воскресенье) в индекс 0 = понедельник ... 6 = воскресенье. */
        fun weekdayOf(calendarDow: Int): Int = (calendarDow + 5) % 7

        /** Дата ГГГГММДД дня, в который попадает момент ms. */
        fun ymd(ms: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Int {
            val c = java.util.Calendar.getInstance(tz)
            c.timeInMillis = ms
            return ymd(c)
        }

        fun ymd(c: java.util.Calendar): Int =
            c.get(java.util.Calendar.YEAR) * 10000 + (c.get(java.util.Calendar.MONTH) + 1) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH)

        /** Полночь дня ГГГГММДД. */
        fun midnightOf(ymd: Int, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Long {
            val c = java.util.Calendar.getInstance(tz)
            c.clear()
            c.set(ymd / 10000, ymd / 100 % 100 - 1, ymd % 100, 0, 0, 0)
            return c.timeInMillis
        }

        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val UTC = java.util.TimeZone.getTimeZone("UTC")

        /** Номер дня ГГГГММДД от 1970-01-01: для счёта «каждые N дней». */
        fun epochDay(ymd: Int): Long = midnightOf(ymd, UTC) / DAY_MS

        fun fromEpochDay(day: Long): Int = ymd(day * DAY_MS, UTC)

        /** Есть ли такой день в календаре (для данных из кода и базы). */
        fun validYmd(ymd: Int): Boolean {
            val y = ymd / 10000
            val m = ymd / 100 % 100
            val d = ymd % 100
            if (y !in 2000..2199 || m !in 1..12 || d < 1) return false
            val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            c.clear()
            c.set(y, m - 1, 1)
            return d <= c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        }

        /** «Каждый день», «Будни», «Выходные» или список дней: «Пн, Ср, Пт». */
        fun daysText(days: Int): String = when (days and ALL_DAYS) {
            ALL_DAYS -> "каждый день"
            WORKDAYS -> "будни"
            WEEKEND -> "выходные"
            0 -> "ни одного дня"
            else -> (0..6).filter { days and (1 shl it) != 0 }.joinToString(", ") { DAY_SHORT[it] }
        }
    }
}
