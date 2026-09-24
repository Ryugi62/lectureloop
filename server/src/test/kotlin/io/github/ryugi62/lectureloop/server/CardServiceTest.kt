package io.github.ryugi62.lectureloop.server

import io.github.ryugi62.lectureloop.application.AnalyzerException
import io.github.ryugi62.lectureloop.application.BuildCard
import io.github.ryugi62.lectureloop.application.LectureAnalyzer
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Concept
import io.github.ryugi62.lectureloop.domain.QuizItem
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.domain.Violation
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal fun serverCard(quiz: Int = 5) = ReviewCard(
    "T", "S", (1..3).map { Concept("c$it", "e", Timestamp(it)) }, emptyList(),
    List(quiz) { QuizItem("q", listOf("a", "b", "c", "d"), 0, "e", Timestamp(5)) }, emptyList(),
)

internal class CountingAnalyzer(private val answer: () -> ReviewCard) : LectureAnalyzer {
    var calls = 0
    override suspend fun analyze(audio: AudioRef, feedback: List<Violation>): ReviewCard { calls++; return answer() }
}

class CardServiceTest {
    private val wed = Instant.parse("2026-09-23T05:00:00Z")
    private val seoul = ZoneId.of("Asia/Seoul")
    private val audio = AudioRef("/tmp/x.m4a", "audio/mp4", 148)
    private fun usage() = FileUsageStore(Files.createTempFile("usage", ".json").toFile().apply { delete() })

    private fun service(analyzer: LectureAnalyzer, pro: Set<String> = emptySet(), store: UsageStore = usage(), now: Instant = wed) = CardService(
        buildCard = BuildCard(analyzer),
        entitlements = object : EntitlementChecker { override suspend fun isPro(appUserId: String) = appUserId in pro },
        usage = store,
        clock = { now },
        freePerWeek = 2,
    )

    @Test fun freeUserGetsTwoCardsAWeekThenPaymentRequiredWithoutAnAiCall() = runTest {
        val analyzer = CountingAnalyzer { serverCard() }
        val s = service(analyzer)
        assertIs<Served.Card>(s.serve("u1", seoul, audio))
        assertIs<Served.Card>(s.serve("u1", seoul, audio))
        val third = assertIs<Served.Limit>(s.serve("u1", seoul, audio))
        assertEquals(2, third.access.used)
        assertEquals(Instant.parse("2026-09-27T15:00:00Z"), third.access.resetsAt)
        assertEquals(2, analyzer.calls)
    }

    @Test fun eachUserHasTheirOwnAllowance() = runTest {
        val s = service(CountingAnalyzer { serverCard() })
        repeat(2) { s.serve("u1", seoul, audio) }
        assertIs<Served.Card>(s.serve("u2", seoul, audio))
    }

    @Test fun proSkipsTheCountAndIsNeverLimited() = runTest {
        val s = service(CountingAnalyzer { serverCard() }, pro = setOf("paid"))
        repeat(5) { assertIs<Served.Card>(s.serve("paid", seoul, audio)) }
    }

    @Test fun rejectedCardsAndAiErrorsDoNotUseTheAllowance() = runTest {
        val store = usage()
        val bad = service(CountingAnalyzer { serverCard(quiz = 4) }, store = store)
        assertIs<Served.Rejected>(bad.serve("u1", seoul, audio))
        val busy = service(CountingAnalyzer { throw AnalyzerException.RateLimited() }, store = store)
        assertIs<Served.Busy>(busy.serve("u1", seoul, audio))
        val ok = service(CountingAnalyzer { serverCard() }, store = store)
        assertIs<Served.Card>(ok.serve("u1", seoul, audio))
        assertIs<Served.Card>(ok.serve("u1", seoul, audio))
    }

    @Test fun theWeekResetsOnMondayInTheStudentsTimeZone() = runTest {
        val store = usage()
        repeat(2) { service(CountingAnalyzer { serverCard() }, store = store).serve("u1", seoul, audio) }
        val nextMonday = Instant.parse("2026-09-27T15:00:00Z")
        assertIs<Served.Card>(service(CountingAnalyzer { serverCard() }, store = store, now = nextMonday).serve("u1", seoul, audio))
    }

    @Test fun usageSurvivesARestart() = runTest {
        val file = Files.createTempFile("usage", ".json").toFile().apply { delete() }
        repeat(2) { service(CountingAnalyzer { serverCard() }, store = FileUsageStore(file)).serve("u1", seoul, audio) }
        assertIs<Served.Limit>(service(CountingAnalyzer { serverCard() }, store = FileUsageStore(file)).serve("u1", seoul, audio))
    }
}
