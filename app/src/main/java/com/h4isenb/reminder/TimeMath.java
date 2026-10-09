package com.h4isenb.reminder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Расчёт времён уведомлений. Без Android-классов, поэтому проверяется обычными тестами. */
final class TimeMath {
    static final long MIN = 60_000L;

    private TimeMath() {
    }

    static class Event {
        Rule rule;
        int index;
        int minute;           // время события (минуты от полуночи), без учёта «за N минут»
        long at;              // момент, когда нужно показать уведомление
        long baseAt;          // момент основного уведомления (у повторов совпадает с основным)
        int repeat;           // 0 = основное уведомление, 1.. = номер повтора
        int groupFirstRepeat; // после collapse(): номер самого раннего события группы

        /** Идентификатор «основного уведомления вместе с его повторами». */
        String key() {
            return rule.id + ":" + baseAt;
        }
    }

    static String fmt(int m) {
        return String.format(Locale.US, "%02d:%02d", (m / 60) % 24, m % 60);
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
        return events(rules, fromExcl, toIncl, TimeZone.getDefault(), new HashSet<String>());
    }

    static List<Event> events(List<Rule> rules, long fromExcl, long toIncl, TimeZone tz) {
        return events(rules, fromExcl, toIncl, tz, new HashSet<String>());
    }

    /**
     * Все уведомления (основные и повторы) в промежутке (fromExcl, toIncl], по возрастанию времени.
     * done: ключи основных уведомлений, на которые пользователь ответил «Готово»; их повторы пропускаются.
     */
    static List<Event> events(List<Rule> rules, long fromExcl, long toIncl, TimeZone tz, Set<String> done) {
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
        day.add(Calendar.DAY_OF_YEAR, -1);

        int guard = 0;
        while (day.getTimeInMillis() - 3 * 60 * MIN <= toIncl && guard++ < 60) {
            for (int i = 0; i < rules.size(); i++) {
                Rule r = rules.get(i);
                int reps = r.effectiveRepeats();
                for (int t : all.get(i)) {
                    // Время считается по часам на стене (а не прибавлением минут к полуночи),
                    // чтобы в день перевода часов 09:00 оставалось 09:00.
                    Calendar c = Calendar.getInstance(tz);
                    c.clear();
                    c.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH),
                            0, t - r.lead, 0);
                    long base = c.getTimeInMillis();
                    boolean isDone = reps > 0 && r.id > 0 && done.contains(r.id + ":" + base);
                    for (int k = 0; k <= reps; k++) {
                        if (k > 0 && isDone) break;
                        long at = base + (long) k * r.repeatEvery * MIN;
                        if (at <= fromExcl || at > toIncl) continue;
                        Event e = new Event();
                        e.rule = r;
                        e.index = i;
                        e.minute = t;
                        e.at = at;
                        e.baseAt = base;
                        e.repeat = k;
                        e.groupFirstRepeat = k;
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
     * Если будильник опоздал и накопилось несколько повторов одного уведомления, показывать нужно только
     * последний (все они заменяют друг друга). Возвращает по одному событию на группу, по порядку появления.
     */
    static List<Event> collapse(List<Event> events) {
        Map<String, Event> latest = new LinkedHashMap<>();
        Map<String, Integer> first = new LinkedHashMap<>();
        for (Event e : events) {
            String k = e.key();
            if (!first.containsKey(k)) first.put(k, e.repeat);
            latest.put(k, e);
        }
        List<Event> out = new ArrayList<>();
        for (Map.Entry<String, Event> en : latest.entrySet()) {
            Event e = en.getValue();
            e.groupFirstRepeat = first.get(en.getKey());
            out.add(e);
        }
        return out;
    }
}
