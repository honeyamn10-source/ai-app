package ai.byak.app.billing

import ai.byak.app.BuildConfig
import ai.byak.app.data.local.dao.EntitlementDao
import ai.byak.app.data.local.entity.EntitlementEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Singleton
class BillingVerificationRepository @Inject constructor(
    private val client: HttpClient,
    private val dao: EntitlementDao,
) {
    val entitlement: Flow<VerifiedEntitlement?> = dao.observePremium().map { item ->
        item?.let { VerifiedEntitlement(it.active, it.productId, it.expiresAt) }
    }

    suspend fun verify(productId: String, purchaseToken: String): Result<VerifiedEntitlement> = runCatching {
        val response = client.post(BuildConfig.BYAK_BACKEND_BASE_URL.trimEnd('/') + "/api/v1/billing/verify") {
            contentType(ContentType.Application.Json)
            setBody(VerificationRequest(productId, purchaseToken))
        }
        if (!response.status.isSuccess()) error("Purchase verification failed (${response.status.value})")
        val verified = response.body<VerificationResponse>()
        require(verified.verified && verified.active) { "Google Play has not confirmed an active subscription" }
        dao.upsert(
            EntitlementEntity(
                productId = verified.productId,
                purchaseTokenHash = MessageDigest.getInstance("SHA-256")
                    .digest(purchaseToken.encodeToByteArray()).joinToString("") { "%02x".format(it) },
                active = true,
                verifiedAt = System.currentTimeMillis(),
                expiresAt = verified.expiresAtEpochMillis,
            ),
        )
        VerifiedEntitlement(true, verified.productId, verified.expiresAtEpochMillis)
    }

    suspend fun clear() = dao.clear()
}

@Serializable
private data class VerificationRequest(val productId: String, val purchaseToken: String)

@Serializable
private data class VerificationResponse(
    val verified: Boolean,
    val active: Boolean,
    val productId: String,
    val expiresAtEpochMillis: Long? = null,
)
