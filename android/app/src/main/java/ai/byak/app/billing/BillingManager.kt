package ai.byak.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [trial] is e.g. "7-day free trial" when Google Play offers this user a free phase. */
data class PlanOffer(
    val productId: String, val basePlanId: String, val title: String, val price: String, val details: ProductDetails, val offerToken: String,
    val trial: String? = null, val priceMicros: Long = 0, val currency: String = "", val periodMonths: Int = 1
)

/** Yearly savings versus paying monthly for 12 months, e.g. $10/yr vs $1/mo → 17. Null when not comparable. */
fun yearlySavingsPercent(monthly: PlanOffer?, yearly: PlanOffer?): Int? =
    if (monthly == null || yearly == null || monthly.currency != yearly.currency) null else savingsPercent(monthly.priceMicros, yearly.priceMicros)

fun savingsPercent(monthlyMicros: Long, yearlyMicros: Long): Int? {
    if (monthlyMicros <= 0 || yearlyMicros <= 0) return null
    return kotlin.math.round((1.0 - yearlyMicros.toDouble() / (monthlyMicros * 12.0)) * 100).toInt().takeIf { it in 1..95 }
}

/** Formats a per-month equivalent such as "$0.83" in the offer's currency. */
fun perMonth(offer: PlanOffer): String? {
    if (offer.priceMicros <= 0 || offer.currency.isBlank() || offer.periodMonths <= 1) return null
    return runCatching { java.text.NumberFormat.getCurrencyInstance().apply { currency = java.util.Currency.getInstance(offer.currency) }.format(offer.priceMicros / 1_000_000.0 / offer.periodMonths) }.getOrNull()
}

/** Converts an ISO-8601 billing period such as P7D, P1W or P1M into "7-day", "1-week", "1-month". */
fun describePeriod(iso: String): String? {
    val match = Regex("^P(\\d+)([DWMY])$").find(iso) ?: return null
    val unit = when (match.groupValues[2]) { "D" -> "day"; "W" -> "week"; "M" -> "month"; else -> "year" }
    return "${match.groupValues[1]}-$unit"
}

sealed interface PurchaseOutcome {
    data class Purchased(val tokens: List<String>) : PurchaseOutcome
    data object Pending : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/**
 * Thin wrapper over Play Billing. Purchases are never trusted on-device: every purchase token is sent to
 * the BYAK server, which verifies it with Google, acknowledges it and grants the entitlement.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {
    private val outcomes = MutableSharedFlow<PurchaseOutcome>(extraBufferCapacity = 4)
    val purchases: SharedFlow<PurchaseOutcome> = outcomes.asSharedFlow()
    private val connectLock = Mutex()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private suspend fun ready(): Boolean = connectLock.withLock {
        if (client.isReady) return true
        val result = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) { result.complete(billingResult.responseCode == BillingClient.BillingResponseCode.OK) }
            override fun onBillingServiceDisconnected() { result.complete(false) }
        })
        result.await()
    }

    /** One offer per base plan of [productId] (e.g. byak_pro → monthly, yearly), in the order given. */
    suspend fun offers(productId: String, basePlanIds: List<String>): List<PlanOffer> {
        if (!ready()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        val details: List<ProductDetails> = client.queryProductDetails(params).productDetailsList ?: emptyList()
        val product = details.firstOrNull { it.productId == productId } ?: return emptyList()
        val all = product.subscriptionOfferDetails.orEmpty()
        return basePlanIds.mapNotNull { basePlan ->
            val forPlan = all.filter { it.basePlanId == basePlan }
            // Play only returns offers this user is eligible for: prefer a free trial, otherwise the base plan itself.
            val trialOffer = forPlan.firstOrNull { o -> o.offerId != null && o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            val offer = trialOffer ?: forPlan.firstOrNull { it.offerId == null } ?: forPlan.firstOrNull() ?: return@mapNotNull null
            val phases = offer.pricingPhases.pricingPhaseList
            val recurring = phases.lastOrNull() ?: return@mapNotNull null
            val trial = phases.firstOrNull { it.priceAmountMicros == 0L }?.let { describePeriod(it.billingPeriod) }?.let { "$it free trial" }
            val months = when { recurring.billingPeriod.endsWith("Y") -> 12 * (recurring.billingPeriod.drop(1).dropLast(1).toIntOrNull() ?: 1); recurring.billingPeriod.endsWith("M") -> recurring.billingPeriod.drop(1).dropLast(1).toIntOrNull() ?: 1; else -> 1 }
            PlanOffer(product.productId, basePlan, product.name, recurring.formattedPrice, product, offer.offerToken, trial, recurring.priceAmountMicros, recurring.priceCurrencyCode, months)
        }
    }

    /** Opens the Play purchase sheet. The account id binds the purchase to this BYAK user server-side. */
    fun launch(activity: Activity, offer: PlanOffer, obfuscatedAccountId: String, replacingPurchaseToken: String? = null): String? {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details).setOfferToken(offer.offerToken).build()
        val builder = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product))
        if (obfuscatedAccountId.isNotBlank()) builder.setObfuscatedAccountId(obfuscatedAccountId)
        // Switching base plans (monthly → yearly) replaces the current subscription; unused time is credited.
        if (replacingPurchaseToken != null) builder.setSubscriptionUpdateParams(
            BillingFlowParams.SubscriptionUpdateParams.newBuilder().setOldPurchaseToken(replacingPurchaseToken)
                .setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION).build()
        )
        val result = client.launchBillingFlow(activity, builder.build())
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null else result.debugMessage.ifBlank { "Google Play couldn't start the purchase (${result.responseCode})" }
    }

    /** Subscriptions this Play account currently owns (active, in grace, or cancelled but not yet expired). */
    suspend fun activePurchases(): List<Purchase> {
        if (!ready()) return emptyList()
        return client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
            .purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    }

    /** Acknowledges on the device (on-device mode has no server to do it); unacknowledged purchases are refunded after 3 days. */
    suspend fun acknowledge(purchaseToken: String) {
        if (!ready()) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build())
    }

    /** Tokens of subscriptions this Play account currently owns, used for "Restore purchases". */
    suspend fun ownedPurchaseTokens(): List<String> {
        if (!ready()) return emptyList()
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        return result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.map { it.purchaseToken }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val outcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                val done = list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.map { it.purchaseToken }
                if (done.isNotEmpty()) PurchaseOutcome.Purchased(done) else if (list.any { it.purchaseState == Purchase.PurchaseState.PENDING }) PurchaseOutcome.Pending else null
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseOutcome.Failed("You already own this plan — tap Restore purchases.")
            else -> PurchaseOutcome.Failed(result.debugMessage.ifBlank { "Purchase failed (${result.responseCode})" })
        }
        outcome?.let { outcomes.tryEmit(it) }
    }

    fun close() { if (client.isReady) client.endConnection() }
}
