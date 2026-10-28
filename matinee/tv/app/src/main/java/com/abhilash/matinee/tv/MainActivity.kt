package com.abhilash.matinee.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abhilash.matinee.tv.data.Settings
import com.abhilash.matinee.tv.ui.Intro
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.Session
import com.abhilash.matinee.tv.data.SessionState
import com.abhilash.matinee.tv.ui.MatineeApp
import com.abhilash.matinee.tv.ui.MatineeTheme
import com.abhilash.matinee.tv.ui.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLink = MutableStateFlow<Route?>(null)
    private var intro by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink.value = route(intent)
        // The ident plays on a fresh start, over the app, which loads underneath it meanwhile
        intro = savedInstanceState == null && Settings.intro
        setContent {
            MatineeTheme {
                Box {
                    MatineeApp(deepLink)
                    if (intro) Intro { intro = false }
                }
            }
        }
    }

    // Any key skips the ident, and never reaches the app behind it
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!intro) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) intro = false
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)?.let { deepLink.value = it }
    }

    // Picks up progress and library changes made in other Stremio apps (and by the player that just closed)
    override fun onResume() {
        super.onResume()
        if (Session.state.value == SessionState.SignedIn) MatineeApp.scope.launch { runCatching { Library.refresh() } }
    }

    // matinee://details/{type}/{id} (Google TV's Continue watching row) and stremio:///detail/{type}/{id}
    private fun route(intent: Intent?): Route? {
        val uri = intent?.data ?: return null
        val parts = (listOfNotNull(uri.host) + uri.pathSegments).filter { it.isNotEmpty() }
        val at = parts.indexOfFirst { it == "details" || it == "detail" }
        if (at < 0 || parts.size < at + 3) return null
        return Route.Details(parts[at + 1], parts[at + 2])
    }
}
