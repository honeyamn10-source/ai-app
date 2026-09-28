package ai.byak.app.billing

import android.app.Activity
import android.content.Context
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
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlanOffer(val productId: String, val title: String, val price: String, val details: ProductDetails, val offerToken: String)

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

    suspend fun offers(productIds: List<String>): List<PlanOffer> {
        if (productIds.isEmpty() || !ready()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder().setProductList(productIds.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
        }).build()
        val details: List<ProductDetails> = client.queryProductDetails(params).productDetailsList ?: emptyList()
        return details.mapNotNull { product ->
            val offers = product.subscriptionOfferDetails.orEmpty()
            // Base plan (no offerId) first; promotional offers can be added later deliberately.
            val offer = offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() ?: return@mapNotNull null
            val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@mapNotNull null
            PlanOffer(product.productId, product.name, price, product, offer.offerToken)
        }.sortedBy { productIds.indexOf(it.productId) }
    }

    /** Opens the Play purchase sheet. The account id binds the purchase to this BYAK user server-side. */
    fun launch(activity: Activity, offer: PlanOffer, obfuscatedAccountId: String): String? {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details).setOfferToken(offer.offerToken).build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product))
            .apply { if (obfuscatedAccountId.isNotBlank()) setObfuscatedAccountId(obfuscatedAccountId) }.build()
        val result = client.launchBillingFlow(activity, params)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null else result.debugMessage.ifBlank { "Google Play couldn't start the purchase (${result.responseCode})" }
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
