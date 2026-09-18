package io.github.shici.app.data

import android.database.sqlite.SQLiteDatabase
import io.github.shici.core.Phase
import io.github.shici.core.StudyDays
import java.time.ZoneId

/** Runs inside SQLiteOpenHelper's upgrade transaction; no learning history is deleted. */
internal object DailyScheduleMigration {
    fun migrate(db: SQLiteDatabase, zone: ZoneId = ZoneId.systemDefault()) {
        val memories = db.rawQuery("SELECT * FROM memory", null).use { rows -> buildList {
            while (rows.moveToNext()) add(Triple(rows.long("book_id"), rows.string("word"), requireNotNull(rows.memoryOrNull())))
        } }
        for ((book, word, memory) in memories) {
            val due = StudyDays.migrateDue(memory.lastReviewedAt, memory.dueAt, zone).toEpochMilli()
            db.execSQL("UPDATE memory SET due_at=?,phase=?,step=0 WHERE book_id=? AND word=?",
                arrayOf<Any>(due, Phase.REVIEW.name, book, word))
            db.execSQL("UPDATE additions SET available_at=? WHERE book_id=? AND word=? AND completed_at IS NULL AND available_at>0",
                arrayOf<Any>(due, book, word))
        }
        val sessions = db.rawQuery("SELECT payload FROM sessions", null).use { rows -> buildList {
            while (rows.moveToNext()) add(StudyCodec.decode(rows.getString(0)))
        } }
        for (session in sessions) {
            val items = session.items.map { item ->
                if (item.taskId == null) item else db.rawQuery("SELECT available_at FROM additions WHERE id=?", arrayOf(item.taskId)).use {
                    if (it.moveToFirst()) item.copy(availableAt = java.time.Instant.ofEpochMilli(it.getLong(0))) else item
                }
            }
            // Old undo snapshots contain minute-based schedules. Preserve the history but close that undo window.
            val migrated = session.copy(items = items, lastActionId = null)
            db.execSQL("UPDATE sessions SET payload=? WHERE book_id=? AND mode=?",
                arrayOf<Any>(StudyCodec.encode(migrated), session.bookId, session.mode.name))
        }
    }
}
