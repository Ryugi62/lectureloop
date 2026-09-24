package io.github.ryugi62.lectureloop.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewCardRulesTest {
    private val audioSeconds = 148

    @Test fun validCardHasNoViolations() {
        assertEquals(emptyList(), ReviewCardRules.check(validCard(), audioSeconds))
    }

    @Test fun quizMustHaveExactlyFiveItems() {
        val card = validCard().copy(quiz = validCard().quiz.take(4))
        assertEquals(listOf<Violation>(Violation.QuizSize(4)), ReviewCardRules.check(card, audioSeconds))
    }

    @Test fun conceptsMustBeThreeToFive() {
        val two = validCard().copy(concepts = validCard().concepts.take(2))
        val six = validCard().copy(concepts = List(6) { validCard().concepts[0] })
        assertEquals(listOf<Violation>(Violation.ConceptCount(2)), ReviewCardRules.check(two, audioSeconds))
        assertEquals(listOf<Violation>(Violation.ConceptCount(6)), ReviewCardRules.check(six, audioSeconds))
    }

    @Test fun everyQuizItemNeedsFourChoicesAndAnAnswerInRange() {
        val quiz = validCard().quiz.toMutableList()
        quiz[1] = quiz[1].copy(choices = listOf("A", "B", "C"))
        quiz[2] = quiz[2].copy(answerIndex = 4)
        val violations = ReviewCardRules.check(validCard().copy(quiz = quiz), audioSeconds)
        assertEquals(listOf(Violation.ChoiceCount(item = 2, actual = 3), Violation.AnswerOutOfRange(item = 3, index = 4)), violations)
    }

    @Test fun textMayNotBeBlank() {
        val points = listOf(validCard().examPoints[0].copy(cue = "  "))
        val violations = ReviewCardRules.check(validCard().copy(examPoints = points), audioSeconds)
        assertEquals(listOf<Violation>(Violation.BlankText("exam point 1 cue")), violations)
    }

    @Test fun timestampsMustFallInsideTheRecording() {
        val todos = listOf(validCard().todos[0].copy(at = ts("02:29")))
        val violations = ReviewCardRules.check(validCard().copy(todos = todos), audioSeconds)
        assertEquals(listOf<Violation>(Violation.TimestampBeyondAudio("to-do 1", ts("02:29"), audioSeconds)), violations)
    }

    @Test fun noExamPointsIsAllowed_weNeverInventThem() {
        assertEquals(emptyList(), ReviewCardRules.check(validCard().copy(examPoints = emptyList()), audioSeconds))
    }

    @Test fun everyViolationExplainsItselfForTheRetryPrompt() {
        val all = listOf(
            Violation.QuizSize(4), Violation.ConceptCount(2), Violation.ChoiceCount(1, 3),
            Violation.AnswerOutOfRange(1, 4), Violation.BlankText("title"),
            Violation.TimestampBeyondAudio("quiz 1", ts("03:00"), 148),
        )
        all.forEach { assertTrue(it.message.length > 10, "message for $it") }
    }
}
