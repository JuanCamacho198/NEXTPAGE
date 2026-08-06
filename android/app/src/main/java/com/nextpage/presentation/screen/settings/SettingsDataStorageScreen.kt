package com.nextpage.presentation.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextpage.BuildConfig
import com.nextpage.R
import com.nextpage.data.remote.drive.DriveTokenStore
import com.nextpage.data.remote.drive.EncryptedDriveTokenStore
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.data.remote.drive.InMemoryDriveTokenStore
import com.nextpage.data.session.ReaderPreferences
import com.nextpage.ui.components.molecules.NextPagePreferenceItem
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage
import kotlinx.coroutines.launch

@Composable
fun SettingsDataStorageScreen(
    onNavigateToStatistics: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readerPreferences = remember { ReaderPreferences(context) }
    val tokenStore: DriveTokenStore = remember {
        runCatching { EncryptedDriveTokenStore(context) }
            .getOrElse { InMemoryDriveTokenStore() }
    }

    var isAuthorizing by remember { mutableStateOf(false) }
    var driveAuthorized by remember { mutableStateOf(tokenStore.isAuthorized()) }

    val oauthErrorText = stringResource(R.string.settings_drive_error_oauth)

    val driveAuthHelper = remember {
        GoogleDriveAuthHelper(
            context = context,
            clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
            tokenStore = tokenStore
        )
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            val token = driveAuthHelper.handleSignInResult(result.data)
            isAuthorizing = false
            if (token != null) {
                driveAuthorized = true
            } else {
                Toast.makeText(
                    context,
                    oauthErrorText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    NextPageSettingsSubPage(
        title = stringResource(R.string.settings_data_storage_title),
        onBack = onBack
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        NextPagePreferenceItem(
            icon = Icons.Outlined.Storage,
            label = stringResource(R.string.settings_storage),
            onClick = {}
        )

        NextPagePreferenceItem(
            icon = Icons.Outlined.CloudSync,
            label = stringResource(R.string.settings_sync),
            onClick = {}
        )

        NextPagePreferenceItem(
            icon = Icons.Outlined.BarChart,
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
                        tokenStore.clear()
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
                        val clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
                        if (clientId.isBlank()) {
                            Toast.makeText(
                                context,
                                errorConfigText,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        isAuthorizing = true
                        signInLauncher.launch(driveAuthHelper.signInClient().signInIntent)
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
