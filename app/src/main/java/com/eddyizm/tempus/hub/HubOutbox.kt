package com.eddyizm.tempus.hub

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Durable events, not the chronology cache (which keeps only the latest play of each song). */
internal class HubOutbox(context: Context) : SQLiteOpenHelper(context, "hub_sync.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE outbox(account TEXT NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, body TEXT NOT NULL, PRIMARY KEY(account,kind,id))")
        db.execSQL("CREATE TABLE daily(account TEXT PRIMARY KEY, body TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun put(account: String, kind: String, id: String, body: String) {
        writableDatabase.insertWithOnConflict("outbox", null, ContentValues().apply {
            put("account", account); put("kind", kind); put("id", id); put("body", body)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun pending(account: String, kind: String, limit: Int): List<Pair<String, String>> =
        readableDatabase.query("outbox", arrayOf("id", "body"), "account=? AND kind=?", arrayOf(account, kind), null, null, "rowid", limit.toString()).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
    fun acknowledge(account: String, kind: String, ids: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try { ids.forEach { db.delete("outbox", "account=? AND kind=? AND id=?", arrayOf(account, kind, it)) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    fun cache(account: String, body: String) {
        writableDatabase.insertWithOnConflict("daily", null, ContentValues().apply { put("account", account); put("body", body) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun cached(account: String): String? = readableDatabase.query("daily", arrayOf("body"), "account=?", arrayOf(account), null, null, null).use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}
