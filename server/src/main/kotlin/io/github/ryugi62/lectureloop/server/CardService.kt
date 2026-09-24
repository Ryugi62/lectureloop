package io.github.ryugi62.lectureloop.server

import io.github.ryugi62.lectureloop.application.BuildCard
import io.github.ryugi62.lectureloop.application.CardOutcome
import io.github.ryugi62.lectureloop.application.FailureReason
import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.AccessPolicy
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.ReviewCard
import java.time.Instant
import java.time.ZoneId

/** Answer of the server for one recording. */
sealed interface Served {
    data class Card(val card: ReviewCard) : Served
    data class Limit(val access: Access.Paywalled) : Served
    data class Rejected(val reason: FailureReason) : Served
    data object Busy : Served
    data class Unreachable(val detail: String) : Served
}

/** Is this RevenueCat app user entitled to `pro`? */
interface EntitlementChecker {
    suspend fun isPro(appUserId: String): Boolean
}

/** Successful cards per user, for the weekly free allowance. */
interface UsageStore {
    fun countSince(appUserId: String, since: Instant): Int
    fun record(appUserId: String, at: Instant)
}

/**
 * The server-side gate: AI credits are only spent for a `pro` user or a free user with allowance left,
 * using the same [AccessPolicy] and [BuildCard] rules as the app. Only built cards count.
 */
class CardService(
    private val buildCard: BuildCard,
    private val entitlements: EntitlementChecker,
    private val usage: UsageStore,
    private val clock: () -> Instant,
    private val freePerWeek: Int,
) {
    suspend fun serve(appUserId: String, zone: ZoneId, audio: AudioRef): Served {
        val now = clock()
        val pro = entitlements.isPro(appUserId)
        if (!pro) {
            val used = usage.countSince(appUserId, AccessPolicy.weekStart(now, zone))
            val access = AccessPolicy.decide(isPro = false, usedThisWeek = used, freePerWeek = freePerWeek, now = now, zone = zone)
            if (access is Access.Paywalled) return Served.Limit(access)
        }
        return when (val outcome = buildCard(audio)) {
            is CardOutcome.Built -> {
                usage.record(appUserId, now)
                Served.Card(outcome.card)
            }
            is CardOutcome.Rejected -> when (val reason = outcome.reason) {
                FailureReason.Busy -> Served.Busy
                is FailureReason.Unreachable -> Served.Unreachable(reason.detail)
                else -> Served.Rejected(reason)
            }
        }
    }
}
