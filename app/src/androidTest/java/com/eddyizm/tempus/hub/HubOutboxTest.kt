package com.eddyizm.tempus.hub

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class HubOutboxTest {
    /** Isolated test directory: never delete or edit the user's real outbox. */
    private fun context(): Pair<Context, File> {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "hub-test-${UUID.randomUUID()}").apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) = File(dir, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        } to dir
    }

    @Test fun accountScopedEventsSurviveRestartAndOnlyAcknowledgedEventsAreRemoved() {
        val (context, dir) = context()
        try {
            HubOutbox(context).use { db ->
                db.put("server:alice", "play", "first", "one")
                db.put("server:alice", "play", "second", "two")
                db.put("server:bob", "play", "first", "other user")
                db.put("server:alice", "import", "first", "playlist")
                db.cache("server:alice", "alice mix")
            }
            HubOutbox(context).use { db ->
                assertEquals(listOf("first", "second"), db.pending("server:alice", "play", 100).map { it.first })
                db.acknowledge("server:alice", "play", listOf("first"))
                assertEquals(listOf("second"), db.pending("server:alice", "play", 100).map { it.first })
                assertEquals("other user", db.pending("server:bob", "play", 100).single().second)
                assertEquals("playlist", db.pending("server:alice", "import", 100).single().second)
                assertEquals("alice mix", db.cached("server:alice"))
                assertNull(db.cached("server:bob"))
            }
        } finally { dir.deleteRecursively() }
    }
}
