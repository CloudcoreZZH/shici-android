package io.github.shici.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeLabelsTest {
    private val zone = ZoneId.of("Asia/Hong_Kong")
    private val now = Instant.parse("2026-09-18T15:55:00Z")

    @Test fun `next date is one day even when midnight is minutes away`() {
        val due = Instant.parse("2026-09-18T16:00:00Z")
        assertEquals("1 天", intervalLabel(now, due, zone))
        assertEquals("明天", dueLabel(now, due, zone))
        assertEquals("今天可以复习", dueLabel(due, due, zone))
    }

    @Test fun `dates omit clock times and disambiguate future years`() {
        assertEquals("后天", dueLabel(now, Instant.parse("2026-09-19T16:00:00Z"), zone))
        assertEquals("10月1日", dueLabel(now, Instant.parse("2026-09-30T16:00:00Z"), zone))
        assertEquals("2027年1月1日", dueLabel(now, Instant.parse("2026-12-31T16:00:00Z"), zone))
    }
}
