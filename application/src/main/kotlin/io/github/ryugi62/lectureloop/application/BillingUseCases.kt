package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.PlanMath

/** UC-4a: load the offer (plans ordered Semester first) and record the paywall impression. */
class LoadPaywall(private val billing: BillingGateway) {
    suspend operator fun invoke(): Offer {
        val offer = billing.offer()
        billing.paywallShown(offer.id)
        return offer.copy(plans = PlanMath.order(offer.plans))
    }
}

/** UC-4b */
class Purchase(private val billing: BillingGateway) {
    suspend operator fun invoke(planId: String): PurchaseOutcome = billing.purchase(planId)
}

/** UC-4c */
class Restore(private val billing: BillingGateway) {
    suspend operator fun invoke(): ProStatus = billing.restore()
}
