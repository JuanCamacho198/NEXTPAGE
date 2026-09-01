package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nextpage.di.AppContainer
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.screen.AuthScreen
import com.nextpage.presentation.screen.ForgotScreen
import com.nextpage.presentation.screen.RegisterScreen
import com.nextpage.presentation.viewmodel.AuthViewModel

/**
 * Feature NavGraph for auth: 3 routes (auth, auth/register, auth/forgot).
 *
 * Receives host-scoped [authViewModel] and [appContainer] (for readingGoalPreferences).
 * Never calls viewModel() inside composable — VM scoping stays host-owned.
 * Preserves NextPageDestination strings, fade transitions, and popUpTo(Auth){inclusive=true}
 * verbatim from the monolith.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    authViewModel: AuthViewModel,
    appContainer: AppContainer
) {
    composable(
        route = NextPageDestination.Auth.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        AuthScreen(
            viewModel = authViewModel,
            onAuthenticated = {
                val destination = if (appContainer.readingGoalPreferences.load() == null) {
                    NextPageDestination.OnboardingGoal.route
                } else {
                    NextPageDestination.Home.route
                }
                navController.navigate(destination) {
                    popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                }
            },
            onContinueLocal = {
                authViewModel.continueLocally()
                val destination = if (appContainer.readingGoalPreferences.load() == null) {
                    NextPageDestination.OnboardingGoal.route
                } else {
                    NextPageDestination.Home.route
                }
                navController.navigate(destination) {
                    popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                }
            },
            onNavigateToRegister = {
                navController.navigate(NextPageDestination.AuthRegister.route) {
                    launchSingleTop = true
                }
            },
            onNavigateToForgot = {
                navController.navigate(NextPageDestination.AuthForgot.route) {
                    launchSingleTop = true
                }
            }
        )
    }

    composable(
        route = NextPageDestination.AuthRegister.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        RegisterScreen(
            viewModel = authViewModel,
            onAuthenticated = {
                val destination = if (appContainer.readingGoalPreferences.load() == null) {
                    NextPageDestination.OnboardingGoal.route
                } else {
                    NextPageDestination.Home.route
                }
                navController.navigate(destination) {
                    popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = NextPageDestination.AuthForgot.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        ForgotScreen(
            viewModel = authViewModel,
            onAuthenticated = {
                val destination = if (appContainer.readingGoalPreferences.load() == null) {
                    NextPageDestination.OnboardingGoal.route
                } else {
                    NextPageDestination.Home.route
                }
                navController.navigate(destination) {
                    popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
