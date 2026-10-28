package com.abhilash.matinee.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.MetaPreview
import com.abhilash.matinee.tv.data.Repo
import com.abhilash.matinee.tv.data.typeLabel
import kotlinx.coroutines.delay

private const val BILLBOARD = "billboard"
private val BillboardHeight = 400.dp

// Home, and the Series and Movies pages (the same page narrowed to one type)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(type: String? = null) {
    val nav = LocalNav.current
    val allRows by Repo.home.collectAsState()
    val rows = remember(allRows, type) { if (type == null) allRows else allRows.filter { it.type == type } }
    val library by Library.items.collectAsState()
    val continueWatching = remember(library, type) { Library.continueWatching(library).filter { type == null || it.type == type }.take(20) }
    val progress = remember(continueWatching) { continueWatching.associate { it.id to it.progress } }
    val continueWatchingPreviews = remember(continueWatching) { continueWatching.map { it.preview() } }

    // The billboard features the top title of the first row, filled in from its full details
    val featuredPreview = rows.firstOrNull()?.items?.firstOrNull()
    var featured by remember(featuredPreview?.id) { mutableStateOf(featuredPreview) }
    LaunchedEffect(featuredPreview?.id) {
        val f = featuredPreview ?: return@LaunchedEffect
        if (f.background == null || f.logo == null || f.description == null) Repo.meta(f.type, f.id)?.preview?.let { featured = it }
    }

    // Which card had focus ("billboard" for its buttons): restored when coming back from a title
    var focusId by rememberSaveable { mutableStateOf<String?>(null) }
    var inBillboard by remember { mutableStateOf(true) }
    var focused by remember { mutableStateOf<MetaPreview?>(null) }
    var hero by remember { mutableStateOf<MetaPreview?>(null) }

    // The strip follows focus after a short pause, so fast scrolling doesn't churn through backdrops. It changes
    // once everything is in hand (full details, backdrop decoded), so the crossfade never dips to black.
    val context = LocalPlatformContext.current
    LaunchedEffect(focused) {
        val target = focused ?: return@LaunchedEffect
        if (hero == null) hero = target
        delay(200)
        val full = if (target.background == null || target.description == null) Repo.meta(target.type, target.id)?.preview ?: target else target
        full.background?.let { context.imageLoader.execute(ImageRequest.Builder(context).data(it).build()) }
        hero = full
    }

    val shown = if (inBillboard || hero == null) featured else hero
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Backdrop(shown?.background)
        AnimatedVisibility(!inBillboard, enter = fadeIn(tween(300)), exit = fadeOut(tween(200))) { InfoStrip(hero) }
        if (rows.isEmpty() && continueWatching.isEmpty()) {
            SkeletonRows(Modifier.padding(top = BillboardHeight))
        } else {
            // Worked out once per visit (not per focus move), so moving focus doesn't recompose every row
            val restoreKey = remember {
                focusId ?: if (featured != null) BILLBOARD else continueWatching.firstOrNull()?.let { "cw/${it.id}" }
            }
            val play = remember { FocusRequester() }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(HeroHeight + 40.dp)) {
                LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 320.dp)) {
                    featured?.let { f ->
                        item(BILLBOARD) {
                            Billboard(
                                f, visible = inBillboard, play = play,
                                onFocus = { inBillboard = true; focusId = BILLBOARD },
                                onPlay = {
                                    if (f.type == "movie") nav.push(Route.Streams(f, null, library.firstOrNull { it.id == f.id }?.timeOffset ?: 0))
                                    else nav.push(Route.Details(f.type, f.id))
                                },
                                onInfo = { nav.push(Route.Details(f.type, f.id)) },
                            )
                        }
                    }
                    if (continueWatching.isNotEmpty()) item("cw") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot()) {
                            PosterRow(
                                "Continue Watching", continueWatchingPreviews, restoreKey, "cw",
                                progress = { progress[it.id] },
                                onFocus = { key, meta -> inBillboard = false; focusId = key; focused = meta },
                                onClick = { nav.push(Route.Details(it.type, it.id)) },
                            )
                        }
                    }
                    items(rows, key = { it.key }) { row ->
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot()) {
                            PosterRow(
                                row.title, row.items, restoreKey, row.key,
                                onFocus = { key, meta -> inBillboard = false; focusId = key; focused = meta },
                                onClick = { nav.push(Route.Details(it.type, it.id)) },
                            )
                        }
                    }
                }
            }
            if (restoreKey == BILLBOARD) LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
        }
    }
}

