package io.github.ryugi62.lectureloop.domain

/** Something the lecturer signalled will be tested, with the lecturer's own words as [cue]. */
data class ExamPoint(val point: String, val cue: String, val at: Timestamp)

/** One core idea explained in one or two plain sentences. */
data class Concept(val name: String, val explanation: String, val at: Timestamp)

/** A 4-choice question; [answerIndex] is 0-based. */
data class QuizItem(
    val question: String,
    val choices: List<String>,
    val answerIndex: Int,
    val explanation: String,
    val at: Timestamp,
)

/** Reading or homework the lecturer assigned; [due] is copied as spoken ("next Monday before class"). */
data class Todo(val task: String, val due: String?, val at: Timestamp)

/** What the student studies right after class. */
data class ReviewCard(
    val title: String,
    val summary: String,
    val concepts: List<Concept>,
    val examPoints: List<ExamPoint>,
    val quiz: List<QuizItem>,
    val todos: List<Todo>,
)
