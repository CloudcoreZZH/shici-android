package io.github.shici.core

import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * FSRS-6, deterministic (fuzz disabled). Formula reference and default coefficients:
 * https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm
 * State transitions match py-fsrs: 1m/10m initial learning, 10m relearning.
 * No wall clock or Android dependency: callers supply an Instant, enabling reproducible tests.
 */
class FsrsScheduler(private val retention: Double = 0.9) {
    init { require(retention.isFinite() && retention in 0.70..0.97) }
    private val w = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194,
        0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629,
        1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )
    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    fun retrievability(state: MemoryState, now: Instant): Double {
        val days = Duration.between(state.lastReviewedAt, now).toDays().coerceAtLeast(0)
        return (1 + factor * days / state.stability).pow(decay)
    }

    fun review(previous: MemoryState?, rating: Rating, now: Instant): MemoryState {
        require(previous == null || !now.isBefore(previous.lastReviewedAt)) {
            "设备时间早于上次学习时间，请检查系统时间后重试。"
        }
        val stability = when {
            previous == null -> w[rating.value - 1]
            Duration.between(previous.lastReviewedAt, now).toDays() < 1 -> {
                val increase = exp(w[17] * (rating.value - 3 + w[18])) * previous.stability.pow(-w[19])
                previous.stability * if (rating == Rating.AGAIN) increase else max(increase, 1.0)
            }
            rating == Rating.AGAIN -> {
                val s = previous.stability
                min(w[11] * previous.difficulty.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) *
                    exp((1 - retrievability(previous, now)) * w[14]), s / exp(w[17] * w[18]))
            }
            else -> {
                val hard = if (rating == Rating.HARD) w[15] else 1.0
                val easy = if (rating == Rating.EASY) w[16] else 1.0
                previous.stability * (1 + exp(w[8]) * (11 - previous.difficulty) *
                    previous.stability.pow(-w[9]) * (exp((1 - retrievability(previous, now)) * w[10]) - 1) * hard * easy)
            }
        }.coerceAtLeast(0.001)
        val difficulty = if (previous == null) initialDifficulty(rating) else {
            val delta = -w[6] * (rating.value - 3)
            w[7] * initialDifficulty(Rating.EASY) +
                (1 - w[7]) * (previous.difficulty + delta * (10 - previous.difficulty) / 9)
        }
        val phase = previous?.phase ?: Phase.LEARNING
        val step = previous?.step ?: 0
        val (nextPhase, nextStep, interval) = nextInterval(phase, step, rating, stability)
        return MemoryState(
            stability, difficulty.coerceIn(1.0, 10.0), now, now.plus(interval), nextPhase, nextStep,
            repetitions = (previous?.repetitions ?: 0) + 1,
            lapses = (previous?.lapses ?: 0) + if (phase == Phase.REVIEW && rating == Rating.AGAIN) 1 else 0,
        )
    }

    private fun initialDifficulty(rating: Rating) = w[4] - exp(w[5] * (rating.value - 1)) + 1

    private fun longInterval(stability: Double): Duration {
        val days = round(stability / factor * (retention.pow(1 / decay) - 1)).toLong().coerceIn(1, 36500)
        return Duration.ofDays(days)
    }

    private fun nextInterval(phase: Phase, step: Int, rating: Rating, stability: Double): Triple<Phase, Int, Duration> {
        if (phase == Phase.REVIEW) {
            return if (rating == Rating.AGAIN) Triple(Phase.RELEARNING, 0, Duration.ofMinutes(10))
            else Triple(Phase.REVIEW, 0, longInterval(stability))
        }
        val steps = if (phase == Phase.LEARNING) listOf(60L, 600L) else listOf(600L)
        val safeStep = step.coerceAtMost(steps.lastIndex)
        return when (rating) {
            Rating.AGAIN -> Triple(phase, 0, Duration.ofSeconds(steps[0]))
            Rating.HARD -> Triple(phase, safeStep, Duration.ofSeconds(
                if (safeStep != 0) steps[safeStep]
                else if (steps.size == 1) steps[0] * 3 / 2 else (steps[0] + steps[1]) / 2
            ))
            Rating.GOOD -> if (safeStep == steps.lastIndex) Triple(Phase.REVIEW, 0, longInterval(stability))
                else Triple(phase, safeStep + 1, Duration.ofSeconds(steps[safeStep + 1]))
            Rating.EASY -> Triple(Phase.REVIEW, 0, longInterval(stability))
        }
    }
}
