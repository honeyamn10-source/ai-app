package ai.byak.app.billing

import android.app.Activity
import android.content.Context
import ai.byak.app.core.di.ApplicationScope
import ai.byak.app.data.security.SecureStore
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.GetBillingChoiceInfoParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val verifier: BillingVerificationRepository,
    private val secureStore: SecureStore,
    @ApplicationScope private val scope: CoroutineScope,
) : PurchasesUpdatedListener {
    private val mutableState = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = mutableState.asStateFlow()
    private val details = mutableMapOf<String, ProductDetails>()
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        scope.launch {
            verifier.entitlement.collect { entitlement ->
                mutableState.update { it.copy(active = entitlement?.active == true) }
            }
        }
        connect()
    }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        mutableState.update { it.copy(ready = true, loading = true, message = null) }
                        queryProducts()
                        restorePurchases()
                        queryBillingChoiceInfo()
                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> mutableState.value = BillingState(
                        loading = false,
                        message = "Google Play Billing is unavailable or blocked by this device's system software.",
                    )
                    else -> mutableState.value = BillingState(
                        loading = false,
                        message = result.debugMessage.ifBlank { "Google Play Billing is unavailable" },
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.update { it.copy(ready = false, message = "Reconnecting to Google Play…") }
            }
        })
    }

    private fun queryProducts() {
        val products = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                handleBillingError(result)
                return@queryProductDetailsAsync
            }
            details.clear()
            queryResult.productDetailsList.forEach { details[it.productId] = it }
            val offers = queryResult.productDetailsList.mapNotNull { product ->
                val phase = product.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull() ?: return@mapNotNull null
                PlanOffer(
                    productId = product.productId,
                    title = if (product.productId == MONTHLY) "BYAK Pro Monthly" else "BYAK Pro Annual",
                    price = phase.formattedPrice,
                    period = if (product.productId == MONTHLY) "per month" else "per year",
                )
            }
            mutableState.update {
                it.copy(
                    loading = false,
                    offers = offers,
                    message = if (offers.isEmpty()) "Plans become available after both subscriptions are activated in this app's Play Console test track." else null,
                )
            }
        }
    }

    private fun queryBillingChoiceInfo() {
        val params = GetBillingChoiceInfoParams.newBuilder()
            .setBillingProgram(BillingClient.BillingProgram.BILLING_CHOICE)
            .build()
        client.getBillingChoiceInfoAsync(params) { result, info ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && info != null) {
                mutableState.update {
                    it.copy(
                        billingChoiceImageUrl = info.playBillingChoiceImageUrl,
                        billingChoiceLoyaltyInfo = info.playBillingLoyaltyInfo,
                    )
                }
            }
        }
    }

    private fun restorePurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(purchases)
            else handleBillingError(result)
        }
    }

    fun purchase(activity: Activity, productId: String) {
        if (secureStore.snapshot().localSession) {
            mutableState.update { it.copy(message = "Sign in with a cloud account before purchasing so your subscription can be securely verified and restored.") }
            return
        }
        val product = details[productId]
        if (product == null) {
            mutableState.update { it.copy(message = "This plan is not available in the current Play Store test track.") }
            return
        }
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken.isNullOrBlank()) {
            mutableState.update { it.copy(message = "No eligible subscription offer is configured for this account.") }
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()
        handleBillingError(
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
            ),
            ignoreSuccess = true,
        )
    }

    fun restore() = restorePurchases()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> mutableState.update { it.copy(message = "Purchase cancelled") }
            else -> handleBillingError(result)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val purchased = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isEmpty()) return
        mutableState.update { it.copy(verifying = true, message = "Confirming the purchase securely…") }
        purchased.forEach { purchase ->
            val productId = purchase.products.firstOrNull { it in PRODUCT_IDS } ?: return@forEach
            scope.launch {
                verifier.verify(productId, purchase.purchaseToken)
                    .onSuccess {
                        if (!purchase.isAcknowledged) acknowledge(purchase)
                        mutableState.update { state -> state.copy(verifying = false, active = true, message = "BYAK Pro is active") }
                    }
                    .onFailure { error ->
                        mutableState.update { state ->
                            state.copy(verifying = false, active = false, message = error.message ?: "Purchase could not be verified")
                        }
                    }
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { result -> if (result.responseCode != BillingClient.BillingResponseCode.OK) handleBillingError(result) }
    }

    private fun handleBillingError(result: BillingResult, ignoreSuccess: Boolean = false) {
        if (ignoreSuccess && result.responseCode == BillingClient.BillingResponseCode.OK) return
        val message = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> null
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play Billing is unavailable or blocked by this device's system software."
            BillingClient.BillingResponseCode.NETWORK_ERROR -> "Google Play could not connect. Check your internet connection and try again."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "This subscription is already owned. Tap Restore purchases."
            else -> result.debugMessage.ifBlank { "Google Play Billing error ${result.responseCode}" }
        }
        mutableState.update { it.copy(loading = false, message = message) }
    }

    companion object {
        const val MONTHLY = "byak_monthly_1"
        const val ANNUAL = "byak_annual_10"
        private val PRODUCT_IDS = setOf(MONTHLY, ANNUAL)
    }
}
