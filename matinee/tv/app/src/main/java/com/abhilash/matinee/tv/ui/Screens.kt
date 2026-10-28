package com.abhilash.matinee.tv.ui

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.abhilash.matinee.tv.BuildConfig
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.Repo
import com.abhilash.matinee.tv.data.SearchRow
import com.abhilash.matinee.tv.data.Session
import com.abhilash.matinee.tv.data.SessionState
import com.abhilash.matinee.tv.data.Settings
import com.abhilash.matinee.tv.data.Stremio
import com.abhilash.matinee.tv.player.PlayRequest
import com.abhilash.matinee.tv.report.ReportScreen
import com.abhilash.matinee.tv.report.deviceReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen() {
    val nav = LocalNav.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchRow>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var focusId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(450)
        searching = true
        results = runCatching { Repo.search(query.trim()) }.getOrDefault(emptyList())
        searching = false
    }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(top = TopBarHeight + 8.dp)) {
        InputField(query, { query = it }, "Search movies and series", Modifier.padding(horizontal = Edge).width(520.dp), imeAction = ImeAction.Search)
        Spacer(Modifier.height(20.dp))
        when {
            searching && results.isEmpty() -> Spinner(Modifier.padding(start = Edge))
            query.trim().length >= 2 && !searching && results.isEmpty() -> Text("Nothing found for \"$query\"", color = Muted, modifier = Modifier.padding(start = Edge))
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(RowTitleHeight)) {
            LazyColumn {
                items(results, key = { it.title }) { row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot()) {
                        PosterRow(row.title, row.items, focusId, row.title, onFocus = { key, _ -> focusId = key }, onClick = { nav.push(Route.Details(it.type, it.id)) })
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen() {
    val nav = LocalNav.current
    val items by Library.items.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val types = listOf(null to "All", "movie" to "Movies", "series" to "Series")
    val shown = remember(items, filter) {
        items.filter { it.inLibrary && (types[filter].first == null || it.type == types[filter].first) }.sortedByDescending { it.mtime }
    }
    var focusId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(top = TopBarHeight + 8.dp)) {
        Row(Modifier.padding(horizontal = Edge), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            types.forEachIndexed { i, (_, label) ->
                Button(onClick = { filter = i }) { Text(label, color = if (i == filter) Gold else Color.Unspecified, fontWeight = if (i == filter) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (shown.isEmpty()) {
            Text(
                if (Session.state.value == SessionState.Guest) "Sign in to see your Stremio library here." else "Titles you add to My List (here or in any Stremio app) show up here.",
                color = Muted, modifier = Modifier.padding(start = Edge),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            contentPadding = PaddingValues(start = Edge, end = Edge, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(shown, key = { it.id }) { li ->
                PosterCard(li.preview(), focusId == li.id, li.progress.takeIf { li.inContinueWatching }, onFocus = { focusId = li.id }) {
                    nav.push(Route.Details(li.type, li.id))
                }
            }
        }
    }
}

private val LANGUAGES = listOf(
    null to "Default", "en" to "English", "hi" to "Hindi", "te" to "Telugu", "ta" to "Tamil", "ml" to "Malayalam", "kn" to "Kannada",
    "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ja" to "Japanese", "ko" to "Korean",
    "zh" to "Chinese", "ar" to "Arabic", "ru" to "Russian",
)

private fun nextLanguage(current: String?, offLabel: String): Pair<String?, String> {
    val i = LANGUAGES.indexOfFirst { it.first == current }
    val next = LANGUAGES[(i + 1) % LANGUAGES.size]
    return next.first to (if (next.first == null) offLabel else next.second)
}

private fun languageLabel(code: String?, offLabel: String) = if (code == null) offLabel else LANGUAGES.firstOrNull { it.first == code }?.second ?: code

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) } // bumps to re-read settings after a change
    val session by Session.state.collectAsState()

    class Item(val title: String, val value: String, val note: String? = null, val action: () -> Unit)

    val items = remember(version, session) {
        listOf(
            Item(
                "Account",
                if (session == SessionState.SignedIn) "Signed in as ${Session.email ?: "your Stremio account"} · Sign out" else "Not signed in · Sign in",
            ) {
                scope.launch {
                    Session.authKey?.let { Stremio.logout(it) }
                    Library.clear()
                    Repo.clear()
                    Session.signOut()
                }
            },
            Item("Match the TV's frame rate", if (Settings.matchFrameRate) "On" else "Off",
                "Switches the TV to 24 Hz for films (and 50 Hz for 25 fps), so motion is smooth instead of juddering") {
                Settings.matchFrameRate = !Settings.matchFrameRate
            },
            Item("Audio language", languageLabel(Settings.audioLanguage, "Stream's default")) {
                Settings.audioLanguage = nextLanguage(Settings.audioLanguage, "").first
            },
            Item("Subtitles", languageLabel(Settings.subtitleLanguage, "Off"), "Subtitles in this language turn on automatically, from the stream or your subtitle addons") {
                Settings.subtitleLanguage = nextLanguage(Settings.subtitleLanguage, "Off").first
            },
            Item("Subtitle size", when { Settings.subtitleScale < 0.95f -> "Small"; Settings.subtitleScale > 1.05f -> "Large"; else -> "Medium" }) {
                Settings.subtitleScale = when { Settings.subtitleScale < 0.95f -> 1f; Settings.subtitleScale > 1.05f -> 0.85f; else -> 1.25f }
            },
            Item("Tunneled playback", if (Settings.tunneling) "On" else "Off",
                "Hands audio/video sync to the TV's chip. Try it if 4K HDR stutters or drifts out of sync; leave it off otherwise") {
                Settings.tunneling = !Settings.tunneling
            },
            Item("Audio decoding fixes", "Reset", "Forget every \"Decode audio on this device\" choice, so passthrough is tried again everywhere") {
                Settings.clearForcePcm()
            },
            Item("Playback tests", "Open", "Dolby Vision, HDR10 and 4K test clips to check this TV") { nav.push(Route.Tests) },
            Item("Device report", "Open", "What this TV can decode, display and pass through") { nav.push(Route.Report) },
            Item("About", "Matinee ${BuildConfig.VERSION_NAME}") {},
        )
    }
    // The scrolling column must start below the tab bar: Down from a tab skips focus groups that overlap it
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(top = TopBarHeight + 12.dp).verticalScroll(rememberScrollState())
            .padding(start = Edge, end = Edge, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Surface(
                onClick = { item.action(); version++ },
                modifier = Modifier.width(720.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Panel, focusedContainerColor = Color(0xFF2A2A2A), contentColor = Color.White, focusedContentColor = Color.White),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(item.value, fontSize = 15.sp, color = Gold)
                    }
                    item.note?.let { Text(it, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
    }
}

// Public test clips (Jellyfin's test-videos, CC BY-SA 4.0): check HDR, Dolby Vision and 4K on a real TV
private const val TESTS = "https://repo.jellyfin.org/test-videos/"
private val TEST_CLIPS = listOf(
    "1080p H.264 (SDR baseline)" to "SDR/AVC/Test%20Jellyfin%201080p%20AVC%203M.mp4",
    "4K HEVC 10-bit SDR" to "SDR/HEVC%2010bit/Test%20Jellyfin%204K%20HEVC%2010bit%2040M.mp4",
    "4K HEVC HDR10" to "HDR/HDR10/HEVC/Test%20Jellyfin%204K%20HEVC%20HDR10%2040M.mp4",
    "4K AV1 HDR10" to "HDR/HDR10/AV1/Test%20Jellyfin%204K%20AV1%20HDR10%2040M.mp4",
    "4K Dolby Vision profile 8.1 (HDR10 fallback)" to "HDR/Dolby%20Vision/Test%20Jellyfin%204K%20DV%20P8.1.mp4",
    "4K Dolby Vision profile 5 (DV-only TVs)" to "HDR/Dolby%20Vision/Test%20Jellyfin%204K%20DV%20P5.mp4",
    "4K Dolby Vision profile 8.4 (HLG)" to "HDR/Dolby%20Vision/Test%20Jellyfin%204K%20DV%20P8.4.mp4",
    "1080p HEVC HDR10" to "HDR/HDR10/HEVC/Test%20Jellyfin%201080p%20HEVC%20HDR10%2010M.mp4",
    "1080p Dolby Vision profile 8.1" to "HDR/Dolby%20Vision/Test%20Jellyfin%201080p%20DV%20P8.1.mp4",
    "1080p Dolby Vision profile 5" to "HDR/Dolby%20Vision/Test%20Jellyfin%201080p%20DV%20P5.mp4",
)

@Composable
fun PlaybackTestsScreen() {
    val activity = LocalContext.current as Activity
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LazyColumn(
        Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(horizontal = Edge, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("Playback tests", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
            Text("Open Info in the player to see the decoder, the display mode and how audio is sent.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        }
        items(TEST_CLIPS, key = { it.second }) { (title, path) ->
            Surface(
                onClick = { activity.startActivity(PlayRequest(url = TESTS + path, title = title).intent(activity)) },
                modifier = Modifier.width(640.dp).then(if (path == TEST_CLIPS.first().second) Modifier.focusRequester(first) else Modifier),
                colors = ClickableSurfaceDefaults.colors(containerColor = Panel, focusedContainerColor = Color(0xFF2A2A2A), contentColor = Color.White, focusedContentColor = Color.White),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
            ) {
                Text(title, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            }
        }
    }
}

@Composable
fun DeviceReportScreen() {
    val activity = LocalContext.current as Activity
    val sections = remember { deviceReport(activity) }
    ReportScreen(sections)
}

@Composable
fun SignInScreen() {
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf<Stremio.LinkCode?>(null) }
    var emailMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // A fresh code every 4.5 minutes; polled until it's approved on link.stremio.com
    LaunchedEffect(Unit) {
        while (true) {
            link = runCatching { Stremio.createLink() }.getOrNull()
            val code = link?.code
            val started = System.currentTimeMillis()
            while (System.currentTimeMillis() - started < 270_000) {
                delay(3_000)
                if (code == null) break
                runCatching { Stremio.readLink(code) }.getOrNull()?.let { Session.signIn(it); return@LaunchedEffect }
            }
            if (code == null) delay(5_000)
        }
    }

    Row(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 72.dp, vertical = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.abhilash.matinee.tv.R.drawable.wordmark), "Matinee", Modifier.width(220.dp))
            Spacer(Modifier.height(28.dp))
            Text("Sign in with your Stremio account", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Your addons, library and Continue Watching come with it, and stay in sync with every Stremio app.", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(top = 6.dp).width(520.dp))
            Spacer(Modifier.height(24.dp))
            if (emailMode) {
                InputField(email, { email = it }, "Email", Modifier.width(420.dp), keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, imeAction = ImeAction.Next)
                Spacer(Modifier.height(10.dp))
                InputField(password, { password = it }, "Password", Modifier.width(420.dp), password = true)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        if (busy) return@Button
                        busy = true; error = null
                        scope.launch {
                            runCatching { Stremio.login(email.trim(), password) }
                                .onSuccess { Session.signIn(it) }
                                .onFailure { error = it.message ?: "Couldn't sign in" }
                            busy = false
                        }
                    }) { Text(if (busy) "Signing in…" else "Sign in") }
                    Button(onClick = { emailMode = false }) { Text("Use a code instead") }
                }
            } else {
                Text("1.  On your phone, scan the QR code, or open link.stremio.com", fontSize = 16.sp, color = Color.White)
                Text("2.  Sign in there and enter this code:", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp))
                Text(link?.code ?: "…", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Gold, letterSpacing = 8.sp, modifier = Modifier.padding(vertical = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { emailMode = true }) { Text("Use email and password") }
                    Button(onClick = { Session.browseAsGuest() }) { Text("Browse without an account") }
                }
            }
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        }
        if (!emailMode) {
            Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(16.dp)) {
                link?.qrUrl?.let { coil3.compose.AsyncImage(it, "QR code", Modifier.width(240.dp).height(240.dp)) } ?: Spinner()
            }
        }
    }
}
