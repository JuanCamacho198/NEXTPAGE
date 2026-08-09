package com.nextpage.presentation.screen.settings

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextpage.BuildConfig
import com.nextpage.R
import com.nextpage.data.remote.drive.DriveAuthResult
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.ui.components.molecules.NextPagePreferenceItem
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage
import com.nextpage.ui.icons.NextPageIcons

/**
 * Data & Storage settings: Google Drive authorization via the PKCE browser flow.
 *
 * The flow is driven by the [GoogleDriveAuthHelper] singleton (see [driveAuthHelper]):
 * the authorize button launches the browser intent; the redirect arrives through
 * `MainActivity.onNewIntent` → helper, and the outcome is observed on
 * [GoogleDriveAuthHelper.authResult]. Per spec, **cancellation is silent** — the
 * spinner stops and NO toast is shown — while failures surface an actionable toast.
 */
@Composable
fun SettingsDataStorageScreen(
    driveAuthHelper: GoogleDriveAuthHelper,
    onNavigateToStatistics: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isAuthorizing by remember { mutableStateOf(false) }
    var driveAuthorized by remember { mutableStateOf(driveAuthHelper.isAuthorized()) }

    val oauthErrorText = stringResource(R.string.settings_drive_error_oauth)

    // Redirect-driven outcome (browser → MainActivity.onNewIntent → helper.onRedirect):
    // Success → authorized; Failure → actionable toast; Canceled → silent (no toast).
    LaunchedEffect(driveAuthHelper) {
        driveAuthHelper.authResult.collect { result ->
            if (result != null) {
                isAuthorizing = false
                when (result) {
                    is DriveAuthResult.Success -> driveAuthorized = true
                    is DriveAuthResult.Failure -> Toast.makeText(
                        context,
                        oauthErrorText,
                        Toast.LENGTH_SHORT
                    ).show()
                    DriveAuthResult.Canceled -> Unit // cancellation is not an error — no toast
                }
                driveAuthHelper.consumeResult()
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // The browser task closed without a redirect (user pressed back) — canceled.
        // Spinner off, NO toast. Redirect results arrive through driveAuthHelper.authResult.
        if (result.resultCode != Activity.RESULT_OK) {
            isAuthorizing = false
        }
    }

    NextPageSettingsSubPage(
        title = stringResource(R.string.settings_data_storage_title),
        onBack = onBack
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        NextPagePreferenceItem(
            icon = NextPageIcons.Storage,
            label = stringResource(R.string.settings_storage),
            onClick = {}
        )

        NextPagePreferenceItem(
            icon = NextPageIcons.CloudSync,
            label = stringResource(R.string.settings_sync),
            onClick = {}
        )

        NextPagePreferenceItem(
            icon = NextPageIcons.ChartBar,
            label = stringResource(R.string.settings_statistics_title),
            onClick = onNavigateToStatistics
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Google Drive Section ─────────────────────────────────────
        Text(
            text = stringResource(R.string.settings_drive_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.settings_drive_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isAuthorizing -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            driveAuthorized -> {
                OutlinedButton(
                    onClick = {
                        driveAuthHelper.disconnect()
                        driveAuthorized = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = stringResource(R.string.settings_drive_disconnect))
                }
            }
            else -> {
                val errorConfigText = stringResource(R.string.settings_drive_error_config)
                Button(
                    onClick = {
                        val clientId = BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID
                        if (clientId.isBlank()) {
                            Toast.makeText(
                                context,
                                errorConfigText,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        isAuthorizing = true
                        signInLauncher.launch(driveAuthHelper.beginAuth())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isAuthorizing
                ) {
                    Text(text = if (isAuthorizing) {
                        stringResource(R.string.settings_drive_authorizing)
                    } else {
                        stringResource(R.string.settings_drive_authorize)
                    })
                }
            }
        }
    }
}
