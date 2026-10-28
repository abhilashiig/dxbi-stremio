package com.abhilash.matinee.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.abhilash.matinee.tv.R
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.MetaPreview
import com.abhilash.matinee.tv.data.Repo
import com.abhilash.matinee.tv.data.Session
import com.abhilash.matinee.tv.data.SessionState
import com.abhilash.matinee.tv.data.Video
import com.abhilash.matinee.tv.tv.WatchNext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

sealed interface Route {
    data object Main : Route
    data class Details(val type: String, val id: String) : Route
    data class Streams(val meta: MetaPreview, val video: Video?, val resumeMs: Long) : Route
    data object Report : Route
    data object Tests : Route
}

class Nav(private val stack: MutableList<Route>) {
    fun push(route: Route) { stack += route }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no Nav") }

@Composable
fun MatineeApp(deepLink: StateFlow<Route?>) {
    val session by Session.state.collectAsState()
    val context = LocalContext.current
    if (session == SessionState.SignedOut) return SignInScreen()

    val stack = remember { mutableStateListOf<Route>(Route.Main) }
    val nav = remember { Nav(stack) }
    val holder = rememberSaveableStateHolder()
    val link by deepLink.collectAsState()

    LaunchedEffect(session) {
        launch {
            runCatching { Library.refresh() }
            WatchNext.publish(context, Library.continueWatching(Library.items.value))
        }
        runCatching { Repo.refreshAddons() }
        runCatching { Repo.refreshHome() }
    }
    LaunchedEffect(link) { link?.let { nav.push(it) } }

    CompositionLocalProvider(LocalNav provides nav) {
        val top = stack.last()
        holder.SaveableStateProvider("${stack.lastIndex}:$top") {
            when (top) {
                Route.Main -> MainScreen()
                is Route.Details -> DetailsScreen(top.type, top.id)
                is Route.Streams -> StreamsScreen(top)
                Route.Report -> DeviceReportScreen()
                Route.Tests -> PlaybackTestsScreen()
            }
        }
    }
    BackHandler(stack.size > 1) { nav.pop() }
}

private val TABS = listOf("Home", "Search", "My List", "Settings")

@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Home takes focus when Main is entered (launch, or Back from a title), not while the tab bar is browsed
    var tabBrowsed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (tab) {
            0 -> HomeScreen(takeFocus = !tabBrowsed)
            1 -> SearchScreen()
            2 -> LibraryScreen()
            else -> SettingsScreen()
        }
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                .padding(start = Edge, top = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.wordmark), "Matinee", Modifier.width(110.dp).padding(end = 28.dp))
            TabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onFocus = { if (tab != i) tabBrowsed = true; tab = i }) {
                        Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
    BackHandler(tab != 0) { tab = 0 }
}

// Where a focused item settles: rows keep the page margin; columns leave room for the row title above the card
@Composable
fun rememberPivot(leading: androidx.compose.ui.unit.Dp = Edge): androidx.compose.foundation.gestures.BringIntoViewSpec {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(density, leading) { with(density) { startPivot(leading.toPx()) } }
}

val RowTitleHeight = 34.dp

// Space the top bar takes over content
val TopBarHeight = 64.dp
val HeroHeight = 250.dp
