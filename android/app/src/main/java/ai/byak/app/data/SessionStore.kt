package ai.byak.app.data

import android.content.Context
import ai.byak.app.security.CredentialVault
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("byak_secure_session")

/** Persists the session with tokens encrypted by a hardware-backed Android Keystore key. */
class SessionStore(private val context: Context, private val vault: CredentialVault = CredentialVault()) {
    private val access = stringPreferencesKey("access_enc")
    private val refresh = stringPreferencesKey("refresh_enc")
    private val name = stringPreferencesKey("name")
    private val email = stringPreferencesKey("email")
    private val legacyAccess = stringPreferencesKey("access")
    private val legacyRefresh = stringPreferencesKey("refresh")

    val session: Flow<Session?> = context.dataStore.data.map { p ->
        val accessToken = p[access]?.let(::open) ?: p[legacyAccess]
        val refreshToken = p[refresh]?.let(::open) ?: p[legacyRefresh]
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) null
        else Session(accessToken, refreshToken, p[name].orEmpty(), p[email].orEmpty())
    }

    suspend fun current(): Session? = session.first()

    suspend fun save(value: Session) {
        context.dataStore.edit {
            it[access] = vault.encrypt(value.accessToken); it[refresh] = vault.encrypt(value.refreshToken)
            it[name] = value.name; it[email] = value.email
            it.remove(legacyAccess); it.remove(legacyRefresh)
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { it[access] = vault.encrypt(accessToken); it[refresh] = vault.encrypt(refreshToken); it.remove(legacyAccess); it.remove(legacyRefresh) }
    }

    suspend fun updateProfile(displayName: String, emailAddress: String) { context.dataStore.edit { it[name] = displayName; it[email] = emailAddress } }

    suspend fun clear() { context.dataStore.edit { it.clear() } }

    // A key invalidated by the OS (e.g. after a device restore) just signs the user out.
    private fun open(value: String): String? = runCatching { vault.decrypt(value) }.getOrNull()
}
