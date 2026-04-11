package com.nextpage.data.session

import android.content.Context
import com.nextpage.domain.model.AuthSession

class PreferencesSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
