package com.abhilash.matinee.tv.ui

import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.abhilash.matinee.tv.data.AddonStreams
import com.abhilash.matinee.tv.data.Repo
import com.abhilash.matinee.tv.data.Settings
import com.abhilash.matinee.tv.player.Capabilities
import com.abhilash.matinee.tv.player.PlayRequest
import com.abhilash.matinee.tv.player.QUALITIES
import com.abhilash.matinee.tv.player.RankedStream
import com.abhilash.matinee.tv.player.StreamChoice
import com.abhilash.matinee.tv.player.StreamTags
import com.abhilash.matinee.tv.player.audioSummary
import com.abhilash.matinee.tv.player.hdrLabel
import com.abhilash.matinee.tv.player.instantOnly
import com.abhilash.matinee.tv.player.quality
import com.abhilash.matinee.tv.player.rankStreams
import com.abhilash.matinee.tv.player.releaseLabel
import com.abhilash.matinee.tv.player.sizeLabel
import com.abhilash.matinee.tv.player.sourceLabel
import com.abhilash.matinee.tv.player.time
import com.abhilash.matinee.tv.player.videoSummary

private val Instant = Color(0xFF3CC47C)
private val Amber = Color(0xFFFFB74D)

// One shelf of version cards per quality, under a billboard with everything about the focused version
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StreamsScreen(route: Route.Streams) {
    val activity = LocalContext.current as Activity
    val meta = route.meta
    val videoId = route.video?.id ?: meta.id
    val caps = remember { Capabilities.rank(activity) }
    var results by remember { mutableStateOf<List<AddonStreams>>(emptyList()) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(videoId) {
        results = emptyList()
        Repo.streams(meta.type, videoId).collect { results = results + it }
        done = true
    }

    // Instant-only and the addon filter are remembered across titles
    var instant by remember { mutableStateOf(Settings.instantOnly) }
    var addon by remember { mutableStateOf(Settings.preferredAddon) }

    val ranked = remember(results) { rankStreams(results.flatMap { it.streams }.distinctBy { it.url }, caps) }
    val pool = remember(ranked, instant) { if (instant) instantOnly(ranked) else ranked }
    val addons = remember(ranked, addon) { (ranked.map { it.stream.addon } + listOfNotNull(addon)).distinct() }
    val fromAddon = remember(pool, addon) { if (addon == null) pool else pool.filter { it.stream.addon == addon } }
    // Best resolution first, one shelf each, ranked within it
    val shelves = remember(fromAddon) {
        QUALITIES.mapNotNull { q -> fromAddon.filter { quality(it.tags) == q }.takeIf { it.isNotEmpty() }?.let { q to it } }
    }
    val streams = remember(shelves) { shelves.flatMap { it.second } }

    val first = remember { FocusRequester() }
    val showAll = remember { FocusRequester() }
    val list = rememberLazyListState()
    // Until a key is pressed, focus follows the best stream as slower addons answer and reorder the list
    var touched by remember { mutableStateOf(false) }
    // By URL: every addon that answers re-ranks the list into new objects, the focused row included
    var focusedUrl by remember { mutableStateOf<String?>(null) }
    val detail = streams.firstOrNull { it.stream.url == focusedUrl } ?: streams.firstOrNull()

    fun play(r: RankedStream) = activity.startActivity(
        PlayRequest(
            url = r.stream.url, headers = r.stream.headers, title = meta.name,
            subtitle = route.video?.takeIf { it.label.isNotEmpty() }?.let { "${it.label} · ${it.title}" },
            metaId = meta.id, type = meta.type, videoId = videoId, poster = meta.poster, resumeMs = route.resumeMs,
            logo = meta.logo, background = meta.background, streamText = r.stream.text,
            alternatives = fallbacksFor(r, streams),
        ).intent(activity)
    )

    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) touched = true; false }) {
        FullBackdrop(meta.background)
        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = Edge, end = Edge - 4.dp)) {
                Column(Modifier.weight(1f)) {
                    if (meta.logo != null) {
                        AsyncImage(meta.logo, meta.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                            modifier = Modifier.heightIn(max = 52.dp).widthIn(max = 280.dp))
                    } else {
                        Text(meta.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val episode = route.video?.takeIf { it.label.isNotEmpty() }?.let { v -> listOf(v.label, v.title).filter { it.isNotEmpty() }.joinToString(" · ") }
                    val resume = if (route.resumeMs > 0) "Resumes at ${time(route.resumeMs)}" else null
                    if (episode != null || resume != null) Row(Modifier.padding(top = 8.dp)) {
                        episode?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFDDDDDD), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)) }
                        resume?.let { Text((if (episode != null) "  ·  " else "") + it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gold) }
                    }
                    val failed = results.filter { it.failed }.map { it.addon }
                    Text(
                        buildString {
                            append("${ranked.size} streams from ${results.count { it.streams.isNotEmpty() }} addons")
                            if (!done) append(" · loading…")
                            if (failed.isNotEmpty()) append(" · no answer from ${failed.joinToString()}")
                        },
                        fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // Rows scroll only when a pill would be hidden: the TV default pivot cut "Instant only" off at the left
                if (ranked.isNotEmpty()) CompositionLocalProvider(LocalBringIntoViewSpec provides rememberMinimalScroll()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.widthIn(max = 560.dp).focusRestorer()) {
                        item { FilterPill("Instant only", instant, icon = true) { instant = !instant; Settings.instantOnly = instant } }
                        item { Spacer(Modifier.width(8.dp)) }
                        item { FilterPill("All addons", addon == null) { addon = null; Settings.preferredAddon = null } }
                        items(addons, key = { it }) { a ->
                            FilterPill("$a  ${pool.count { it.stream.addon == a }}", addon == a) {
                                addon = if (addon == a) null else a
                                Settings.preferredAddon = addon
                            }
                        }
                    }
                }
            }
            // Fixed height, so the shelves don't jump as focus moves between versions that say more or less about themselves
            Box(Modifier.fillMaxWidth().height(170.dp).padding(start = Edge, end = Edge, top = 18.dp)) {
                detail?.let { StreamBillboard(it, best = it === streams.first()) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    streams.isNotEmpty() -> CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(36.dp)) {
                        val top = streams.first()
                        LazyColumn(state = list, contentPadding = PaddingValues(top = 4.dp, bottom = 220.dp)) {
                            items(shelves, key = { it.first }) { (q, versions) ->
                                Column {
                                    Text("$q  ·  ${versions.size}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                        modifier = Modifier.padding(start = Edge))
                                    // Vertical room so a focused card's scale and gold edge aren't clipped by the row
                                    // A lazy row keeps its first visible card in place when better versions arrive ahead of it
                                    // (each addon's answer re-ranks the shelf), leaving the best ones scrolled off to the left
                                    val row = rememberLazyListState()
                                    var rowFocused by remember { mutableStateOf(false) }
                                    LaunchedEffect(versions.first().stream.url) { if (!rowFocused || !touched) row.scrollToItem(0) }
                                    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot()) {
                                        LazyRow(
                                            state = row,
                                            contentPadding = PaddingValues(horizontal = Edge, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.onFocusChanged { rowFocused = it.hasFocus }.focusRestorer(),
                                        ) {
                                            items(versions, key = { it.stream.url }) { r ->
                                                StreamCard(
                                                    r, best = r === top, modifier = if (r === top) Modifier.focusRequester(first) else Modifier,
                                                    onFocus = { focusedUrl = r.stream.url },
                                                ) { play(r) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ranked.isNotEmpty() && done -> Column(Modifier.align(Alignment.Center).width(440.dp)) {
                        Text(
                            if (instant && pool.isEmpty()) "None of these streams are ready to play instantly. Uncached ones can take a while to start, or fail."
                            else "Nothing matches these filters.",
                            fontSize = 16.sp, color = Color.White,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { instant = false; addon = null }, modifier = Modifier.focusRequester(showAll)) {
                            Text("Show all ${ranked.size} streams")
                        }
                        LaunchedEffect(Unit) { if (!touched) runCatching { showAll.requestFocus() } }
                    }
                    done -> Text(
                        "No playable streams found.\n\nMatinee plays direct links from your debrid and HTTP addons. Torrent-only results are hidden.",
                        fontSize = 16.sp, color = Color.White, modifier = Modifier.align(Alignment.Center).width(440.dp),
                    )
                    else -> Spinner(Modifier.align(Alignment.Center))
                }
            }
        }
    }
    LaunchedEffect(streams.firstOrNull()?.stream?.url) {
        if (streams.isEmpty() || touched) return@LaunchedEffect
        list.scrollToItem(0)
        // The new best may be a frame or two from being laid out (its shelf scrolling back, or a new shelf above)
        repeat(10) {
            withFrameNanos { }
            if (runCatching { first.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}

// Everything about the focused version, in words rather than the addon's raw text
@Composable
private fun StreamBillboard(r: RankedStream, best: Boolean) {
    val t = r.tags
    val text = r.stream.text
    Column(Modifier.fillMaxSize()) {
        Text(if (best) "BEST FOR THIS TV" else "SELECTED VERSION", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, color = Gold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(quality(t).let { if (it == "Other") "Unknown" else it }, fontSize = 30.sp, fontWeight = FontWeight.Black,
                color = if (t.height >= 2160) Gold else Color.White)
            hdrLabel(t)?.let { label ->
                FormatLogo(label, height = if (label.startsWith("HDR")) 18.dp else 26.dp, modifier = Modifier.padding(start = 14.dp)) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gold, modifier = Modifier.padding(start = 12.dp))
                }
            }
            Spacer(Modifier.width(18.dp))
            when (t.cached) {
                true -> {
                    Icon(Icons.Bolt, null, Modifier.size(14.dp), tint = Instant)
                    Text("Instant", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Instant, modifier = Modifier.padding(start = 3.dp))
                }
                false -> Text("Not cached yet", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Amber)
                null -> Unit
            }
            Text((if (t.cached != null) "  ·  " else "") + r.stream.addon, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Spec("Video", videoSummary(t, text))
                Spec("Audio", audioSummary(t, text))
            }
            Column(Modifier.weight(1f)) {
                Spec("Source", sourceLabel(text))
                Spec("Size", sizeLabel(t.sizeBytes))
            }
        }
        r.warning?.let { Text("⚠ $it", fontSize = 13.sp, color = Amber, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
            ?: (r.stream.filename ?: r.stream.description.takeIf { it.isNotBlank() })?.let {
                Text(it, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
    }
}

// A version on its shelf: HDR type, what tells the file apart, audio and size
@Composable
private fun StreamCard(r: RankedStream, best: Boolean, modifier: Modifier = Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val t = r.tags
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(232.dp).height(100.dp).onFocusChanged { if (it.hasFocus) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = glassRowColors(),
        border = glassRowBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                val hdr = hdrShort(t)
                if (hdr != null) FormatLogo(hdr, height = if (hdr.startsWith("HDR")) 11.dp else 17.dp) {
                    Text(hdr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold)
                } else Text("SDR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
                Spacer(Modifier.weight(1f))
                if (r.warning != null) Text("⚠", fontSize = 13.sp, color = Amber, modifier = Modifier.padding(start = 6.dp))
                if (t.cached == true) Icon(Icons.Bolt, "Instant", Modifier.padding(start = 6.dp).size(14.dp), tint = Instant)
                if (best) Box(Modifier.padding(start = 6.dp)) { Chip("BEST", Gold, Color.Black) }
            }
            Text(releaseLabel(r.stream), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp).weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(audioSummary(t, r.stream.text) ?: r.stream.addon, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text(sizeLabel(t.sizeBytes) ?: "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDDDDDD), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun Spec(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, color = Muted, modifier = Modifier.width(62.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun hdrShort(t: StreamTags) = when {
    t.dolbyVision -> "DV"
    t.hdr10Plus -> "HDR10+"
    t.hdr10 -> "HDR"
    t.hlg -> "HLG"
    else -> null
}

// One version per line, columns aligned so a long list can be scanned: quality, what sets the file apart, size
@Composable
fun StreamRow(r: RankedStream, modifier: Modifier = Modifier, tag: String? = null, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    val t = r.tags
    val q = quality(t)
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = glassRowColors(),
        border = glassRowBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (q == "Other") "—" else q, fontSize = 17.sp, fontWeight = FontWeight.Black, color = if (t.height >= 2160) Gold else Color.White)
                hdrShort(t)?.let { label ->
                    FormatLogo(label, height = if (label.startsWith("HDR")) 10.dp else 15.dp, modifier = Modifier.padding(top = 2.dp)) {
                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (t.cached == true) {
                        Icon(Icons.Bolt, "Instant", Modifier.size(14.dp), tint = Instant)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(releaseLabel(r.stream), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    tag?.let { Box(Modifier.padding(start = 8.dp)) { Chip(it, Gold, Color.Black) } }
                }
                Text(
                    r.warning?.let { "⚠ $it" } ?: listOfNotNull(audioSummary(t, r.stream.text), r.stream.addon).joinToString("  ·  "),
                    fontSize = 12.sp, color = if (r.warning != null) Amber else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(sizeLabel(t.sizeBytes) ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFDDDDDD), textAlign = TextAlign.End,
                modifier = Modifier.width(76.dp))
        }
    }
}

// The next-best streams after the one picked, in list order, skipping ones that look like the same file: a copy of the
// same file from another addon usually sits on the same (possibly broken) debrid server
fun fallbacksFor(picked: RankedStream, shown: List<RankedStream>, max: Int = 4): List<StreamChoice> {
    fun file(r: RankedStream) = r.stream.filename?.lowercase() ?: r.tags.sizeBytes?.let { "size:${it / 50_000_000}" } ?: r.stream.url
    val seen = mutableSetOf(file(picked))
    return shown.dropWhile { it !== picked }.drop(1)
        .filter { seen.add(file(it)) }
        .take(max)
        .map { StreamChoice(it.stream.url, it.stream.headers, it.stream.text) }
}
