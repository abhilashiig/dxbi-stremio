package com.abhilash.matinee.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

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

    // The billboard cycles through the first row's top titles (those with artwork), each filled in from its full
    // details and with its backdrop decoded before it's shown, so every change is a clean crossfade
    val context = LocalPlatformContext.current
    val featuredList = remember(rows) {
        val top = rows.firstOrNull()?.items.orEmpty()
        top.filter { it.background != null && it.poster != null }.take(6).ifEmpty { top.take(1) }
    }
    // Only ever counts up, so the hand of cards always turns the same way round, including from the last to the first
    var featuredIndex by rememberSaveable(type) { mutableIntStateOf(0) }
    var featured by remember { mutableStateOf<MetaPreview?>(null) }
    LaunchedEffect(featuredList, featuredIndex) {
        val f = featuredList.getOrNull(featuredIndex % featuredList.size.coerceAtLeast(1)) ?: return@LaunchedEffect
        if (featured == null) featured = f
        val full = if (f.background == null || f.logo == null || f.description == null) Repo.meta(f.type, f.id)?.preview ?: f else f
        full.background?.let { context.imageLoader.execute(ImageRequest.Builder(context).data(it).build()) }
        featured = full
    }

    // Which card had focus ("billboard" for its buttons): restored when coming back from a title
    var focusId by rememberSaveable { mutableStateOf<String?>(null) }
    // Where focus was when this visit began (coming back from a title), before anything on the page takes it
    val entryFocus = remember { focusId }
    // Saved so that coming back from a title, the page underneath is already in the state it was left in
    var inBillboard by rememberSaveable { mutableStateOf(true) }
    var focused by remember { mutableStateOf<MetaPreview?>(null) }
    var hero by remember { mutableStateOf<MetaPreview?>(null) }

    // Next title every 9 s while the billboard is in view (the timer restarts after each change)
    LaunchedEffect(inBillboard, featuredList, featuredIndex, featured?.id) {
        if (!inBillboard || featuredList.size < 2) return@LaunchedEffect
        delay(9_000)
        featuredIndex++
    }

    // The strip follows focus after a short pause, so fast scrolling doesn't churn through backdrops. It changes
    // once everything is in hand (full details, backdrop decoded), so the crossfade never dips to black.
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
            // Worked out per visit (not per focus move, so rows don't recompose as focus moves), and again once the
            // billboard's titles arrive: the saved library can load a moment before them
            val restoreKey = remember(featuredList.isNotEmpty()) {
                entryFocus ?: if (featuredList.isNotEmpty()) BILLBOARD else continueWatching.firstOrNull()?.let { "cw/${it.id}" }
            }
            val play = remember { FocusRequester() }
            val list = rememberLazyListState()
            // Rows scrolled above the focused one would draw over the info strip, so the list fades out behind it
            val cut by animateFloatAsState(if (inBillboard) 0f else 1f, tween(250), label = "cut")
            val listModifier = Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = if (cut > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
                .drawWithContent {
                    drawContent()
                    if (cut == 0f) return@drawWithContent
                    val h = HeroHeight.toPx()
                    val end = h + 6.dp.toPx()
                    drawRect(Brush.verticalGradient(h / end to Color.Black.copy(alpha = cut), 1f to Color.Transparent, endY = end),
                        size = Size(size.width, end), blendMode = BlendMode.DstOut)
                }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(HeroHeight + 40.dp)) {
                LazyColumn(state = list, modifier = listModifier, contentPadding = PaddingValues(bottom = 320.dp)) {
                    if (featuredList.isNotEmpty()) {
                        item(BILLBOARD) {
                            val f = featured ?: featuredList.first()
                            Billboard(
                                f, featuredList, featuredIndex, visible = inBillboard, play = play,
                                onFocus = { inBillboard = true; focusId = BILLBOARD },
                                onNext = { featuredIndex++ },
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
                                "Continue Watching", continueWatchingPreviews, restoreKey, "cw", Modifier.dimUnlessFocused(!inBillboard),
                                progress = { progress[it.id] },
                                onFocus = { key, meta -> inBillboard = false; focusId = key; focused = meta },
                                onClick = { nav.push(Route.Details(it.type, it.id)) },
                            )
                        }
                    }
                    items(rows, key = { it.key }) { row ->
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot()) {
                            PosterRow(
                                row.title, row.items, restoreKey, row.key, Modifier.dimUnlessFocused(!inBillboard),
                                onFocus = { key, meta -> inBillboard = false; focusId = key; focused = meta },
                                onClick = { nav.push(Route.Details(it.type, it.id)) },
                            )
                        }
                    }
                }
            }
            // Saved library can arrive before the rows and focus Continue Watching, scrolling the billboard away: bring
            // the top back, then focus it once it's laid out
            if (restoreKey == BILLBOARD) LaunchedEffect(Unit) {
                list.scrollToItem(0)
                repeat(10) {
                    withFrameNanos { }
                    if (runCatching { play.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
                }
            }
        }
    }
}

