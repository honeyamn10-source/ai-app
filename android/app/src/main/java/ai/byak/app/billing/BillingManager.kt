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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlanOffer(val productId: String, val title: String, val price: String, val period: String)
data class BillingState(
    val ready: Boolean = false,
    val loading: Boolean = true,
    val active: Boolean = false,
    val offers: List<PlanOffer> = emptyList(),
    val message: String? = null
)

class BillingManager(context: Context) : PurchasesUpdatedListener {
    private val _state = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = _state.asStateFlow()
    private val details = mutableMapOf<String, ProductDetails>()
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init { connect() }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(ready = true, loading = true, message = null)
                    queryProducts()
                    restorePurchases()
                } else {
                    _state.value = BillingState(loading = false, message = result.debugMessage.ifBlank { "Google Play Billing is unavailable" })
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(ready = false, message = "Reconnecting to Google Play…")
            }
        })
    }

    private fun queryProducts() {
        val products = PRODUCT_IDS.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(loading = false, message = result.debugMessage)
                return@queryProductDetailsAsync
            }
            details.clear()
            queryResult.productDetailsList.forEach { details[it.productId] = it }
            val offers = queryResult.productDetailsList.mapNotNull { product ->
                val phase = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    ?: return@mapNotNull null
                PlanOffer(
                    productId = product.productId,
                    title = if (product.productId == MONTHLY) "BYAK Pro Monthly" else "BYAK Pro Annual",
                    price = phase.formattedPrice,
                    period = if (product.productId == MONTHLY) "per month" else "per year"
                )
            }
            _state.value = _state.value.copy(loading = false, offers = offers, message = if (offers.isEmpty()) "Plans appear after this app and its subscription products are published in Google Play Console." else null)
        }
    }

    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(purchases)
        }
    }

    fun purchase(activity: Activity, productId: String) {
        val product = details[productId]
        if (product == null) {
            _state.value = _state.value.copy(message = "This plan is not available in the current Play Store test track.")
            return
        }
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "No eligible subscription offer is configured for this account.")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offerToken).build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) _state.value = _state.value.copy(message = result.debugMessage)
    }

    fun restore() = restorePurchases()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.value = _state.value.copy(message = "Purchase cancelled")
            else -> _state.value = _state.value.copy(message = result.debugMessage)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val purchased = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        _state.value = _state.value.copy(active = purchased.isNotEmpty(), message = if (purchased.isNotEmpty()) "BYAK Pro is active" else _state.value.message)
        purchased.filterNot { it.isAcknowledged }.forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) _state.value = _state.value.copy(message = result.debugMessage)
            }
        }
    }

    companion object {
        const val MONTHLY = "byak_monthly_1"
        const val ANNUAL = "byak_annual_10"
        private val PRODUCT_IDS = listOf(MONTHLY, ANNUAL)
    }
}
