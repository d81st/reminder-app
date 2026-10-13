package com.h4isenb.reminder

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Напоминания и журнал лежат в SQLite, служебные значения (тема, время последнего срабатывания) в SharedPreferences. */
object Store {
    private const val PREFS = "reminder"
    private const val DAY_MS = 24L * 3600 * 1000
    private var helper: Db? = null

    class LogItem(val at: Long, val title: String, val text: String)

    class Snooze(val id: Long, val ruleId: Long, val at: Long, val title: String, val text: String)

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun db(c: Context): SQLiteDatabase {
        val h = helper ?: Db(c).also { helper = it }
        return h.writableDatabase
    }

    fun defaults(): List<Rule> = listOf(
        Rule(
            "Каждый час (ровно :00)", 9 * 60, 17 * 60, 60, false,
            "Запиши текст в тетрадь ручкой и отправь сообщение в Telegram.", color = 0,
        ),
        Rule("Каждые 30 минут (без целых часов)", 9 * 60, 17 * 60, 30, true, "Отправь сообщение в Telegram.", color = 1),
    )

    private fun ruleFromJson(o: JSONObject) = Rule(
        name = o.optString("name", "Напоминание"),
        start = o.optInt("start", 9 * 60),
        end = o.optInt("end", 17 * 60),
        interval = o.optInt("interval", 60),
        skipOverlap = o.optBoolean("skipOverlap", false),
        text = o.optString("text", ""),
        enabled = o.optBoolean("enabled", true),
        lead = o.optInt("lead", 0),
    )

