package io.github.ryugi62.lectureloop.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Name of the RevenueCat entitlement that unlocks unlimited lectures. */
const val ENTITLEMENT_PRO = "pro"

sealed interface Access {
    /** [remainingFree] is null for Pro (unlimited). */
    data class Granted(val remainingFree: Int?) : Access

    data class Paywalled(val used: Int, val limit: Int, val resetsAt: Instant) : Access
}

/** Weekly free allowance: a free student can turn [DEFAULT_FREE_PER_WEEK] lectures a week into review cards. */
object AccessPolicy {
    const val DEFAULT_FREE_PER_WEEK = 2
    private const val MAX_FREE_PER_WEEK = 14

    fun decide(isPro: Boolean, usedThisWeek: Int, freePerWeek: Int, now: Instant, zone: ZoneId): Access = when {
        isPro -> Access.Granted(remainingFree = null)
        usedThisWeek < freePerWeek -> Access.Granted(remainingFree = freePerWeek - usedThisWeek)
        else -> Access.Paywalled(used = usedThisWeek, limit = freePerWeek, resetsAt = nextWeekStart(now, zone))
    }

    /** Monday 00:00 in the student's time zone. */
    fun weekStart(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant()

    fun nextWeekStart(now: Instant, zone: ZoneId): Instant =
        weekStart(now, zone).atZone(zone).plusWeeks(1).toInstant()

    /** Remote value from offering metadata; anything missing or negative falls back to the default. */
    fun sanitizeAllowance(remote: Int?): Int = when {
        remote == null || remote < 0 -> DEFAULT_FREE_PER_WEEK
        else -> remote.coerceAtMost(MAX_FREE_PER_WEEK)
    }
}
