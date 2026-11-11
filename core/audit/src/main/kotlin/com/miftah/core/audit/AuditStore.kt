package com.miftah.core.audit

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.miftah.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local SQLite operation log. Callers must pass already-redacted output. Nothing leaves the device. */
class AuditStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "miftah_audit.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE audit (id TEXT PRIMARY KEY, ts INTEGER NOT NULL, name TEXT NOT NULL, target TEXT NOT NULL, " +
                "level TEXT NOT NULL, outcome TEXT NOT NULL, exit_code INTEGER, stdout TEXT NOT NULL, stderr TEXT NOT NULL, " +
                "previous TEXT, verified_state TEXT, verified INTEGER NOT NULL, rollback TEXT NOT NULL, rollback_note TEXT)",
        )
        db.execSQL("CREATE INDEX audit_ts ON audit(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema version 1 only; migrations will be added with the first schema change.
    }

    suspend fun record(entry: AuditEntry) = withContext(Dispatchers.IO) {
        val r = entry.result
        val v = ContentValues().apply {
            put("id", r.operationId)
            put("ts", r.timestampMillis)
            put("name", r.name)
            put("target", entry.target)
            put("level", r.accessLevel.name)
            put("outcome", r.outcome.name)
            put("exit_code", r.exitCode)
            put("stdout", r.stdout)
            put("stderr", r.stderr)
            put("previous", r.previousState)
            put("verified_state", r.verifiedState)
            put("verified", if (r.verified) 1 else 0)
            put("rollback", r.rollback.name)
            put("rollback_note", r.rollbackNote)
        }
        writableDatabase.insertOrThrow("audit", null, v)
        Unit
    }

    suspend fun recent(limit: Int = 200): List<AuditEntry> = withContext(Dispatchers.IO) {
        readableDatabase.query("audit", null, null, null, null, null, "ts DESC", limit.toString()).use { c ->
            val out = ArrayList<AuditEntry>()
            val exitIdx = c.getColumnIndexOrThrow("exit_code")
            val prevIdx = c.getColumnIndexOrThrow("previous")
            val vsIdx = c.getColumnIndexOrThrow("verified_state")
            val noteIdx = c.getColumnIndexOrThrow("rollback_note")
            while (c.moveToNext()) {
                val result = OperationResult(
                    operationId = c.getString(c.getColumnIndexOrThrow("id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    timestampMillis = c.getLong(c.getColumnIndexOrThrow("ts")),
                    accessLevel = AccessLevel.valueOf(c.getString(c.getColumnIndexOrThrow("level"))),
                    outcome = Outcome.valueOf(c.getString(c.getColumnIndexOrThrow("outcome"))),
                    exitCode = if (c.isNull(exitIdx)) null else c.getInt(exitIdx),
                    stdout = c.getString(c.getColumnIndexOrThrow("stdout")),
                    stderr = c.getString(c.getColumnIndexOrThrow("stderr")),
                    previousState = if (c.isNull(prevIdx)) null else c.getString(prevIdx),
                    verifiedState = if (c.isNull(vsIdx)) null else c.getString(vsIdx),
                    verified = c.getInt(c.getColumnIndexOrThrow("verified")) != 0,
                    rollback = RollbackAvailability.valueOf(c.getString(c.getColumnIndexOrThrow("rollback"))),
                    rollbackNote = if (c.isNull(noteIdx)) null else c.getString(noteIdx),
                )
                out += AuditEntry(c.getString(c.getColumnIndexOrThrow("target")), result)
            }
            out
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { writableDatabase.delete("audit", null, null); Unit }
}
