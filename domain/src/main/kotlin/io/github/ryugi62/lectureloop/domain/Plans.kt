package io.github.ryugi62.lectureloop.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

enum class PlanKind { SEMESTER, MONTHLY, OTHER }

/** A purchasable package as the paywall shows it. Prices come from the store, never from code. */
data class PlanOption(
    val id: String,
    val kind: PlanKind,
    val title: String,
    val priceText: String,
    val priceMicros: Long,
    val currency: String,
    val periodMonths: Int?,
)

object PlanMath {
    private const val WEEKS_PER_SIX_MONTHS = 26

    /** Semester Pass first, then monthly, then anything else. */
    fun order(plans: List<PlanOption>): List<PlanOption> = plans.sortedBy { it.kind.ordinal }

    fun kindOf(packageId: String, periodMonths: Int?): PlanKind = when {
        periodMonths == 6 || packageId == "\$rc_six_month" || packageId.contains("semester", ignoreCase = true) -> PlanKind.SEMESTER
        periodMonths == 1 || packageId == "\$rc_monthly" -> PlanKind.MONTHLY
        else -> PlanKind.OTHER
    }

    /** Percent saved by the Semester Pass against paying monthly for the same months; null when not comparable. */
    fun savingsPercent(semester: PlanOption, monthly: PlanOption): Int? {
        val months = semester.periodMonths ?: return null
        if (semester.currency != monthly.currency || monthly.priceMicros <= 0) return null
        val monthlyTotal = monthly.priceMicros * months
        if (semester.priceMicros >= monthlyTotal) return null
        return BigDecimal(monthlyTotal - semester.priceMicros).multiply(BigDecimal(100))
            .divide(BigDecimal(monthlyTotal), 0, RoundingMode.DOWN).toInt()
    }

    /** Price per week, rounded to a whole cent (10_000 micros). */
    fun weeklyMicros(plan: PlanOption): Long? {
        val months = plan.periodMonths ?: return null
        val weeks = BigDecimal(WEEKS_PER_SIX_MONTHS).multiply(BigDecimal(months)).divide(BigDecimal(6))
        val perWeek = BigDecimal(plan.priceMicros).divide(weeks, 0, RoundingMode.HALF_UP)
        return perWeek.divide(BigDecimal(10_000), 0, RoundingMode.HALF_UP).multiply(BigDecimal(10_000)).toLong()
    }

    fun formatMicros(micros: Long, currency: String): String {
        val cur = Currency.getInstance(currency)
        val amount = BigDecimal(micros).divide(BigDecimal(1_000_000)).setScale(cur.defaultFractionDigits, RoundingMode.HALF_UP)
        return cur.getSymbol(Locale.US) + amount.toPlainString()
    }
}
