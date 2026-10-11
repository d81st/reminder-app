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
    /** Ключ карточки в интерфейсе: не меняется, когда новое напоминание получает id при сохранении. */
    val key: Long = if (id > 0) id else nextKey(),
) {
    val isAlarm: Boolean get() = kind == KIND_ALARM

    /** Работает ли напоминание в этот день недели (0 = понедельник ... 6 = воскресенье). */
    fun onDay(weekday: Int): Boolean = days and (1 shl weekday) != 0

    /** Все времена окна без учёта других напоминаний. */
    fun rawTimes(): List<Int> {
        if (interval < 1 || end < start) return emptyList()
        return (start..end step interval).toList()
    }

    /** Сколько повторов реально будет: серия должна уложиться раньше соседнего времени этого же напоминания. */
    fun effectiveRepeats(): Int {
        if (isAlarm) return 0 // будильник звонит, пока его не выключат: серия уведомлений не нужна
        if (repeatCount <= 0 || repeatEvery < 1 || interval < 2) return 0
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

        const val ALL_DAYS = 0b1111111
        const val WORKDAYS = 0b0011111
        const val WEEKEND = 0b1100000

        val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        private var keys = 0L

        @Synchronized
        private fun nextKey(): Long = --keys

        /** День недели из Calendar.DAY_OF_WEEK (1 = воскресенье) в индекс 0 = понедельник ... 6 = воскресенье. */
        fun weekdayOf(calendarDow: Int): Int = (calendarDow + 5) % 7

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
