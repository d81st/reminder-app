package com.h4isenb.reminder

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Локальная база SQLite внутри телефона: напоминания, журнал, «Готово» для повторов и отложенные будильники. */
class Db(c: Context) : SQLiteOpenHelper(c.applicationContext, "reminder.db", null, 9) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE rules ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "text TEXT NOT NULL,"
                + "enabled INTEGER NOT NULL,"
                + "skip_overlap INTEGER NOT NULL,"
                + "start_min INTEGER NOT NULL,"
                + "end_min INTEGER NOT NULL,"
                + "interval_min INTEGER NOT NULL,"
                + "lead_min INTEGER NOT NULL,"
                + "position INTEGER NOT NULL,"
                + "color INTEGER NOT NULL DEFAULT 0,"
                + "repeat_every INTEGER NOT NULL DEFAULT 5,"
                + "repeat_count INTEGER NOT NULL DEFAULT 0,"
                + "repeat_mode INTEGER NOT NULL DEFAULT 0,"
                + ALARM_COLUMNS.joinToString(",") + ","
                + DATE_COLUMN + "," + EVERY_COLUMN + ")"
        )
        createLog(db)
        createDone(db)
        createSnooze(db)
        addSnoozeKind(db)
        createLists(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE rules ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE rules SET color = position % 6")
            createLog(db)
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE rules ADD COLUMN repeat_every INTEGER NOT NULL DEFAULT 5")
            db.execSQL("ALTER TABLE rules ADD COLUMN repeat_count INTEGER NOT NULL DEFAULT 0")
            createDone(db)
        }
        if (oldVersion < 4) {
            // 0 = повторы до времени (Rule.REPEAT_BEFORE), 1 = после времени
            db.execSQL("ALTER TABLE rules ADD COLUMN repeat_mode INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            // дни недели и будильник; старые напоминания остаются уведомлениями на каждый день
            for (col in ALARM_COLUMNS) db.execSQL("ALTER TABLE rules ADD COLUMN $col")
            createSnooze(db)
        }
        if (oldVersion < 6) {
            // разовые напоминания: 0 = по дням недели
            db.execSQL("ALTER TABLE rules ADD COLUMN $DATE_COLUMN")
        }
        if (oldVersion < 7) {
            // «каждые N дней» от даты: 0 = нет
            db.execSQL("ALTER TABLE rules ADD COLUMN $EVERY_COLUMN")
        }
        if (oldVersion < 8) addSnoozeKind(db)
        if (oldVersion < 9) createLists(db)
    }

    private fun createLog(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "text TEXT NOT NULL)"
        )
    }

    /** «Готово» по серии: ключ серии и момент ответа (всё, что в серии позже этого момента, не приходит). */
    private fun createDone(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS done (ev_key TEXT PRIMARY KEY, at INTEGER NOT NULL)")
    }

    /** Отложенные будильники: когда снова зазвонить и что показать. */
    private fun createSnooze(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS snooze ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "rule_id INTEGER NOT NULL,"
                + "at INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "text TEXT NOT NULL)"
        )
    }

    /** Списки дел: пункты хранятся JSON-массивом в одной строке. */
    private fun createLists(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS lists ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "items TEXT NOT NULL,"
                + "color INTEGER NOT NULL DEFAULT 0,"
                + "position INTEGER NOT NULL,"
                + "updated INTEGER NOT NULL DEFAULT 0)"
        )
    }

    /** Отложить можно и обычное уведомление: 1 = будильник (звонит), 0 = уведомление. */
    private fun addSnoozeKind(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE snooze ADD COLUMN alarm INTEGER NOT NULL DEFAULT 1")
    }

    private companion object {
        const val DATE_COLUMN = "date_ymd INTEGER NOT NULL DEFAULT 0"
        const val EVERY_COLUMN = "every_days INTEGER NOT NULL DEFAULT 0"
        val ALARM_COLUMNS = listOf(
            "days INTEGER NOT NULL DEFAULT ${Rule.ALL_DAYS}",
            "kind INTEGER NOT NULL DEFAULT ${Rule.KIND_NOTIFY}",
            "alarm_sound TEXT NOT NULL DEFAULT ''",
            "snooze_min INTEGER NOT NULL DEFAULT 5",
            "challenge INTEGER NOT NULL DEFAULT ${Rule.CHALLENGE_NONE}",
            "gradual INTEGER NOT NULL DEFAULT 1",
        )
    }
}
