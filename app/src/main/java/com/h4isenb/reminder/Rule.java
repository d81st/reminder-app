package com.h4isenb.reminder;

import java.util.ArrayList;
import java.util.List;

/** Одно напоминание: окно времени, интервал, «за N минут», повторы, текст и цвет. Времена в минутах от полуночи. */
class Rule {
    long id = 0; // 0 = ещё не сохранено в базе
    String name;
    String text;
    boolean enabled = true;
    boolean skipOverlap = false;
    int start = 9 * 60;
    int end = 17 * 60;
    int interval = 60;
    int lead = 0;
    int color = 0; // индекс цвета карточки
    int repeatEvery = 5; // повторять каждые N минут
    int repeatCount = 0; // сколько раз повторить (0 = повторов нет)

    Rule(String name, int start, int end, int interval, boolean skipOverlap, String text) {
        this.name = name;
        this.start = start;
        this.end = end;
        this.interval = interval;
        this.skipOverlap = skipOverlap;
        this.text = text;
    }

    /** Все времена окна без учёта других напоминаний. */
    List<Integer> rawTimes() {
        List<Integer> out = new ArrayList<>();
        if (interval < 1 || end < start) return out;
        for (int t = start; t <= end; t += interval) out.add(t);
        return out;
    }

    /** Сколько повторов реально будет: они должны закончиться раньше следующего времени этого же напоминания. */
    int effectiveRepeats() {
        if (repeatCount <= 0 || repeatEvery < 1 || interval < 2) return 0;
        return Math.min(repeatCount, (interval - 1) / repeatEvery);
    }
}
