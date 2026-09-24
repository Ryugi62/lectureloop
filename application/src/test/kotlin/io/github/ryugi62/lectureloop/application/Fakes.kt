package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Concept
import io.github.ryugi62.lectureloop.domain.ExamPoint
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.LectureId
import io.github.ryugi62.lectureloop.domain.QuizItem
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.domain.Todo
import io.github.ryugi62.lectureloop.domain.Violation
import java.time.Instant
import java.time.ZoneId

internal val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
internal val WED_14 = Instant.parse("2026-09-23T05:00:00Z")

internal fun card(quizSize: Int = 5) = ReviewCard(
    title = "Sampling and aliasing",
    summary = "Sampling rate, Nyquist rate, aliasing.",
    concepts = (1..3).map { Concept("Concept $it", "Explained $it.", Timestamp(10 * it)) },
    examPoints = listOf(ExamPoint("Sample faster than 2 f max.", "this will be on the midterm", Timestamp(38))),
    quiz = (1..quizSize).map { QuizItem("Q$it?", listOf("a", "b", "c", "d"), 1, "Because.", Timestamp(20)) },
    todos = listOf(Todo("Homework 3", "next Monday", Timestamp(133))),
)

internal val AUDIO = AudioRef(location = "file:lecture.m4a", mimeType = "audio/mp4", durationSeconds = 148)

internal class FixedClock(var instant: Instant = WED_14, override val zone: ZoneId = SEOUL) : Clock {
    override fun now(): Instant = instant
}

internal class SeqIds : IdSource {
    private var n = 0
    override fun next(): LectureId = LectureId("lecture-${++n}")
}

internal class InMemoryLectures(initial: List<Lecture> = emptyList()) : LectureRepository {
    val items = initial.associateBy { it.id }.toMutableMap()
    override suspend fun save(lecture: Lecture) { items[lecture.id] = lecture }
    override suspend fun get(id: LectureId): Lecture? = items[id]
    override suspend fun all(): List<Lecture> = items.values.sortedByDescending { it.recordedAt }
}

internal sealed interface Answer
internal data class Ok(val card: ReviewCard) : Answer
internal data class Err(val error: Exception) : Answer
internal fun ok(card: ReviewCard): Answer = Ok(card)
internal fun err(error: Exception): Answer = Err(error)

/** Returns queued answers in order and records the feedback it was given. */
internal class ScriptedAnalyzer(vararg answers: Answer) : LectureAnalyzer {
    private val queue = ArrayDeque(answers.toList())
    val feedbackSeen = mutableListOf<List<Violation>>()
    override suspend fun analyze(audio: AudioRef, feedback: List<Violation>): ReviewCard {
        feedbackSeen += feedback
        return when (val next = queue.removeFirst()) {
            is Ok -> next.card
            is Err -> throw next.error
        }
    }
}

internal class FakeBilling(
    var pro: Boolean = false,
    var freePerWeek: Int? = null,
    var offerFails: Boolean = false,
) : BillingGateway {
    var purchases = 0
    var impressions = 0
    var nextPurchase: PurchaseOutcome = PurchaseOutcome.Unlocked
    override suspend fun status(): ProStatus = ProStatus(isPro = pro, planTitle = if (pro) "Semester Pass" else null, expiresAt = null)
    override suspend fun offer(): Offer {
        if (offerFails) throw IllegalStateException("offline")
        return Offer(id = "default", headline = "Keep every lecture in the loop", plans = emptyList(), freePerWeek = freePerWeek)
    }
    override suspend fun purchase(planId: String): PurchaseOutcome {
        purchases++
        if (nextPurchase == PurchaseOutcome.Unlocked) pro = true
        return nextPurchase
    }
    override suspend fun restore(): ProStatus = status()
    override fun paywallShown(offerId: String) { impressions++ }
}