    /** При первом запуске переносит настройки самой старой версии (JSON) или создаёт два напоминания по умолчанию. */
    private fun seedIfNeeded(c: Context) {
        val p = prefs(c)
        if (p.getBoolean("seeded", false)) return
        var seed: List<Rule> = emptyList()
        p.getString("rules", null)?.let { s ->
            seed = try {
                val a = JSONArray(s)
                (0 until a.length()).map { ruleFromJson(a.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
        if (seed.isEmpty()) seed = defaults()
        saveRaw(c, seed)
        p.edit().putBoolean("seeded", true).apply()
    }

    private fun Cursor.int(col: String) = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.str(col: String) = getString(getColumnIndexOrThrow(col)) ?: ""

    @Synchronized
    fun load(c: Context): List<Rule> {
        seedIfNeeded(c)
        val list = ArrayList<Rule>()
        db(c).query("rules", null, null, null, null, null, "position, id").use { cur ->
            while (cur.moveToNext()) {
                list.add(
                    Rule(
                        name = cur.str("name"),
                        start = cur.int("start_min"),
                        end = cur.int("end_min"),
                        interval = cur.int("interval_min"),
                        skipOverlap = cur.int("skip_overlap") == 1,
                        text = cur.str("text"),
                        id = cur.getLong(cur.getColumnIndexOrThrow("id")),
                        enabled = cur.int("enabled") == 1,
                        lead = cur.int("lead_min"),
                        color = cur.int("color"),
                        repeatEvery = cur.int("repeat_every"),
                        repeatCount = cur.int("repeat_count"),
                        repeatMode = if (cur.int("repeat_mode") == Rule.REPEAT_AFTER) Rule.REPEAT_AFTER else Rule.REPEAT_BEFORE,
                        days = cur.int("days") and Rule.ALL_DAYS,
                        kind = if (cur.int("kind") == Rule.KIND_ALARM) Rule.KIND_ALARM else Rule.KIND_NOTIFY,
                        alarmSound = cur.str("alarm_sound"),
                        snoozeMin = cur.int("snooze_min"),
                        challenge = cur.int("challenge"),
                        gradual = cur.int("gradual") == 1,
                        date = cur.int("date_ymd").let { if (Rule.validYmd(it)) it else 0 },
                        everyDays = cur.int("every_days").let { if (it in 2..Rule.MAX_EVERY_DAYS) it else 0 },
                    )
                )
            }
        }
        return list
    }

    fun rule(c: Context, id: Long): Rule? = load(c).firstOrNull { it.id == id }

    /**
     * Приводит таблицу в соответствие со списком: обновляет, добавляет новые, удаляет пропавшие.
     * Возвращает тот же список, где у новых напоминаний проставлены id (ключи карточек не меняются).
     */
    @Synchronized
    fun save(c: Context, rules: List<Rule>): List<Rule> {
        seedIfNeeded(c)
        return saveRaw(c, rules)
    }

    private fun saveRaw(c: Context, rules: List<Rule>): List<Rule> {
        val db = db(c)
        val out = ArrayList<Rule>()
        db.beginTransaction()
        try {
            for ((i, r) in rules.withIndex()) {
                val v = ContentValues().apply {
                    put("name", r.name)
                    put("text", r.text)
                    put("enabled", if (r.enabled) 1 else 0)
                    put("skip_overlap", if (r.skipOverlap) 1 else 0)
                    put("start_min", r.start)
                    put("end_min", r.end)
                    put("interval_min", r.interval)
                    put("lead_min", r.lead)
                    put("position", i)
                    put("color", r.color)
                    put("repeat_every", r.repeatEvery)
                    put("repeat_count", r.repeatCount)
                    put("repeat_mode", if (r.repeatMode == Rule.REPEAT_AFTER) Rule.REPEAT_AFTER else Rule.REPEAT_BEFORE)
                    put("days", r.days and Rule.ALL_DAYS)
                    put("kind", r.kind)
                    put("alarm_sound", r.alarmSound)
                    put("snooze_min", r.snoozeMin)
                    put("challenge", r.challenge)
                    put("gradual", if (r.gradual) 1 else 0)
                    put("date_ymd", r.date)
                    put("every_days", r.everyDays)
                }
                val updated = r.id > 0 && db.update("rules", v, "id=?", arrayOf(r.id.toString())) > 0
                out.add(if (updated) r else r.copy(id = db.insert("rules", null, v)))
            }
            val keep = out.map { it.id }.toSet()
            val remove = ArrayList<Long>()
            db.rawQuery("SELECT id FROM rules", null).use { cur ->
                while (cur.moveToNext()) cur.getLong(0).let { if (it !in keep) remove.add(it) }
            }
            for (id in remove) {
                db.delete("rules", "id=?", arrayOf(id.toString()))
                // отложенный будильник удалённого напоминания звонить не должен
                db.delete("snooze", "rule_id=?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return out
    }

    // ---------- «Готово»: остановка повторов ----------

    /**
     * Помечает серию как принятую: всё, что в ней должно прийти позже этого момента, не придёт.
     * Если серия уже помечена, остаётся самый первый момент (повторный тап по старому уведомлению
     * не должен «воскрешать» уже отменённые уведомления).
     */
    @Synchronized
    fun markDone(c: Context, ruleId: Long, baseAt: Long) {
        if (ruleId <= 0) return
        try {
            val db = db(c)
            val v = ContentValues().apply {
                put("ev_key", "$ruleId:$baseAt")
                put("at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("done", null, v, SQLiteDatabase.CONFLICT_IGNORE)
            db.delete("done", "at < ?", arrayOf((System.currentTimeMillis() - 3 * DAY_MS).toString()))
        } catch (ignored: Exception) {
        }
    }

    /** Серии с ответом «Готово»: ключ серии -> момент ответа. */
    @Synchronized
    fun doneMap(c: Context): Map<String, Long> {
        val map = HashMap<String, Long>()
        try {
            val since = System.currentTimeMillis() - 3 * DAY_MS
            db(c).query("done", arrayOf("ev_key", "at"), "at >= ?", arrayOf(since.toString()), null, null, null).use { cur ->
                while (cur.moveToNext()) map[cur.getString(0)] = cur.getLong(1)
            }
        } catch (ignored: Exception) {
        }
        return map
    }

    // ---------- отложенные будильники ----------

    @Synchronized
    fun addSnooze(c: Context, ruleId: Long, at: Long, title: String, text: String) {
        val v = ContentValues().apply {
            put("rule_id", ruleId)
            put("at", at)
            put("title", title)
            put("text", text)
        }
        db(c).insert("snooze", null, v)
    }

    @Synchronized
    fun snoozes(c: Context): List<Snooze> {
        val list = ArrayList<Snooze>()
        try {
            db(c).query("snooze", null, null, null, null, null, "at").use { cur ->
                while (cur.moveToNext()) {
                    list.add(
                        Snooze(
                            cur.getLong(cur.getColumnIndexOrThrow("id")),
                            cur.getLong(cur.getColumnIndexOrThrow("rule_id")),
                            cur.getLong(cur.getColumnIndexOrThrow("at")),
                            cur.str("title"),
                            cur.str("text"),
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
        }
        return list
    }

    @Synchronized
    fun deleteSnooze(c: Context, id: Long) {
        try {
            db(c).delete("snooze", "id=?", arrayOf(id.toString()))
        } catch (ignored: Exception) {
        }
    }

    // ---------- журнал ----------

    @Synchronized
    fun addLog(c: Context, at: Long, title: String, text: String?) {
        try {
            val db = db(c)
            val v = ContentValues().apply {
                put("at", at)
                put("title", title)
                put("text", text ?: "")
            }
            db.insert("log", null, v)
            db.execSQL("DELETE FROM log WHERE id NOT IN (SELECT id FROM log ORDER BY id DESC LIMIT 200)")
        } catch (ignored: Exception) {
            // журнал не должен ломать показ уведомления
        }
    }

    @Synchronized
    fun loadLog(c: Context, limit: Int): List<LogItem> {
        val list = ArrayList<LogItem>()
        try {
            db(c).query("log", null, null, null, null, null, "id DESC", limit.toString()).use { cur ->
                while (cur.moveToNext()) {
                    list.add(LogItem(cur.getLong(cur.getColumnIndexOrThrow("at")), cur.str("title"), cur.str("text")))
                }
            }
        } catch (ignored: Exception) {
        }
        return list
    }

    @Synchronized
    fun clearLog(c: Context) {
        try {
            db(c).delete("log", null, null)
        } catch (ignored: Exception) {
        }
    }

    // ---------- тема и служебное ----------

    fun isDark(c: Context): Boolean {
        val t = prefs(c).getInt("theme", -1)
        if (t >= 0) return t == 1
        val night = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    fun setDark(c: Context, dark: Boolean) = prefs(c).edit().putInt("theme", if (dark) 1 else 0).apply()

    /** Звук уведомлений: "default" (системный по умолчанию), "silent" (без звука) или адрес выбранной мелодии. */
    fun getSound(c: Context): String = prefs(c).getString("sound", "default") ?: "default"

    fun setSound(c: Context, value: String) = prefs(c).edit().putString("sound", value).apply()

    fun getLastFired(c: Context): Long {
        val p = prefs(c)
        var v = p.getLong("lastFired", -1L)
        if (v < 0) {
            v = System.currentTimeMillis()
            p.edit().putLong("lastFired", v).apply()
        }
        return v
    }

    fun setLastFired(c: Context, v: Long) = prefs(c).edit().putLong("lastFired", v).apply()

    /**
     * Секрет, который кладётся в уведомления. По нему экран отличает настоящий тап по уведомлению
     * от команды «Готово», присланной чужим приложением.
     */
    @Synchronized
    fun intentToken(c: Context): String {
        val p = prefs(c)
        p.getString("intentToken", null)?.let { return it }
        val t = java.util.UUID.randomUUID().toString()
        p.edit().putString("intentToken", t).apply()
        return t
    }
}
