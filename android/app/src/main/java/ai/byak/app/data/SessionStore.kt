package ai.byak.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("byak_secure_session")

class SessionStore(private val context: Context) {
    private val access = stringPreferencesKey("access")
    private val refresh = stringPreferencesKey("refresh")
    private val name = stringPreferencesKey("name")
    private val email = stringPreferencesKey("email")
    val session: Flow<Session?> = context.dataStore.data.map { p -> p[access]?.let { Session(it, p[refresh].orEmpty(), p[name].orEmpty(), p[email].orEmpty()) } }
    suspend fun save(value: Session) = context.dataStore.edit { it[access] = value.accessToken; it[refresh] = value.refreshToken; it[name] = value.name; it[email] = value.email }
    suspend fun updateTokens(accessToken: String, refreshToken: String) = context.dataStore.edit { it[access] = accessToken; it[refresh] = refreshToken }
    suspend fun clear() = context.dataStore.edit { it.clear() }
}

