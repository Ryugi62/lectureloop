package io.github.ryugi62.lectureloop.domain

internal fun ts(label: String) = Timestamp.parse(label) ?: error("bad test timestamp $label")

internal fun quizItem(n: Int, at: String = "00:10") = QuizItem(
    question = "Question $n?",
    choices = listOf("A", "B", "C", "D"),
    answerIndex = n % 4,
    explanation = "Because $n.",
    at = ts(at),
)

internal fun validCard() = ReviewCard(
    title = "Sampling and aliasing",
    summary = "How fast to sample and what goes wrong when you do not.",
    concepts = listOf(
        Concept("Sampling rate", "fs is one over T.", ts("00:15")),
        Concept("Nyquist rate", "Twice the highest frequency.", ts("00:34")),
        Concept("Aliasing", "Too-slow sampling folds a tone down.", ts("01:09")),
    ),
    examPoints = listOf(
        ExamPoint("Sample faster than twice the highest frequency.", "this will be on the midterm", ts("00:38")),
        ExamPoint("Compute the alias frequency.", "Practice that calculation.", ts("01:56")),
    ),
    quiz = (1..5).map { quizItem(it) },
    todos = listOf(Todo("Homework 3: problems 4.2, 4.5, 4.8", "next Monday before class", ts("02:13"))),
)
