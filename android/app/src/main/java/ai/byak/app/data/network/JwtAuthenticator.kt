package ai.byak.app.data.network

import ai.byak.app.data.security.SecureStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

@Singleton
class JwtAuthenticator @Inject constructor(
    private val secureStore: SecureStore,
    private val refreshService: RefreshTokenService,
) : Authenticator {
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.retryCount() >= 2) return null
        return synchronized(refreshLock) {
            val state = secureStore.snapshot()
            if (state.localSession || state.refreshToken.isBlank()) return@synchronized null

            val tokenUsed = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (tokenUsed != null && tokenUsed != state.accessToken && state.accessToken.isNotBlank()) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer ${state.accessToken}")
                    .build()
            }

            val session = refreshService.refresh(state.refreshToken)
            if (session == null) {
                runBlocking { secureStore.clearSession() }
                return@synchronized null
            }
            val expiresAt = System.currentTimeMillis() / 1_000 + session.expiresIn
            runBlocking {
                secureStore.saveSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAtEpochSeconds = expiresAt,
                    userId = session.user.id,
                    name = session.user.name,
                    email = session.user.email,
                )
            }
            response.request.newBuilder()
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()
        }
    }

    private fun Response.retryCount(): Int {
        var count = 1
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
