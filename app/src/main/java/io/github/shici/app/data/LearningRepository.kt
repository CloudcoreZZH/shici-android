package io.github.shici.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.shici.core.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

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
        val reviewed = db.rawQuery("SELECT COUNT(*) FROM reviews WHERE book_id=? AND mode='REVIEW' AND undone=0 AND reviewed_at>=? AND reviewed_at<?",
            arrayOf(bookId.toString(), start.toString(), end.toString())).use { it.moveToFirst(); it.getInt(0) }
        val ready = pendingTasks(bookId).distinctBy { it.word }.count { !it.availableAt.isAfter(now) }
        return BookSnapshot(book, words, completed, reviewed, ready)
    }

    @Synchronized fun pendingTasks(bookId: Long): List<LearningTask> = db.rawQuery(
        "SELECT * FROM additions WHERE book_id=? AND completed_at IS NULL ORDER BY added_at,id", arrayOf(bookId.toString())
    ).use { rows -> buildList {
        while (rows.moveToNext()) add(LearningTask(rows.string("id"), bookId, rows.string("word"), Instant.ofEpochMilli(rows.long("added_at")),
            Instant.ofEpochMilli(rows.long("available_at"))))
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
            if (exists("SELECT 1 FROM additions WHERE id=? AND available_at>?", arrayOf(taskId, now.toEpochMilli().toString()))) return@transaction false
        } else {
            if (previous == null || previous.lastReviewedAt != item.memoryVersion || previous.dueAt.isAfter(now)) return@transaction false
            if (exists("SELECT 1 FROM additions WHERE book_id=? AND word=? AND completed_at IS NULL",
                    arrayOf(bookId.toString(), item.word))) return@transaction false
        }
        val next = FsrsScheduler(retention).review(previous, rating, now)
        if (mode == SessionMode.LEARN) {
            if (rating == Rating.AGAIN) db.execSQL("UPDATE additions SET available_at=? WHERE id=?",
                arrayOf<Any>(next.dueAt.toEpochMilli(), requireNotNull(item.taskId)))
            else db.execSQL("UPDATE additions SET completed_at=? WHERE id=?",
                arrayOf<Any>(now.toEpochMilli(), requireNotNull(item.taskId)))
        }
        db.insertWithOnConflict("memory", null, next.values(bookId, item.word), SQLiteDatabase.CONFLICT_REPLACE)
        db.insertOrThrow("reviews", null, ContentValues().apply {
            put("action_id", actionId); put("book_id", bookId); put("word", item.word); put("rating", rating.value)
            put("reviewed_at", now.toEpochMilli()); put("mode", mode.name); put("task_id", item.taskId)
            put("previous_memory", previous?.let(StudyCodec::memory))
        })
        true
    }

    @Synchronized fun removeWord(bookId: Long, word: String) = transaction {
        val arguments = arrayOf(bookId.toString(), word)
        db.delete("additions", "book_id=? AND word=?", arguments)
        db.delete("memory", "book_id=? AND word=?", arguments)
        db.delete("reviews", "book_id=? AND word=?", arguments)
        db.delete("sessions", "book_id=?", arrayOf(bookId.toString()))
    }

    @Synchronized fun reset() = transaction {
        db.delete("sessions", null, null)
        db.delete("reviews", null, null); db.delete("memory", null, null)
        db.delete("additions", null, null); db.delete("books", null, null)
        db.execSQL("INSERT INTO books(id,name) VALUES(1,'考研生词本')")
    }

    @Synchronized fun session(bookId: Long, mode: SessionMode): StudyProgress? = db.rawQuery(
        "SELECT payload FROM sessions WHERE book_id=? AND mode=?", arrayOf(bookId.toString(), mode.name)
    ).use { if (it.moveToFirst()) StudyCodec.decode(it.getString(0)) else null }

    @Synchronized fun beginSession(bookId: Long, mode: SessionMode, limit: Int, now: Instant, fresh: Boolean = false): StudyProgress? = transaction {
        require(limit in 1..50)
        session(bookId, mode)?.takeUnless { it.finished || fresh }?.let { previous ->
            val pending = pendingTasks(bookId).associateBy { it.id }
            val due = reviewQueue(snapshot(bookId, now).words, now).associateBy { it.word }
            val valid = previous.items.filter { item ->
                if (mode == SessionMode.LEARN) pending[item.taskId]?.word == item.word
                else due[item.word]?.memory?.lastReviewedAt == item.memoryVersion
            }
            if (valid.isNotEmpty()) return@transaction previous.copy(items = valid, total = previous.completed + valid.size)
                .ordered(now).also(::saveSession)
            db.delete("sessions", "book_id=? AND mode=?", arrayOf(bookId.toString(), mode.name))
        }
        val items = if (mode == SessionMode.LEARN) learningGroup(pendingTasks(bookId), limit, now)
                .filter { !fresh || !it.availableAt.isAfter(now) }
            else reviewQueue(snapshot(bookId, now).words, now).take(limit)
                .map { StudyItem(it.word, memoryVersion = it.memory!!.lastReviewedAt) }
        if (items.isEmpty()) return@transaction null
        StudyProgress(UUID.randomUUID().toString(), bookId, mode, items, items.size, startedAt = now).also(::saveSession)
    }

    /** Memory, addition, log and resumable group advance in the same database transaction. */
    @Synchronized fun answerSession(bookId: Long, mode: SessionMode, sessionId: String, expected: StudyItem,
                                    rating: Rating, actionId: String, now: Instant, retention: Double): StudyProgress = transaction {
        val progress = requireNotNull(session(bookId, mode)).ordered(now)
        check(progress.id == sessionId && progress.items.firstOrNull() == expected) { "学习进度已更新，请返回后继续。" }
        check(!expected.availableAt.isAfter(now)) { "这个词还在等待巩固，请稍后继续。" }
        check(answer(bookId, expected, mode, rating, actionId, now, retention)) { "这次答题已保存，未重复计分。" }
        db.execSQL("UPDATE reviews SET previous_session=? WHERE action_id=?", arrayOf(StudyCodec.encode(progress), actionId))
        progress.afterAnswer(rating, requireNotNull(memory(bookId, expected.word)), now, actionId).also(::saveSession)
    }

    @Synchronized fun undoAnswer(bookId: Long, mode: SessionMode, actionId: String): StudyProgress = transaction {
        val progress = requireNotNull(session(bookId, mode))
        check(progress.lastActionId == actionId) { "只能撤销本组最近一次答题。" }
        val record = db.rawQuery("SELECT * FROM reviews WHERE action_id=? AND undone=0", arrayOf(actionId)).use { row ->
            check(row.moveToFirst()) { "这次答题已撤销。" }
            UndoRecord(row.string("word"), row.long("reviewed_at"), row.nullableString("previous_memory"),
                requireNotNull(row.nullableString("previous_session")))
        }
        check(memory(bookId, record.word)?.lastReviewedAt?.toEpochMilli() == record.reviewedAt) { "这个词已有新的记录，不能撤销旧答题。" }
        check(!exists("SELECT 1 FROM reviews WHERE book_id=? AND word=? AND undone=0 AND rowid>(SELECT rowid FROM reviews WHERE action_id=?)",
            arrayOf(bookId.toString(), record.word, actionId))) { "这个词已有新的记录。" }
        val restored = StudyCodec.decode(record.progress)
        val task = restored.items.firstOrNull()?.taskId
        if (task != null) {
            check(exists("SELECT 1 FROM additions WHERE id=? AND book_id=?", arrayOf(task, bookId.toString())))
            db.execSQL("UPDATE additions SET completed_at=NULL,available_at=? WHERE id=?",
                arrayOf<Any>(restored.items.first().availableAt.toEpochMilli(), task))
        }
        if (record.memory == null) db.delete("memory", "book_id=? AND word=?", arrayOf(bookId.toString(), record.word))
        else db.insertWithOnConflict("memory", null, StudyCodec.memory(record.memory).values(bookId, record.word), SQLiteDatabase.CONFLICT_REPLACE)
        db.execSQL("UPDATE reviews SET undone=1 WHERE action_id=?", arrayOf(actionId))
        restored.also(::saveSession)
    }

    private fun saveSession(progress: StudyProgress) {
        db.insertWithOnConflict("sessions", null, ContentValues().apply {
            put("book_id", progress.bookId); put("mode", progress.mode.name); put("payload", StudyCodec.encode(progress))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private data class UndoRecord(val word: String, val reviewedAt: Long, val memory: String?, val progress: String)

    private fun exists(sql: String, arguments: Array<String>) = db.rawQuery(sql, arguments).use { it.moveToFirst() }
    private fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try { return block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
}

internal fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))
internal fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))
internal fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
internal fun Cursor.nullableString(name: String): String? = if (isNull(getColumnIndexOrThrow(name))) null else string(name)
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
