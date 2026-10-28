package com.abhilash.matinee.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.MetaPreview
import com.abhilash.matinee.tv.data.Repo
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(takeFocus: Boolean) {
    val nav = LocalNav.current
    val rows by Repo.home.collectAsState()
    val library by Library.items.collectAsState()
    val continueWatching = remember(library) { Library.continueWatching(library).take(20) }
    val progress = remember(continueWatching) { continueWatching.associate { it.id to it.progress } }
    val continueWatchingPreviews = remember(continueWatching) { continueWatching.map { it.preview() } }

    // Which card had focus: restored when coming back from a title, so Back lands where you were
    var focusId by rememberSaveable { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf<MetaPreview?>(null) }
    var hero by remember { mutableStateOf<MetaPreview?>(null) }

    // The hero follows focus after a short pause, so fast scrolling doesn't churn through backdrops. It changes
    // once everything is in hand (full details, backdrop decoded), so the crossfade never dips to black.
    val context = LocalPlatformContext.current
    LaunchedEffect(focused) {
        val target = focused ?: return@LaunchedEffect
        if (hero == null) hero = target // first paint: the title at once, the rest when it arrives
        delay(200)
        val full = if (target.background == null || target.description == null) Repo.meta(target.type, target.id)?.preview ?: target else target
        full.background?.let { context.imageLoader.execute(ImageRequest.Builder(context).data(it).build()) }
        hero = full
    }

    // Before anything is focused (arriving via the tab bar), the hero shows the first title
    LaunchedEffect(rows.isNotEmpty() || continueWatching.isNotEmpty()) {
        if (focused == null) focused = continueWatchingPreviews.firstOrNull() ?: rows.firstOrNull()?.items?.firstOrNull()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Hero(hero)
        if (rows.isEmpty() && continueWatching.isEmpty()) {
            SkeletonRows(Modifier.padding(top = HeroHeight))
        } else {
            // Worked out once per visit (not per focus move), so moving focus doesn't recompose every row
            val restoreKey = remember {
                if (!takeFocus) null else focusId ?: if (continueWatching.isNotEmpty()) "cw/${continueWatching.first().id}"
                else rows.firstOrNull()?.let { r -> r.items.firstOrNull()?.let { "${r.key}/${it.id}" } }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(RowTitleHeight)) {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize().padding(top = HeroHeight),
                ) {
                    if (continueWatching.isNotEmpty()) item("cw") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot()) {
                            PosterRow(
                                "Continue Watching", continueWatchingPreviews, restoreKey, "cw",
                                progress = { progress[it.id] },
                                onFocus = { key, meta -> focusId = key; focused = meta },
                                onClick = { nav.push(Route.Details(it.type, it.id)) },
                            )
                        }
                    }
                    items(rows, key = { it.key }) { row ->
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot()) {
                            PosterRow(
                                row.title, row.items, restoreKey, row.key,
                                onFocus = { key, meta -> focusId = key; focused = meta },
                                onClick = { nav.push(Route.Details(it.type, it.id)) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(240.dp)) }
                }
            }
        }
    }
}

@Composable
fun Hero(meta: MetaPreview?) {
    Box(Modifier.fillMaxWidth().height(HeroHeight + 120.dp)) {
        Crossfade(meta?.background, Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(), tween(700), label = "backdrop") { url ->
            if (url != null) {
                // Each new backdrop settles in with a slow push-out, Apple TV style; finite, so it costs nothing at rest
                val zoom = remember { Animatable(1.06f) }
                LaunchedEffect(Unit) { zoom.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing)) }
                AsyncImage(url, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value })
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.25f to Color.Black, 0.6f to Color(0x99000000), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black)))
        AnimatedContent(
            meta,
            transitionSpec = {
                ((fadeIn(tween(450, delayMillis = 120)) + slideInVertically(tween(450, delayMillis = 120)) { it / 14 }) togetherWith fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "hero",
        ) { m ->
            if (m != null) {
                Column(Modifier.padding(start = Edge, top = TopBarHeight + 8.dp).width(440.dp)) {
                    if (m.logo != null) {
                        AsyncImage(m.logo, m.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                            modifier = Modifier.heightIn(max = 64.dp).fillMaxWidth(0.8f))
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
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.0f to Color(0xF2000000), 0.55f to Color(0xB3000000), 1f to Color(0x33000000))))
        Box(Modifier.fillMaxWidth().fillMaxHeight().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black)))
    }
}
