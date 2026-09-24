package io.github.ryugi62.lectureloop.domain

import java.time.Duration
import java.time.Instant

/** Days after the lecture on which the quiz comes back. */
enum class ReviewStep(val dayOffset: Long) { DAY_1(1), DAY_3(3), DAY_7(7) }

data class ReviewAttempt(val at: Instant, val step: ReviewStep, val score: Int)

/** Where a lecture is in the loop. `step == null` means mastered. */
data class ReviewState(
    val startedAt: Instant,
    val step: ReviewStep?,
    val dueAt: Instant?,
    val attempts: List<ReviewAttempt> = emptyList(),
) {
    val mastered: Boolean get() = step == null

    fun isDue(now: Instant): Boolean = dueAt != null && !dueAt.isAfter(now)
}

/** Day 1 → 3 → 7 spaced review. Pass (≥ [QuizGrader.PASS_SCORE]) moves on; fail repeats tomorrow. */
object ReviewScheduler {
    private val ONE_DAY: Duration = Duration.ofDays(1)

    fun start(lectureAt: Instant): ReviewState =
        ReviewState(startedAt = lectureAt, step = ReviewStep.DAY_1, dueAt = lectureAt + ONE_DAY)

    fun record(state: ReviewState, score: Int, at: Instant): ReviewState {
        val step = state.step ?: return state
        val attempts = state.attempts + ReviewAttempt(at, step, score)
        if (score < QuizGrader.PASS_SCORE) {
            return state.copy(dueAt = at + ONE_DAY, attempts = attempts)
        }
        val next = ReviewStep.entries.getOrNull(step.ordinal + 1)
            ?: return state.copy(step = null, dueAt = null, attempts = attempts)
        val onSchedule = state.startedAt + Duration.ofDays(next.dayOffset)
        val keepGap = at + Duration.ofDays(next.dayOffset - step.dayOffset)
        return state.copy(step = next, dueAt = maxOf(onSchedule, keepGap), attempts = attempts)
    }
}
