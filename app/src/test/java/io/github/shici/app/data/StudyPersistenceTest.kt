package io.github.shici.app.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.shici.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StudyPersistenceTest {
    private lateinit var database: LearningDatabase
    private lateinit var repo: LearningRepository
    private val now = Instant.parse("2026-09-18T08:00:00Z")
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup() { app.deleteDatabase("learning.db"); database = LearningDatabase(app); repo = LearningRepository(database) }
    @After fun close() { database.close() }
    private fun add(word: String, id: String = word) { repo.add(1, word, id, now) }
    private fun start(mode: SessionMode = SessionMode.LEARN, at: Instant = now) = repo.beginSession(1, mode, 10, at)!!
    private fun grade(progress: StudyProgress, rating: Rating, id: String, at: Instant = now) =
        repo.answerSession(1, progress.mode, progress.id, progress.items.first(), rating, id, at, 0.9)

    @Test fun `failed learning keeps pending count then resumes after due time`() {
        add("address")
        val progress = grade(start(), Rating.AGAIN, "forgot")
        assertEquals(1, repo.snapshot(1, now).pendingCount)
        assertEquals(0, repo.snapshot(1, now).completedToday)
        assertEquals(StudyDays.dueAfter(now, 1, ZoneId.systemDefault()), progress.items.single().availableAt)
        assertThrows(IllegalStateException::class.java) { grade(progress, Rating.GOOD, "early") }
        val nextDay = progress.items.single().availableAt
        val done = grade(progress, Rating.GOOD, "remember", nextDay)
        assertTrue(done.finished)
        assertEquals(0, repo.snapshot(1, nextDay).pendingCount)
        assertEquals(1, repo.snapshot(1, nextDay).completedToday)
    }

    @Test fun `session and memory survive process style database reopen`() {
        add("address"); add("claim")
        val progress = grade(start(), Rating.AGAIN, "forgot")
        val memory = repo.memory(1, "address")
        database.close(); database = LearningDatabase(app); repo = LearningRepository(database)
        assertEquals(progress, start())
        assertEquals(memory, repo.memory(1, "address"))
        assertEquals("claim", start().items.first().word)
    }

    @Test fun `waiting can open another group without losing tasks or bypassing the same word delay`() {
        add("address", "a1"); add("address", "a2"); add("claim")
        val first = repo.beginSession(1, SessionMode.LEARN, 1, now)!!
        grade(first, Rating.AGAIN, "forgot")
        val other = repo.beginSession(1, SessionMode.LEARN, 10, now, fresh = true)!!
        assertEquals(listOf("claim"), other.items.map { it.word })
        assertEquals(3, repo.snapshot(1, now).pendingCount)
        assertEquals(1, repo.snapshot(1, now).readyLearningWords)
        grade(other, Rating.GOOD, "claim-answer")
        val waiting = start()
        assertEquals("a1", waiting.items.single().taskId)
        assertEquals(StudyDays.dueAfter(now, 1, ZoneId.systemDefault()), waiting.items.single().availableAt)
    }

    @Test fun `undo first answer restores original task and removes newly created memory`() {
        add("address")
        val before = start()
        grade(before, Rating.GOOD, "good")
        val restored = repo.undoAnswer(1, SessionMode.LEARN, "good")
        assertEquals(before, restored)
        assertNull(repo.memory(1, "address"))
        assertEquals(1, repo.snapshot(1, now).pendingCount)
        assertEquals(0, repo.snapshot(1, now).completedToday)
        assertTrue(grade(restored, Rating.GOOD, "retry").finished)
    }

    @Test fun `undo failure restores availability and all scheduling fields`() {
        add("address"); val before = start()
        grade(before, Rating.AGAIN, "forgot")
        val restored = repo.undoAnswer(1, SessionMode.LEARN, "forgot")
        assertEquals(Instant.EPOCH, repo.pendingTasks(1).single().availableAt)
        assertNull(repo.memory(1, "address"))
        assertEquals(before, restored)
    }

    @Test fun `undo review restores exact prior memory and today review count`() {
        add("address"); grade(start(), Rating.EASY, "learn")
        val before = repo.memory(1, "address")!!
        val review = start(SessionMode.REVIEW, before.dueAt)
        grade(review, Rating.AGAIN, "review", before.dueAt)
        assertEquals(1, repo.snapshot(1, before.dueAt).reviewedToday)
        repo.undoAnswer(1, SessionMode.REVIEW, "review")
        assertEquals(before, repo.memory(1, "address"))
        assertEquals(0, repo.snapshot(1, before.dueAt).reviewedToday)
    }

    @Test fun `failed transaction cannot advance group or complete its task`() {
        add("address"); val before = start()
        assertThrows(IllegalArgumentException::class.java) {
            repo.answerSession(1, SessionMode.LEARN, before.id, before.items.first(), Rating.GOOD, "bad", now, 0.1)
        }
        assertEquals(before, repo.session(1, SessionMode.LEARN))
        assertNull(repo.memory(1, "address"))
        assertEquals(1, repo.snapshot(1, now).pendingCount)
    }

    @Test fun `same word additions are completed in separate groups`() {
        add("address", "a1"); add("address", "a2")
        val first = start(); assertEquals(1, first.total)
        grade(first, Rating.GOOD, "first")
        val next = start(); assertNotEquals(first.id, next.id)
        assertEquals(1, repo.snapshot(1, now).pendingCount)
        grade(next, Rating.GOOD, "second")
        assertEquals(2, repo.snapshot(1, now).totalAdditions)
        assertEquals(0, repo.snapshot(1, now).pendingCount)
    }

    @Test fun `readding a paused review word does not strand the review group`() {
        add("address"); add("claim")
        var learning = start()
        learning = grade(learning, Rating.EASY, "learn1")
        grade(learning, Rating.EASY, "learn2")
        val due = repo.memory(1, "address")!!.dueAt
        start(SessionMode.REVIEW, due)
        repo.add(1, "address", "again", due)
        val resumed = start(SessionMode.REVIEW, due)
        assertEquals(listOf("claim"), resumed.items.map { it.word })
        assertTrue(grade(resumed, Rating.GOOD, "review", due).finished)
    }

    @Test fun `version one migration preserves books additions memory and review history`() {
        database.close()
        app.deleteDatabase("learning.db")
        val old = SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath("learning.db"), null)
        old.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)")
        old.execSQL("INSERT INTO books VALUES(1,'旧词书')")
        old.execSQL("CREATE TABLE additions(id TEXT PRIMARY KEY,book_id INTEGER NOT NULL,word TEXT NOT NULL,added_at INTEGER NOT NULL,completed_at INTEGER)")
        old.execSQL("INSERT INTO additions VALUES('old',1,'address',?,NULL)", arrayOf<Any>(now.toEpochMilli()))
        old.execSQL("CREATE TABLE memory(book_id INTEGER,word TEXT,stability REAL,difficulty REAL,reviewed_at INTEGER,due_at INTEGER,phase TEXT,step INTEGER,repetitions INTEGER,lapses INTEGER,PRIMARY KEY(book_id,word))")
        old.execSQL("INSERT INTO memory VALUES(1,'address',2.3,5.0,?,?,'LEARNING',1,2,0)",
            arrayOf<Any>(now.toEpochMilli(), now.plusSeconds(600).toEpochMilli()))
        old.execSQL("CREATE TABLE reviews(action_id TEXT PRIMARY KEY,book_id INTEGER,word TEXT,rating INTEGER,reviewed_at INTEGER,mode TEXT,task_id TEXT)")
        old.execSQL("INSERT INTO reviews VALUES('old-r',1,'address',3,?,'LEARN','old')", arrayOf<Any>(now.toEpochMilli()))
        old.version = 1; old.close()
        database = LearningDatabase(app); repo = LearningRepository(database)
        assertEquals("旧词书", repo.books().single().name)
        assertEquals("old", repo.pendingTasks(1).single().id)
        assertEquals(Instant.EPOCH, repo.pendingTasks(1).single().availableAt)
        assertEquals(3, database.writableDatabase.version)
        assertEquals(2.3, repo.memory(1, "address")!!.stability, 0.000001)
        assertEquals(StudyDays.dueAfter(now, 1, ZoneId.systemDefault()), repo.memory(1, "address")!!.dueAt)
        assertEquals(1, start().total)
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM reviews WHERE action_id='old-r' AND undone=0", null).use {
            assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
        }
    }

    @Test fun `duplicate and out of order submissions cannot double count or undo another answer`() {
        add("address"); add("claim")
        val first = start()
        val next = grade(first, Rating.GOOD, "a1")
        assertThrows(IllegalStateException::class.java) { grade(first, Rating.GOOD, "a1") }
        assertEquals(1, repo.snapshot(1, now).completedToday)
        grade(next, Rating.GOOD, "a2")
        assertThrows(IllegalStateException::class.java) { repo.undoAnswer(1, SessionMode.LEARN, "a1") }
        assertEquals(2, repo.snapshot(1, now).completedToday)
        assertEquals(0, repo.snapshot(1, now).pendingCount)
    }

    @Test fun `version two migration preserves deferred group and cannot restore minute schedules through undo`() {
        add("address", "a1"); add("address", "a2")
        val progress = grade(start(), Rating.AGAIN, "forgot")
        val minuteDue = now.plusSeconds(60)
        val oldGroup = progress.copy(items = progress.items.map { it.copy(availableAt = minuteDue) })
        database.writableDatabase.execSQL("UPDATE memory SET due_at=?,phase='LEARNING',step=0", arrayOf<Any>(minuteDue.toEpochMilli()))
        database.writableDatabase.execSQL("UPDATE additions SET available_at=? WHERE id='a1'", arrayOf<Any>(minuteDue.toEpochMilli()))
        database.writableDatabase.execSQL("UPDATE sessions SET payload=?", arrayOf<Any>(StudyCodec.encode(oldGroup)))
        val before = repo.memory(1, "address")!!
        database.writableDatabase.version = 2
        database.close(); database = LearningDatabase(app); repo = LearningRepository(database)
        val migrated = repo.session(1, SessionMode.LEARN)!!
        val nextDay = StudyDays.dueAfter(now, 1, ZoneId.systemDefault())
        assertEquals(nextDay, migrated.items.single().availableAt)
        assertNull(migrated.lastActionId)
        assertEquals(2, repo.snapshot(1, now).pendingCount)
        assertEquals(2, repo.snapshot(1, now).totalAdditions)
        assertEquals(before.stability, repo.memory(1, "address")!!.stability, 0.0)
        assertEquals(before.repetitions, repo.memory(1, "address")!!.repetitions)
        assertEquals(before.lastReviewedAt, repo.memory(1, "address")!!.lastReviewedAt)
        assertEquals(nextDay, repo.memory(1, "address")!!.dueAt)
        assertThrows(IllegalStateException::class.java) { repo.undoAnswer(1, SessionMode.LEARN, "forgot") }
        assertThrows(IllegalStateException::class.java) { grade(migrated, Rating.GOOD, "early", now.plusSeconds(3600)) }
        assertTrue(grade(migrated, Rating.GOOD, "next-day", nextDay).finished)
        assertEquals(1, repo.snapshot(1, nextDay).pendingCount)
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM reviews WHERE action_id='forgot'", null).use {
            assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
        }
    }

    @Test fun `editorial notes preserve dictionary meaning and clearly separate examples`() {
        val dictionary = DictionaryStore(app)
        val entry = dictionary.find("address")!!
        assertTrue(entry.editorialSenses.first().text.contains("处理"))
        assertTrue(entry.senses.any { it.text.contains("地址") })
        assertFalse(entry.hasExamStatistics)
        assertTrue(entry.example.isNotBlank())
        assertTrue(dictionary.editorialSize >= 100)
        val rows = app.assets.open("editorial-notes.tsv").bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val words = rows.map { it.substringBefore('\t') }
        assertEquals(words.size, words.toSet().size)
        words.forEach { word ->
            val note = requireNotNull(dictionary.find(word)) { word }
            assertTrue(word, note.editorialSenses.isNotEmpty())
            assertTrue(word, note.example.isNotBlank() && note.exampleTranslation.isNotBlank())
            assertFalse(word, note.hasExamStatistics)
        }
    }
}
