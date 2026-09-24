package io.github.ryugi62.lectureloop.domain

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewSchedulerTest {
    private val monday10 = Instant.parse("2026-09-21T01:00:00Z") // Mon 10:00 KST
    private fun days(n: Long) = Duration.ofDays(n)

    @Test fun firstReviewIsTheNextDay() {
        val state = ReviewScheduler.start(monday10)
        assertEquals(ReviewStep.DAY_1, state.step)
        assertEquals(monday10 + days(1), state.dueAt)
    }

    @Test fun passingWalksDayOneThreeSevenThenMastered() {
        var state = ReviewScheduler.start(monday10)
        state = ReviewScheduler.record(state, score = 5, at = monday10 + days(1))
        assertEquals(ReviewStep.DAY_3, state.step)
        assertEquals(monday10 + days(3), state.dueAt)
        state = ReviewScheduler.record(state, score = 4, at = monday10 + days(3))
        assertEquals(ReviewStep.DAY_7, state.step)
        assertEquals(monday10 + days(7), state.dueAt)
        state = ReviewScheduler.record(state, score = 4, at = monday10 + days(7))
        assertNull(state.step)
        assertNull(state.dueAt)
        assertTrue(state.mastered)
        assertEquals(3, state.attempts.size)
    }

    @Test fun failingRepeatsTheSameStepTomorrow() {
        val start = ReviewScheduler.start(monday10)
        val state = ReviewScheduler.record(start, score = 3, at = monday10 + days(1))
        assertEquals(ReviewStep.DAY_1, state.step)
        assertEquals(monday10 + days(2), state.dueAt)
    }

    @Test fun aLatePassKeepsTheGapInsteadOfBunchingReviews() {
        val start = ReviewScheduler.start(monday10)
        val late = monday10 + days(2) // one day late for the day-1 review
        val state = ReviewScheduler.record(start, score = 5, at = late)
        assertEquals(ReviewStep.DAY_3, state.step)
        assertEquals(late + days(2), state.dueAt) // gap between day 1 and day 3 is 2 days
    }

    @Test fun dueMeansAtOrBeforeNow() {
        val state = ReviewScheduler.start(monday10)
        assertTrue(state.isDue(monday10 + days(1)))
        assertTrue(!state.isDue(monday10 + days(1) - Duration.ofMinutes(1)))
    }
}
