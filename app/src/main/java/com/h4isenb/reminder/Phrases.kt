package com.h4isenb.reminder

import java.util.Locale

/** Описания напоминаний обычными словами: для карточек и редактора. Без Android, проверяется тестами. */
object Phrases {
    const val MODE_QUIET = 0
    const val MODE_PERSISTENT = 1
    const val MODE_ALARM = 2

    /** Как напоминает: тихо (одно уведомление), настойчиво (с повторами) или будильником. */
    fun mode(r: Rule): Int = when {
        r.isAlarm -> MODE_ALARM
        r.repeatCount > 0 -> MODE_PERSISTENT
        else -> MODE_QUIET
    }

    /** «9» для целого часа, иначе «9:30». */
    fun clock(m: Int): String {
        val h = Math.floorMod(m, 1440) / 60
        val mm = Math.floorMod(m, 60)
        return if (mm == 0) h.toString() else String.format(Locale.US, "%d:%02d", h, mm)
    }

    /** «Каждый час с 9 до 17», «Каждые 30 мин с 9:30 до 16:30», «Раз в день в 12:00». */
    fun schedule(r: Rule): String {
        val t = r.rawTimes()
        if (t.isEmpty()) return "Нет времён: «до» раньше, чем «с»"
        if (t.size == 1) return (if (r.isDated) "В " else "Раз в день в ") + TimeMath.fmt(t[0])
        val every = when {
            r.interval == 60 -> "Каждый час"
            r.interval % 60 == 0 -> "Каждые ${r.interval / 60} ч"
            else -> "Каждые ${r.interval} мин"
        }
        return "$every с ${clock(r.start)} до ${clock(t.last())}"
    }

    /** «по будням», «каждый день», «по пн, ср, пт». */
    fun days(days: Int): String = when (days and Rule.ALL_DAYS) {
        Rule.ALL_DAYS -> "каждый день"
        Rule.WORKDAYS -> "по будням"
        Rule.WEEKEND -> "по выходным"
        0 -> "ни в один день"
        else -> "по " + (0..6).filter { days and (1 shl it) != 0 }.joinToString(", ") { Rule.DAY_SHORT[it].lowercase(Locale("ru")) }
    }

    private val MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября",
        "октября", "ноября", "декабря")

    /** «ср, 15 октября»; год — только если не текущий: «пт, 8 января 2027». */
    fun date(ymd: Int, todayYmd: Int): String {
        val y = ymd / 10000
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(y, ymd / 100 % 100 - 1, ymd % 100)
        val wd = Rule.DAY_SHORT[Rule.weekdayOf(c.get(java.util.Calendar.DAY_OF_WEEK))].lowercase(Locale("ru"))
        return "$wd, ${ymd % 100} ${MONTHS[ymd / 100 % 100 - 1]}" + if (y != todayYmd / 10000) " $y" else ""
    }

    /** Дни напоминания: «по будням» или, для разового, «ср, 15 октября». */
    fun days(r: Rule, todayYmd: Int): String = if (r.isDated) date(r.date, todayYmd) else days(r.days)

    /** Метка на карточке: «Тихо · одно уведомление», «Настойчиво · ещё 3 раза», «Будильник». */
    fun modeLabel(r: Rule): String = when (mode(r)) {
        MODE_ALARM -> "Будильник"
        MODE_PERSISTENT -> {
            val eff = r.effectiveRepeats()
            if (eff == 0) "Настойчиво · повторы не помещаются"
            else "Настойчиво · ещё $eff " + TimeMath.plural(eff, "раз", "раза", "раз")
        }
        else -> "Тихо · одно уведомление"
    }

    /** «до 15:30», «до завтра 00:00», «до пн 09:00» — до какого момента пауза. */
    fun pauseUntil(until: Long, now: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        val a = java.util.Calendar.getInstance(tz).apply { timeInMillis = now }
        val b = java.util.Calendar.getInstance(tz).apply { timeInMillis = until }
        val time = TimeMath.fmt(b.get(java.util.Calendar.HOUR_OF_DAY) * 60 + b.get(java.util.Calendar.MINUTE))
        val days = ((TimeMath.endOfDay(until - 1, tz) - TimeMath.endOfDay(now, tz)) / (24 * 3600 * 1000L)).toInt()
        return when {
            a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
                a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR) -> "до $time"
            time == "00:00" && days == 0 -> "до конца дня"
            days <= 1 -> "до завтра $time"
            else -> "до " + Rule.DAY_SHORT[Rule.weekdayOf(b.get(java.util.Calendar.DAY_OF_WEEK))].lowercase(Locale("ru")) + " $time"
        }
    }

    /** Все уведомления для времени t (минуты от полуночи), по порядку. */
    fun series(r: Rule, t: Int): List<Int> = TimeMath.seriesMinutes(r, t)

    /** Заголовок k-го уведомления серии для времени t — тот же, что покажет телефон. */
    fun title(r: Rule, t: Int, k: Int): String {
        val s = series(r, t)
        val at = s[k.coerceIn(0, s.size - 1)]
        val base = t - r.lead
        val e = TimeMath.Event(r, 0, t, at * TimeMath.MIN, base * TimeMath.MIN, 0, k + 1, s.size)
        return TimeMath.title(e)
    }

    /** Пояснение под примером: сколько уведомлений и что делает «Готово». */
    fun explain(r: Rule, t: Int, doneAt: Int = -1): String {
        val s = series(r, t)
        val n = s.size
        return when {
            r.isAlarm -> "В ${TimeMath.fmt(s[0])} зазвонит будильник. Звонит, пока не нажмёте «Выключить» или «Отложить»."
            n == 1 && r.repeatCount > 0 ->
                "Повторы не помещаются: следующее время уже через ${r.interval} мин. Уменьшите промежуток или число повторов."
            n == 1 -> "Придёт одно уведомление в ${TimeMath.fmt(s[0])}. Повторов не будет."
            doneAt >= 0 -> {
                val left = n - 1 - doneAt
                "Вы нажали «Готово» в ${TimeMath.fmt(s[doneAt])}: остальные $left " +
                    TimeMath.plural(left, "уведомление", "уведомления", "уведомлений") + " не придут."
            }
            else -> "Придёт $n " + TimeMath.plural(n, "уведомление", "уведомления", "уведомлений") +
                " с ${TimeMath.fmt(s[0])} до ${TimeMath.fmt(s[n - 1])}. Каждое новое заменяет прошлое, а «Готово» останавливает серию."
        }
    }
}
