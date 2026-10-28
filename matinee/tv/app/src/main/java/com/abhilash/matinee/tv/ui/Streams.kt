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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.abhilash.matinee.tv.data.AddonStreams
import com.abhilash.matinee.tv.data.Repo
import com.abhilash.matinee.tv.player.Capabilities
import com.abhilash.matinee.tv.player.PlayRequest
import com.abhilash.matinee.tv.player.RankedStream
import com.abhilash.matinee.tv.player.rankStreams

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
    val ranked = remember(results) { rankStreams(results.flatMap { it.streams }.distinctBy { it.url }, caps) }
    val first = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FullBackdrop(meta.background)
        Row(Modifier.fillMaxSize().padding(start = Edge, top = 40.dp, end = 32.dp)) {
            Column(Modifier.width(250.dp)) {
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
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    ranked.isNotEmpty() -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                        items(ranked, key = { it.stream.url }) { r ->
                            StreamCard(r, Modifier.then(if (r === ranked.first()) Modifier.focusRequester(first) else Modifier)) {
                                activity.startActivity(
                                    PlayRequest(
                                        url = r.stream.url, headers = r.stream.headers, title = meta.name,
                                        subtitle = route.video?.takeIf { it.label.isNotEmpty() }?.let { "${it.label} · ${it.title}" },
                                        metaId = meta.id, type = meta.type, videoId = videoId, poster = meta.poster, resumeMs = route.resumeMs,
                                    ).intent(activity)
                                )
                            }
                        }
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
    // Focus the best stream as soon as the first ones arrive, once (later arrivals don't steal focus)
    LaunchedEffect(ranked.isNotEmpty()) {
        if (ranked.isNotEmpty() && !focusedOnce) { focusedOnce = true; runCatching { first.requestFocus() } }
    }
}

@Composable
private fun StreamCard(r: RankedStream, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xCC161616), focusedContainerColor = Color(0xFF2A2A2A), contentColor = Color.White, focusedContentColor = Color.White),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Gold), shape = RoundedCornerShape(8.dp))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                r.badges.forEach { b ->
                    when (b) {
                        "Dolby Vision", "HDR10+", "HDR10", "HLG", "Atmos" -> Chip(b, Gold, Color.Black)
                        "Instant" -> Chip("⚡ Instant", Color(0xFF1F5F3A))
                        else -> Chip(b)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(r.stream.addon, fontSize = 12.sp, color = Muted, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Text(r.stream.name.replace('\n', ' '), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.stream.description.isNotBlank()) {
                Text(r.stream.description, fontSize = 13.sp, color = Color(0xFFBDBDBD), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            }
            r.warning?.let { Text("⚠ $it", fontSize = 13.sp, color = Color(0xFFFFB74D), modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}
