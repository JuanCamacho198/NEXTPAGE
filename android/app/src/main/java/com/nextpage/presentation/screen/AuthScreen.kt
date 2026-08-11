package com.nextpage.presentation.screen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.nextpage.BuildConfig
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.AuthFailureKind
import com.nextpage.presentation.viewmodel.AuthUiState
import com.nextpage.presentation.viewmodel.AuthViewModel
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageTextField
import com.nextpage.ui.icons.NextPageIcons
import kotlinx.coroutines.launch

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
    AuthScreenContent(
        uiState = uiState,
        onAuthenticated = onAuthenticated,
        onContinueLocal = onContinueLocal,
        onGoogleIdToken = viewModel::handleGoogleIdToken,
        onSetError = viewModel::setError,
        onSignIn = viewModel::signIn,
        onSignUp = viewModel::signUp
    )
}

@Composable
private fun AuthScreenContent(
    uiState: AuthUiState,
    onAuthenticated: () -> Unit,
    onContinueLocal: () -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onSetError: (String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit
) {
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
                    .background(NextPageTheme.colors.welcomeBrandBlue),
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

            // ─── Google sign-in button (Credential Manager) ───────────
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            NextPageButton(
                onClick = {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setServerClientId(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val result = credentialManager.getCredential(
                                context = context,
                                request = request
                            )
                            val credential = result.credential
                            if (credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                val googleIdTokenCredential = try {
                                    GoogleIdTokenCredential.createFrom(credential.data)
                                } catch (e: GoogleIdTokenParsingException) {
                                    Log.e(AUTH_SCREEN_TAG, "Failed to parse Google ID token", e)
                                    return@launch
                                }
                                onGoogleIdToken(googleIdTokenCredential.idToken)
                            } else {
                                    Log.w(AUTH_SCREEN_TAG, "Unexpected credential type: ${credential.type}")
                                    onSetError("Tipo de credencial inesperado: ${credential.type}")
                                }
                            } catch (e: GetCredentialCancellationException) {
                                Log.d(AUTH_SCREEN_TAG, "Google sign-in cancelled by user")
                            } catch (e: GetCredentialException) {
                                Log.e(AUTH_SCREEN_TAG, "Google credential error: type=${e.type} msg=${e.message}", e)
                                onSetError("Error de credencial (${e.type}): ${e.message}")
                            } catch (e: Exception) {
                                Log.e(AUTH_SCREEN_TAG, "Unexpected error in Google sign-in", e)
                                onSetError("Error inesperado: ${e.message}")
                            }
                    }
                },
                enabled = buttonEnabled,
                variant = NextPageButtonVariant.OUTLINED,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = NextPageTheme.colors.welcomeBrandBlue
                ),
                border = BorderStroke(1.dp, NextPageTheme.colors.welcomeBrandBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
            NextPageButton(
                onClick = { showEmailAuth = !showEmailAuth },
                variant = NextPageButtonVariant.FILLED,
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                ) {
                    Icon(
                        imageVector = NextPageIcons.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                    NextPageTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = stringResource(R.string.auth_email_label),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NextPageTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.auth_password_label),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NextPageButton(
                            onClick = { onSignIn(email, password) },
                            enabled = !uiState.isLoading,
                            variant = NextPageButtonVariant.FILLED,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.auth_sign_in))
                        }

                        NextPageButton(
                            onClick = { onSignUp(email, password) },
                            enabled = !uiState.isLoading,
                            variant = NextPageButtonVariant.OUTLINED,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.auth_sign_up))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Dev bypass ───────────────────────────────────────────
            NextPageButton(
                onClick = onContinueLocal,
                variant = NextPageButtonVariant.TEXT
            ) {
                Icon(
                    imageVector = NextPageIcons.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
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

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    AuthScreenContent(
        uiState = AuthUiState(
            currentSession = null,
            isCheckingSession = false,
            isConfigured = true,
            hasWiringIssue = false,
            isLoading = false,
            errorMessage = null,
            failureKind = AuthFailureKind.NONE
        ),
        onAuthenticated = {},
        onContinueLocal = {},
        onGoogleIdToken = {},
        onSetError = {},
        onSignIn = { _, _ -> },
        onSignUp = { _, _ -> }
    )
}
