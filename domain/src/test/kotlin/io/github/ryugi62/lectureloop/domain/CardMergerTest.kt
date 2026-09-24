package io.github.ryugi62.lectureloop.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Long lectures are analysed in windows; the domain shifts and merges the partial cards (SPEC AC-17, AC-18). */
class CardMergerTest {
    private fun part(tag: String, at: Int) = ReviewCard(
        title = "Part $tag", summary = "About $tag.",
        concepts = (1..4).map { Concept("$tag concept $it", "e", Timestamp(at + it)) },
        examPoints = listOf(ExamPoint("$tag exam", "cue", Timestamp(at + 30))),
        quiz = (1..5).map { QuizItem("$tag q$it", listOf("a", "b", "c", "d"), 0, "e", Timestamp(at + 40 + it)) },
        todos = listOf(Todo("Read chapter 4", "next class", Timestamp(at + 50))),
    )

    @Test fun shiftingMovesEveryTimestampByTheWindowOffset() {
        val shifted = part("A", 0).shiftedBy(600)
        assertEquals(Timestamp(601), shifted.concepts[0].at)
        assertEquals(Timestamp(630), shifted.examPoints[0].at)
        assertEquals(Timestamp(641), shifted.quiz[0].at)
        assertEquals(Timestamp(650), shifted.todos[0].at)
    }

    @Test fun mergedCardDrawsFromEveryPartOfTheLecture() {
        val parts = listOf(part("A", 0), part("B", 600), part("C", 1200))
        val merged = CardMerger.merge(parts, title = "Whole lecture", summary = "All of it.")
        assertEquals("Whole lecture", merged.title)
        assertEquals(5, merged.quiz.size)
        // picked round-robin (A1 B1 C1 A2 B2), shown in lecture order
        assertEquals(listOf("A q1", "A q2", "B q1", "B q2", "C q1"), merged.quiz.map { it.question })
        assertEquals(5, merged.concepts.size)
        assertTrue(merged.concepts.map { it.name.first() }.toSet() == setOf('A', 'B', 'C'))
        assertEquals(listOf("A exam", "B exam", "C exam"), merged.examPoints.map { it.point })
    }

    @Test fun itemsStayInLectureOrderAndDuplicateToDosCollapse() {
        val merged = CardMerger.merge(listOf(part("A", 0), part("B", 600)), "t", "s")
        assertEquals(merged.quiz.sortedBy { it.at }, merged.quiz)
        assertEquals(merged.concepts.sortedBy { it.at }, merged.concepts)
        assertEquals(1, merged.todos.size) // "Read chapter 4" said in both windows
        assertEquals(Timestamp(50), merged.todos[0].at)
    }

    @Test fun examPointsAreCappedAtEightSpreadAcrossTheLecture() {
        val many = (0 until 5).map { i ->
            part("P$i", i * 600).copy(examPoints = (1..3).map { ExamPoint("P$i e$it", "cue", Timestamp(i * 600 + it)) })
        }
        val merged = CardMerger.merge(many, "t", "s")
        assertEquals(8, merged.examPoints.size)
        assertEquals(5, merged.examPoints.map { it.point.substringBefore(' ') }.toSet().size)
    }

    @Test fun withMoreWindowsThanQuestionsTheQuizSpansFirstToLastWindow() {
        assertEquals(listOf(0, 2, 4, 5, 7, 1, 3, 6), CardMerger.spreadOrder(8, 5))
        val eight = (0 until 8).map { part("W$it", it * 600) }
        val merged = CardMerger.merge(eight, "t", "s")
        assertEquals(listOf("W0", "W2", "W4", "W5", "W7"), merged.quiz.map { it.question.substringBefore(' ') })
    }

    @Test fun windowsCoverTheRecordingAndAShortTailJoinsThePreviousWindow() {
        assertEquals(listOf(0 to 148), CardMerger.windows(148, windowSeconds = 600))
        assertEquals(listOf(0 to 600, 600 to 1200, 1200 to 1500), CardMerger.windows(1500, windowSeconds = 600))
        assertEquals(listOf(0 to 600, 600 to 1290), CardMerger.windows(1290, windowSeconds = 600)) // 90 s tail joins
    }
}
