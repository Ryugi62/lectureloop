package io.github.ryugi62.lectureloop.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessPolicyTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val wednesday = Instant.parse("2026-09-23T05:00:00Z") // Wed 14:00 KST
    private val nextMondayMidnight = Instant.parse("2026-09-27T15:00:00Z") // Mon 2026-09-28 00:00 KST

    @Test fun weekStartsMondayMidnightLocalTime() {
        assertEquals(Instant.parse("2026-09-20T15:00:00Z"), AccessPolicy.weekStart(wednesday, seoul))
        // Sunday 23:30 KST still belongs to the same week
        assertEquals(Instant.parse("2026-09-20T15:00:00Z"), AccessPolicy.weekStart(Instant.parse("2026-09-27T14:30:00Z"), seoul))
        // Monday 00:00 KST starts the next one
        assertEquals(nextMondayMidnight, AccessPolicy.weekStart(nextMondayMidnight, seoul))
    }

    @Test fun freeUserWithAllowanceLeftIsGranted() {
        assertEquals(Access.Granted(remainingFree = 1), AccessPolicy.decide(isPro = false, usedThisWeek = 1, freePerWeek = 2, now = wednesday, zone = seoul))
    }

    @Test fun freeUserAtTheLimitHitsThePaywallWithResetTime() {
        assertEquals(
            Access.Paywalled(used = 2, limit = 2, resetsAt = nextMondayMidnight),
            AccessPolicy.decide(isPro = false, usedThisWeek = 2, freePerWeek = 2, now = wednesday, zone = seoul),
        )
    }

    @Test fun proIsUnlimited() {
        assertEquals(Access.Granted(remainingFree = null), AccessPolicy.decide(isPro = true, usedThisWeek = 40, freePerWeek = 2, now = wednesday, zone = seoul))
    }

    @Test fun remoteAllowanceIsClampedToSaneRange() {
        assertEquals(2, AccessPolicy.sanitizeAllowance(null))
        assertEquals(2, AccessPolicy.sanitizeAllowance(-3))
        assertEquals(0, AccessPolicy.sanitizeAllowance(0))
        assertEquals(7, AccessPolicy.sanitizeAllowance(7))
        assertEquals(14, AccessPolicy.sanitizeAllowance(99))
    }
}
