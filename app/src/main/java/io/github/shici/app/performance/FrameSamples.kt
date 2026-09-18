package io.github.shici.app.performance

import kotlin.math.ceil

internal data class FrameSummary(val frames: Int, val withDeadline: Int, val missedDeadline: Int,
                                 val p95Millis: Double, val maxMillis: Double, val lostCallbacks: Int,
                                 val excludedFirstDraws: Int, val capacityReached: Boolean)

/** Bounded memory, no allocations per frame. Sorting only occurs after capture has stopped. */
internal class FrameSamples(private val capacity: Int = 20_000) {
    private val durations = LongArray(capacity)
    private var count = 0
    private var withDeadline = 0
    private var missed = 0
    private var lost = 0
    private var firstDraws = 0
    private var capped = false

    fun add(duration: Long, deadline: Long, firstDraw: Boolean, lostCallbacks: Int) {
        lost += lostCallbacks.coerceAtLeast(0)
        if (firstDraw) { firstDraws++; return }
        if (duration < 0) return
        if (count == capacity) { capped = true; return }
        durations[count++] = duration
        if (deadline > 0) {
            withDeadline++
            if (duration >= deadline) missed++
        }
    }

    fun summary(): FrameSummary {
        val sorted = durations.copyOf(count).apply { sort() }
        return FrameSummary(count, withDeadline, missed,
            if (count == 0) 0.0 else sorted[(ceil(count * .95).toInt() - 1).coerceAtLeast(0)] / 1_000_000.0,
            (sorted.lastOrNull() ?: 0) / 1_000_000.0, lost, firstDraws, capped)
    }
}
