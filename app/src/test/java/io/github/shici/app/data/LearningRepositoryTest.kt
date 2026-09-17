package io.github.shici.app.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.shici.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LearningRepositoryTest {
    private lateinit var helper: LearningDatabase
    private lateinit var repository: LearningRepository
    private val now = Instant.parse("2026-09-17T08:00:00Z")

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.deleteDatabase("learning.db")
        helper = LearningDatabase(app)
        repository = LearningRepository(helper)
    }
    @After fun close() { helper.close() }

    @Test fun `each addition creates a distinct pending learning task`() {
        repeat(4) { repository.add(1, "address", "add-$it", now.plusSeconds(it.toLong())) }
        val word = repository.snapshot(1, now).words.single()
        assertEquals(4, word.additionCount)
        assertEquals(4, word.pendingCount)
        assertEquals(4, repository.pendingTasks(1).size)
        assertNull(repository.memory(1, "address"))
    }

    @Test fun `duplicate command callbacks are idempotent but distinct clicks are not merged`() {
        repository.add(1, "address", "a", now)
        repository.add(1, "address", "a", now)
        repository.add(1, "address", "b", now)
        assertEquals(2, repository.pendingTasks(1).size)
    }

    @Test fun `answer consumes only the exact task and cannot be recorded twice`() {
        repeat(3) { repository.add(1, "address", "a$it", now) }
        val item = StudyItem("address", "a0")
        assertTrue(repository.answer(1, item, SessionMode.LEARN, Rating.GOOD, "review1", now, 0.9))
        assertFalse(repository.answer(1, item, SessionMode.LEARN, Rating.GOOD, "review1", now, 0.9))
        assertFalse(repository.answer(1, item, SessionMode.LEARN, Rating.GOOD, "review2", now, 0.9))
        val snapshot = repository.snapshot(1, now)
        assertEquals(2, snapshot.pendingCount)
        assertEquals(3, snapshot.totalAdditions)
        assertEquals(1, snapshot.completedToday)
        assertEquals(1, repository.memory(1, "address")!!.repetitions)
    }

    @Test fun `re-addition preserves existing memory and creates immediate learning task`() {
        repository.add(1, "address", "a", now)
        repository.answer(1, StudyItem("address", "a"), SessionMode.LEARN, Rating.EASY, "r", now, 0.9)
        val before = repository.memory(1, "address")
        repository.add(1, "address", "b", now.plusSeconds(1))
        assertEquals(before, repository.memory(1, "address"))
        assertEquals(1, repository.pendingTasks(1).size)
    }

    @Test fun `failed scheduling rolls back task completion`() {
        repository.add(1, "address", "a", now)
        repository.answer(1, StudyItem("address", "a"), SessionMode.LEARN, Rating.GOOD, "r", now, 0.9)
        repository.add(1, "address", "b", now)
        assertThrows(IllegalArgumentException::class.java) {
            repository.answer(1, StudyItem("address", "b"), SessionMode.LEARN, Rating.GOOD, "bad", now.minusSeconds(10), 0.9)
        }
        assertEquals(1, repository.pendingTasks(1).size)
        assertEquals(1, repository.memory(1, "address")!!.repetitions)
    }

    @Test fun `future or stale reviews are rejected`() {
        repository.add(1, "address", "a", now)
        repository.answer(1, StudyItem("address", "a"), SessionMode.LEARN, Rating.EASY, "r", now, 0.9)
        val card = repository.memory(1, "address")!!
        val item = StudyItem("address", memoryVersion = now)
        assertFalse(repository.answer(1, item, SessionMode.REVIEW, Rating.GOOD, "early", now, 0.9))
        assertTrue(repository.answer(1, item, SessionMode.REVIEW, Rating.GOOD, "due", card.dueAt, 0.9))
        assertFalse(repository.answer(1, item, SessionMode.REVIEW, Rating.GOOD, "stale", card.dueAt, 0.9))
    }

    @Test fun `removing a word does not touch the same word in another book`() {
        val other = repository.createBook("阅读生词")
        repository.add(1, "address", "a", now)
        repository.add(other, "address", "b", now)
        repository.removeWord(1, "address")
        assertTrue(repository.snapshot(1, now).words.isEmpty())
        assertEquals(1, repository.snapshot(other, now).pendingCount)
    }

    @Test fun `counts survive database reopen`() {
        repository.add(1, "address", "a", now)
        helper.close()
        helper = LearningDatabase(ApplicationProvider.getApplicationContext())
        repository = LearningRepository(helper)
        assertEquals(1, repository.snapshot(1, now).pendingCount)
    }

    @Test fun `concurrent additions are not lost`() {
        val workers = Executors.newFixedThreadPool(4)
        try {
            val jobs = (1..40).map { index -> workers.submit { repository.add(1, "address", "parallel-$index", now) } }
            jobs.forEach { it.get() }
            assertEquals(40, repository.snapshot(1, now).pendingCount)
        } finally { workers.shutdownNow() }
    }

    @Test fun `local day boundary counts completed tasks accurately`() {
        repository.add(1, "address", "a", now)
        repository.answer(1, StudyItem("address", "a"), SessionMode.LEARN, Rating.GOOD, "r", now, 0.9)
        assertEquals(1, repository.snapshot(1, now, ZoneId.of("Asia/Hong_Kong")).completedToday)
        assertEquals(0, repository.snapshot(1, Instant.parse("2026-09-17T16:00:00Z"), ZoneId.of("Asia/Hong_Kong")).completedToday)
    }

    @Test fun `reset removes all personal data and recreates only empty default book`() {
        repository.add(1, "address", "a", now)
        repository.createBook("其他")
        repository.reset()
        assertEquals(listOf(WordBook(1, "考研生词本")), repository.books())
        assertTrue(repository.pendingTasks(1).isEmpty())
        assertNull(repository.memory(1, "address"))
    }
}
