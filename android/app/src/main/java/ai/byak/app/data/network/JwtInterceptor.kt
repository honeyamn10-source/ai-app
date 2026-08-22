package ai.byak.app.data.network

import ai.byak.app.BuildConfig
import ai.byak.app.data.security.SecureStore
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class JwtInterceptor @Inject constructor(
    private val secureStore: SecureStore,
) : Interceptor {
    private val backendHost = runCatching { URI(BuildConfig.BYAK_BACKEND_BASE_URL).host }.getOrNull()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val state = secureStore.snapshot()
        val isBackend = backendHost != null && request.url.host.equals(backendHost, ignoreCase = true)
        val isPublicAuth = request.url.encodedPath in PUBLIC_AUTH_PATHS
        if (!isBackend || isPublicAuth || state.localSession || state.accessToken.isBlank()) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer ${state.accessToken}")
                .build(),
        )
    }

    private companion object {
        val PUBLIC_AUTH_PATHS = setOf("/v1/auth/login", "/v1/auth/register", "/v1/auth/refresh")
    }
}
