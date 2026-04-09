package com.nextpage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nextpage.di.AppContainer
import com.nextpage.presentation.navigation.NextPageNavHost
import com.nextpage.presentation.theme.NextPageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = AppContainer(context = this)
        setContent {
            NextPageTheme {
                NextPageNavHost(appContainer = appContainer)
            }
        }
    }
}
