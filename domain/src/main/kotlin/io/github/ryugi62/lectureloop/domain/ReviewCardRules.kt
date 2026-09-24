package io.github.ryugi62.lectureloop.domain

/** Why a generated review card is rejected. [message] is written for the model's retry prompt. */
sealed interface Violation {
    val message: String

    data class QuizSize(val actual: Int) : Violation {
        override val message get() = "The quiz must have exactly ${ReviewCardRules.QUIZ_SIZE} questions, not $actual."
    }

    data class ConceptCount(val actual: Int) : Violation {
        override val message get() =
            "There must be ${ReviewCardRules.CONCEPTS_MIN} to ${ReviewCardRules.CONCEPTS_MAX} concepts, not $actual."
    }

    data class ChoiceCount(val item: Int, val actual: Int) : Violation {
        override val message get() = "Quiz question $item must have exactly ${ReviewCardRules.CHOICES} choices, not $actual."
    }

    data class AnswerOutOfRange(val item: Int, val index: Int) : Violation {
        override val message get() = "Quiz question $item has answerIndex $index; it must be between 0 and ${ReviewCardRules.CHOICES - 1}."
    }

    data class BlankText(val where: String) : Violation {
        override val message get() = "The $where is empty; write it from what the lecturer said."
    }

    data class TimestampBeyondAudio(val where: String, val at: Timestamp, val audioSeconds: Int) : Violation {
        override val message get() =
            "The $where points to ${at.label}, but the recording is only ${Timestamp(audioSeconds).label} long."
    }
}

/** Acceptance rules for a review card. Pure: same card and length, same answer. */
object ReviewCardRules {
    const val QUIZ_SIZE = 5
    const val CHOICES = 4
    const val CONCEPTS_MIN = 3
    const val CONCEPTS_MAX = 5

    fun check(card: ReviewCard, audioSeconds: Int): List<Violation> {
        val out = mutableListOf<Violation>()
        fun text(value: String, where: String) {
            if (value.isBlank()) out += Violation.BlankText(where)
        }
        fun time(at: Timestamp, where: String) {
            if (at.seconds > audioSeconds) out += Violation.TimestampBeyondAudio(where, at, audioSeconds)
        }

        text(card.title, "title")
        text(card.summary, "summary")
        if (card.concepts.size !in CONCEPTS_MIN..CONCEPTS_MAX) out += Violation.ConceptCount(card.concepts.size)
        card.concepts.forEachIndexed { i, c ->
            text(c.name, "concept ${i + 1} name")
            text(c.explanation, "concept ${i + 1} explanation")
            time(c.at, "concept ${i + 1}")
        }
        card.examPoints.forEachIndexed { i, p ->
            text(p.point, "exam point ${i + 1}")
            text(p.cue, "exam point ${i + 1} cue")
            time(p.at, "exam point ${i + 1}")
        }
        if (card.quiz.size != QUIZ_SIZE) out += Violation.QuizSize(card.quiz.size)
        card.quiz.forEachIndexed { i, q ->
            val n = i + 1
            text(q.question, "quiz $n question")
            if (q.choices.size != CHOICES) out += Violation.ChoiceCount(n, q.choices.size)
            if (q.answerIndex !in 0 until CHOICES) out += Violation.AnswerOutOfRange(n, q.answerIndex)
            q.choices.forEachIndexed { j, choice -> text(choice, "quiz $n choice ${j + 1}") }
            time(q.at, "quiz $n")
        }
        card.todos.forEachIndexed { i, t ->
            text(t.task, "to-do ${i + 1}")
            time(t.at, "to-do ${i + 1}")
        }
        return out
    }
}
