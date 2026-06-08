package com.nextpage

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import com.nextpage.data.session.AppLanguagePreferences
import com.nextpage.data.session.AppThemePreferences
import com.nextpage.di.AppContainer
import com.nextpage.domain.model.ThemeMode
import com.nextpage.presentation.navigation.NextPageNavHost
import com.nextpage.presentation.theme.NextPageTheme

class MainActivity : AppCompatActivity() {
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        val langPrefs = rememberLanguagePrefs()
        val langCode = langPrefs.load()
        if (langCode != null) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(langCode)
            )
        }
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(context = this)
        handleAuthIntent(intent)
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

    private fun rememberLanguagePrefs(): AppLanguagePreferences {
        return AppLanguagePreferences(this)
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
}
