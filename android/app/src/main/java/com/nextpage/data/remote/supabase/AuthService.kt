package com.nextpage.data.remote.supabase

import android.util.Log
import io.github.jan-tennert.supabase.SupabaseClient
import io.github.jan-tennert.supabase.gotrue.GoTrue
import io.github.jan-tennert.supabase.gotrue.gotrue
import io.github.jan-tennert.supabase.gotrue.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

class AuthService(private val client: SupabaseClient?) {
    companion object {
        private const val TAG = "AuthService"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        if (client != null) {
            client.gotrue.addOnAuthListener(object : io.github.jan-tennert.supabase.gotrue.StatusListener {
                override fun onSessionReceived(session: UserSession) {
                    Log.d(TAG, "Session received: ${session.user?.id}")
                    _authState.value = AuthState.Authenticated(
                        userId = session.user?.id ?: "",
                        email = session.user?.email ?: ""
                    )
                }

                override fun onSuccess() {
                    Log.d(TAG, "Auth success")
                }

                override fun onFailure(error: io.github.jan-tennert.supabase.gotrue.AuthException) {
                    Log.e(TAG, "Auth failure: ${error.message}")
                    _authState.value = AuthState.Error(error.message ?: "Auth failed")
                }
            })

            kotlinx.coroutines.MainScope().let { scope ->
                kotlinx.coroutines.launch { checkCurrentSession() }
            }
        } else {
            _authState.value = AuthState.NotConfigured
        }
    }

    private suspend fun checkCurrentSession() {
        client?.let { c ->
            try {
                val session = c.gotrue.currentSessionOrNull()
                if (session != null) {
                    _authState.value = AuthState.Authenticated(
                        userId = session.user?.id ?: "",
                        email = session.user?.email ?: ""
                    )
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check session", e)
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    suspend fun signUp(email: String, password: String): AuthResult<UserSession> {
        if (client == null) return AuthResult.Error("Supabase not configured")

        return try {
            val session = client.gotrue.signUpWith(email, password) {
                this.email = email
                this.password = password
            }
            AuthResult.Success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Sign up failed", e)
            AuthResult.Error(e.message ?: "Sign up failed")
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult<UserSession> {
        if (client == null) return AuthResult.Error("Supabase not configured")

        return try {
            val session = client.gotrue.signInWith(email, password) {
                this.email = email
                this.password = password
            }
            AuthResult.Success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            AuthResult.Error(e.message ?: "Sign in failed")
        }
    }

    suspend fun signOut() {
        client?.let { c ->
            try {
                c.gotrue.signOut()
                _authState.value = AuthState.Unauthenticated
            } catch (e: Exception) {
                Log.e(TAG, "Sign out failed", e)
            }
        }
    }
}

sealed class AuthState {
    data object NotConfigured : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val userId: String, val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}