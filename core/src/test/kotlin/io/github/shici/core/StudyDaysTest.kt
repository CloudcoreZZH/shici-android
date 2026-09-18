package io.github.shici.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class StudyDaysTest {
    private val hongKong = ZoneId.of("Asia/Hong_Kong")

    @Test fun `calendar days cross year and leap day correctly`() {
        assertEquals(Instant.parse("2027-01-01T16:00:00Z"), StudyDays.dueAfter(Instant.parse("2026-12-31T14:00:00Z"), 2, hongKong))
        assertEquals(Instant.parse("2028-02-29T16:00:00Z"), StudyDays.dueAfter(Instant.parse("2028-02-28T14:00:00Z"), 2, hongKong))
    }

    @Test fun `daylight saving spring and autumn keep local dates instead of 24 hour blocks`() {
        val zone = ZoneId.of("America/New_York")
        val spring = Instant.parse("2026-03-08T05:00:00Z")
        val autumn = Instant.parse("2026-11-01T04:00:00Z")
        assertEquals(23, Duration.between(spring, StudyDays.dueAfter(spring, 1, zone)).toHours().toInt())
        assertEquals(25, Duration.between(autumn, StudyDays.dueAfter(autumn, 1, zone)).toHours().toInt())
        assertEquals(1, StudyDays.between(spring, StudyDays.dueAfter(spring, 1, zone), zone).toInt())
    }

    @Test fun `legacy minute schedule moves forward and overdue history stays overdue`() {
        val reviewed = Instant.parse("2026-09-17T08:00:00Z")
        val migrated = StudyDays.migrateDue(reviewed, reviewed.plusSeconds(60), hongKong)
        assertEquals(Instant.parse("2026-09-17T16:00:00Z"), migrated)
        assertTrue(migrated.isBefore(Instant.parse("2026-09-19T00:00:00Z")))
        assertEquals(Instant.parse("2026-09-24T16:00:00Z"), StudyDays.migrateDue(reviewed, reviewed.plusSeconds(8 * 86400), hongKong))
    }
}
