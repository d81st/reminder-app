package com.h4isenb.reminder

/** Заготовки для кнопки «+»: одно нажатие, и остаётся поправить время. */
object Templates {
    class Template(val title: String, val hint: String, val make: () -> Rule)

    val all = listOf(
        Template("Утренний будильник", "07:00 · будни · с нарастающей громкостью") {
            Rule("Подъём", 7 * 60, 7 * 60, 60, false, "Доброе утро!", kind = Rule.KIND_ALARM, days = Rule.WORKDAYS)
        },
        Template("Каждый час на работе", "09:00–17:00 · будни") {
            Rule("Каждый час", 9 * 60, 17 * 60, 60, false, "Текст напоминания", days = Rule.WORKDAYS)
        },
        Template("Каждые 30 минут", "09:00–17:00 · будни") {
            Rule("Каждые 30 минут", 9 * 60, 17 * 60, 30, false, "Текст напоминания", days = Rule.WORKDAYS)
        },
        Template("Один раз, на дату", "завтра в 10:00 · дату можно поменять") {
            val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            Rule("Не забыть", 10 * 60, 10 * 60, 60, false, "Текст напоминания", date = Rule.ymd(tomorrow))
        },
        Template("Раз в день", "12:00 · каждый день") {
            Rule("Напоминание", 12 * 60, 12 * 60, 60, false, "Текст напоминания")
        },
    )

    /** Новое разовое на день ГГГГММДД (из календаря): в 9:00, а сегодня — в ближайший целый час. */
    fun onDate(ymd: Int, now: Long): Rule {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val nextHour = (c.get(java.util.Calendar.HOUR_OF_DAY) + 1).coerceAtMost(23) * 60
        val at = if (ymd == Rule.ymd(now)) maxOf(9 * 60, nextHour) else 9 * 60
        return Rule("Напоминание", at, at, 60, false, "Текст напоминания", date = ymd)
    }

    /** Пустое напоминание, как раньше. */
    fun blank() = Rule("Новое напоминание", 9 * 60, 17 * 60, 60, false, "Текст напоминания")
}
