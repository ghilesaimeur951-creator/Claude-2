package com.streetblocks.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.streetblocks.app.service.Notifications
import com.streetblocks.app.ui.AppRoot
import com.streetblocks.app.ui.LocalAppContainer
import com.streetblocks.app.ui.theme.StreetBlocksTheme

class MainActivity : ComponentActivity() {

    private val openSessionRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        openSessionRequest.value = intent?.getBooleanExtra(Notifications.EXTRA_OPEN_SESSION, false) == true
        val container = (application as StreetBlocksApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                StreetBlocksTheme {
                    AppRoot(
                        openSessionRequest = openSessionRequest.value,
                        onOpenSessionHandled = { openSessionRequest.value = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Notifications.EXTRA_OPEN_SESSION, false)) openSessionRequest.value = true
    }
}
