package ai.byak.app.data.repository

import ai.byak.app.BuildConfig
import ai.byak.app.data.remote.ApiErrorDto
import ai.byak.app.data.remote.AuthRequest
import ai.byak.app.data.remote.AuthSessionDto
import ai.byak.app.data.security.SecureStore
import ai.byak.app.data.security.hasValidSession
import ai.byak.app.domain.model.Session
import ai.byak.app.domain.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val client: HttpClient,
    private val secureStore: SecureStore,
) : AuthRepository {
    override val session: Flow<Session?> = secureStore.state.map { state ->
        if (!state.hasValidSession(System.currentTimeMillis() / 1_000)) null
        else Session(
            userId = state.userId,
            name = state.userName,
            email = state.userEmail,
            expiresAtEpochSeconds = state.tokenExpiresAtEpochSeconds,
            localOnly = state.localSession,
        )
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        authenticate("/v1/auth/login", AuthRequest(email.trim().lowercase(), password))

    override suspend fun register(name: String, email: String, password: String): Result<Unit> =
        authenticate("/v1/auth/register", AuthRequest(email.trim().lowercase(), password, name.trim()))

    override suspend fun continueOnDevice(name: String): Result<Unit> = runCatching {
        val tokenBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        secureStore.saveSession(
            accessToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes),
            refreshToken = "",
            expiresAtEpochSeconds = Long.MAX_VALUE,
            userId = "local-${UUID.randomUUID()}",
            name = name.trim().ifBlank { "BYAK User" },
            email = "On-device account",
            localOnly = true,
        )
    }

    override suspend fun signOut() {
        val state = secureStore.snapshot()
        if (!state.localSession && state.accessToken.isNotBlank()) {
            runCatching { client.post(url("/v1/auth/logout")) }
        }
        secureStore.clearSession()
    }

    private suspend fun authenticate(path: String, request: AuthRequest): Result<Unit> = runCatching {
        require(request.email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Enter a valid email address" }
        require(request.password.length >= 8) { "Password must contain at least 8 characters" }
        val response = client.post(url(path)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            val message = runCatching { response.body<ApiErrorDto>().error ?: response.body<ApiErrorDto>().message }.getOrNull()
            error(message ?: "Authentication failed (${response.status.value})")
        }
        val session = response.body<AuthSessionDto>()
        secureStore.saveSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAtEpochSeconds = System.currentTimeMillis() / 1_000 + session.expiresIn,
            userId = session.user.id,
            name = session.user.name,
            email = session.user.email,
        )
    }

    private fun url(path: String): String = BuildConfig.BYAK_BACKEND_BASE_URL.trimEnd('/') + path
}
