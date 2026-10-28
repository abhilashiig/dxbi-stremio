package com.abhilash.matinee.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.abhilash.matinee.tv.data.parseIso
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.MetaDetail
import com.abhilash.matinee.tv.data.Repo
import com.abhilash.matinee.tv.data.Video
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(type: String, id: String) {
    val nav = LocalNav.current
    var meta by remember { mutableStateOf<MetaDetail?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(type, id) {
        meta = runCatching { Repo.meta(type, id) }.getOrNull()
        failed = meta == null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val m = meta
        when {
            m != null -> Details(m, nav)
            failed -> Text("Couldn't load this title from your addons.", color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> Spinner(Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Details(meta: MetaDetail, nav: Nav) {
    val library by Library.items.collectAsState()
    val item = library.firstOrNull { it.id == meta.preview.id }
    val scope = rememberCoroutineScope()
    val isSeries = meta.videos.size > 1 || meta.preview.type == "series"

    // What Play does: resume the episode/movie in progress, else start from the first episode
    val next: Video? = if (isSeries) meta.videos.firstOrNull { it.id == item?.videoId } ?: meta.videos.firstOrNull { it.season > 0 } ?: meta.videos.firstOrNull() else null
    val resumeMs = item?.takeIf { it.timeOffset > 0 && it.videoId == (next?.id ?: meta.preview.id) }?.timeOffset ?: 0L
    val playLabel = buildString {
        append(if (resumeMs > 0) "Resume" else "Play")
        next?.label?.takeIf { it.isNotEmpty() }?.let { append(" $it") }
    }
    val play = remember { FocusRequester() }
    var season by rememberSaveable { mutableIntStateOf(next?.season ?: meta.seasons.firstOrNull() ?: 1) }

    FullBackdrop(meta.preview.background)
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberMinimalScroll()) {
    LazyColumn(contentPadding = PaddingValues(top = 56.dp, bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = Edge).width(560.dp)) {
                Text(if (isSeries) "SERIES" else "FILM", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = Gold,
                    modifier = Modifier.padding(bottom = 8.dp))
                if (meta.preview.logo != null) {
                    AsyncImage(meta.preview.logo, meta.preview.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                        modifier = Modifier.heightIn(max = 96.dp).widthIn(max = 400.dp))
                } else {
                    Text(meta.preview.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                MetaLine(meta.preview, meta.runtime)
                Spacer(Modifier.height(10.dp))
                meta.preview.description?.let { Text(it, fontSize = 15.sp, color = Color(0xFFDDDDDD), maxLines = 5, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp) }
                if (meta.cast.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Starring " + meta.cast.take(4).joinToString(", "), fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { nav.push(Route.Streams(meta.preview, next, resumeMs)) }, modifier = Modifier.focusRequester(play),
                        colors = ButtonDefaults.colors(containerColor = Color(0xFFE8E8E8), contentColor = Color.Black, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                    ) {
                        Icon(Icons.Play, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(playLabel, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { scope.launch { Library.setInLibrary(meta.preview, item?.inLibrary != true) } },
                        colors = ButtonDefaults.colors(containerColor = Color(0x66808080), contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                    ) {
                        Icon(if (item?.inLibrary == true) Icons.Check else Icons.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("My List", fontWeight = FontWeight.Bold)
                    }
                }
                if (resumeMs > 0 && item != null && item.duration > 0) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(240.dp).height(3.dp).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(item.progress).height(3.dp).background(Gold))
                    }
                }
            }
        }
        if (isSeries && meta.videos.isNotEmpty()) {
            episodes(meta, season, { season = it }, item?.videoId, item?.progress ?: 0f, item?.timeOffset ?: 0) { v, resume ->
                nav.push(Route.Streams(meta.preview, v, resume))
            }
        }
    }
    }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
}

private val airedFormat get() = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private val upcomingFormat get() = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

@OptIn(ExperimentalComposeUiApi::class)
private fun LazyListScope.episodes(
    meta: MetaDetail, season: Int, onSeason: (Int) -> Unit, watchingId: String?, watchingProgress: Float, resumeMs: Long, open: (Video, Long) -> Unit,
) {
    val now = System.currentTimeMillis()
    val all = meta.videos.filter { it.season == season }
    // Unknown air dates count as aired; only a date in the future makes an episode "coming soon"
    val (upcoming, aired) = all.partition { parseIso(it.released) > now }
    item("seasons") {
        Column(Modifier.padding(top = 28.dp)) {
            Text("Episodes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = Edge, bottom = 4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Edge, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                items(meta.seasons) { s -> FilterPill(if (s == 0) "Specials" else "Season $s", s == season) { onSeason(s) } }
            }
        }
    }
    items(aired, key = { it.id }) { v ->
        EpisodeRow(v, null, if (v.id == watchingId) watchingProgress else 0f) { open(v, if (v.id == watchingId) resumeMs else 0) }
    }
    if (upcoming.isNotEmpty()) {
        item("upcoming") {
            Text("Coming soon", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(start = Edge, top = 20.dp, bottom = 4.dp))
        }
        items(upcoming, key = { it.id }) { v -> EpisodeRow(v, "Airs " + upcomingFormat.format(parseIso(v.released)), 0f, null) }
    }
}

// Netflix-style episode line: still with its number, then title, air date and synopsis
@Composable
private fun EpisodeRow(v: Video, airs: String?, progress: Float, onClick: (() -> Unit)?) {
    val number = if (v.season == 0) "Special ${v.episode}" else "${v.episode}"
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.padding(horizontal = Edge - 12.dp, vertical = 3.dp).width(820.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0xE6262626), contentColor = Color.White, focusedContentColor = Color.White),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Gold), shape = RoundedCornerShape(10.dp))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(12.dp).alpha(if (airs != null) 0.6f else 1f), verticalAlignment = Alignment.CenterVertically) {
            Text(number, fontSize = if (v.season == 0) 14.sp else 28.sp, fontWeight = FontWeight.Black, color = Color(0xFFBDBDBD),
                modifier = Modifier.width(if (v.season == 0) 84.dp else 44.dp))
            Box(Modifier.size(208.dp, 117.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1E1E))) {
                Text("E${v.episode}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.align(Alignment.Center))
                v.thumbnail?.let { AsyncImage(it, v.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x99000000))) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Gold))
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title.ifEmpty { "Episode ${v.episode}" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    airs ?: v.released?.let { parseIso(it) }?.takeIf { it > 0 }?.let { airedFormat.format(it) } ?: "",
                    fontSize = 13.sp, color = if (airs != null) Gold else Muted, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                v.overview?.let { Text(it, fontSize = 13.sp, color = Color(0xFFCFCFCF), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp) }
            }
        }
    }
}
