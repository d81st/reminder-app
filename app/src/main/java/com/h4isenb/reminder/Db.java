package com.h4isenb.reminder;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Локальная база SQLite внутри телефона: таблица напоминаний. */
class Db extends SQLiteOpenHelper {
    Db(Context c) {
        super(c.getApplicationContext(), "reminder.db", null, 1);
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
                + "position INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // пока одна версия схемы
    }
}
