package com.h4isenb.reminder;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Напоминания и журнал лежат в SQLite, служебные значения (тема, время последнего срабатывания) в SharedPreferences. */
class Store {
    private static final String PREFS = "reminder";
    private static Db helper;

    static class LogItem {
        long at;
        String title;
        String text;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static synchronized Db helper(Context c) {
        if (helper == null) helper = new Db(c);
        return helper;
    }

    static List<Rule> defaults() {
        List<Rule> list = new ArrayList<>();
        Rule hour = new Rule("Каждый час (ровно :00)", 9 * 60, 17 * 60, 60, false,
                "Запиши текст в тетрадь ручкой и отправь сообщение в Telegram.");
        hour.color = 0;
        Rule half = new Rule("Каждые 30 минут (без целых часов)", 9 * 60, 17 * 60, 30, true,
                "Отправь сообщение в Telegram.");
        half.color = 1;
        list.add(hour);
        list.add(half);
        return list;
    }

    private static Rule ruleFromJson(JSONObject o) {
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

    /** При первом запуске переносит настройки самой старой версии (JSON) или создаёт два напоминания по умолчанию. */
    private static void seedIfNeeded(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("seeded", false)) return;
        List<Rule> seed = new ArrayList<>();
        String s = p.getString("rules", null);
        if (s != null) {
            try {
                JSONArray a = new JSONArray(s);
                for (int i = 0; i < a.length(); i++) seed.add(ruleFromJson(a.getJSONObject(i)));
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
                r.color = cur.getInt(cur.getColumnIndexOrThrow("color"));
                r.repeatEvery = cur.getInt(cur.getColumnIndexOrThrow("repeat_every"));
                r.repeatCount = cur.getInt(cur.getColumnIndexOrThrow("repeat_count"));
                r.repeatMode = cur.getInt(cur.getColumnIndexOrThrow("repeat_mode")) == Rule.REPEAT_AFTER
                        ? Rule.REPEAT_AFTER : Rule.REPEAT_BEFORE;
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
                v.put("color", r.color);
                v.put("repeat_every", r.repeatEvery);
                v.put("repeat_count", r.repeatCount);
                v.put("repeat_mode", r.repeatMode == Rule.REPEAT_AFTER ? Rule.REPEAT_AFTER : Rule.REPEAT_BEFORE);
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

    // ---------- «Готово»: остановка повторов ----------

    /**
     * Помечает серию как принятую: всё, что в ней должно прийти позже этого момента, не придёт.
     * Если серия уже помечена, остаётся самый первый момент (повторный тап по старому уведомлению
     * не должен «воскрешать» уже отменённые уведомления).
     */
    static synchronized void markDone(Context c, long ruleId, long baseAt) {
        if (ruleId <= 0) return;
        try {
            SQLiteDatabase db = helper(c).getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("ev_key", ruleId + ":" + baseAt);
            v.put("at", System.currentTimeMillis());
            db.insertWithOnConflict("done", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            long old = System.currentTimeMillis() - 3L * 24 * 3600 * 1000;
            db.delete("done", "at < ?", new String[]{String.valueOf(old)});
        } catch (Exception ignored) {
        }
    }

    /** Серии с ответом «Готово»: ключ серии -> момент ответа. */
    static synchronized Map<String, Long> doneMap(Context c) {
        Map<String, Long> map = new HashMap<>();
        try {
            SQLiteDatabase db = helper(c).getReadableDatabase();
            long since = System.currentTimeMillis() - 3L * 24 * 3600 * 1000;
            Cursor cur = db.query("done", new String[]{"ev_key", "at"}, "at >= ?",
                    new String[]{String.valueOf(since)}, null, null, null);
            try {
                while (cur.moveToNext()) map.put(cur.getString(0), cur.getLong(1));
            } finally {
                cur.close();
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    // ---------- журнал ----------

    static synchronized void addLog(Context c, long at, String title, String text) {
        try {
            SQLiteDatabase db = helper(c).getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("at", at);
            v.put("title", title);
            v.put("text", text == null ? "" : text);
            db.insert("log", null, v);
            db.execSQL("DELETE FROM log WHERE id NOT IN (SELECT id FROM log ORDER BY id DESC LIMIT 200)");
        } catch (Exception ignored) {
            // журнал не должен ломать показ уведомления
        }
    }

    static synchronized List<LogItem> loadLog(Context c, int limit) {
        List<LogItem> list = new ArrayList<>();
        try {
            SQLiteDatabase db = helper(c).getReadableDatabase();
            Cursor cur = db.query("log", null, null, null, null, null, "id DESC", String.valueOf(limit));
            try {
                while (cur.moveToNext()) {
                    LogItem it = new LogItem();
                    it.at = cur.getLong(cur.getColumnIndexOrThrow("at"));
                    it.title = cur.getString(cur.getColumnIndexOrThrow("title"));
                    it.text = cur.getString(cur.getColumnIndexOrThrow("text"));
                    list.add(it);
                }
            } finally {
                cur.close();
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    static synchronized void clearLog(Context c) {
        try {
            helper(c).getWritableDatabase().delete("log", null, null);
        } catch (Exception ignored) {
        }
    }

    // ---------- тема и служебное ----------

    static boolean isDark(Context c) {
        int t = prefs(c).getInt("theme", -1);
        if (t >= 0) return t == 1;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static void setDark(Context c, boolean dark) {
        prefs(c).edit().putInt("theme", dark ? 1 : 0).apply();
    }

    /** Звук уведомлений: "default" (системный по умолчанию), "silent" (без звука) или адрес выбранной мелодии. */
    static String getSound(Context c) {
        return prefs(c).getString("sound", "default");
    }

    static void setSound(Context c, String value) {
        prefs(c).edit().putString("sound", value).apply();
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
