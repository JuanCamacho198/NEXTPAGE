package com.nextpage.presentation.screen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showEmailAuth by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Logo 80dp ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.app_logo_initials),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Brand: NextPage ──────────────────────────────────────
            Text(
                text = stringResource(R.string.auth_brand_title),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Subtitle ─────────────────────────────────────────────
            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ─── Config/Wiring error messages ─────────────────────────
            if (!uiState.isConfigured) {
                Text(
                    text = stringResource(R.string.auth_config_error_google_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ─── Google sign-in button ────────────────────────────────
            OutlinedButton(
                onClick = { viewModel.startGoogleSignIn() },
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.auth_continue_with_google),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!buttonEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                val disabledReasonText = googleButtonDisabledReasonMessageRes(buttonDisabledReason)
                    ?.let { messageRes -> stringResource(messageRes) }
                if (disabledReasonText != null) {
                    Text(
                        text = disabledReasonText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (uiState.hasWiringIssue) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.auth_wiring_error_incomplete),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Email sign-in button ─────────────────────────────────
            Button(
                onClick = { showEmailAuth = !showEmailAuth },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = stringResource(R.string.auth_sign_in_email),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ─── Email/password fields (animated expand) ──────────────
            AnimatedVisibility(
                visible = showEmailAuth,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.auth_email_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.auth_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.signIn(email, password) },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(stringResource(R.string.auth_sign_in))
                        }

                        OutlinedButton(
                            onClick = { viewModel.signUp(email, password) },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(stringResource(R.string.auth_sign_up))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Dev bypass ───────────────────────────────────────────
            TextButton(
                onClick = onContinueLocal
            ) {
                Text(
                    text = stringResource(R.string.auth_continue_local_dev),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }

            // ─── Error message ────────────────────────────────────────
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = authFailureMessageTemplateRes(uiState.failureKind)
                        ?.let { messageTemplateRes -> stringResource(messageTemplateRes, error) }
                        ?: error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