// Full-bleed artwork behind the page: each new backdrop crossfades in and settles with a slow push-out
@Composable
private fun Backdrop(url: String?) {
    Crossfade(url, Modifier.fillMaxSize(), tween(700), label = "backdrop") { u ->
        if (u != null) {
            val zoom = remember { Animatable(1.05f) }
            LaunchedEffect(Unit) { zoom.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing)) }
            AsyncImage(u, null, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value })
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black, 0.4f to Color(0xB3000000), 0.75f to Color.Transparent)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 0.85f to Color(0xE6000000), 1f to Color.Black)))
}

// The buttons sit within the top HeroHeight, so focusing them scrolls the page back to the very top
@Composable
private fun Billboard(meta: MetaPreview, visible: Boolean, play: FocusRequester, onFocus: () -> Unit, onPlay: () -> Unit, onInfo: () -> Unit) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(250), label = "billboard")
    Column(Modifier.fillMaxWidth().height(BillboardHeight).graphicsLayer { this.alpha = alpha }.padding(start = Edge, top = 48.dp)) {
        Text(if (meta.type == "movie") "FILM" else typeLabel(meta.type).removeSuffix("s").uppercase(),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = Gold)
        Spacer(Modifier.height(8.dp))
        if (meta.logo != null) {
            AsyncImage(meta.logo, meta.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                modifier = Modifier.heightIn(max = 90.dp).widthIn(max = 380.dp))
        } else {
            Text(meta.name, fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(480.dp))
        }
        Spacer(Modifier.height(12.dp))
        meta.description?.let {
            Text(it, fontSize = 14.sp, color = Color(0xFFE0E0E0), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
                modifier = Modifier.width(440.dp))
        }
        Spacer(Modifier.height(18.dp))
        val last = LocalLastFocus.current
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.onFocusChanged { if (it.hasFocus) { last.requester = play; onFocus() } }) {
            Button(
                onClick = onPlay, modifier = Modifier.focusRequester(play),
                colors = ButtonDefaults.colors(containerColor = Color(0xFFE8E8E8), contentColor = Color.Black, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
            ) {
                Icon(Icons.Play, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onInfo,
                colors = ButtonDefaults.colors(containerColor = Color(0x66808080), contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
            ) {
                Icon(Icons.Info, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("More Info", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// What the focused card is, above the rows
@Composable
private fun InfoStrip(meta: MetaPreview?) {
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        AnimatedContent(
            meta,
            transitionSpec = {
                ((fadeIn(tween(450, delayMillis = 120)) + slideInVertically(tween(450, delayMillis = 120)) { it / 14 }) togetherWith fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "hero",
        ) { m ->
            if (m != null) {
                Column(Modifier.padding(start = Edge, top = 36.dp).width(460.dp)) {
                    if (m.logo != null) {
                        AsyncImage(m.logo, m.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                            modifier = Modifier.heightIn(max = 64.dp).widthIn(max = 320.dp))
                    } else {
                        Text(m.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    MetaLine(m)
                    Spacer(Modifier.height(8.dp))
                    m.description?.let {
                        Text(it, fontSize = 14.sp, color = Color(0xFFCFCFCF), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FullBackdrop(url: String?) {
    Box(Modifier.fillMaxSize()) {
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.0f to Color(0xF2000000), 0.55f to Color(0xB3000000), 1f to Color(0x33000000))))
        Box(Modifier.fillMaxWidth().fillMaxHeight().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black)))
    }
}
