package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.Violation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The retry rule on its own — the server uses BuildCard without the app's allowance or storage. */
class BuildCardTest {
    @Test fun validFirstAnswerIsBuiltWithoutRetry() = runTest {
        val analyzer = ScriptedAnalyzer(ok(card()))
        assertIs<CardOutcome.Built>(BuildCard(analyzer)(AUDIO))
        assertEquals(1, analyzer.feedbackSeen.size)
    }

    @Test fun ruleViolationsGoBackAsFeedbackOnce() = runTest {
        val analyzer = ScriptedAnalyzer(ok(card(quizSize = 6)), ok(card(quizSize = 6)))
        val outcome = BuildCard(analyzer)(AUDIO)
        assertEquals(CardOutcome.Rejected(FailureReason.CardRejected(listOf(Violation.QuizSize(6)))), outcome)
        assertEquals(listOf(emptyList(), listOf<Violation>(Violation.QuizSize(6))), analyzer.feedbackSeen)
    }
}

class WindowedBuildCardTest {
    private val long = io.github.ryugi62.lectureloop.domain.AudioRef("file:long.m4a", "audio/mp4", durationSeconds = 1500)

    private class FakeSplitter(private val canSplit: Boolean = true) : AudioSplitter {
        var released = 0
        override suspend fun split(audio: io.github.ryugi62.lectureloop.domain.AudioRef, windows: List<Pair<Int, Int>>) =
            if (!canSplit) null else windows.mapIndexed { i, (s, e) -> audio.copy(location = "part$i", durationSeconds = e - s) }
        override fun release(parts: List<io.github.ryugi62.lectureloop.domain.AudioRef>) { released += parts.size }
    }

    /** Answers by window: each window's card has timestamps local to that window. */
    private class ByWindow(private val failWindow: String? = null) : LectureAnalyzer {
        val seen = mutableListOf<String>()
        override suspend fun analyze(audio: io.github.ryugi62.lectureloop.domain.AudioRef, feedback: List<Violation>): io.github.ryugi62.lectureloop.domain.ReviewCard {
            synchronized(seen) { seen += audio.location }
            if (audio.location == failWindow) return card(quizSize = 4)
            val c = card()
            return c.copy(
                title = audio.location,
                quiz = c.quiz.map { it.copy(question = "${audio.location} ${it.question}") },
                concepts = c.concepts.map { it.copy(name = "${audio.location} ${it.name}") },
            )
        }
    }

    private val composer = object : CardComposer {
        override suspend fun titleAndSummary(parts: List<io.github.ryugi62.lectureloop.domain.ReviewCard>) = "Whole lecture" to "Everything."
    }

    @Test fun longRecordingIsAnalysedPerWindowShiftedAndMerged() = runTest {
        val splitter = FakeSplitter()
        val analyzer = ByWindow()
        val outcome = BuildCard(analyzer, splitter, composer)(long)
        val card = assertIs<CardOutcome.Built>(outcome).card
        assertEquals(setOf("part0", "part1", "part2"), analyzer.seen.toSet())
        assertEquals("Whole lecture", card.title)
        assertEquals(5, card.quiz.size)
        // windows start at 0, 600, 1200: the fake's local 00:20 lands at 00:20, 10:20, 20:20
        assertEquals(setOf(20, 620, 1220), card.quiz.map { it.at.seconds }.toSet())
        assertEquals(3, splitter.released)
    }

    @Test fun aWindowThatFailsTwiceFailsTheCard() = runTest {
        val outcome = BuildCard(ByWindow(failWindow = "part1"), FakeSplitter(), composer)(long)
        assertIs<CardOutcome.Rejected>(outcome)
    }

    @Test fun formatsThatCannotBeCutUseOneCall() = runTest {
        val analyzer = ByWindow()
        assertIs<CardOutcome.Built>(BuildCard(analyzer, FakeSplitter(canSplit = false), composer)(long))
        assertEquals(listOf("file:long.m4a"), analyzer.seen)
    }
}
