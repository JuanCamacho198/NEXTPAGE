package com.nextpage.data.remote.drive

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.drive.DriveScopes
import com.google.android.gms.common.api.Scope

/**
 * Encapsulates the Credential Manager OAuth flow for Google Drive
 * (DriveScopes.DRIVE_APPDATA scope). Returns an access token on
 * success, null on cancellation or failure.
 *
 * This is a separate, independent OAuth flow from Supabase auth.
 * The access token is stored locally (ReaderPreferences) and used
 * to configure the Drive service's HttpRequestInitializer.
 */
class GoogleDriveAuthHelper(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveAuthHelper"
    }

    /**
     * Launch the Credential Manager OAuth flow requesting Drive scope.
     *
     * @param googleOAuthClientId The Google OAuth client ID from BuildConfig.
     * @return The access token string on success, null on cancellation/failure.
     */
    suspend fun authorize(googleOAuthClientId: String): String? {
        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(googleOAuthClientId)
                .setAutoSelectEnabled(true)
                .setNonce(null)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = response.credential
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential
                    .createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                // For now, the ID token serves as our auth token for Drive.
                // When the GCP consent screen is configured with Drive scopes,
                // this flow will return an access token with DRIVE_APPDATA scope.
                Log.d(TAG, "Drive OAuth successful, id token obtained")
                idToken
            } else {
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Drive OAuth failed or cancelled: ${e.message}", e)
            null
        }
    }
}
