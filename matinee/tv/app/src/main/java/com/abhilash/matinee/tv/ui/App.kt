package com.abhilash.matinee.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
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
    Search("Search", NavIcons.Search), Home("Home", NavIcons.Home), Series("Series", NavIcons.Series), Movies("Movies", NavIcons.Movies),
    MyList("My List", NavIcons.MyList), Settings("Settings", NavIcons.Settings),
}

val RailWidth = 64.dp
private val ItemHeight = 40.dp
private val ItemGap = 8.dp

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
    var focusedIndex by remember { mutableIntStateOf(tab.ordinal) }
    val glide = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val width by animateDpAsState(if (expanded) 280.dp else RailWidth, glide, label = "width")
    val scrim by animateFloatAsState(if (expanded) 1f else 0f, tween(260), label = "scrim")
    // The highlight glides to whichever item has focus; the gold bar marks the page you're on
    val highlight by animateDpAsState((ItemHeight + ItemGap) * focusedIndex, glide, label = "highlight")
    val marker by animateDpAsState((ItemHeight + ItemGap) * tab.ordinal, glide, label = "marker")

    Box(Modifier.fillMaxHeight().width(width)) {
        Box(Modifier.fillMaxHeight().width(RailWidth).background(Color.Black))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim }
            .background(Brush.horizontalGradient(0f to Color.Black, 0.55f to Color(0xF0000000), 1f to Color.Transparent)))
        // The brand stays on screen: the "M" mark on the slim rail, the full wordmark when it opens
        AnimatedVisibility(!expanded, enter = fadeIn(tween(250, 120)), exit = fadeOut(tween(120))) {
            Box(Modifier.width(RailWidth).padding(top = 30.dp), contentAlignment = Alignment.TopCenter) {
                Image(MatineeMark, "Matinee", Modifier.size(30.dp))
            }
        }
        AnimatedVisibility(expanded, enter = fadeIn(tween(300, 80)) + slideInHorizontally { -it / 6 }, exit = fadeOut(tween(120))) {
            Image(painterResource(R.drawable.wordmark), "Matinee", Modifier.padding(start = 22.dp, top = 36.dp).width(100.dp))
        }
        Box(Modifier.align(Alignment.CenterStart)) {
            Box(Modifier.offset(x = 3.dp, y = marker + 11.dp).size(3.dp, 18.dp).background(Gold, RoundedCornerShape(2.dp)))
            Box(
                Modifier.offset(x = 12.dp, y = highlight).height(ItemHeight).width(ItemHeight + (136.dp - ItemHeight) * scrim)
                    .graphicsLayer { alpha = scrim }.background(Color(0x29FFFFFF), RoundedCornerShape(12.dp))
            )
            Column(
                Modifier.padding(start = 12.dp)
                    .onFocusChanged { expanded = it.hasFocus; onFocus(it.hasFocus) }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) { onRight(); true } else false
                    }
                    .focusRestorer(current)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(ItemGap),
            ) {
                Tab.entries.forEach { t ->
                    RailItem(t, selected = t == tab, expanded = expanded,
                        modifier = (if (t == tab) Modifier.focusRequester(current) else Modifier)
                            .onFocusChanged { if (it.isFocused) focusedIndex = t.ordinal }) { onSelect(t) }
                }
            }
        }
    }
}

@Composable
private fun RailItem(tab: Tab, selected: Boolean, expanded: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "scale")
    val color by animateColorAsState(
        when { focused -> Color.White; selected -> Color(0xFFEDEDED); else -> Color(0xFF8A8A8A) }, tween(180), label = "color",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(ItemHeight).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(tab.icon, tab.label, Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale }, tint = color)
            AnimatedVisibility(expanded, enter = fadeIn(tween(220, 60)) + slideInHorizontally { -it / 3 }, exit = fadeOut(tween(90))) {
                Text(tab.label, fontSize = 15.sp, color = color, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 14.dp, end = 8.dp))
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
