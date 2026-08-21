package ai.byak.app.billing

import androidx.compose.runtime.Immutable

enum class PlayCatalogStatus { CONNECTING, READY, NOT_PUBLISHED, UNAVAILABLE, ERROR }

@Immutable
data class PlanOffer(val productId: String, val title: String, val price: String, val period: String)

@Immutable
data class BillingState(
    val ready: Boolean = false,
    val loading: Boolean = true,
    val verifying: Boolean = false,
    val active: Boolean = false,
    val offers: List<PlanOffer> = emptyList(),
    val catalogStatus: PlayCatalogStatus = PlayCatalogStatus.CONNECTING,
    val message: String? = null,
    val billingChoiceImageUrl: String? = null,
    val billingChoiceLoyaltyInfo: String? = null,
)

@Immutable
data class VerifiedEntitlement(val active: Boolean, val productId: String, val expiresAtEpochMillis: Long?)
