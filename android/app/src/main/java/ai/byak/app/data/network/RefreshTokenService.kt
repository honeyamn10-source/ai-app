package ai.byak.app.data.network

import ai.byak.app.BuildConfig
import ai.byak.app.core.di.BareOkHttp
import ai.byak.app.data.remote.AuthSessionDto
import ai.byak.app.data.remote.RefreshRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class RefreshTokenService @Inject constructor(
    @BareOkHttp private val client: OkHttpClient,
    private val json: Json,
) {
    fun refresh(refreshToken: String): AuthSessionDto? {
        if (refreshToken.isBlank()) return null
        val request = Request.Builder()
            .url(BuildConfig.BYAK_BACKEND_BASE_URL.trimEnd('/') + "/v1/auth/refresh")
            .post(
                json.encodeToString(RefreshRequest(refreshToken))
                    .toRequestBody("application/json; charset=utf-8".toMediaType()),
            )
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string().takeIf(String::isNotBlank)?.let {
                    json.decodeFromString<AuthSessionDto>(it)
                }
            }
        }.getOrNull()
    }
}
