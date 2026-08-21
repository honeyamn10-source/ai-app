package ai.byak.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val email: String,
    val password: String,
    val name: String? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthUserDto(
    val id: String,
    val email: String,
    val name: String,
)

@Serializable
data class AuthSessionDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: AuthUserDto,
)

@Serializable
data class ApiErrorDto(val error: String? = null, val message: String? = null)
