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
                        restorePurchases()
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
            }.sortedBy { it.productId != MONTHLY }
            val published = offers.isNotEmpty()
            mutableState.update {
                it.copy(
                    loading = false,
                    offers = offers,
                    catalogStatus = if (published) PlayCatalogStatus.READY else PlayCatalogStatus.NOT_PUBLISHED,
                    message = if (published) null else
                        "Plans are not published for this installation. Install BYAK from its Google Play internal-test or production listing to enable checkout.",
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
        if (!client.isReady) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(purchases)
            else handleBillingError(result)
        }
    }

    fun purchase(activity: Activity, productId: String) {
        if (secureStore.snapshot().localSession) {
            mutableState.update {
                it.copy(message = "A cloud account is required so BYAK can verify and restore your subscription securely. Sign out, then choose cloud login.")
            }
            return
        }
        val product = details[productId]
        if (product == null) {
            mutableState.update {
                it.copy(message = "This plan is not available for this Play Store installation. Use the internal-test Play link, then try again.")
            }
            return
        }
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken.isNullOrBlank()) {
            mutableState.update { it.copy(message = "No eligible base plan is configured for this Google Play account.") }
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

    fun restore() {
        if (!client.isReady) {
            mutableState.update { it.copy(message = "Google Play is still connecting. Try again in a moment.") }
            return
        }
        mutableState.update { it.copy(message = "Checking your Google Play purchases…") }
        restorePurchases()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> mutableState.update { it.copy(message = "Purchase cancelled.") }
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
                        mutableState.update { state ->
                            state.copy(verifying = false, active = true, message = "BYAK Pro is active.")
                        }
                    }
                    .onFailure { error ->
                        mutableState.update { state ->
                            state.copy(
                                verifying = false,
                                active = false,
                                message = error.message ?: "The server could not verify this purchase.",
                            )
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

    companion object {
        const val MONTHLY = "byak_monthly_1"
        const val ANNUAL = "byak_annual_10"
        private val PRODUCT_IDS = setOf(MONTHLY, ANNUAL)
    }
}
