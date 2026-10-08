package com.h4isenb.reminder;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Напоминания хранятся в SQLite, служебные значения (время последнего срабатывания) в SharedPreferences. */
class Store {
    private static final String PREFS = "reminder";
    private static Db helper;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static synchronized Db helper(Context c) {
        if (helper == null) helper = new Db(c);
        return helper;
    }

    static List<Rule> defaults() {
        List<Rule> list = new ArrayList<>();
        list.add(new Rule("Каждый час (ровно :00)", 9 * 60, 17 * 60, 60, false,
                "Запиши текст в тетрадь ручкой и отправь сообщение в Telegram."));
        list.add(new Rule("Каждые 30 минут (без целых часов)", 9 * 60, 17 * 60, 30, true,
                "Отправь сообщение в Telegram."));
        return list;
    }

    /** При первом запуске переносит настройки из старой версии (JSON) или создаёт два расписания по умолчанию. */
    private static void seedIfNeeded(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("seeded", false)) return;
        List<Rule> seed = new ArrayList<>();
        String s = p.getString("rules", null);
        if (s != null) {
            try {
                JSONArray a = new JSONArray(s);
                for (int i = 0; i < a.length(); i++) seed.add(Rule.fromJson(a.getJSONObject(i)));
            } catch (Exception ignored) {
                seed.clear();
            }
        }
        if (seed.isEmpty()) seed = defaults();
        save(c, seed);
        p.edit().putBoolean("seeded", true).apply();
    }

    static synchronized List<Rule> load(Context c) {
        seedIfNeeded(c);
        SQLiteDatabase db = helper(c).getReadableDatabase();
        List<Rule> list = new ArrayList<>();
        Cursor cur = db.query("rules", null, null, null, null, null, "position, id");
        try {
            while (cur.moveToNext()) {
                Rule r = new Rule(
                        cur.getString(cur.getColumnIndexOrThrow("name")),
                        cur.getInt(cur.getColumnIndexOrThrow("start_min")),
                        cur.getInt(cur.getColumnIndexOrThrow("end_min")),
                        cur.getInt(cur.getColumnIndexOrThrow("interval_min")),
                        cur.getInt(cur.getColumnIndexOrThrow("skip_overlap")) == 1,
                        cur.getString(cur.getColumnIndexOrThrow("text")));
                r.id = cur.getLong(cur.getColumnIndexOrThrow("id"));
                r.enabled = cur.getInt(cur.getColumnIndexOrThrow("enabled")) == 1;
                r.lead = cur.getInt(cur.getColumnIndexOrThrow("lead_min"));
                list.add(r);
            }
        } finally {
            cur.close();
        }
        return list;
    }

    /** Приводит таблицу в соответствие со списком: обновляет, добавляет новые, удаляет пропавшие. */
    static synchronized void save(Context c, List<Rule> rules) {
        SQLiteDatabase db = helper(c).getWritableDatabase();
        db.beginTransaction();
        try {
            Set<Long> keep = new HashSet<>();
            for (int i = 0; i < rules.size(); i++) {
                Rule r = rules.get(i);
                ContentValues v = new ContentValues();
                v.put("name", r.name);
                v.put("text", r.text);
                v.put("enabled", r.enabled ? 1 : 0);
                v.put("skip_overlap", r.skipOverlap ? 1 : 0);
                v.put("start_min", r.start);
                v.put("end_min", r.end);
                v.put("interval_min", r.interval);
                v.put("lead_min", r.lead);
                v.put("position", i);
                boolean updated = r.id > 0
                        && db.update("rules", v, "id=?", new String[]{String.valueOf(r.id)}) > 0;
                if (!updated) r.id = db.insert("rules", null, v);
                keep.add(r.id);
            }
            List<Long> remove = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT id FROM rules", null);
            try {
                while (cur.moveToNext()) {
                    long id = cur.getLong(0);
                    if (!keep.contains(id)) remove.add(id);
                }
            } finally {
                cur.close();
            }
            for (long id : remove) db.delete("rules", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
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
