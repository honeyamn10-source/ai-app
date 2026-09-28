package ai.byak.app.billing

import androidx.compose.runtime.Immutable

enum class PlayCatalogStatus { CONNECTING, READY, NOT_PUBLISHED, UNAVAILABLE, ERROR }

internal object BillingCatalog {
    const val PRODUCT_ID = "byak_pro"
    const val MONTHLY_BASE_PLAN_ID = "monthly"
    const val YEARLY_BASE_PLAN_ID = "yearly"
    val basePlanOrder = listOf(MONTHLY_BASE_PLAN_ID, YEARLY_BASE_PLAN_ID)

    fun title(basePlanId: String): String = when (basePlanId) {
        MONTHLY_BASE_PLAN_ID -> "BYAK Pro Monthly"
        YEARLY_BASE_PLAN_ID -> "BYAK Pro Yearly"
        else -> "BYAK Pro"
    }

    fun period(basePlanId: String): String = when (basePlanId) {
        MONTHLY_BASE_PLAN_ID -> "per month"
        YEARLY_BASE_PLAN_ID -> "per year"
        else -> ""
    }
}

@Immutable
data class PlanOffer(
    val planId: String,
    val productId: String,
    val title: String,
    val price: String,
    val period: String,
)

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
