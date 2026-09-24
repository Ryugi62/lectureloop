package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Lecture
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
    /** [parts] > 1 when a long lecture is analysed in windows. */
    data class Listening(val attempt: Int, val parts: Int = 1) : ProcessPhase
    data object CheckingCard : ProcessPhase
    data object Saving : ProcessPhase
}

sealed interface FailureReason {
    data class CardRejected(val violations: List<Violation>) : FailureReason
    data object Malformed : FailureReason
    data object Busy : FailureReason
    data class Unreachable(val detail: String) : FailureReason
    /** Only from the server: it counted differently (e.g. the app was reinstalled). */
    data class LimitReached(val access: Access.Paywalled) : FailureReason
}

/** UC-1: allowance → build the card (check + one retry with feedback) → save and start the loop. */
class ProcessLecture(
    private val buildCard: BuildCard,
    private val lectures: LectureRepository,
    private val billing: BillingGateway,
    private val clock: Clock,
    private val ids: IdSource,
) {
    constructor(analyzer: LectureAnalyzer, lectures: LectureRepository, billing: BillingGateway, clock: Clock, ids: IdSource) :
        this(BuildCard(analyzer), lectures, billing, clock, ids)

    suspend operator fun invoke(course: String, audio: AudioRef, onPhase: (ProcessPhase) -> Unit = {}): ProcessResult {
        onPhase(ProcessPhase.CheckingAllowance)
        val access = AccessStatus(lectures, billing, clock).invoke()
        if (access is Access.Paywalled) return ProcessResult.Paywalled(access)

        val card = when (val outcome = buildCard(audio, onPhase)) {
            is CardOutcome.Built -> outcome.card
            is CardOutcome.Rejected -> {
                val reason = outcome.reason
                return if (reason is FailureReason.LimitReached) ProcessResult.Paywalled(reason.access) else ProcessResult.Failed(reason)
            }
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
}
