package io.github.shici.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FsrsSchedulerTest {
    private val now = Instant.parse("2026-09-17T08:00:00Z")
    private val zone = ZoneId.of("Asia/Hong_Kong")
    private val scheduler = FsrsScheduler(zone = zone)

    @Test fun `new cards use FSRS initial stability with whole day intervals`() {
        val intervals = Rating.entries.map { StudyDays.between(now, scheduler.review(null, it, now).dueAt, zone) }
        assertEquals(listOf(1L, 1L, 2L, 8L), intervals)
        assertEquals(2.3065, scheduler.review(null, Rating.GOOD, now).stability, 0.000001)
    }

    @Test fun `new learning uses daily review directly without minute steps`() {
        val initial = scheduler.review(null, Rating.GOOD, now)
        val reviewed = scheduler.review(initial, Rating.GOOD, initial.dueAt)
        assertEquals(Phase.REVIEW, reviewed.phase)
        assertEquals(Phase.REVIEW, initial.phase)
        assertTrue(StudyDays.between(initial.dueAt, reviewed.dueAt, zone) >= 1)
        assertEquals(2, reviewed.repetitions)
    }

    @Test fun `lapse is preserved and forgetting is scheduled for a future day`() {
        val initial = scheduler.review(null, Rating.EASY, now)
        val forgotten = scheduler.review(initial, Rating.AGAIN, initial.dueAt)
        assertEquals(Phase.REVIEW, forgotten.phase)
        assertEquals(1, forgotten.lapses)
        assertTrue(StudyDays.between(initial.dueAt, forgotten.dueAt, zone) >= 1)
        assertEquals(0, forgotten.dueAt.atZone(zone).hour)
        assertTrue(forgotten.stability < initial.stability)
    }

    @Test fun `higher retention shortens intervals and all grades stay finite`() {
        val card = scheduler.review(null, Rating.EASY, now)
        for (grade in Rating.entries) {
            val ordinary = scheduler.review(card, grade, card.dueAt)
            val conservative = FsrsScheduler(0.95, zone).review(card, grade, card.dueAt)
            assertFalse(conservative.dueAt.isAfter(ordinary.dueAt))
            assertTrue(ordinary.difficulty in 1.0..10.0)
            assertTrue(ordinary.stability.isFinite())
        }
    }

    @Test fun `near midnight review is available throughout its due date`() {
        val late = Instant.parse("2026-09-17T15:59:30Z")
        assertEquals(Instant.parse("2026-09-17T16:00:00Z"), scheduler.review(null, Rating.AGAIN, late).dueAt)
        assertEquals(Instant.parse("2026-09-18T16:00:00Z"), scheduler.review(null, Rating.GOOD, late).dueAt)
    }

    @Test fun `elapsed time follows calendar days even after a short night`() {
        val card = scheduler.review(null, Rating.GOOD, Instant.parse("2026-09-17T15:59:00Z"))
        val morning = Instant.parse("2026-09-18T00:00:00Z")
        assertTrue(scheduler.retrievability(card, morning) < 1.0)
    }

    @Test fun `all grades at both retention extremes schedule valid future dates`() {
        for (retention in listOf(0.7, 0.9, 0.97)) {
            val algorithm = FsrsScheduler(retention, zone)
            for (rating in Rating.entries) {
                val card = algorithm.review(null, rating, now)
                assertTrue(StudyDays.between(now, card.dueAt, zone) >= 1)
                assertEquals(0, card.dueAt.atZone(zone).hour)
                assertTrue(card.dueAt.isAfter(now))
            }
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
