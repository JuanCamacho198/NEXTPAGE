package com.nextpage

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nextpage.di.AppContainer
import com.nextpage.presentation.navigation.NextPageNavHost
import com.nextpage.presentation.theme.NextPageTheme

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(context = this)
        handleAuthIntent(intent)
        setContent {
            NextPageTheme {
                NextPageNavHost(appContainer = appContainer)
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
}
