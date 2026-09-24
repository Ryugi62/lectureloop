package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.ReviewStep
import io.github.ryugi62.lectureloop.domain.Violation
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProcessLectureTest {
    private val clock = FixedClock()
    private val repo = InMemoryLectures()

    private fun useCase(analyzer: LectureAnalyzer, billing: FakeBilling = FakeBilling()) =
        ProcessLecture(analyzer, repo, billing, clock, SeqIds())

    @Test fun validCardIsSavedAndEntersTheLoop() = runTest {
        val result = useCase(ScriptedAnalyzer(ok(card()))).invoke("DSP", AUDIO)
        val done = assertIs<ProcessResult.Done>(result)
        assertEquals("DSP", done.lecture.course)
        assertEquals(ReviewStep.DAY_1, done.lecture.review.step)
        assertEquals(clock.now() + Duration.ofDays(1), done.lecture.review.dueAt)
        assertEquals(done.lecture, repo.items.values.single())
    }

    @Test fun invalidCardIsRetriedOnceWithTheViolationsAsFeedback() = runTest {
        val analyzer = ScriptedAnalyzer(ok(card(quizSize = 4)), ok(card()))
        val result = useCase(analyzer).invoke("DSP", AUDIO)
        assertIs<ProcessResult.Done>(result)
        assertEquals(listOf(emptyList(), listOf<Violation>(Violation.QuizSize(4))), analyzer.feedbackSeen)
    }

    @Test fun phasesAreReportedInOrderIncludingTheRetry() = runTest {
        val analyzer = ScriptedAnalyzer(ok(card(quizSize = 4)), ok(card()))
        val phases = mutableListOf<ProcessPhase>()
        useCase(analyzer).invoke("DSP", AUDIO) { phases += it }
        assertEquals(
            listOf(
                ProcessPhase.CheckingAllowance, ProcessPhase.Listening(1), ProcessPhase.CheckingCard,
                ProcessPhase.Listening(2), ProcessPhase.CheckingCard, ProcessPhase.Saving,
            ),
            phases,
        )
    }

    @Test fun malformedAnswerIsRetriedOnce() = runTest {
        val analyzer = ScriptedAnalyzer(err(AnalyzerException.Malformed("not json")), ok(card()))
        assertIs<ProcessResult.Done>(useCase(analyzer).invoke("DSP", AUDIO))
    }

    @Test fun twoBadAnswersFailAndSaveNothing() = runTest {
        val analyzer = ScriptedAnalyzer(ok(card(quizSize = 4)), ok(card(quizSize = 6)))
        val result = useCase(analyzer).invoke("DSP", AUDIO)
        val failed = assertIs<ProcessResult.Failed>(result)
        assertEquals(FailureReason.CardRejected(listOf(Violation.QuizSize(6))), failed.reason)
        assertTrue(repo.items.isEmpty())
    }

    @Test fun rateLimitIsNotRetriedAndIsReportedAsSuch() = runTest {
        val analyzer = ScriptedAnalyzer(err(AnalyzerException.RateLimited()))
        val failed = assertIs<ProcessResult.Failed>(useCase(analyzer).invoke("DSP", AUDIO))
        assertEquals(FailureReason.Busy, failed.reason)
        assertEquals(1, analyzer.feedbackSeen.size)
    }

    @Test fun freeUserOverTheWeeklyAllowanceHitsThePaywallBeforeAnyAiCall() = runTest {
        val analyzer = ScriptedAnalyzer(ok(card()), ok(card()), ok(card()))
        val process = useCase(analyzer)
        assertIs<ProcessResult.Done>(process("DSP", AUDIO))
        assertIs<ProcessResult.Done>(process("DSP", AUDIO))
        val third = assertIs<ProcessResult.Paywalled>(process("DSP", AUDIO))
        assertEquals(Access.Paywalled(used = 2, limit = 2, resetsAt = java.time.Instant.parse("2026-09-27T15:00:00Z")), third.access)
        assertEquals(2, analyzer.feedbackSeen.size)
    }

    @Test fun failedAttemptsDoNotUseTheAllowance() = runTest {
        val analyzer = ScriptedAnalyzer(
            err(AnalyzerException.Network("offline")),
            ok(card()), ok(card()),
        )
        val process = useCase(analyzer)
        assertIs<ProcessResult.Failed>(process("DSP", AUDIO))
        assertIs<ProcessResult.Done>(process("DSP", AUDIO))
        assertIs<ProcessResult.Done>(process("DSP", AUDIO))
    }

    @Test fun theServersLimitWinsOverTheAppsOwnCount() = runTest {
        val serverSays = Access.Paywalled(used = 2, limit = 2, resetsAt = java.time.Instant.parse("2026-09-27T15:00:00Z"))
        val analyzer = ScriptedAnalyzer(err(AnalyzerException.LimitReached(serverSays)))
        val result = useCase(analyzer).invoke("DSP", AUDIO)
        assertEquals(ProcessResult.Paywalled(serverSays), result)
        assertEquals(1, analyzer.feedbackSeen.size) // not retried
        assertTrue(repo.items.isEmpty())
    }

    @Test fun proUserIsNeverPaywalled() = runTest {
        val analyzer = ScriptedAnalyzer(*Array(3) { ok(card()) })
        val process = useCase(analyzer, FakeBilling(pro = true))
        repeat(3) { assertIs<ProcessResult.Done>(process("DSP", AUDIO)) }
    }

    @Test fun remoteAllowanceFromOfferingMetadataIsUsedAndFallsBackWhenOffline() = runTest {
        val one = useCase(ScriptedAnalyzer(ok(card())), FakeBilling(freePerWeek = 1))
        assertIs<ProcessResult.Done>(one("DSP", AUDIO))
        assertIs<ProcessResult.Paywalled>(one("DSP", AUDIO))

        repo.items.clear()
        val offline = useCase(ScriptedAnalyzer(ok(card()), ok(card())), FakeBilling(offerFails = true))
        assertIs<ProcessResult.Done>(offline("DSP", AUDIO))
        assertIs<ProcessResult.Done>(offline("DSP", AUDIO)) // default 2 when the offer cannot be loaded
        assertIs<ProcessResult.Paywalled>(offline("DSP", AUDIO))
    }
}
