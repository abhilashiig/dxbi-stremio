package com.abhilash.matinee.tv

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink.value = route(intent)
        setContent { MatineeTheme { MatineeApp(deepLink) } }
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
