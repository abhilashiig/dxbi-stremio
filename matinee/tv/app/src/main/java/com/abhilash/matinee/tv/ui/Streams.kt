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

// Two panes: the title and everything about the focused version on the left, a compact list to scan on the right
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

    // Instant-only and the addon filter are remembered across titles; quality is per visit
    var instant by remember { mutableStateOf(Settings.instantOnly) }
    var addon by remember { mutableStateOf(Settings.preferredAddon) }
    var only by remember { mutableStateOf<String?>(null) }

    val ranked = remember(results) { rankStreams(results.flatMap { it.streams }.distinctBy { it.url }, caps) }
    val pool = remember(ranked, instant) { if (instant) instantOnly(ranked) else ranked }
    val addons = remember(ranked, addon) { (ranked.map { it.stream.addon } + listOfNotNull(addon)).distinct() }
    val fromAddon = remember(pool, addon) { if (addon == null) pool else pool.filter { it.stream.addon == addon } }
    val qualityCounts = remember(fromAddon) { fromAddon.groupingBy { quality(it.tags) }.eachCount() }
    // Best resolution first, ranked within each
    val streams = remember(fromAddon, only) {
        QUALITIES.filter { only == null || it == only }.flatMap { q -> fromAddon.filter { quality(it.tags) == q } }
    }

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
        Row(Modifier.fillMaxSize().padding(start = Edge, top = 36.dp, end = 32.dp)) {
            Column(Modifier.width(320.dp).fillMaxHeight().padding(bottom = 28.dp)) {
                if (meta.logo != null) {
                    AsyncImage(meta.logo, meta.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                        modifier = Modifier.heightIn(max = 68.dp).widthIn(max = 300.dp))
                } else {
                    Text(meta.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                route.video?.takeIf { it.label.isNotEmpty() }?.let { v ->
                    Text(listOf(v.label, v.title).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = Color(0xFFDDDDDD), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                }
                if (route.resumeMs > 0) Text("Resumes at ${time(route.resumeMs)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gold,
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(18.dp))
                Box(Modifier.weight(1f)) { detail?.let { StreamDetail(it, best = it === streams.first()) } }
                val failed = results.filter { it.failed }.map { it.addon }
                Text(
                    buildString {
                        append("${ranked.size} streams from ${results.count { it.streams.isNotEmpty() }} addons")
                        if (!done) append(" · loading…")
                        if (failed.isNotEmpty()) append("\nNo answer from ${failed.joinToString()}")
                    },
                    fontSize = 12.sp, color = Muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Rows scroll only when a pill would be hidden: the TV default pivot cut "Instant only" off at the left
                if (ranked.isNotEmpty()) CompositionLocalProvider(LocalBringIntoViewSpec provides rememberMinimalScroll()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp), modifier = Modifier.focusRestorer()) {
                        item { FilterPill("Instant only", instant, icon = true) { instant = !instant; Settings.instantOnly = instant } }
                        item { Spacer(Modifier.width(8.dp)) }
                        item { FilterPill("All qualities", only == null) { only = null } }
                        items(QUALITIES.filter { (qualityCounts[it] ?: 0) > 0 || it == only }, key = { it }) { q ->
                            FilterPill("$q  ${qualityCounts[q] ?: 0}", only == q) { only = if (only == q) null else q }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp), modifier = Modifier.focusRestorer()) {
                        item { FilterPill("All addons", addon == null) { addon = null; Settings.preferredAddon = null } }
                        items(addons, key = { it }) { a ->
                            FilterPill("$a  ${pool.count { it.stream.addon == a }}", addon == a) {
                                addon = if (addon == a) null else a
                                Settings.preferredAddon = addon
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        streams.isNotEmpty() -> LazyColumn(
                            state = list, verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 48.dp),
                        ) {
                            val top = streams.first()
                            items(streams, key = { it.stream.url }) { r ->
                                StreamRow(
                                    r, if (r === top) Modifier.focusRequester(first) else Modifier,
                                    tag = if (r === top) "BEST" else null, onFocus = { focusedUrl = r.stream.url },
                                ) { play(r) }
                            }
                        }
                        ranked.isNotEmpty() && done -> Column(Modifier.align(Alignment.Center).width(440.dp)) {
                            Text(
                                if (instant && pool.isEmpty()) "None of these streams are ready to play instantly. Uncached ones can take a while to start, or fail."
                                else "Nothing matches these filters.",
                                fontSize = 16.sp, color = Color.White,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { instant = false; addon = null; only = null }, modifier = Modifier.focusRequester(showAll)) {
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
    }
    LaunchedEffect(streams.firstOrNull()?.stream?.url) {
        if (streams.isEmpty() || touched) return@LaunchedEffect
        list.scrollToItem(0)
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
}

// Everything about one version, in words rather than the addon's raw text
@Composable
private fun StreamDetail(r: RankedStream, best: Boolean) {
    val t = r.tags
    val text = r.stream.text
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text(if (best) "BEST FOR THIS TV" else "SELECTED VERSION", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, color = Gold)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(quality(t).let { if (it == "Other") "Unknown" else it }, fontSize = 30.sp, fontWeight = FontWeight.Black,
                color = if (t.height >= 2160) Gold else Color.White)
            hdrLabel(t)?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gold, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
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
        Spacer(Modifier.height(14.dp))
        Spec("Video", videoSummary(t, text))
        Spec("Audio", audioSummary(t, text))
        Spec("Source", sourceLabel(text))
        Spec("Size", sizeLabel(t.sizeBytes))
        (r.stream.filename ?: r.stream.description.takeIf { it.isNotBlank() })?.let {
            Text(it, fontSize = 12.sp, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp))
        }
        r.warning?.let { Text(it, fontSize = 13.sp, color = Amber, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp)) }
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
                hdrShort(t)?.let { Text(it, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold) }
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
