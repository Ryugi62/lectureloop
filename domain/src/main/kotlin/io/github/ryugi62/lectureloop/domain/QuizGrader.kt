package io.github.ryugi62.lectureloop.domain

object QuizGrader {
    /** 4 of 5 correct moves the lecture to the next review step. */
    const val PASS_SCORE = 4

    fun grade(quiz: List<QuizItem>, answers: List<Int?>): Int {
        require(answers.size == quiz.size) { "Expected ${quiz.size} answers, got ${answers.size}" }
        return quiz.zip(answers).count { (item, answer) -> answer == item.answerIndex }
    }
}
