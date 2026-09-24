package io.github.ryugi62.lectureloop.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanMathTest {
    private val monthly = PlanOption("\$rc_monthly", PlanKind.MONTHLY, "Monthly", "$4.99", 4_990_000, "USD", periodMonths = 1)
    private val semester = PlanOption("\$rc_six_month", PlanKind.SEMESTER, "Semester Pass", "$19.99", 19_990_000, "USD", periodMonths = 6)

    @Test fun semesterPassIsShownFirst() {
        assertEquals(listOf(semester, monthly), PlanMath.order(listOf(monthly, semester)))
    }

    @Test fun savingsAgainstPayingMonthlyForTheSameMonths() {
        assertEquals(33, PlanMath.savingsPercent(semester, monthly))
    }

    @Test fun noSavingsClaimWhenCurrenciesDiffer() {
        assertNull(PlanMath.savingsPercent(semester.copy(currency = "KRW"), monthly))
    }

    @Test fun weeklyEquivalentUses26WeeksPerSixMonths() {
        assertEquals(770_000, PlanMath.weeklyMicros(semester)) // 19.99 / 26 = 0.7688 → one cent rounding
        assertEquals("$0.77", PlanMath.formatMicros(PlanMath.weeklyMicros(semester)!!, "USD"))
    }

    @Test fun kindFromPackageIdentifierOrPeriod() {
        assertEquals(PlanKind.SEMESTER, PlanMath.kindOf(packageId = "\$rc_six_month", periodMonths = 6))
        assertEquals(PlanKind.MONTHLY, PlanMath.kindOf(packageId = "\$rc_monthly", periodMonths = 1))
        assertEquals(PlanKind.SEMESTER, PlanMath.kindOf(packageId = "semester", periodMonths = null))
        assertEquals(PlanKind.OTHER, PlanMath.kindOf(packageId = "\$rc_annual", periodMonths = 12))
    }
}
