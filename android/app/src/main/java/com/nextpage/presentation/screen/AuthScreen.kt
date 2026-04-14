package com.nextpage.presentation.screen

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextpage.R
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

internal fun googleButtonDisabledReasonMessageRes(reason: GoogleButtonDisabledReason): Int? {
    return when (reason) {
        GoogleButtonDisabledReason.LOADING -> R.string.auth_google_disabled_loading
        GoogleButtonDisabledReason.CONFIG_ERROR -> R.string.auth_google_disabled_config_error
        GoogleButtonDisabledReason.WIRING_ERROR -> R.string.auth_google_disabled_wiring_error
        GoogleButtonDisabledReason.NONE -> null
    }
}

internal fun authFailureMessageTemplateRes(failureKind: AuthFailureKind?): Int? {
    return when (failureKind) {
        AuthFailureKind.CONFIG_ERROR -> R.string.auth_failure_config_error_with_details
        AuthFailureKind.WIRING_ERROR -> R.string.auth_failure_wiring_error_with_details
        else -> null
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
            text = stringResource(R.string.auth_brand_title),
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.auth_sign_in_title),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!uiState.isConfigured) {
            Text(
                text = stringResource(R.string.auth_config_error_google_unavailable),
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
                Text(stringResource(R.string.auth_continue_with_google))
            }
        }

        if (!buttonEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            val disabledReasonText = googleButtonDisabledReasonMessageRes(buttonDisabledReason)
                ?.let { messageRes -> stringResource(messageRes) }
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
                text = stringResource(R.string.auth_wiring_error_incomplete),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onContinueLocal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.auth_continue_local_mode))
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = authFailureMessageTemplateRes(uiState.failureKind)
                    ?.let { messageTemplateRes -> stringResource(messageTemplateRes, error) }
                    ?: error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
