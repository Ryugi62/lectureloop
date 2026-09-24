package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.ReviewCardRules
import io.github.ryugi62.lectureloop.domain.ReviewScheduler
import io.github.ryugi62.lectureloop.domain.Violation

sealed interface ProcessResult {
    data class Done(val lecture: Lecture) : ProcessResult
    data class Paywalled(val access: Access.Paywalled) : ProcessResult
    data class Failed(val reason: FailureReason) : ProcessResult
}

/** Real progress, reported as it happens (the UI names these steps instead of a bare spinner). */
sealed interface ProcessPhase {
    data object CheckingAllowance : ProcessPhase
    data class Listening(val attempt: Int) : ProcessPhase
    data object CheckingCard : ProcessPhase
    data object Saving : ProcessPhase
}

sealed interface FailureReason {
    data class CardRejected(val violations: List<Violation>) : FailureReason
    data object Malformed : FailureReason
    data object Busy : FailureReason
    data class Unreachable(val detail: String) : FailureReason
}

/** UC-1: allowance → analyze → check → (one retry with feedback) → save and start the loop. */
class ProcessLecture(
    private val analyzer: LectureAnalyzer,
    private val lectures: LectureRepository,
    private val billing: BillingGateway,
    private val clock: Clock,
    private val ids: IdSource,
) {
    suspend operator fun invoke(course: String, audio: AudioRef, onPhase: (ProcessPhase) -> Unit = {}): ProcessResult {
        onPhase(ProcessPhase.CheckingAllowance)
        val access = AccessStatus(lectures, billing, clock).invoke()
        if (access is Access.Paywalled) return ProcessResult.Paywalled(access)

        val card = when (val attempt = analyzeWithOneRetry(audio, onPhase)) {
            is Attempt.Ok -> attempt.card
            is Attempt.Bad -> return ProcessResult.Failed(attempt.reason)
        }
        onPhase(ProcessPhase.Saving)
        val now = clock.now()
        val lecture = Lecture(
            id = ids.next(),
            course = course.trim().ifEmpty { "My course" },
            recordedAt = now,
            audio = audio,
            card = card,
            review = ReviewScheduler.start(now),
        )
        lectures.save(lecture)
        return ProcessResult.Done(lecture)
    }

    private sealed interface Attempt {
        data class Ok(val card: ReviewCard) : Attempt
        data class Bad(val reason: FailureReason, val feedback: List<Violation>?) : Attempt
    }

    private suspend fun analyzeWithOneRetry(audio: AudioRef, onPhase: (ProcessPhase) -> Unit): Attempt {
        val first = attempt(audio, emptyList(), 1, onPhase)
        if (first !is Attempt.Bad || first.feedback == null) return first
        return attempt(audio, first.feedback, 2, onPhase)
    }

    /** `feedback == null` on a Bad attempt means "do not retry". */
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
    } catch (e: AnalyzerException) {
        Attempt.Bad(FailureReason.Unreachable(e.message ?: "unknown"), null)
    }
}
