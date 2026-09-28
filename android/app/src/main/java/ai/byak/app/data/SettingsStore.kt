package ai.byak.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("byak_settings")

enum class ThemeMode { System, Light, Dark }

/** Device-level preferences that survive sign-out: which server to talk to and how the app looks. */
class SettingsStore(private val context: Context, private val defaultServerUrl: String) {
    private val server = stringPreferencesKey("server_url")
    private val theme = stringPreferencesKey("theme_mode")
    private val dynamic = booleanPreferencesKey("dynamic_color")

    val serverUrl: Flow<String> = context.settingsStore.data.map { it[server]?.takeIf(String::isNotBlank) ?: defaultServerUrl }
    val themeMode: Flow<ThemeMode> = context.settingsStore.data.map { p -> ThemeMode.entries.firstOrNull { it.name == p[theme] } ?: ThemeMode.System }
    val dynamicColor: Flow<Boolean> = context.settingsStore.data.map { it[dynamic] ?: false }

    suspend fun currentServerUrl(): String = serverUrl.first()
    suspend fun setServerUrl(url: String) { context.settingsStore.edit { if (url.isBlank()) it.remove(server) else it[server] = url.trim().trimEnd('/') } }
    suspend fun setThemeMode(mode: ThemeMode) { context.settingsStore.edit { it[theme] = mode.name } }
    suspend fun setDynamicColor(enabled: Boolean) { context.settingsStore.edit { it[dynamic] = enabled } }

    companion object {
        /** Release builds only talk to HTTPS servers; debug builds may use the emulator/localhost over HTTP. */
        fun validate(url: String, allowHttp: Boolean): String? {
            val value = url.trim()
            if (value.isBlank()) return null
            val parsed = runCatching { java.net.URI(value) }.getOrNull() ?: return "That doesn't look like a URL"
            if (parsed.host.isNullOrBlank()) return "Include the host, e.g. https://api.example.com"
            return when (parsed.scheme) { "https" -> null; "http" -> if (allowHttp) null else "Use an https:// address"; else -> "Use an https:// address" }
        }
    }
}
