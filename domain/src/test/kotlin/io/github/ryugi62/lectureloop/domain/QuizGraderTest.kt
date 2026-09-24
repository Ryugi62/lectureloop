package io.github.ryugi62.lectureloop.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuizGraderTest {
    private val quiz = validCard().quiz

    @Test fun countsCorrectAnswers() {
        val answers = quiz.map { it.answerIndex }.toMutableList<Int?>()
        answers[0] = (answers[0]!! + 1) % 4
        assertEquals(4, QuizGrader.grade(quiz, answers))
    }

    @Test fun unansweredCountsAsWrong() {
        val answers = quiz.map<QuizItem, Int?> { it.answerIndex }.toMutableList()
        answers[4] = null
        assertEquals(4, QuizGrader.grade(quiz, answers))
    }

    @Test fun answerListMustMatchTheQuiz() {
        assertFailsWith<IllegalArgumentException> { QuizGrader.grade(quiz, listOf(0, 1)) }
    }

    @Test fun passMarkIsFourOutOfFive() {
        assertEquals(4, QuizGrader.PASS_SCORE)
    }
}
