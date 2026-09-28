package ai.byak.app.billing

import android.app.Activity
import android.content.Context
import ai.byak.app.core.di.ApplicationScope
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
    @ApplicationScope private val scope: CoroutineScope,
) : PurchasesUpdatedListener {
    private data class PurchasableOffer(
        val product: ProductDetails,
        val offerToken: String,
    )

    private val mutableState = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = mutableState.asStateFlow()
    private val purchasableOffers = mutableMapOf<String, PurchasableOffer>()
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
        mutableState.update {
            it.copy(loading = true, catalogStatus = PlayCatalogStatus.CONNECTING, message = null)
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        mutableState.update {
                            it.copy(
                                ready = true,
                                loading = true,
                                catalogStatus = PlayCatalogStatus.CONNECTING,
                                message = null,
                            )
                        }
                        queryProducts()
                        restorePurchases(showEmptyMessage = false)
                        queryBillingChoiceInfo()
                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> mutableState.value = BillingState(
                        loading = false,
                        catalogStatus = PlayCatalogStatus.UNAVAILABLE,
                        message = "Google Play Billing is unavailable or blocked by this device's system software.",
                    )
                    else -> mutableState.value = BillingState(
                        loading = false,
                        catalogStatus = PlayCatalogStatus.ERROR,
                        message = result.debugMessage.ifBlank { "Google Play Billing is unavailable." },
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.update {
                    it.copy(
                        ready = false,
                        loading = true,
                        catalogStatus = PlayCatalogStatus.CONNECTING,
                        message = "Reconnecting to Google Play…",
                    )
                }
            }
        })
    }

    private fun queryProducts() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BillingCatalog.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                handleBillingError(result)
                return@queryProductDetailsAsync
            }

            purchasableOffers.clear()
            val productDetails = queryResult.productDetailsList
                .firstOrNull { it.productId == BillingCatalog.PRODUCT_ID }
            val subscriptionOffers = productDetails?.subscriptionOfferDetails.orEmpty()
            val offers = if (productDetails == null) {
                emptyList()
            } else {
                BillingCatalog.basePlanOrder.mapNotNull { basePlanId ->
                    val matching = subscriptionOffers.filter { it.basePlanId == basePlanId }
                    val selected = matching.firstOrNull { it.offerId == null }
                        ?: matching.firstOrNull()
                        ?: return@mapNotNull null
                    val phase = selected.pricingPhases.pricingPhaseList.lastOrNull()
                        ?: return@mapNotNull null
                    purchasableOffers[basePlanId] = PurchasableOffer(
                        product = productDetails,
                        offerToken = selected.offerToken,
                    )
                    PlanOffer(
                        planId = basePlanId,
                        productId = productDetails.productId,
                        title = BillingCatalog.title(basePlanId),
                        price = phase.formattedPrice,
                        period = BillingCatalog.period(basePlanId),
                    )
                }
            }
            val published = offers.isNotEmpty()
            mutableState.update {
                it.copy(
                    loading = false,
                    offers = offers,
                    catalogStatus = if (published) PlayCatalogStatus.READY else PlayCatalogStatus.NOT_PUBLISHED,
                    message = if (published) {
                        null
                    } else {
                        "BYAK Pro is not available for this Play account yet. Install BYAK from the closed-test Play link and confirm the byak_pro monthly/yearly base plans are active."
                    },
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
                    )
                }
            }
        }
    }

    private fun restorePurchases(showEmptyMessage: Boolean) {
        if (!client.isReady) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val byakPurchases = purchases.filter { BillingCatalog.PRODUCT_ID in it.products }
                if (byakPurchases.isEmpty()) {
                    mutableState.update {
                        it.copy(
                            loading = false,
                            verifying = false,
                            message = if (showEmptyMessage) {
                                "No active BYAK Pro purchase was found for this Google Play account."
                            } else {
                                it.message
                            },
                        )
                    }
                } else {
                    processPurchases(byakPurchases, showEmptyMessage)
                }
            } else {
                handleBillingError(result)
            }
        }
    }

    fun purchase(activity: Activity, planId: String) {
        if (!client.isReady) {
            mutableState.update { it.copy(message = "Google Play is still connecting. Try again in a moment.") }
            return
        }
        val selected = purchasableOffers[planId]
        if (selected == null) {
            mutableState.update {
                it.copy(message = "This plan is not available for this Play Store account or testing track.")
            }
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(selected.product)
            .setOfferToken(selected.offerToken)
            .build()
        handleBillingError(
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .build(),
            ),
            ignoreSuccess = true,
        )
    }

    fun restore() {
        if (!client.isReady) {
            mutableState.update { it.copy(message = "Google Play is still connecting. Try again in a moment.") }
            return
        }
        mutableState.update { it.copy(message = "Checking your Google Play purchases…") }
        restorePurchases(showEmptyMessage = true)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                processPurchases(purchases.orEmpty(), showEmptyMessage = true)
            BillingClient.BillingResponseCode.USER_CANCELED ->
                mutableState.update { it.copy(message = "Purchase cancelled.") }
            else -> handleBillingError(result)
        }
    }

    private fun processPurchases(purchases: List<Purchase>, showEmptyMessage: Boolean) {
        val matching = purchases.filter { BillingCatalog.PRODUCT_ID in it.products }
        val purchased = matching.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isEmpty()) {
            val pending = matching.any { it.purchaseState == Purchase.PurchaseState.PENDING }
            mutableState.update {
                it.copy(
                    loading = false,
                    verifying = false,
                    message = when {
                        pending -> "Your Google Play purchase is pending. Pro will activate after payment completes."
                        showEmptyMessage -> "No active BYAK Pro purchase was found for this Google Play account."
                        else -> it.message
                    },
                )
            }
            return
        }

        mutableState.update { it.copy(verifying = true, message = "Confirming the purchase securely…") }
        purchased.forEach { purchase ->
            scope.launch {
                verifier.verify(BillingCatalog.PRODUCT_ID, purchase.purchaseToken)
                    .onSuccess {
                        if (!purchase.isAcknowledged) acknowledge(purchase)
                        mutableState.update { state ->
                            state.copy(verifying = false, active = true, message = "BYAK Pro is active.")
                        }
                    }
                    .onFailure { error ->
                        mutableState.update { state ->
                            state.copy(
                                verifying = false,
                                message = error.message
                                    ?: "The BYAK server could not verify this purchase. Your Play purchase remains safe; try Restore purchases again.",
                            )
                        }
                    }
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                handleBillingError(result)
            }
        }
    }

    private fun handleBillingError(result: BillingResult, ignoreSuccess: Boolean = false) {
        if (ignoreSuccess && result.responseCode == BillingClient.BillingResponseCode.OK) return
        val message = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> null
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "Google Play Billing is unavailable or blocked by this device's system software."
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "Google Play could not connect. Check your internet connection and try again."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                "This subscription is already owned. Tap Restore purchases."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "This subscription is not active for your country, account, or testing track."
            else -> result.debugMessage.ifBlank { "Google Play Billing error ${result.responseCode}" }
        }
        mutableState.update {
            it.copy(
                loading = false,
                catalogStatus = if (result.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                    PlayCatalogStatus.UNAVAILABLE
                } else {
                    PlayCatalogStatus.ERROR
                },
                message = message,
            )
        }
    }
}
