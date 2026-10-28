package com.abhilash.matinee.tv.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
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
import com.abhilash.matinee.tv.player.instantOnly
import com.abhilash.matinee.tv.player.quality
import com.abhilash.matinee.tv.player.rankStreams

private val HDR_BADGES = mapOf("Dolby Vision" to "DV", "HDR10+" to "HDR10+", "HDR10" to "HDR10", "HLG" to "HLG")
private val Instant = Color(0xFF2E9E5B)

@OptIn(ExperimentalComposeUiApi::class)
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
    val groups = remember(fromAddon, only) {
        QUALITIES.filter { only == null || it == only }
            .mapNotNull { q -> fromAddon.filter { quality(it.tags) == q }.takeIf { it.isNotEmpty() }?.let { q to it } }
    }

    val first = remember { FocusRequester() }
    val showAll = remember { FocusRequester() }
    val list = rememberLazyListState()
    // Until a key is pressed, focus follows the best stream as slower addons answer and reorder the list
    var touched by remember { mutableStateOf(false) }

    fun play(r: RankedStream) = activity.startActivity(
        PlayRequest(
            url = r.stream.url, headers = r.stream.headers, title = meta.name,
            subtitle = route.video?.takeIf { it.label.isNotEmpty() }?.let { "${it.label} · ${it.title}" },
            metaId = meta.id, type = meta.type, videoId = videoId, poster = meta.poster, resumeMs = route.resumeMs,
            logo = meta.logo, background = meta.background, streamText = r.stream.text,
        ).intent(activity)
    )

    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) touched = true; false }) {
        FullBackdrop(meta.background)
        Row(Modifier.fillMaxSize().padding(start = Edge, top = 40.dp, end = 32.dp)) {
            Column(Modifier.width(230.dp)) {
                meta.poster?.let { AsyncImage(it, meta.name, contentScale = ContentScale.Crop, modifier = Modifier.size(150.dp, 225.dp)) }
                Spacer(Modifier.height(14.dp))
                Text(meta.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                route.video?.takeIf { it.label.isNotEmpty() }?.let { v ->
                    Text("${v.label} · ${v.title}", fontSize = 14.sp, color = Color(0xFFD0D0D0), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(14.dp))
                val failed = results.filter { it.failed }.map { it.addon }
                Text(
                    buildString {
                        append("${ranked.size} streams from ${results.count { it.streams.isNotEmpty() }} addons")
                        if (!done) append(" · loading…")
                        if (failed.isNotEmpty()) append("\nNo answer from ${failed.joinToString()}")
                    },
                    fontSize = 13.sp, color = Muted,
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (ranked.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusRestorer()) {
                        item {
                            Filter("Instant only", instant, icon = true) { instant = !instant; Settings.instantOnly = instant }
                        }
                        item { Spacer(Modifier.width(12.dp)) }
                        item { Filter("All addons", addon == null) { addon = null; Settings.preferredAddon = null } }
                        items(addons, key = { it }) { a ->
                            Filter("$a  ${pool.count { it.stream.addon == a }}", addon == a) {
                                addon = if (addon == a) null else a
                                Settings.preferredAddon = addon
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusRestorer()) {
                        item { Filter("All qualities", only == null) { only = null } }
                        items(QUALITIES.filter { (qualityCounts[it] ?: 0) > 0 || it == only }, key = { it }) { q ->
                            Filter("$q  ${qualityCounts[q] ?: 0}", only == q) { only = if (only == q) null else q }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        groups.isNotEmpty() -> LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                            val top = groups.first().second.first()
                            groups.forEach { (q, streams) ->
                                item(key = "h-$q") {
                                    Text("$q  ·  ${streams.size}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                                }
                                items(streams, key = { it.stream.url }) { r ->
                                    StreamCard(r, if (r === top) Modifier.focusRequester(first) else Modifier) { play(r) }
                                }
                            }
                        }
                        ranked.isNotEmpty() && done -> Column(Modifier.align(Alignment.Center).width(480.dp)) {
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
                            fontSize = 16.sp, color = Color.White, modifier = Modifier.align(Alignment.Center).width(480.dp),
                        )
                        else -> Spinner(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
    LaunchedEffect(groups.firstOrNull()?.second?.firstOrNull()?.stream?.url) {
        if (groups.isEmpty() || touched) return@LaunchedEffect
        list.scrollToItem(0)
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Filter(label: String, selected: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = if (icon) { { Icon(Icons.Bolt, null, Modifier.size(16.dp)) } } else null,
        colors = FilterChipDefaults.colors(
            containerColor = Color(0x33FFFFFF), contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
            selectedContainerColor = Gold.copy(alpha = 0.85f), selectedContentColor = Color.Black,
            focusedSelectedContainerColor = Gold, focusedSelectedContentColor = Color.Black,
        ),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun StreamCard(r: RankedStream, modifier: Modifier, onClick: () -> Unit) {
    val q = quality(r.tags)
    val hdr = r.badges.firstNotNullOfOrNull { HDR_BADGES[it] }
    val rest = r.badges.filter { it !in HDR_BADGES && it != "Instant" && it !in setOf("4K", "1440p", "1080p", "720p", "SD") }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xCC161616), focusedContainerColor = Color(0xFF2A2A2A), contentColor = Color.White, focusedContentColor = Color.White),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Gold), shape = RoundedCornerShape(8.dp))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (q == "Other") "—" else q, fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (q == "4K") Gold else Color.White)
                hdr?.let { Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.tags.cached == true) {
                        Icon(Icons.Bolt, "Instant", Modifier.size(16.dp), tint = Instant)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(r.stream.name.replace('\n', ' '), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(r.stream.addon, fontSize = 12.sp, color = Muted, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
                }
                if (r.stream.description.isNotBlank()) {
                    Text(r.stream.description, fontSize = 13.sp, color = Color(0xFFBDBDBD), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
                if (rest.isNotEmpty()) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rest.forEach { b -> if (b == "Atmos") Chip(b, Gold, Color.Black) else Chip(b) }
                    }
                }
                r.warning?.let { Text("⚠ $it", fontSize = 13.sp, color = Color(0xFFFFB74D), modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}
