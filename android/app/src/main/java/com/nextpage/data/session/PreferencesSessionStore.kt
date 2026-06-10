package com.nextpage.data.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nextpage.domain.model.AuthSession

/**
 * Session store backed by EncryptedSharedPreferences (AES-256 GCM).
 *
 * Uses Android Keystore-backed MasterKey to encrypt both keys and values.
 * Data at rest is not readable even with filesystem access.
 */
class PreferencesSessionStore(context: Context) : SessionStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun read(): AuthSession? {
        val userId = preferences.getString(KEY_USER_ID, null) ?: return null
        val email = preferences.getString(KEY_EMAIL, null)
        return AuthSession(userId = userId, email = email)
    }

    override fun write(session: AuthSession) {
        preferences.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "nextpage_auth_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
