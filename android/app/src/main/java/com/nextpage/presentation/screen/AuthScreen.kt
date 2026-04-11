package com.nextpage.presentation.screen

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextpage.presentation.viewmodel.AuthFailureKind
import com.nextpage.presentation.viewmodel.AuthUiState
import com.nextpage.presentation.viewmodel.AuthViewModel

private const val AUTH_SCREEN_TAG = "AuthScreen"

internal enum class GoogleButtonDisabledReason {
    NONE,
    LOADING,
    CONFIG_ERROR,
    WIRING_ERROR
}

internal fun resolveGoogleButtonDisabledReason(uiState: AuthUiState): GoogleButtonDisabledReason {
    return when {
        uiState.isLoading -> GoogleButtonDisabledReason.LOADING
        !uiState.isConfigured -> GoogleButtonDisabledReason.CONFIG_ERROR
        uiState.hasWiringIssue -> GoogleButtonDisabledReason.WIRING_ERROR
        else -> GoogleButtonDisabledReason.NONE
    }
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onContinueLocal: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val buttonDisabledReason = resolveGoogleButtonDisabledReason(uiState)

    val buttonEnabled = buttonDisabledReason == GoogleButtonDisabledReason.NONE

    LaunchedEffect(uiState.currentSession) {
        if (uiState.currentSession != null) {
            onAuthenticated()
        }
    }

    LaunchedEffect(buttonDisabledReason, uiState.failureKind) {
        Log.d(
            AUTH_SCREEN_TAG,
            "Google sign-in diagnostics: disabledReason=$buttonDisabledReason, isConfigured=${uiState.isConfigured}, hasWiringIssue=${uiState.hasWiringIssue}, isLoading=${uiState.isLoading}, failureKind=${uiState.failureKind}"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "NextPage",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sign in",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!uiState.isConfigured) {
            Text(
                text = "Configuration error: Google sync is unavailable because SUPABASE_URL or SUPABASE_ANON_KEY is invalid. You can continue using local library features.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { viewModel.startGoogleSignIn() },
            enabled = buttonEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Continue with Google")
            }
        }

        if (!buttonEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            val disabledReasonText = when (buttonDisabledReason) {
                GoogleButtonDisabledReason.LOADING -> "Google sign-in is currently in progress."
                GoogleButtonDisabledReason.CONFIG_ERROR -> "Google sign-in is disabled due to a configuration error (SUPABASE_URL or SUPABASE_ANON_KEY)."
                GoogleButtonDisabledReason.WIRING_ERROR -> "Google sign-in is disabled due to an OAuth callback wiring error."
                GoogleButtonDisabledReason.NONE -> null
            }
            if (disabledReasonText != null) {
                Text(
                    text = disabledReasonText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (uiState.hasWiringIssue) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Wiring error: Google OAuth callback wiring is incomplete. Verify redirect and callback handling. Local reading still works.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (!uiState.isConfigured || uiState.hasWiringIssue) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onContinueLocal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue in local mode")
            }
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (uiState.failureKind) {
                    AuthFailureKind.CONFIG_ERROR -> "Configuration error: $error. Continue in local mode until config is fixed."
                    AuthFailureKind.WIRING_ERROR -> "Wiring error: $error. Continue in local mode while wiring is fixed."
                    else -> error
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
