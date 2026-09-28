package ai.byak.app.auth

import android.content.Context
import ai.byak.app.BuildConfig
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** A signed-in Google account: the ID token (for a BYAK server) plus the profile shown in the app. */
data class GoogleAccount(val idToken: String, val email: String, val name: String)

object GoogleSignIn {
    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Shows the Google account picker; null if the user cancelled. Must be called with an Activity context. */
    suspend fun signIn(context: Context): GoogleAccount? {
        check(configured) { "Google Sign-In isn't set up in this build (missing BYAK_GOOGLE_WEB_CLIENT_ID)." }
        val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).setAutoSelectEnabled(false).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) GoogleIdTokenCredential.createFrom(credential.data).let { GoogleAccount(it.idToken, it.id, it.displayName ?: it.givenName ?: it.id.substringBefore('@')) }
            else throw IllegalStateException("Unexpected credential type")
        } catch (e: GetCredentialCancellationException) { null }
        catch (e: NoCredentialException) { throw IllegalStateException("No Google account is available on this device.") }
        catch (e: GetCredentialException) { throw IllegalStateException(explain(e)) }
    }

    /** The common real-world causes, in words a developer testing from Play can act on. */
    private fun explain(e: GetCredentialException): String {
        val raw = e.message.orEmpty()
        return when {
            Regex("\\b10\\b").containsMatchIn(raw) || raw.contains("DEVELOPER_ERROR", true) -> "Google Sign-In isn't set up correctly: the app needs a Web application OAuth client ID, plus an Android OAuth client for ai.byak.app with the Play app-signing SHA-1, in the same Google Cloud project."
            Regex("\\b16\\b").containsMatchIn(raw) || raw.contains("reauth", true) -> "Google couldn't verify this app. Check the SHA-1 fingerprints registered in Google Cloud Console."
            else -> raw.ifBlank { "Google Sign-In failed" }
        }
    }
}
