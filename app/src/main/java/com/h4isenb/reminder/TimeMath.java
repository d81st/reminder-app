package com.h4isenb.reminder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
        int minute; // время события (минуты от полуночи), без учёта «за N минут»
        long at;    // момент, когда нужно показать уведомление
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
        return events(rules, fromExcl, toIncl, TimeZone.getDefault());
    }

    /** Все уведомления в промежутке (fromExcl, toIncl], по возрастанию времени. */
    static List<Event> events(List<Rule> rules, long fromExcl, long toIncl, TimeZone tz) {
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
                for (int t : all.get(i)) {
                    // Время считается по часам на стене (а не прибавлением минут к полуночи),
                    // чтобы в день перевода часов 09:00 оставалось 09:00.
                    Calendar c = Calendar.getInstance(tz);
                    c.clear();
                    c.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH),
                            0, t - r.lead, 0);
                    long at = c.getTimeInMillis();
                    if (at > fromExcl && at <= toIncl) {
                        Event e = new Event();
                        e.rule = r;
                        e.index = i;
                        e.minute = t;
                        e.at = at;
                        out.add(e);
                    }
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        Collections.sort(out, (a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Integer.compare(a.index, b.index));
        return out;
    }
}
