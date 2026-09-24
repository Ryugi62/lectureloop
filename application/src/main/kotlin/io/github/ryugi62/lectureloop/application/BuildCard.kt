package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.CardMerger
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.ReviewCardRules
import io.github.ryugi62.lectureloop.domain.Violation
import io.github.ryugi62.lectureloop.domain.shiftedBy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface CardOutcome {
    data class Built(val card: ReviewCard) : CardOutcome
    data class Rejected(val reason: FailureReason) : CardOutcome
}

/**
 * Analyze → check against [ReviewCardRules] → if the card breaks a rule (or the answer is malformed),
 * analyze once more with the violations as feedback. Shared by the app ([ProcessLecture]) and the server.
 *
 * A recording longer than one window is cut by [splitter] and each window is analysed on its own (in parallel),
 * then the windows' cards are shifted onto the lecture's timeline and merged by [CardMerger].
 */
class BuildCard(
    private val analyzer: LectureAnalyzer,
    private val splitter: AudioSplitter? = null,
    private val composer: CardComposer? = null,
    private val windowSeconds: Int = CardMerger.WINDOW_SECONDS,
    private val parallel: Int = 4,
) {
    suspend operator fun invoke(audio: AudioRef, onPhase: (ProcessPhase) -> Unit = {}): CardOutcome {
        val windows = CardMerger.windows(audio.durationSeconds, windowSeconds)
        val parts = if (windows.size > 1 && splitter != null) splitter.split(audio, windows) else null
        if (parts == null) return single(audio, onPhase)
        try {
            return windowed(audio, windows, parts, onPhase)
        } finally {
            splitter?.release(parts)
        }
    }

    private suspend fun single(audio: AudioRef, onPhase: (ProcessPhase) -> Unit): CardOutcome {
        val first = attempt(audio, emptyList(), 1, onPhase)
        if (first !is Attempt.Bad || first.feedback == null) return first.outcome()
        return attempt(audio, first.feedback, 2, onPhase).outcome()
    }

    private suspend fun windowed(audio: AudioRef, windows: List<Pair<Int, Int>>, parts: List<AudioRef>, onPhase: (ProcessPhase) -> Unit): CardOutcome {
        onPhase(ProcessPhase.Listening(1, parts.size))
        val gate = Semaphore(parallel)
        val outcomes = coroutineScope {
            parts.map { part -> async { gate.withPermit { single(part) {} } } }.awaitAll()
        }
        outcomes.firstOrNull { it is CardOutcome.Rejected }?.let { return it }
        onPhase(ProcessPhase.CheckingCard)
        val cards = outcomes.mapIndexed { i, o -> (o as CardOutcome.Built).card.shiftedBy(windows[i].first) }
        val (title, summary) = try {
            composer?.titleAndSummary(cards) ?: (cards.first().title to cards.first().summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cards.first().title to cards.first().summary
        }
        val merged = CardMerger.merge(cards, title, summary)
        val violations = ReviewCardRules.check(merged, audio.durationSeconds)
        return if (violations.isEmpty()) CardOutcome.Built(merged) else CardOutcome.Rejected(FailureReason.CardRejected(violations))
    }

    private sealed interface Attempt {
        data class Ok(val card: ReviewCard) : Attempt
        /** `feedback == null` means "do not retry". */
        data class Bad(val reason: FailureReason, val feedback: List<Violation>?) : Attempt
    }

    private fun Attempt.outcome(): CardOutcome = when (this) {
        is Attempt.Ok -> CardOutcome.Built(card)
        is Attempt.Bad -> CardOutcome.Rejected(reason)
    }

    private suspend fun attempt(audio: AudioRef, feedback: List<Violation>, number: Int, onPhase: (ProcessPhase) -> Unit): Attempt = try {
        onPhase(ProcessPhase.Listening(number))
        val card = analyzer.analyze(audio, feedback)
        onPhase(ProcessPhase.CheckingCard)
        val violations = ReviewCardRules.check(card, audio.durationSeconds)
        if (violations.isEmpty()) Attempt.Ok(card) else Attempt.Bad(FailureReason.CardRejected(violations), violations)
    } catch (e: AnalyzerException.Malformed) {
        Attempt.Bad(FailureReason.Malformed, emptyList())
    } catch (e: AnalyzerException.RateLimited) {
        Attempt.Bad(FailureReason.Busy, null)
    } catch (e: AnalyzerException.LimitReached) {
        Attempt.Bad(FailureReason.LimitReached(e.access), null)
    } catch (e: AnalyzerException) {
        Attempt.Bad(FailureReason.Unreachable(e.message ?: "unknown"), null)
    }
}
