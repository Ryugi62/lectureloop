package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.LectureId
import io.github.ryugi62.lectureloop.domain.ReviewScheduler
import io.github.ryugi62.lectureloop.domain.ReviewStep
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewUseCasesTest {
    private val clock = FixedClock()

    private fun lecture(id: String, startedDaysAgo: Long, mastered: Boolean = false): Lecture {
        val start = clock.now() - Duration.ofDays(startedDaysAgo)
        var review = ReviewScheduler.start(start)
        if (mastered) review = review.copy(step = null, dueAt = null)
        return Lecture(LectureId(id), "DSP", start, AUDIO, card(), review)
    }

    @Test fun todayQueueShowsOnlyDueLecturesMostOverdueFirst() = runTest {
        val repo = InMemoryLectures(
            listOf(
                lecture("mastered", startedDaysAgo = 10, mastered = true),
                lecture("due-yesterday", startedDaysAgo = 2),
                lecture("due-tomorrow", startedDaysAgo = 0),
                lecture("due-3-days-ago", startedDaysAgo = 4),
            ),
        )
        val due = TodayQueue(repo, clock).invoke().map { it.id.value }
        assertEquals(listOf("due-3-days-ago", "due-yesterday"), due)
    }

    @Test fun submittingAPassingQuizMovesToTheNextStep() = runTest {
        val repo = InMemoryLectures(listOf(lecture("l1", startedDaysAgo = 1)))
        val answers = card().quiz.map { it.answerIndex }.toMutableList<Int?>()
        answers[0] = 0 // one wrong
        val result = SubmitQuiz(repo, clock).invoke(LectureId("l1"), answers)
        assertEquals(4, result.score)
        assertEquals(true, result.passed)
        assertEquals(ReviewStep.DAY_3, repo.items.getValue(LectureId("l1")).review.step)
        assertEquals(result.nextReviewAt, repo.items.getValue(LectureId("l1")).review.dueAt)
    }

    @Test fun aQuizTakenBeforeItIsDueIsPracticeAndDoesNotMoveTheLoop() = runTest {
        val repo = InMemoryLectures(listOf(lecture("fresh", startedDaysAgo = 0)))
        val before = repo.items.getValue(LectureId("fresh")).review
        val result = SubmitQuiz(repo, clock).invoke(LectureId("fresh"), card().quiz.map { it.answerIndex })
        assertEquals(true, result.practice)
        assertEquals(5, result.score)
        assertEquals(before.dueAt, result.nextReviewAt)
        assertEquals(before, repo.items.getValue(LectureId("fresh")).review)
    }

    @Test fun failingKeepsTheStepAndBringsItBackTomorrow() = runTest {
        val repo = InMemoryLectures(listOf(lecture("l1", startedDaysAgo = 1)))
        val result = SubmitQuiz(repo, clock).invoke(LectureId("l1"), List(5) { null })
        assertEquals(0, result.score)
        assertEquals(false, result.passed)
        assertEquals(clock.now() + Duration.ofDays(1), result.nextReviewAt)
    }

    @Test fun masteringReturnsNoNextReview() = runTest {
        val start = clock.now() - Duration.ofDays(7)
        val atDay7 = ReviewScheduler.start(start).copy(step = ReviewStep.DAY_7, dueAt = clock.now())
        val repo = InMemoryLectures(listOf(Lecture(LectureId("l1"), "DSP", start, AUDIO, card(), atDay7)))
        val result = SubmitQuiz(repo, clock).invoke(LectureId("l1"), card().quiz.map { it.answerIndex })
        assertNull(result.nextReviewAt)
        assertEquals(true, result.mastered)
    }
}
