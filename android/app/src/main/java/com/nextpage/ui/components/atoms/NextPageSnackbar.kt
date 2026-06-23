package com.nextpage.ui.components.atoms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material 3 `Snackbar` pre-colored with the NextPage inverse theme
 * tokens. Use as the `snackbar` slot of `Scaffold` driven by a
 * `SnackbarHostState`.
 *
 * @param snackbarData The current message and optional action, provided
 *   by the `SnackbarHostState.showSnackbar` result. Re-read on every
 *   recomposition from the `SnackbarHostState`.
 * @param modifier Modifier applied to the snackbar container.
 *
 * **Visual**: inverse-themed — `colorScheme.inverseSurface` background,
 * `colorScheme.inverseOnSurface` text, `colorScheme.inversePrimary`
 * action label.
 * **Behavior**: delegates dismissal and action handling to Material 3.
 * The snackbar auto-dismisses according to `snackbarData.duration` and
 * forwards the action callback from `snackbarData`.
 * **Recomposition**: recomposes whenever the host state emits a new
 * `SnackbarData` (e.g. after `showSnackbar` is awaited and a new
 * message is queued).
 */
@Composable
fun NextPageSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier,
        snackbarData = snackbarData,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionColor = MaterialTheme.colorScheme.inversePrimary
    )
}
