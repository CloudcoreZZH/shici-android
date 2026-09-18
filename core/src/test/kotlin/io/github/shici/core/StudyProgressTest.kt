package io.github.shici.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StudyProgressTest {
    private val now = Instant.parse("2026-09-18T08:00:00Z")

    @Test fun `group reserves one task per word without destroying repeated additions`() {
        val tasks = listOf(LearningTask("a1", 1, "address", now), LearningTask("a2", 1, "address", now.plusSeconds(1)),
            LearningTask("b1", 1, "claim", now.plusSeconds(2)))
        assertEquals(listOf("a1", "b1"), learningGroup(tasks, 10, now).map { it.taskId })
        assertEquals(3, tasks.size)
        assertEquals(listOf("a2", "b1"), learningGroup(tasks.drop(1), 10, now).map { it.taskId })
    }

    @Test fun `failed learning is deferred and not counted as completed`() {
        val progress = StudyProgress("s", 1, SessionMode.LEARN, listOf(StudyItem("address", "a"), StudyItem("claim", "b")), 2, startedAt = now)
        val memory = FsrsScheduler().review(null, Rating.AGAIN, now)
        val next = progress.afterAnswer(Rating.AGAIN, memory, now, "r")
        assertEquals(0, next.completed)
        assertEquals(1, next.forgotten)
        assertEquals("claim", next.items.first().word)
        assertEquals(now.plusSeconds(60), next.items.last().availableAt)
        assertFalse(next.finished)
    }

    @Test fun `a deferred task cannot be answered before its scheduled time`() {
        val memory = FsrsScheduler().review(null, Rating.AGAIN, now)
        val progress = StudyProgress("s", 1, SessionMode.LEARN, listOf(StudyItem("address", "a", availableAt = memory.dueAt)), 1, startedAt = now)
        assertThrows(IllegalArgumentException::class.java) { progress.afterAnswer(Rating.GOOD, memory, now, "r") }
        assertTrue(progress.afterAnswer(Rating.GOOD, memory, memory.dueAt, "r").finished)
    }

    @Test fun `review group counts an honest failure without claiming mastery`() {
        val progress = StudyProgress("s", 1, SessionMode.REVIEW, listOf(StudyItem("address")), 1, startedAt = now)
        val next = progress.afterAnswer(Rating.AGAIN, FsrsScheduler().review(null, Rating.AGAIN, now), now, "r")
        assertTrue(next.finished)
        assertEquals(1, next.completed)
        assertEquals(1, next.forgotten)
        assertEquals(0, next.answers - next.forgotten)
    }

    @Test fun `editorial priorities do not fabricate statistics or overwrite the full dictionary`() {
        val original = listOf(Sense("o", "n. 地址"))
        val editorial = listOf(Sense("e", "v. 处理"))
        val entry = WordEntry("address", "", original, editorialSenses = editorial)
        assertFalse(entry.hasExamStatistics)
        assertEquals(editorial, entry.learningSenses())
        assertEquals(original, entry.senses)
    }
}
