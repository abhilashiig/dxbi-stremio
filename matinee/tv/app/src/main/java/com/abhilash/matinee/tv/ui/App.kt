package com.abhilash.matinee.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.key
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
            FadeIn {
                when (top) {
                    Route.Main -> MainScreen()
                    is Route.Details -> DetailsScreen(top.type, top.id)
                    is Route.Streams -> StreamsScreen(top)
                    Route.Report -> DeviceReportScreen()
                    Route.Tests -> PlaybackTestsScreen()
                }
            }
        }
    }
    BackHandler(stack.size > 1) { nav.pop() }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Search("Search", Icons.Search), Home("Home", Icons.Home), Series("Series", Icons.Tv), Movies("Movies", Icons.Movie),
    MyList("My List", Icons.Add), Settings("Settings", Icons.Settings),
}

val RailWidth = 76.dp

@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var railFocused by remember { mutableStateOf(false) }
    var enterContent by remember { mutableStateOf(false) }
    val current = remember { FocusRequester() }
    val content = remember { FocusRequester() }
    val last = remember { LastFocus() }
    fun enter() { if (last.requester?.let { runCatching { it.requestFocus(FocusDirection.Enter) }.getOrDefault(false) } != true) runCatching { content.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Entering the page (Right from the rail) goes back to whatever had focus there, not what is geometrically nearest
        Box(Modifier.fillMaxSize().padding(start = RailWidth).focusRequester(content).focusGroup()) {
            key(tab) {
              CompositionLocalProvider(LocalLastFocus provides last) {
                FadeIn {
                    when (tab) {
                        Tab.Home -> HomeScreen()
                        Tab.Series -> HomeScreen("series")
                        Tab.Movies -> HomeScreen("movie")
                        Tab.Search -> SearchScreen()
                        Tab.MyList -> LibraryScreen()
                        Tab.Settings -> SettingsScreen()
                    }
                }
              }
            }
        }
        NavRail(tab, current, onFocus = { railFocused = it }, onRight = ::enter) { t ->
            if (t == tab) enter() else { last.requester = null; tab = t; enterContent = true }
        }
    }
    // Home-style pages focus themselves; the others are entered from the rail
    LaunchedEffect(tab) {
        if (enterContent && tab !in setOf(Tab.Home, Tab.Series, Tab.Movies)) { delay(80); runCatching { content.requestFocus() } }
        enterContent = false
    }
    // Back: from a page to the rail, then to Home, then out of the app
    BackHandler(!railFocused || tab != Tab.Home) {
        if (!railFocused) runCatching { current.requestFocus() } else { tab = Tab.Home; enterContent = true }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NavRail(tab: Tab, current: FocusRequester, onFocus: (Boolean) -> Unit, onRight: () -> Unit, onSelect: (Tab) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxHeight().width(if (expanded) 300.dp else RailWidth)
            .background(if (expanded) Brush.horizontalGradient(0f to Color.Black, 0.6f to Color(0xF2000000), 1f to Color.Transparent) else SolidColor(Color.Black))
    ) {
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            Image(painterResource(R.drawable.wordmark), "Matinee", Modifier.padding(start = 26.dp, top = 30.dp).width(110.dp))
        }
        Column(
            Modifier.fillMaxHeight().padding(start = 14.dp)
                .onFocusChanged { expanded = it.hasFocus; onFocus(it.hasFocus) }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) { onRight(); true } else false
                }
                .focusRestorer(current)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Tab.entries.forEach { t ->
                Surface(
                    onClick = { onSelect(t) },
                    modifier = if (t == tab) Modifier.focusRequester(current) else Modifier,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent, contentColor = if (t == tab) Color.White else Color(0xFF9A9A9A),
                        focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(t.icon, t.label, Modifier.size(24.dp))
                            Box(Modifier.padding(top = 3.dp).size(16.dp, 2.dp).background(if (t == tab) Gold else Color.Transparent))
                        }
                        if (expanded) {
                            Text(t.label, fontSize = 17.sp, fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(start = 16.dp, end = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

// Where a focused item settles: rows keep the page margin; columns leave room for the row title above the card
@Composable
fun rememberPivot(leading: androidx.compose.ui.unit.Dp = Edge): androidx.compose.foundation.gestures.BringIntoViewSpec {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(density, leading) { with(density) { startPivot(leading.toPx()) } }
}

// Room the top info strip on Home takes above the focused row
val HeroHeight = 250.dp
