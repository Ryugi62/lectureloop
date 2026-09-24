package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.Access
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BillingUseCasesTest {
    private val clock = FixedClock()

    @Test fun accessStatusCountsOnlyThisWeeksLectures() = runTest {
        val repo = InMemoryLectures()
        val process = ProcessLecture(ScriptedAnalyzer(ok(card())), repo, FakeBilling(), clock, SeqIds())
        process("DSP", AUDIO)
        assertEquals(Access.Granted(remainingFree = 1), AccessStatus(repo, FakeBilling(), clock).invoke())
        clock.instant = clock.instant.plus(java.time.Duration.ofDays(5)) // next Monday
        assertEquals(Access.Granted(remainingFree = 2), AccessStatus(repo, FakeBilling(), clock).invoke())
    }

    @Test fun showingThePaywallRecordsAnImpression() = runTest {
        val billing = FakeBilling()
        val offer = LoadPaywall(billing).invoke()
        assertEquals("default", offer.id)
        assertEquals(1, billing.impressions)
    }

    @Test fun purchaseUnlocksAndCancelIsNotAnError() = runTest {
        val billing = FakeBilling()
        assertIs<PurchaseOutcome.Unlocked>(Purchase(billing).invoke("\$rc_six_month"))
        billing.pro = false
        billing.nextPurchase = PurchaseOutcome.Cancelled
        assertIs<PurchaseOutcome.Cancelled>(Purchase(billing).invoke("\$rc_six_month"))
    }
}
