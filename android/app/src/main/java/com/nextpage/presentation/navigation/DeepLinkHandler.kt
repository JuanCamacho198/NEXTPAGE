package com.nextpage.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.nextpage.MainActivity

/**
 * Cold-start deep-link handler for `nextpage://auth/reset-password`.
 *
 * Verbatim from host:
 * ```
 * val launchIntent = remember { (context as? MainActivity)?.intent?.data }
 * LaunchedEffect(launchIntent, isCheckingSession) {
 *   val isResetPasswordLink = launchIntent?.scheme == "nextpage" &&
 *     launchIntent.path?.contains("reset-password") == true
 *   if (isResetPasswordLink && !isCheckingSession && !isAuthenticated) {
 *     navController.navigate(NextPageDestination.AuthForgot.route) { launchSingleTop = true }
 *   }
 * }
 * ```
 * Warm-start (onNewIntent) stays in [MainActivity.onNewIntent] + GoogleDriveAuthHelper.
 * No new deepLinks are introduced.
 */
@Composable
fun DeepLinkHandler(
    navController: NavController,
    isCheckingSession: Boolean,
    isAuthenticated: Boolean
) {
    val context = LocalContext.current
    val launchIntent: Uri? = remember { (context as? MainActivity)?.intent?.data }
    LaunchedEffect(launchIntent, isCheckingSession) {
        val isResetPasswordLink = launchIntent?.scheme == "nextpage" &&
            launchIntent.path?.contains("reset-password") == true
        if (isResetPasswordLink && !isCheckingSession && !isAuthenticated) {
            navController.navigate(NextPageDestination.AuthForgot.route) {
                launchSingleTop = true
            }
        }
    }
}
