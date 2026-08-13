package com.nextpage.presentation.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import java.util.Locale

private const val AUTH_SCREEN_TAG = "AuthScreen"

/** Minimum password length enforced client-side on the register screen (SCEN-3). */
private const val MIN_PASSWORD_LENGTH = 8

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

// ─── Login (Pencil hZEAK) ─────────────────────────────────────────────

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onContinueLocal: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgot: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreenContent(
        uiState = uiState,
        onAuthenticated = onAuthenticated,
        onContinueLocal = onContinueLocal,
        onNavigateToRegister = onNavigateToRegister,
        onNavigateToForgot = onNavigateToForgot,
        onGoogleIdToken = viewModel::handleGoogleIdToken,
        onSetError = viewModel::setError,
        onSignIn = viewModel::signIn
    )
}

@Composable
private fun LoginScreenContent(
    uiState: AuthUiState,
    onAuthenticated: () -> Unit,
    onContinueLocal: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgot: () -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onSetError: (String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit
) {
    val buttonDisabledReason = resolveGoogleButtonDisabledReason(uiState)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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

    AuthScreenScaffold(showBackArrow = false, onNavigateBack = {}) {
        AuthLogo()

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.auth_welcome_back),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.auth_login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!uiState.isConfigured) {
            Text(
                text = stringResource(R.string.auth_config_error_google_unavailable),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        NextPageTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.auth_email_label),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.auth_password_label)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onNavigateToForgot) {
                Text(
                    text = stringResource(R.string.auth_forgot_password),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        NextPageButton(
            onClick = { onSignIn(email, password) },
            enabled = !uiState.isLoading,
            variant = NextPageButtonVariant.FILLED,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_login_action),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AuthOrDivider(text = stringResource(R.string.auth_or_login_with))

        Spacer(modifier = Modifier.height(16.dp))

        GoogleSignInButton(
            uiState = uiState,
            onGoogleIdToken = onGoogleIdToken,
            onSetError = onSetError
        )

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

        AuthErrorText(uiState)

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text(
                text = stringResource(R.string.auth_register_footer),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ─── Register (Pencil FQKlw) ──────────────────────────────────────────

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RegisterScreenContent(
        uiState = uiState,
        onAuthenticated = onAuthenticated,
        onNavigateBack = onNavigateBack,
        onGoogleIdToken = viewModel::handleGoogleIdToken,
        onSetError = viewModel::setError,
        onSignUp = viewModel::signUp
    )
}

@Composable
private fun RegisterScreenContent(
    uiState: AuthUiState,
    onAuthenticated: () -> Unit,
    onNavigateBack: () -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onSetError: (String) -> Unit,
    onSignUp: (email: String, password: String, fullName: String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(uiState.currentSession) {
        if (uiState.currentSession != null) {
            onAuthenticated()
        }
    }

    AuthScreenScaffold(showBackArrow = true, onNavigateBack = onNavigateBack) {
        Text(
            text = stringResource(R.string.auth_register_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Labels above fields (label param), full-name placeholder per FQKlw.
        NextPageTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = stringResource(R.string.auth_full_name_label),
            placeholder = stringResource(R.string.auth_full_name_placeholder),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        NextPageTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.auth_email_label),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Hint shown as supporting text; client-side length gate on submit
        // (SCEN-3: < 8 chars → hint visible, no sign-up call).
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.auth_password_label),
            hint = stringResource(R.string.auth_password_hint)
        )

        Spacer(modifier = Modifier.height(16.dp))

        NextPageButton(
            onClick = {
                if (password.length >= MIN_PASSWORD_LENGTH) {
                    onSignUp(email, password, fullName)
                }
            },
            enabled = !uiState.isLoading,
            variant = NextPageButtonVariant.FILLED,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_register_action),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AuthOrDivider(text = stringResource(R.string.auth_or_register_with))

        Spacer(modifier = Modifier.height(16.dp))

        GoogleSignInButton(
            uiState = uiState,
            onGoogleIdToken = onGoogleIdToken,
            onSetError = onSetError
        )

        AuthErrorText(uiState)

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onNavigateBack) {
            Text(
                text = stringResource(R.string.auth_login_footer),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ─── Forgot password ──────────────────────────────────────────────────

@Composable
fun ForgotScreen(
    viewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ForgotScreenContent(
        uiState = uiState,
        onAuthenticated = onAuthenticated,
        onNavigateBack = onNavigateBack,
        onResetPassword = viewModel::resetPassword
    )
}

@Composable
private fun ForgotScreenContent(
    uiState: AuthUiState,
    onAuthenticated: () -> Unit,
    onNavigateBack: () -> Unit,
    onResetPassword: (email: String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    LaunchedEffect(uiState.currentSession) {
        if (uiState.currentSession != null) {
            onAuthenticated()
        }
    }

    AuthScreenScaffold(showBackArrow = true, onNavigateBack = onNavigateBack) {
        Text(
            text = stringResource(R.string.auth_forgot_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.auth_forgot_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        NextPageTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.auth_email_label),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        NextPageButton(
            onClick = { onResetPassword(email) },
            enabled = !uiState.isLoading,
            variant = NextPageButtonVariant.FILLED,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_send_reset_email),
                fontWeight = FontWeight.SemiBold
            )
        }

        AuthErrorText(uiState)
    }
}

// ─── Shared building blocks ───────────────────────────────────────────

@Composable
private fun AuthScreenScaffold(
    showBackArrow: Boolean,
    onNavigateBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        if (showBackArrow) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = NextPageIcons.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

@Composable
private fun AuthLogo() {
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
}

@Composable
private fun AuthOrDivider(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun AuthErrorText(uiState: AuthUiState) {
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

@Composable
private fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String? = null,
    errorMessage: String? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    NextPageTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        hint = hint,
        errorMessage = errorMessage,
        trailingIcon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
        trailingIconContentDescription = stringResource(
            if (passwordVisible) R.string.auth_password_hide else R.string.auth_password_show
        ),
        trailingIconOnClick = { passwordVisible = !passwordVisible },
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GoogleSignInButton(
    uiState: AuthUiState,
    onGoogleIdToken: (String) -> Unit,
    onSetError: (String) -> Unit
) {
    val buttonDisabledReason = resolveGoogleButtonDisabledReason(uiState)
    val buttonEnabled = buttonDisabledReason == GoogleButtonDisabledReason.NONE
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Resolve format templates at composition time (stringResource is
    // @Composable; the credential callbacks run inside a coroutine).
    val unexpectedCredentialTemplate = stringResource(R.string.auth_google_unexpected_credential)
    val credentialErrorTemplate = stringResource(R.string.auth_google_credential_error)
    val unexpectedErrorTemplate = stringResource(R.string.auth_google_unexpected_error)

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
                        onSetError(
                            unexpectedCredentialTemplate.format(Locale.getDefault(), credential.type)
                        )
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.d(AUTH_SCREEN_TAG, "Google sign-in cancelled by user")
                } catch (e: GetCredentialException) {
                    Log.e(AUTH_SCREEN_TAG, "Google credential error: type=${e.type} msg=${e.message}", e)
                    onSetError(
                        credentialErrorTemplate.format(Locale.getDefault(), e.type, e.message)
                    )
                } catch (e: Exception) {
                    Log.e(AUTH_SCREEN_TAG, "Unexpected error in Google sign-in", e)
                    onSetError(
                        unexpectedErrorTemplate.format(Locale.getDefault(), e.message)
                    )
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
}

// ─── Previews ─────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun AuthScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        LoginScreenContent(
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
            onNavigateToRegister = {},
            onNavigateToForgot = {},
            onGoogleIdToken = {},
            onSetError = {},
            onSignIn = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        LoginScreenContent(
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
            onNavigateToRegister = {},
            onNavigateToForgot = {},
            onGoogleIdToken = {},
            onSetError = {},
            onSignIn = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    NextPageTheme(darkTheme = false) {
        RegisterScreenContent(
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
            onNavigateBack = {},
            onGoogleIdToken = {},
            onSetError = {},
            onSignUp = { _, _, _ -> }
        )
    }
}
