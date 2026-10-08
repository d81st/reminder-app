package com.h4isenb.reminder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Одно расписание: окно времени, интервал, «за N минут» и текст. Времена хранятся в минутах от полуночи. */
class Rule {
    String name;
    String text;
    boolean enabled = true;
    boolean skipOverlap = false;
    int start = 9 * 60;
    int end = 17 * 60;
    int interval = 60;
    int lead = 0;

    Rule(String name, int start, int end, int interval, boolean skipOverlap, String text) {
        this.name = name;
        this.start = start;
        this.end = end;
        this.interval = interval;
        this.skipOverlap = skipOverlap;
        this.text = text;
    }

    /** Все времена окна без учёта других расписаний. */
    List<Integer> rawTimes() {
        List<Integer> out = new ArrayList<>();
        if (interval < 1 || end < start) return out;
        for (int t = start; t <= end; t += interval) out.add(t);
        return out;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("text", text);
        o.put("enabled", enabled);
        o.put("skipOverlap", skipOverlap);
        o.put("start", start);
        o.put("end", end);
        o.put("interval", interval);
        o.put("lead", lead);
        return o;
    }

    static Rule fromJson(JSONObject o) {
        Rule r = new Rule(
                o.optString("name", "Напоминание"),
                o.optInt("start", 9 * 60),
                o.optInt("end", 17 * 60),
                o.optInt("interval", 60),
                o.optBoolean("skipOverlap", false),
                o.optString("text", ""));
        r.enabled = o.optBoolean("enabled", true);
        r.lead = o.optInt("lead", 0);
        return r;
    }
}