// Full-bleed artwork behind the page: each new backdrop crossfades in and settles with a slow push-out
@Composable
private fun Backdrop(url: String?) {
    Crossfade(url, Modifier.fillMaxSize().clipToBounds(), tween(700), label = "backdrop") { u ->
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

// The buttons sit within the top HeroHeight, so focusing them scrolls the page back to the very top. Only the title
// block changes as the billboard cycles; the buttons stay put and keep focus.
@Composable
private fun Billboard(
    meta: MetaPreview, deck: List<MetaPreview>, position: Int, visible: Boolean, play: FocusRequester,
    onFocus: () -> Unit, onNext: () -> Unit, onPlay: () -> Unit, onInfo: () -> Unit,
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(250), label = "billboard")
    val last = LocalLastFocus.current
    Box(Modifier.fillMaxWidth().height(BillboardHeight).graphicsLayer { this.alpha = alpha }) {
        if (deck.size > 1) Hand(
            deck, position, Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 24.dp),
            onFocus = { last.requester = play; onFocus() }, onNext = onNext, onOpen = onInfo,
        )
        Column(Modifier.padding(start = Edge, top = 48.dp)) {
            AnimatedContent(
                meta,
                transitionSpec = {
                    ((fadeIn(tween(500, delayMillis = 150)) + slideInHorizontally(tween(500, delayMillis = 150)) { it / 24 }) togetherWith
                        fadeOut(tween(220))).using(SizeTransform(clip = false))
                },
                contentKey = { it.id },
                label = "billboard",
            ) { m ->
                Column(Modifier.height(218.dp)) {
                    Text(if (m.type == "movie") "FILM" else typeLabel(m.type).removeSuffix("s").uppercase(),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = Gold)
                    Spacer(Modifier.height(8.dp))
                    if (m.logo != null) {
                        AsyncImage(m.logo, m.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                            modifier = Modifier.heightIn(max = 90.dp).widthIn(max = 380.dp))
                    } else {
                        Text(m.name, fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(400.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    m.description?.let {
                        Text(it, fontSize = 14.sp, color = Color(0xFFE0E0E0), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
                            modifier = Modifier.width(400.dp))
                    }
                }
            }
            // As low as the buttons go: any lower and focusing them would scroll the page past the top (see the pivot)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.onFocusChanged { if (it.hasFocus) { last.requester = play; onFocus() } }) {
                Button(
                    onClick = onPlay, modifier = Modifier.focusRequester(play),
                    colors = ButtonDefaults.colors(containerColor = Color(0xFFE8E8E8), contentColor = Color.Black, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                ) {
                    Icon(Icons.Play, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
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
}

private val CardWidth = 170.dp
private val CardHeight = 255.dp
// Each card turns about a point well below the hand, like cards held in one hand, so turning alone fans them out
private const val Pivot = 2.6f
private const val FanDegrees = 7f
// How far apart neighbouring cards end up
private val Spread = CardHeight * (Pivot - 0.5f) * sin(FanDegrees * PI.toFloat() / 180f)

// Where card i sits in the hand at a given position: 0 the centre, negative to the left, wrapping round the deck
private fun slot(i: Int, position: Float, n: Int): Float {
    val s = ((i - position) % n + n) % n
    return if (s >= n / 2f) s - n else s
}

// The featured titles as a hand of cards, the current one in the centre. Right flicks it aside and brings up the next;
// every card rides the same arc, so a move only changes layer values, never layout.
@Composable
private fun Hand(deck: List<MetaPreview>, position: Int, modifier: Modifier, onFocus: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    val n = deck.size
    val turn = remember { Animatable(position.toFloat()) }
    LaunchedEffect(position) { turn.animateTo(position.toFloat(), spring(dampingRatio = 0.72f, stiffness = 120f)) }
    // Dealt once per visit: coming back from a title finds the hand already out
    var dealt by rememberSaveable { mutableStateOf(false) }
    val deal = remember { Animatable(if (dealt) 1f else 0f) }
    LaunchedEffect(Unit) { deal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)); dealt = true }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.size(CardWidth + Spread * 4, CardHeight + 48.dp)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionRight -> { onNext(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onOpen(); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.TopCenter,
    ) {
        deck.forEachIndexed { i, m ->
            // Stacking only changes when a card crosses the halfway point, so moves don't recompose every frame
            val depth by remember(n) { derivedStateOf { (abs(slot(i, turn.value, n)) + 0.5f).toInt() } }
            AsyncImage(
                m.poster, m.name, contentScale = ContentScale.Crop,
                modifier = Modifier.zIndex(-depth.toFloat()).size(CardWidth, CardHeight)
                    .graphicsLayer {
                        val s = slot(i, turn.value, n)
                        val a = abs(s)
                        // The centre card lands first and the rest fan out after it
                        val landed = ((deal.value - a * 0.12f) / 0.76f).coerceIn(0f, 1f)
                        // The card leaving the centre lifts as it goes, like one slid off the front of the hand
                        val lift = if (s < 0f && s > -1f) sin(PI.toFloat() * -s) else 0f
                        transformOrigin = TransformOrigin(0.5f, Pivot)
                        rotationZ = s * FanDegrees * landed
                        translationY = (1f - landed) * 220.dp.toPx() - lift * 22.dp.toPx()
                        val scale = 1f - 0.05f * a.coerceAtMost(2f) + lift * 0.04f
                        scaleX = scale; scaleY = scale
                        alpha = (3f - a).coerceIn(0f, 1f) * landed
                        shape = RoundedCornerShape(10.dp)
                        clip = true
                    }
                    .drawWithContent {
                        drawContent()
                        val a = abs(slot(i, turn.value, n))
                        drawRect(Color.Black.copy(alpha = 0.3f * a.coerceAtMost(2f)))
                        if (focused && a < 0.5f) drawRoundRect(Color.White, cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(3.dp.toPx()))
                    },
            )
        }
    }
}

// Rows other than the focused one sit back. A scrim rather than alpha, so no row needs its own offscreen buffer.
@Composable
private fun Modifier.dimUnlessFocused(dim: Boolean): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scrim by animateFloatAsState(if (dim && !focused) 0.5f else 0f, tween(300), label = "dim")
    return onFocusChanged { focused = it.hasFocus }.drawWithContent { drawContent(); if (scrim > 0f) drawRect(Color.Black.copy(alpha = scrim)) }
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
