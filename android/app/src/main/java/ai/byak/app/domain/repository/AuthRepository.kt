package ai.byak.app.domain.repository

import ai.byak.app.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<Session?>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun register(name: String, email: String, password: String): Result<Unit>
    suspend fun continueOnDevice(name: String): Result<Unit>
    suspend fun signOut()
}
