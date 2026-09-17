package io.github.shici.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FsrsSchedulerTest {
    private val now = Instant.parse("2026-09-17T08:00:00Z")
    private val scheduler = FsrsScheduler()

    @Test fun `new cards receive explicit short learning steps`() {
        val intervals = Rating.entries.map { Duration.between(now, scheduler.review(null, it, now).dueAt).seconds }
        assertEquals(listOf(60L, 330L, 600L, 8 * 86400L), intervals)
        assertEquals(2.3065, scheduler.review(null, Rating.GOOD, now).stability, 0.000001)
    }

    @Test fun `two successful recalls graduate to a review card`() {
        val initial = scheduler.review(null, Rating.GOOD, now)
        val reviewed = scheduler.review(initial, Rating.GOOD, initial.dueAt)
        assertEquals(Phase.REVIEW, reviewed.phase)
        assertTrue(reviewed.dueAt.isAfter(initial.dueAt.plusSeconds(86399)))
        assertEquals(2, reviewed.repetitions)
    }

    @Test fun `lapse is preserved and relearning is due in ten minutes`() {
        val initial = scheduler.review(null, Rating.EASY, now)
        val forgotten = scheduler.review(initial, Rating.AGAIN, initial.dueAt)
        assertEquals(Phase.RELEARNING, forgotten.phase)
        assertEquals(1, forgotten.lapses)
        assertEquals(600L, Duration.between(initial.dueAt, forgotten.dueAt).seconds)
        assertTrue(forgotten.stability < initial.stability)
    }

    @Test fun `higher retention shortens intervals and all grades stay finite`() {
        val card = scheduler.review(null, Rating.EASY, now)
        for (grade in Rating.entries) {
            val ordinary = scheduler.review(card, grade, card.dueAt)
            val conservative = FsrsScheduler(0.95).review(card, grade, card.dueAt)
            assertFalse(conservative.dueAt.isAfter(ordinary.dueAt))
            assertTrue(ordinary.difficulty in 1.0..10.0)
            assertTrue(ordinary.stability.isFinite())
        }
    }

    @Test fun `remembered same-day responses do not decrease stability`() {
        val card = scheduler.review(null, Rating.EASY, now)
        for (grade in listOf(Rating.HARD, Rating.GOOD, Rating.EASY)) {
            assertTrue(scheduler.review(card, grade, now.plusSeconds(60)).stability >= card.stability)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `clock rollback must not silently damage history`() {
        scheduler.review(scheduler.review(null, Rating.GOOD, now), Rating.GOOD, now.minusSeconds(1))
    }

    @Test fun `long schedules remain bounded across hundreds of reviews`() {
        var card = scheduler.review(null, Rating.GOOD, now)
        repeat(500) { index ->
            card = scheduler.review(card, Rating.entries[index % 4], card.dueAt)
            assertTrue(card.dueAt > card.lastReviewedAt)
            assertTrue(Duration.between(card.lastReviewedAt, card.dueAt).toDays() <= 36500)
        }
    }

    @Test fun `queue prioritizes re-additions without bringing forward future cards`() {
        val state = scheduler.review(null, Rating.EASY, now)
        fun word(name: String, count: Int, pending: Int = 0, due: Instant = now) =
            BookWord(name, count, pending, now, state.copy(dueAt = due))
        val words = listOf(word("low", 1), word("high", 5), word("future", 99, due = now.plusSeconds(1)),
            word("pending", 10, pending = 1), word("older", 5, due = now.minusSeconds(1)))
        assertEquals(listOf("older", "high", "low"), reviewQueue(words, now).map { it.word })
    }

    @Test fun `unattributed frequency never outranks verified sense statistics`() {
        val entry = WordEntry("test", "", listOf(Sense("a", "A", 999), Sense("b", "B", 3, "2000–2025 英语一")))
        assertEquals("b", entry.examSenses().first().id)
        assertTrue(entry.hasExamStatistics)
        assertEquals("address", normalizeWord("  ADDRESS  "))
    }
}
