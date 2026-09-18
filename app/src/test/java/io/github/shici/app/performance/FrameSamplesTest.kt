package io.github.shici.app.performance

import org.junit.Assert.*
import org.junit.Test

class FrameSamplesTest {
    @Test fun `per frame deadlines track variable refresh without treating lost callbacks as jank`() {
        val samples = FrameSamples()
        samples.add(7_000_000, 8_333_333, false, 0)
        samples.add(12_000_000, 16_666_667, false, 2)
        samples.add(10_000_000, 8_333_333, false, 0)
        samples.add(90_000_000, 8_333_333, true, 0)
        val summary = samples.summary()
        assertEquals(3, summary.frames)
        assertEquals(1, summary.missedDeadline)
        assertEquals(2, summary.lostCallbacks)
        assertEquals(1, summary.excludedFirstDraws)
        assertEquals(12.0, summary.p95Millis, 0.0)
    }

    @Test fun `missing deadlines do not manufacture a success rate`() {
        val samples = FrameSamples()
        samples.add(5_000_000, -1, false, 0)
        samples.add(-1, 8_000_000, false, 0)
        assertEquals(1, samples.summary().frames)
        assertEquals(0, samples.summary().withDeadline)
    }

    @Test fun `bounded capture reports truncation and does not change its percentile population`() {
        val samples = FrameSamples(2)
        samples.add(1_000_000, 2_000_000, false, 0)
        samples.add(2_000_000, 2_000_000, false, 0)
        samples.add(1_000_000_000, 2_000_000, false, 0)
        assertTrue(samples.summary().capacityReached)
        assertEquals(2, samples.summary().frames)
        assertEquals(2.0, samples.summary().maxMillis, 0.0)
        assertEquals(1, samples.summary().missedDeadline)
    }
}
