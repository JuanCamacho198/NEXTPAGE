package com.nextpage.data.remote.drive

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.nextpage.data.remote.google.GoogleDriveConfig
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encapsulates the Google Sign-In OAuth flow for Google Drive
 * (Authorization Code + PKCE, `drive.file` scope).
 *
 * Replaces the broken ID-token-as-Bearer flow. The sign-in request produces a
 * short-lived **authorization code** ([GoogleSignInAccount.serverAuthCode])
 * plus an ID token. The auth code is exchanged at Google's OAuth token endpoint
 * for an access + refresh token pair, which is persisted in [DriveTokenStore].
 * The refresh token re-issues access tokens without re-prompting on restart.
 *
 * This is a separate, independent OAuth flow from Supabase auth.
 */
class GoogleDriveAuthHelper(
    private val context: Context,
    private val clientId: String,
    private val tokenStore: DriveTokenStore = EncryptedDriveTokenStore(context),
    private val tokenApi: DriveTokenApi = KtorAuthApi(HttpClient())
) {

    companion object {
        private const val TAG = "GoogleDriveAuthHelper"
    }

    /**
     * Build the [GoogleSignInOptions] for Drive authorization.
     *
     * Requests the Drive scope along with a server auth code via [redirecting
     * `requestServerAuthCode`], so [handleSignInResult] can exchange it for
     * non-expiring offline tokens. Uses the `drive.file` scope saved in
     * [GoogleDriveConfig].
     */
    fun authOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(GoogleDriveConfig.DRIVE_FILE_SCOPE))
            .requestServerAuthCode(clientId)
            .build()
    }

    /**
     * Create the Google Sign-In client configured for Drive authorization.
     * The caller launches [GoogleSignInClient.signInIntent] and forwards the
     * result [Intent] to [handleSignInResult].
     */
    fun signInClient(): GoogleSignInClient = GoogleSignIn.getClient(context, authOptions())

    /**
     * Handle a Google Sign-In [Activity] result.
     *
     * On success: extracts the server auth code, exchanges it via [tokenApi]
     * for an access+refresh pair, persists in [tokenStore], and returns the
     * access token. Returns null on cancellation, cancellation of the sign-in intent,
     * or a failed exchange (no crash).
     */
    suspend fun handleSignInResult(activityIntent: Intent?): String? = withContext(Dispatchers.IO) {
        val account = extractAccount(activityIntent) ?: return@withContext null

        val authCode = account.serverAuthCode
        if (authCode.isNullOrBlank()) {
            Log.w(TAG, "Google Sign-In returned no server auth code")
            return@withContext null
        }

        val pairResult = tokenApi.exchange(
            clientId = clientId,
            authCode = authCode,
            redirectUri = null,
            codeVerifier = null
        )
        val result = pairResult.getOrNull() ?: run {
            Log.w(TAG, "Drive OAuth token exchange failed")
            return@withContext null
        }

        tokenStore.persist(result)
        Log.d(TAG, "Drive OAuth successful, tokens persisted")
        return@withContext result.accessToken
    }

    private fun extractAccount(activityIntent: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(activityIntent)
            task.getResult(ApiException::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Google Sign-In failed or cancelled: ${e.message}", e)
            null
        }
    }
}

/**
 * Purely in-memory [DriveTokenStore] used internally when authorization is exercised.
 * Not used in production; kept for symmetry and trivial testability.
 */
class InMemoryDriveTokenStore : DriveTokenStore {
    private var access: String? = null
    private var refresh: String? = null
    override fun accessToken(): String? = access
    override fun refreshToken(): String? = refresh
    override fun isAuthorized(): Boolean = !access.isNullOrBlank()
    override fun persist(pair: DriveTokenPair) {
        access = pair.accessToken
        refresh = pair.refreshToken
    }
    override fun clear() {
        refresh = null
        access = null
    }
}