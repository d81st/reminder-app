package com.h4isenb.reminder;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Локальная база SQLite внутри телефона: напоминания, журнал уведомлений и «Готово» для повторов. */
class Db extends SQLiteOpenHelper {
    Db(Context c) {
        super(c.getApplicationContext(), "reminder.db", null, 4);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE rules ("
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
                + "repeat_mode INTEGER NOT NULL DEFAULT 0)");
        createLog(db);
        createDone(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE rules ADD COLUMN color INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE rules SET color = position % 6");
            createLog(db);
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE rules ADD COLUMN repeat_every INTEGER NOT NULL DEFAULT 5");
            db.execSQL("ALTER TABLE rules ADD COLUMN repeat_count INTEGER NOT NULL DEFAULT 0");
            createDone(db);
        }
        if (oldVersion < 4) {
            // 0 = повторы до времени (Rule.REPEAT_BEFORE), 1 = после времени
            db.execSQL("ALTER TABLE rules ADD COLUMN repeat_mode INTEGER NOT NULL DEFAULT 0");
        }
    }

    private void createLog(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "text TEXT NOT NULL)");
    }

    /** «Готово» по серии: ключ серии и момент ответа (всё, что в серии позже этого момента, не приходит). */
    private void createDone(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS done ("
                + "ev_key TEXT PRIMARY KEY,"
                + "at INTEGER NOT NULL)");
    }
}
