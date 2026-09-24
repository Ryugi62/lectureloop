package io.github.ryugi62.lectureloop.adapters.billing

import android.app.Activity
import io.github.ryugi62.lectureloop.application.BillingGateway
import io.github.ryugi62.lectureloop.application.Offer
import io.github.ryugi62.lectureloop.application.ProStatus
import io.github.ryugi62.lectureloop.application.PurchaseOutcome
import io.github.ryugi62.lectureloop.domain.ENTITLEMENT_PRO
import io.github.ryugi62.lectureloop.domain.PlanMath
import io.github.ryugi62.lectureloop.domain.PlanOption
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.paywalls.events.CustomPaywallImpressionParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/** Keeps the resumed Activity so a purchase can be launched from a ViewModel. */
object CurrentActivity {
    private var ref: WeakReference<Activity>? = null
    fun set(activity: Activity?) { ref = activity?.let(::WeakReference) }
    fun get(): Activity? = ref?.get()
}

/**
 * [BillingGateway] on RevenueCat.
 * - Entitlement `pro` decides access (never product ids), so plans can change without an app update.
 * - The current offering's packages become [PlanOption]s; offering metadata carries the paywall
 *   headline and `free_lectures_per_week`, so the free allowance is tuned from the dashboard.
 * - The custom paywall reports impressions so RevenueCat can compute conversion.
 */
class RevenueCatBilling(private val purchases: () -> Purchases = { Purchases.sharedInstance }) : BillingGateway {
    private val _pro = MutableStateFlow(false)
    val pro: StateFlow<Boolean> = _pro
    private var lastOffering: Offering? = null

    fun onCustomerInfo(info: CustomerInfo) { _pro.value = info.isPro() }

    override suspend fun status(): ProStatus = purchases().awaitCustomerInfo().toStatus()

    override suspend fun offer(): Offer {
        val offering = purchases().awaitOfferings().current ?: throw IllegalStateException("No current offering")
        lastOffering = offering
        return Offer(
            id = offering.identifier,
            headline = offering.metadata["headline"] as? String,
            plans = offering.availablePackages.map(::planOf),
            freePerWeek = (offering.metadata["free_lectures_per_week"] as? Number)?.toInt(),
        )
    }

    override suspend fun purchase(planId: String): PurchaseOutcome {
        val activity = CurrentActivity.get() ?: return PurchaseOutcome.Failed("App is not in the foreground")
        val offering = lastOffering ?: purchases().awaitOfferings().current
            ?: return PurchaseOutcome.Failed("Plans are not loaded")
        val pkg = offering.availablePackages.firstOrNull { it.identifier == planId }
            ?: return PurchaseOutcome.Failed("Plan $planId is not offered")
        return try {
            val result = purchases().awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            val status = result.customerInfo.toStatus()
            if (status.isPro) PurchaseOutcome.Unlocked else PurchaseOutcome.Failed("Purchase finished but Pro is not active")
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseOutcome.Cancelled else PurchaseOutcome.Failed(e.error.message)
        }
    }

    override suspend fun restore(): ProStatus = purchases().awaitRestore().toStatus()

    override fun paywallShown(offerId: String) {
        val offering = lastOffering?.takeIf { it.identifier == offerId }
        val params = if (offering != null) CustomPaywallImpressionParams(paywallId = PAYWALL_ID, offering = offering)
        else CustomPaywallImpressionParams(paywallId = PAYWALL_ID)
        runCatching { purchases().trackCustomPaywallImpression(params) }
    }

    private fun CustomerInfo.isPro() = entitlements[ENTITLEMENT_PRO]?.isActive == true

    private fun CustomerInfo.toStatus(): ProStatus {
        val entitlement = entitlements[ENTITLEMENT_PRO]?.takeIf { it.isActive }
        _pro.value = entitlement != null
        return ProStatus(
            isPro = entitlement != null,
            planTitle = entitlement?.productIdentifier,
            expiresAt = entitlement?.expirationDate?.toInstant(),
        )
    }

    companion object {
        const val PAYWALL_ID = "lectureloop-weekly-limit"
    }
}

/** RevenueCat package → the paywall's plan. Prices always come from the store. */
internal fun planOf(pkg: Package): PlanOption {
    val period = pkg.product.period
    val months = when (period?.unit) {
        Period.Unit.MONTH -> period.value
        Period.Unit.YEAR -> period.value * 12
        else -> null
    }
    return PlanOption(
        id = pkg.identifier,
        kind = PlanMath.kindOf(pkg.identifier, months),
        title = pkg.product.name.ifBlank { pkg.product.title },
        priceText = pkg.product.price.formatted,
        priceMicros = pkg.product.price.amountMicros,
        currency = pkg.product.price.currencyCode,
        periodMonths = months,
    )
}

/** Used when no RevenueCat key is configured: everyone is on the free allowance and the paywall explains why. */
class UnconfiguredBilling : BillingGateway {
    override suspend fun status() = ProStatus(isPro = false, planTitle = null, expiresAt = null)
    override suspend fun offer(): Offer = throw IllegalStateException("Add REVENUECAT_TEST_STORE_KEY to local.properties")
    override suspend fun purchase(planId: String) = PurchaseOutcome.Failed("Billing is not configured in this build")
    override suspend fun restore() = status()
    override fun paywallShown(offerId: String) = Unit
}

internal fun Throwable.billingMessage(): String = (this as? PurchasesException)?.error?.message ?: message ?: "Something went wrong"
