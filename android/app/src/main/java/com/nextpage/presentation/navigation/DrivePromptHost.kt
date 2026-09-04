package com.nextpage.presentation.navigation

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.nextpage.R
import com.nextpage.data.remote.drive.DriveAuthResult
import com.nextpage.data.remote.drive.DriveConnectPromptGate
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.data.session.DriveConnectPromptPrefs
import com.nextpage.domain.model.AuthSession
import com.nextpage.presentation.viewmodel.LibraryImportEvent
import com.nextpage.ui.components.atoms.NextPageDialog
import com.nextpage.ui.icons.NextPageIcons
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Consolidates the Drive connect prompt host: single collect(authResult) + consumeResult(),
 * Gate.shouldShow(), DriveConnectPromptPrefs, dialog + driveConnectAuthLauncher.
 *
 * Exactly one collect on [GoogleDriveAuthHelper.authResult] must exist (spec DrivePromptHost singleton).
 * The accept path reuses the PR2 singleton helper (same pending PKCE state as Settings).
 * importEvents success → Gate.shouldShow() → showDriveConnectPrompt.
 */
@Composable
fun DrivePromptHost(
    driveAuthHelper: GoogleDriveAuthHelper,
    prefs: DriveConnectPromptPrefs,
    authSession: AuthSession?,
    importEvents: SharedFlow<LibraryImportEvent>,
    snackbarHostState: SnackbarHostState,
    syncService: com.nextpage.data.remote.sync.SyncService,
    onAuthSuccessPostPush: suspend () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDriveConnectPrompt by rememberSaveable { mutableStateOf(false) }
    var drivePromptAuthInFlight by remember { mutableStateOf(false) }

    // importEvents → snackbar + sync push + Gate prompt
    LaunchedEffect(importEvents) {
        importEvents.collect { event ->
            when (event) {
                is LibraryImportEvent.Success -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.library_import_success, event.title)
                    )
                    scope.launch { syncService.schedulePush() }
                    val shouldOfferPrompt = DriveConnectPromptGate.shouldShow(
                        importSucceeded = true,
                        driveEnabled = driveAuthHelper.isAuthorized(),
                        providerIsGoogle = authSession?.provider == "google",
                        declinedForUser = prefs.declinedForUser(),
                        currentUser = authSession?.userId
                    )
                    if (shouldOfferPrompt) showDriveConnectPrompt = true
                }
                is LibraryImportEvent.Failure -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.library_import_failure, event.message)
                    )
                }
            }
        }
    }

    val driveConnectAuthLauncher: ManagedActivityResultLauncher<android.content.Intent, androidx.activity.result.ActivityResult> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) {
                drivePromptAuthInFlight = false
            }
        }

    // Singleton authResult collector — exactly one in the host
    LaunchedEffect(driveAuthHelper) {
        driveAuthHelper.authResult.collect { result ->
            if (result != null) {
                when (result) {
                    is DriveAuthResult.Success -> {
                        prefs.clearDeclined()
                        scope.launch { syncService.schedulePush() }
                    }
                    is DriveAuthResult.Failure -> {
                        if (drivePromptAuthInFlight) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.settings_drive_error_oauth),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    DriveAuthResult.Canceled -> Unit
                }
                drivePromptAuthInFlight = false
                driveAuthHelper.consumeResult()
            }
        }
    }

    // Sync error collector (previously inline in host; kept here to preserve single drive host ownership)
    LaunchedEffect(syncService) {
        syncService.syncState.collect { state ->
            if (state is com.nextpage.data.remote.sync.DriveSyncState.Error) {
                snackbarHostState.showSnackbar("Sync error: ${state.message}")
            }
        }
    }

    if (showDriveConnectPrompt) {
        NextPageDialog(
            title = context.getString(R.string.drive_connect_prompt_title),
            body = context.getString(R.string.drive_connect_prompt_body),
            confirmText = context.getString(R.string.drive_connect_prompt_accept),
            dismissText = context.getString(R.string.drive_connect_prompt_decline),
            icon = NextPageIcons.CloudDownload,
            onConfirm = {
                showDriveConnectPrompt = false
                val clientId = com.nextpage.BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID
                if (clientId.isBlank()) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.settings_drive_error_config),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@NextPageDialog
                }
                drivePromptAuthInFlight = true
                driveConnectAuthLauncher.launch(driveAuthHelper.beginAuth())
            },
            onDismiss = {
                showDriveConnectPrompt = false
                val userId = authSession?.userId
                DriveConnectPromptGate.markDeclined(userId)?.let {
                    prefs.persistDeclined(it)
                }
            }
        )
    }
}
