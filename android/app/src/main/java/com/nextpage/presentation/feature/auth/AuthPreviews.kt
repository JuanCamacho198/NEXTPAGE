package com.nextpage.presentation.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.AuthFailureKind
import com.nextpage.presentation.viewmodel.AuthUiState

@Preview(showBackground = true)
@Composable
private fun AuthScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        LoginScreenContent(
            uiState = AuthUiState(currentSession = null, isCheckingSession = false, isConfigured = true, hasWiringIssue = false, isLoading = false, errorMessage = null, failureKind = AuthFailureKind.NONE),
            onAuthenticated = {}, onContinueLocal = {}, onNavigateToRegister = {}, onNavigateToForgot = {}, onGoogleIdToken = {}, onSetError = {}, onSignIn = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        LoginScreenContent(
            uiState = AuthUiState(currentSession = null, isCheckingSession = false, isConfigured = true, hasWiringIssue = false, isLoading = false, errorMessage = null, failureKind = AuthFailureKind.NONE),
            onAuthenticated = {}, onContinueLocal = {}, onNavigateToRegister = {}, onNavigateToForgot = {}, onGoogleIdToken = {}, onSetError = {}, onSignIn = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    NextPageTheme(darkTheme = false) {
        RegisterScreenContent(
            uiState = AuthUiState(currentSession = null, isCheckingSession = false, isConfigured = true, hasWiringIssue = false, isLoading = false, errorMessage = null, failureKind = AuthFailureKind.NONE),
            onAuthenticated = {}, onNavigateBack = {}, onGoogleIdToken = {}, onSetError = {}, onSignUp = { _, _, _ -> }
        )
    }
}
