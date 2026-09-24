package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.LectureId
import io.github.ryugi62.lectureloop.domain.QuizGrader
import io.github.ryugi62.lectureloop.domain.ReviewScheduler
import io.github.ryugi62.lectureloop.domain.ReviewStep
import java.time.Instant

/** UC-2: lectures whose quiz is due now, most overdue first. */
class TodayQueue(private val lectures: LectureRepository, private val clock: Clock) {
    suspend operator fun invoke(): List<Lecture> {
        val now = clock.now()
        return lectures.all().filter { it.review.isDue(now) }.sortedBy { it.review.dueAt }
    }
}

data class QuizResult(
    val score: Int,
    val outOf: Int,
    val passed: Boolean,
    val completedStep: ReviewStep,
    val nextReviewAt: Instant?,
    val mastered: Boolean,
    /** Taken before it was due (e.g. right after class): shown, but the loop does not move. */
    val practice: Boolean = false,
)

/** UC-3: grade the quiz and move the lecture along the loop. */
class SubmitQuiz(private val lectures: LectureRepository, private val clock: Clock) {
    suspend operator fun invoke(id: LectureId, answers: List<Int?>): QuizResult {
        val lecture = lectures.get(id) ?: throw NoSuchElementException("No lecture ${id.value}")
        val score = QuizGrader.grade(lecture.card.quiz, answers)
        val step = lecture.review.step ?: ReviewStep.DAY_7
        val now = clock.now()
        if (!lecture.review.mastered && !lecture.review.isDue(now)) {
            return QuizResult(score, lecture.card.quiz.size, score >= QuizGrader.PASS_SCORE, step, lecture.review.dueAt, mastered = false, practice = true)
        }
        val next = if (lecture.review.mastered) lecture.review else ReviewScheduler.record(lecture.review, score, now)
        lectures.save(lecture.copy(review = next))
        return QuizResult(
            score = score,
            outOf = lecture.card.quiz.size,
            passed = score >= QuizGrader.PASS_SCORE,
            completedStep = step,
            nextReviewAt = next.dueAt,
            mastered = next.mastered,
        )
    }
}
