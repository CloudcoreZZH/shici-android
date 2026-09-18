package io.github.shici.core

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Review dates follow the local calendar, including 23/25-hour daylight-saving days. */
object StudyDays {
    fun between(from: Instant, to: Instant, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(from.atZone(zone).toLocalDate(), to.atZone(zone).toLocalDate())

    fun dueAfter(now: Instant, days: Long, zone: ZoneId): Instant {
        require(days in 1..36500)
        return now.atZone(zone).toLocalDate().plusDays(days).atStartOfDay(zone).toInstant()
    }

    /** Preserve old scheduled dates, but move same-day steps to the next calendar day. */
    fun migrateDue(reviewedAt: Instant, dueAt: Instant, zone: ZoneId): Instant =
        dueAfter(reviewedAt, between(reviewedAt, dueAt, zone).coerceIn(1, 36500), zone)
}
