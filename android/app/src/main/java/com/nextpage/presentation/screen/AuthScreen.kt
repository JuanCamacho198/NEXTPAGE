package com.nextpage.presentation.screen

import androidx.compose.runtime.Composable
import com.nextpage.presentation.viewmodel.AuthViewModel

@Composable
fun AuthScreen(viewModel: AuthViewModel, onAuthenticated: () -> Unit, onContinueLocal: () -> Unit, onNavigateToRegister: () -> Unit, onNavigateToForgot: () -> Unit) {
    com.nextpage.presentation.feature.auth.AuthScreen(viewModel = viewModel, onAuthenticated = onAuthenticated, onContinueLocal = onContinueLocal, onNavigateToRegister = onNavigateToRegister, onNavigateToForgot = onNavigateToForgot)
}

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onAuthenticated: () -> Unit, onNavigateBack: () -> Unit) {
    com.nextpage.presentation.feature.auth.RegisterScreen(viewModel = viewModel, onAuthenticated = onAuthenticated, onNavigateBack = onNavigateBack)
}

@Composable
fun ForgotScreen(viewModel: AuthViewModel, onAuthenticated: () -> Unit, onNavigateBack: () -> Unit) {
    com.nextpage.presentation.feature.auth.ForgotScreen(viewModel = viewModel, onAuthenticated = onAuthenticated, onNavigateBack = onNavigateBack)
}
