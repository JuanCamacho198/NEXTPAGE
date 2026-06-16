package com.nextpage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.nextpage.data.session.AppThemePreferences
import com.nextpage.debug.CrashNotificationHelper
import com.nextpage.debug.DebugLog
import com.nextpage.debug.DebugPrefs
import com.nextpage.debug.DebugStateHolder
import com.nextpage.di.AppContainer
import com.nextpage.domain.model.ThemeMode
import com.nextpage.presentation.navigation.NextPageNavHost
import com.nextpage.presentation.theme.NextPageTheme

class MainActivity : AppCompatActivity() {

    private lateinit var appContainer: AppContainer

    // Must be registered before onCreate (per the AndroidX ActivityResult API contract).
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result is informational — we post only if granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(context = this)
        handleAuthIntent(intent)

        // Debug-only: capture pending crash from previous run, ensure the
        // notification channel exists, and request POST_NOTIFICATIONS on 33+.
        if (BuildConfig.DEBUG && DebugPrefs.isEnabled(this)) {
            CrashNotificationHelper.ensureChannel(this)
            CrashNotificationHelper.showCrashNotificationIfAny(this)
            maybeRequestNotificationPermission()
        }

        setContent {
            val appThemePrefs = remember { AppThemePreferences(this@MainActivity) }
            var appThemeMode by remember { mutableStateOf(appThemePrefs.load()) }

            val darkTheme = when (appThemeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            NextPageTheme(darkTheme = darkTheme) {
                NextPageNavHost(
                    appContainer = appContainer,
                    appThemeMode = appThemeMode,
                    onAppThemeModeChanged = { mode ->
                        appThemeMode = mode
                        appThemePrefs.save(mode)
                    }
                )
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "nextpage" && data.host == "auth" && data.path == "/callback") {
            appContainer.submitAuthCallback(data.toString())
        }
    }

    // ── ActionMode override (debug only) ──────────────────────────────
    //
    // Android's [Activity.onActionModeStarted] is the activity-level hook
    // for the system-managed selection toolbar. In debug builds we kill any
    // ActionMode immediately so the native Copy/Share/Select All floating
    // bar never appears on top of the custom color-picker / context menu.
    //
    // For TYPE_FLOATING (the floating toolbar introduced in API 23), the
    // [View.startActionMode] variant is what creates it; the WebView
    // setCustomSelectionActionModeCallback only suppresses the regular
    // text-selection mode. Overriding onActionModeStarted at the Activity
    // level is the most reliable cross-API nuclear option.
    override fun onActionModeStarted(mode: ActionMode) {
        if (BuildConfig.DEBUG) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mode.type.toString()
            } else "PRIMARY"
            DebugLog.warn(
                "ActionMode",
                "onActionModeStarted: title='${mode.title}', type=$type"
            )
            DebugStateHolder.recordActionModeEvent("onActionModeStarted", type)
            mode.finish()
            return
        }
        super.onActionModeStarted(mode)
    }

    override fun onActionModeFinished(mode: ActionMode) {
        if (BuildConfig.DEBUG) {
            DebugLog.info("ActionMode", "onActionModeFinished: title='${mode.title}'")
            DebugStateHolder.recordActionModeEvent("onActionModeFinished", "—")
        }
        super.onActionModeFinished(mode)
    }
}
