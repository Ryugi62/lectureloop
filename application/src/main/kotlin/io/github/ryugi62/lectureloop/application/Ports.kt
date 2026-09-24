package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.LectureId
import io.github.ryugi62.lectureloop.domain.PlanOption
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.Violation
import java.time.Instant
import java.time.ZoneId

/** Turns a recording into a review card. [feedback] lists the rules the previous answer broke (empty on the first call). */
interface LectureAnalyzer {
    suspend fun analyze(audio: AudioRef, feedback: List<Violation> = emptyList()): ReviewCard
}

sealed class AnalyzerException(message: String) : Exception(message) {
    /** The model answered, but not in the agreed shape. Worth one retry. */
    class Malformed(detail: String) : AnalyzerException("Malformed answer: $detail")

    /** Quota or rate limit on the AI side. Retrying immediately makes it worse. */
    class RateLimited : AnalyzerException("Rate limited")

    class Network(detail: String) : AnalyzerException("Network: $detail")

    class Service(val code: Int, detail: String) : AnalyzerException("Service $code: $detail")
}

interface LectureRepository {
    suspend fun save(lecture: Lecture)
    suspend fun get(id: LectureId): Lecture?
    suspend fun all(): List<Lecture>
}

data class ProStatus(val isPro: Boolean, val planTitle: String?, val expiresAt: Instant?)

/** What the paywall shows: plans from the store and copy/allowance from offering metadata. */
data class Offer(val id: String, val headline: String?, val plans: List<PlanOption>, val freePerWeek: Int?)

sealed interface PurchaseOutcome {
    data object Unlocked : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/** Everything the app needs from the subscription backend (RevenueCat in production). */
interface BillingGateway {
    suspend fun status(): ProStatus
    suspend fun offer(): Offer
    suspend fun purchase(planId: String): PurchaseOutcome
    suspend fun restore(): ProStatus
    fun paywallShown(offerId: String)
}

interface Clock {
    fun now(): Instant
    val zone: ZoneId
}

interface IdSource {
    fun next(): LectureId
}
