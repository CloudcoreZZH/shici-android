package io.github.shici.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.shici.core.*
import java.time.Instant
import java.time.ZoneId

/** One transaction per user action. Idempotency keys prevent duplicate callbacks from losing progress. */
class LearningRepository(private val helper: LearningDatabase) {
    private val db get() = helper.writableDatabase

    @Synchronized fun books(): List<WordBook> = db.rawQuery("SELECT id,name FROM books ORDER BY id", null)
        .use { rows -> buildList { while (rows.moveToNext()) add(WordBook(rows.getLong(0), rows.getString(1))) } }

    @Synchronized fun createBook(name: String): Long {
        require(name.trim().length in 1..40) { "词书名称需为 1–40 个字符。" }
        return db.insertOrThrow("books", null, ContentValues().apply { put("name", name.trim()) })
    }

    @Synchronized fun add(bookId: Long, word: String, actionId: String, now: Instant) {
        require(normalizeWord(word).isNotEmpty())
        db.insertWithOnConflict("additions", null, ContentValues().apply {
            put("id", actionId); put("book_id", bookId); put("word", normalizeWord(word)); put("added_at", now.toEpochMilli())
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized fun snapshot(bookId: Long, now: Instant, zone: ZoneId = ZoneId.systemDefault()): BookSnapshot {
        val book = books().first { it.id == bookId }
        val words = db.rawQuery("""SELECT a.word,COUNT(*) AS additions,
            SUM(CASE WHEN a.completed_at IS NULL THEN 1 ELSE 0 END) AS pending,
            MAX(a.added_at) AS last_added,m.stability,m.difficulty,m.reviewed_at,m.due_at,
            m.phase,m.step,m.repetitions,m.lapses FROM additions a
            LEFT JOIN memory m ON a.book_id=m.book_id AND a.word=m.word
            WHERE a.book_id=? GROUP BY a.word ORDER BY additions DESC,last_added DESC""", arrayOf(bookId.toString()))
            .use { rows -> buildList {
                while (rows.moveToNext()) add(BookWord(rows.string("word"), rows.int("additions"), rows.int("pending"),
                    Instant.ofEpochMilli(rows.long("last_added")), rows.memoryOrNull()))
            } }
        val today = now.atZone(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val completed = db.rawQuery("SELECT COUNT(*) FROM additions WHERE book_id=? AND completed_at>=? AND completed_at<?",
            arrayOf(bookId.toString(), start.toString(), end.toString())).use { it.moveToFirst(); it.getInt(0) }
        return BookSnapshot(book, words, completed)
    }

    @Synchronized fun pendingTasks(bookId: Long): List<LearningTask> = db.rawQuery(
        "SELECT * FROM additions WHERE book_id=? AND completed_at IS NULL ORDER BY added_at,id", arrayOf(bookId.toString())
    ).use { rows -> buildList {
        while (rows.moveToNext()) add(LearningTask(rows.string("id"), bookId, rows.string("word"), Instant.ofEpochMilli(rows.long("added_at"))))
    } }

    @Synchronized fun memory(bookId: Long, word: String): MemoryState? = db.rawQuery(
        "SELECT * FROM memory WHERE book_id=? AND word=?", arrayOf(bookId.toString(), word)
    ).use { if (it.moveToFirst()) it.memoryOrNull() else null }

    @Synchronized fun answer(bookId: Long, item: StudyItem, mode: SessionMode, rating: Rating,
                             actionId: String, now: Instant, retention: Double): Boolean = transaction {
        if (exists("SELECT 1 FROM reviews WHERE action_id=?", arrayOf(actionId))) return@transaction false
        val previous = memory(bookId, item.word)
        if (mode == SessionMode.LEARN) {
            val taskId = requireNotNull(item.taskId)
            if (!exists("SELECT 1 FROM additions WHERE id=? AND book_id=? AND word=? AND completed_at IS NULL",
                    arrayOf(taskId, bookId.toString(), item.word))) return@transaction false
        } else {
            if (previous == null || previous.lastReviewedAt != item.memoryVersion || previous.dueAt.isAfter(now)) return@transaction false
            if (exists("SELECT 1 FROM additions WHERE book_id=? AND word=? AND completed_at IS NULL",
                    arrayOf(bookId.toString(), item.word))) return@transaction false
        }
        val next = FsrsScheduler(retention).review(previous, rating, now)
        if (mode == SessionMode.LEARN) db.execSQL("UPDATE additions SET completed_at=? WHERE id=?",
            arrayOf<Any>(now.toEpochMilli(), requireNotNull(item.taskId)))
        db.insertWithOnConflict("memory", null, next.values(bookId, item.word), SQLiteDatabase.CONFLICT_REPLACE)
        db.insertOrThrow("reviews", null, ContentValues().apply {
            put("action_id", actionId); put("book_id", bookId); put("word", item.word); put("rating", rating.value)
            put("reviewed_at", now.toEpochMilli()); put("mode", mode.name); put("task_id", item.taskId)
        })
        true
    }

    @Synchronized fun removeWord(bookId: Long, word: String) = transaction {
        val arguments = arrayOf(bookId.toString(), word)
        db.delete("additions", "book_id=? AND word=?", arguments)
        db.delete("memory", "book_id=? AND word=?", arguments)
        db.delete("reviews", "book_id=? AND word=?", arguments)
    }

    @Synchronized fun reset() = transaction {
        db.delete("reviews", null, null); db.delete("memory", null, null)
        db.delete("additions", null, null); db.delete("books", null, null)
        db.execSQL("INSERT INTO books(id,name) VALUES(1,'考研生词本')")
    }

    private fun exists(sql: String, arguments: Array<String>) = db.rawQuery(sql, arguments).use { it.moveToFirst() }
    private fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try { return block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
}

internal fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))
internal fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))
internal fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
internal fun Cursor.memoryOrNull(): MemoryState? {
    if (isNull(getColumnIndexOrThrow("stability"))) return null
    return MemoryState(getDouble(getColumnIndexOrThrow("stability")), getDouble(getColumnIndexOrThrow("difficulty")),
        Instant.ofEpochMilli(long("reviewed_at")), Instant.ofEpochMilli(long("due_at")), Phase.valueOf(string("phase")),
        int("step"), int("repetitions"), int("lapses"))
}
private fun MemoryState.values(bookId: Long, word: String) = ContentValues().apply {
    put("book_id", bookId); put("word", word); put("stability", stability); put("difficulty", difficulty)
    put("reviewed_at", lastReviewedAt.toEpochMilli()); put("due_at", dueAt.toEpochMilli()); put("phase", phase.name)
    put("step", step); put("repetitions", repetitions); put("lapses", lapses)
}
