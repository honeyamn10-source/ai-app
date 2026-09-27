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

object GoogleSignIn {
    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Returns a Google ID token for the BYAK server, null if the user cancelled. Must be called with an Activity context. */
    suspend fun idToken(context: Context): String? {
        check(configured) { "Google Sign-In isn't set up in this build (missing BYAK_GOOGLE_WEB_CLIENT_ID)." }
        val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).setAutoSelectEnabled(false).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) GoogleIdTokenCredential.createFrom(credential.data).idToken
            else throw IllegalStateException("Unexpected credential type")
        } catch (e: GetCredentialCancellationException) { null }
        catch (e: NoCredentialException) { throw IllegalStateException("No Google account is available on this device.") }
        catch (e: GetCredentialException) { throw IllegalStateException(e.message ?: "Google Sign-In failed") }
    }
}
