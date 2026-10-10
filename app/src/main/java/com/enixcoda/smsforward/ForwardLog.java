package com.enixcoda.smsforward;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Pro: on-device history of the newest forwards. Nothing is logged for free users. */
final class ForwardLog extends SQLiteOpenHelper {
    static final String SMS = "sms"; // channel for SMS rows; Telegram/Web use ForwardWorker's channel names
    static final String SENT = "sent";           // SMS handed to the carrier
    static final String DELIVERED = "delivered"; // Telegram/Web accepted it
    static final String QUEUED = "queued";       // waiting for a retry
    static final String FAILED = "failed";

    private static final int MAX_ROWS = 100;
    private static ForwardLog instance;

    static final class Entry {
        final long id, time;
        final String channel, target, sender, message, text, status;

        Entry(Cursor c) {
            id = c.getLong(0);
            time = c.getLong(1);
            channel = c.getString(2);
            target = c.getString(3);
            sender = c.getString(4);
            message = c.getString(5);
            text = c.getString(6);
            status = c.getString(7);
        }
    }

    private static synchronized ForwardLog get(Context context) {
        if (instance == null) instance = new ForwardLog(context.getApplicationContext());
        return instance;
    }

    private ForwardLog(Context context) {
        super(context, "forward_log.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE log (_id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, channel TEXT NOT NULL,"
                + " target TEXT, sender TEXT NOT NULL, message TEXT NOT NULL, text TEXT NOT NULL, status TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /** Returns the row id, or -1 when not logged (free user). */
    static long add(Context context, String channel, String target, String sender, String message, String text, String status) {
        if (!Billing.isPro(context)) return -1;
        ContentValues values = new ContentValues();
        values.put("time", System.currentTimeMillis());
        values.put("channel", channel);
        values.put("target", target);
        values.put("sender", sender);
        values.put("message", message);
        values.put("text", text);
        values.put("status", status);
        SQLiteDatabase db = get(context).getWritableDatabase();
        long id = db.insert("log", null, values);
        db.delete("log", "_id <= ?", new String[]{String.valueOf(id - MAX_ROWS)});
        return id;
    }

    static void setStatus(Context context, long id, String status) {
        if (id < 0) return;
        ContentValues values = new ContentValues();
        values.put("status", status);
        get(context).getWritableDatabase().update("log", values, "_id = ?", new String[]{String.valueOf(id)});
    }

    static List<Entry> recent(Context context) {
        List<Entry> entries = new ArrayList<>();
        try (Cursor c = get(context).getReadableDatabase().query("log",
                new String[]{"_id", "time", "channel", "target", "sender", "message", "text", "status"},
                null, null, null, null, "_id DESC")) {
            while (c.moveToNext()) entries.add(new Entry(c));
        }
        return entries;
    }

    static void clear(Context context) {
        get(context).getWritableDatabase().delete("log", null, null);
    }
}
