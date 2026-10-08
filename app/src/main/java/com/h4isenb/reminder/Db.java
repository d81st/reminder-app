package com.h4isenb.reminder;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Локальная база SQLite внутри телефона: напоминания и журнал уведомлений. */
class Db extends SQLiteOpenHelper {
    Db(Context c) {
        super(c.getApplicationContext(), "reminder.db", null, 2);
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
                + "color INTEGER NOT NULL DEFAULT 0)");
        createLog(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE rules ADD COLUMN color INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE rules SET color = position % 6");
            createLog(db);
        }
    }

    private void createLog(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "text TEXT NOT NULL)");
    }
}
