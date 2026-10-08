package com.h4isenb.reminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Хранение настроек и времени последнего сработавшего уведомления. */
class Store {
    private static final String PREFS = "reminder";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<Rule> defaults() {
        List<Rule> list = new ArrayList<>();
        list.add(new Rule("Каждый час (ровно :00)", 9 * 60, 17 * 60, 60, false,
                "Запиши текст в тетрадь ручкой и отправь сообщение в Telegram."));
        list.add(new Rule("Каждые 30 минут (без целых часов)", 9 * 60, 17 * 60, 30, true,
                "Отправь сообщение в Telegram."));
        return list;
    }

    static List<Rule> load(Context c) {
        String s = prefs(c).getString("rules", null);
        if (s == null) return defaults();
        try {
            JSONArray a = new JSONArray(s);
            List<Rule> list = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) list.add(Rule.fromJson(a.getJSONObject(i)));
            return list.isEmpty() ? defaults() : list;
        } catch (Exception e) {
            return defaults();
        }
    }

    static void save(Context c, List<Rule> rules) {
        try {
            JSONArray a = new JSONArray();
            for (Rule r : rules) a.put(r.toJson());
            prefs(c).edit().putString("rules", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static long getLastFired(Context c) {
        SharedPreferences p = prefs(c);
        long v = p.getLong("lastFired", -1L);
        if (v < 0) {
            v = System.currentTimeMillis();
            p.edit().putLong("lastFired", v).apply();
        }
        return v;
    }

    static void setLastFired(Context c, long v) {
        prefs(c).edit().putLong("lastFired", v).apply();
    }
}
