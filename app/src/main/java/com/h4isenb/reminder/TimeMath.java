package com.h4isenb.reminder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Расчёт времён уведомлений. Без Android-классов, поэтому проверяется обычными тестами.
 *
 * Модель: у каждого времени T есть основное уведомление в момент B = T - lead («за N минут»).
 * Если включены повторы, вокруг B строится серия из repeats = effectiveRepeats() дополнительных уведомлений
 * с шагом repeatEvery минут: ДО B (серия заканчивается в B) или ПОСЛЕ B (серия начинается в B).
 */
final class TimeMath {
    static final long MIN = 60_000L;

    /**
     * Насколько раньше начала суток может оказаться уведомление этих суток: основное до 3 часов
     * («за N минут») плюс серия до 12 часов (она всегда короче интервала, а интервал не больше 720 минут).
     */
    private static final long REACH = 16 * 60 * MIN;

    private TimeMath() {
    }

    static class Event {
        Rule rule;
        int index;
        int minute;  // время T (минуты от полуночи), без учёта «за N минут»
        long at;     // момент, когда нужно показать уведомление
        long baseAt; // момент основного уведомления B (общий для всей серии)
        int repeat;  // 0 = основное уведомление, k = на k шагов от основного
        int seq;     // номер в серии по времени, с 1
        int total;   // сколько уведомлений в серии (основное + повторы)

        /** Идентификатор серии: основное уведомление вместе с его повторами. */
        String key() {
            return rule.id + ":" + baseAt;
        }
    }

    static String fmt(int m) {
        int w = Math.floorMod(m, 1440);
        return String.format(Locale.US, "%02d:%02d", w / 60, w % 60);
    }

    /** Заголовок уведомления: название, время, «через N мин» и номер в серии. */
    static String title(Event e) {
        Rule r = e.rule;
        boolean early = r.lead > 0 || e.total > 1;
        String t = r.name + " · " + (early ? "в " : "") + fmt(e.minute);
        if (early) {
            long left = Math.round((e.baseAt + (long) r.lead * MIN - e.at) / (double) MIN); // минут до самого времени
            if (left > 0) t += " (через " + left + " мин)";
            else if (left == 0) t += " (сейчас)";
            else t += " (" + (-left) + " мин назад)";
        }
        if (e.total > 1) t += " · " + e.seq + "/" + e.total;
        return t;
    }

    /** Минуты от полуночи всех уведомлений серии для времени t, по порядку (могут выходить за границы суток). */
    static List<Integer> seriesMinutes(Rule r, int t) {
        int eff = r.effectiveRepeats();
        int base = t - r.lead; // момент основного уведомления
        boolean before = r.repeatMode == Rule.REPEAT_BEFORE;
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i <= eff; i++) {
            out.add(before ? base - (eff - i) * r.repeatEvery : base + i * r.repeatEvery);
        }
        return out;
    }

    /** Времена серии для подписи в карточке: «09:55 · 09:56 · … · 10:00» (длинные серии сокращаются). */
    static String seriesText(Rule r, int t) {
        List<Integer> items = seriesMinutes(r, t);
        StringBuilder sb = new StringBuilder();
        int n = items.size();
        for (int i = 0; i < n; i++) {
            if (n > 8 && i == 3) {
                sb.append(" · …");
                i = n - 3; // середину пропускаем, дальше выводим два последних
                continue;
            }
            if (sb.length() > 0) sb.append(" · ");
            sb.append(fmt(items.get(i)));
        }
        return sb.toString();
    }

    static String plural(int n, String one, String few, String many) {
        int a = n % 100;
        int b = n % 10;
        if (a >= 11 && a <= 14) return many;
        if (b == 1) return one;
        if (b >= 2 && b <= 4) return few;
        return many;
    }

    /** Времена напоминания с учётом «не повторять время другого напоминания». */
    static List<Integer> times(List<Rule> rules, int idx) {
        Rule r = rules.get(idx);
        List<Integer> t = r.rawTimes();
        if (r.skipOverlap) {
            Set<Integer> other = new HashSet<>();
            for (int i = 0; i < rules.size(); i++) {
                Rule o = rules.get(i);
                if (i != idx && o.enabled && !o.skipOverlap) other.addAll(o.rawTimes());
            }
            t.removeAll(other);
        }
        return t;
    }

    static List<Event> events(List<Rule> rules, long fromExcl, long toIncl) {
        return events(rules, fromExcl, toIncl, TimeZone.getDefault(), new HashMap<String, Long>());
    }

    static List<Event> events(List<Rule> rules, long fromExcl, long toIncl, TimeZone tz) {
        return events(rules, fromExcl, toIncl, tz, new HashMap<String, Long>());
    }

    /**
     * Все уведомления (основные и повторы) в промежутке (fromExcl, toIncl], по возрастанию времени.
     * done: серии, на которые пользователь ответил «Готово» (ключ серии -> момент ответа).
     * Всё, что в такой серии должно прийти позже этого момента, пропускается.
     */
    static List<Event> events(List<Rule> rules, long fromExcl, long toIncl, TimeZone tz, Map<String, Long> done) {
        List<Event> out = new ArrayList<>();
        if (toIncl <= fromExcl) return out;

        List<List<Integer>> all = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            all.add(rules.get(i).enabled ? times(rules, i) : new ArrayList<Integer>());
        }

        Calendar day = Calendar.getInstance(tz);
        day.setTimeInMillis(fromExcl);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        // Серия ПОСЛЕ времени может тянуться из вчерашнего дня (до 12 ч), поэтому начинаем со вчера
        day.add(Calendar.DAY_OF_YEAR, -1);

        int guard = 0;
        // Серия ДО времени может начаться раньше своих суток, поэтому идём дальше конца окна на REACH
        while (day.getTimeInMillis() - REACH <= toIncl && guard++ < 60) {
            for (int i = 0; i < rules.size(); i++) {
                Rule r = rules.get(i);
                int reps = r.effectiveRepeats();
                boolean before = r.repeatMode == Rule.REPEAT_BEFORE;
                long step = (long) r.repeatEvery * MIN;
                for (int t : all.get(i)) {
                    // Время считается по часам на стене (а не прибавлением минут к полуночи),
                    // чтобы в день перевода часов 09:00 оставалось 09:00.
                    Calendar c = Calendar.getInstance(tz);
                    c.clear();
                    c.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH),
                            0, t - r.lead, 0);
                    long base = c.getTimeInMillis();
                    Long doneAt = reps > 0 && r.id > 0 ? done.get(r.id + ":" + base) : null;
                    for (int k = 0; k <= reps; k++) {
                        long at = before ? base - k * step : base + k * step;
                        if (at <= fromExcl || at > toIncl) continue;
                        if (doneAt != null && at > doneAt) continue;
                        Event e = new Event();
                        e.rule = r;
                        e.index = i;
                        e.minute = t;
                        e.at = at;
                        e.baseAt = base;
                        e.repeat = k;
                        e.seq = before ? reps - k + 1 : k + 1;
                        e.total = reps + 1;
                        out.add(e);
                    }
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        Collections.sort(out, (a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Integer.compare(a.index, b.index));
        return out;
    }

    /**
     * Если будильник опоздал и накопилось несколько уведомлений одной серии, показывать нужно только
     * последнее по времени (все они заменяют друг друга). Возвращает по одному событию на серию.
     */
    static List<Event> collapse(List<Event> events) {
        Map<String, Event> latest = new LinkedHashMap<>();
        for (Event e : events) latest.put(e.key(), e); // события идут по времени, остаётся самое позднее
        return new ArrayList<>(latest.values());
    }
}
