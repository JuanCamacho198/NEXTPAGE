package com.nextpage.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextpage.presentation.viewmodel.AuthFailureKind
import com.nextpage.presentation.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onContinueLocal: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.currentSession) {
        if (uiState.currentSession != null) {
            onAuthenticated()
        }
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
            enabled = uiState.isConfigured && !uiState.hasWiringIssue && !uiState.isLoading,
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
