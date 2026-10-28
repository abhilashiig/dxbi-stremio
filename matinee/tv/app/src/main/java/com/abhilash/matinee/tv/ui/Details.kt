package com.abhilash.matinee.tv.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
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

    FullBackdrop(meta.preview.background)
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
    LazyColumn(contentPadding = PaddingValues(top = 56.dp, bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = Edge).width(560.dp)) {
                if (meta.preview.logo != null) {
                    AsyncImage(meta.preview.logo, meta.preview.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                        modifier = Modifier.heightIn(max = 96.dp).fillMaxWidth(0.75f))
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
                    Button(onClick = { nav.push(Route.Streams(meta.preview, next, resumeMs)) }, modifier = Modifier.focusRequester(play)) { Text(playLabel) }
                    Button(onClick = { scope.launch { Library.setInLibrary(meta.preview, item?.inLibrary != true) } }) {
                        Text(if (item?.inLibrary == true) "✓ In My List" else "+ My List")
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
        if (isSeries && meta.videos.isNotEmpty()) item { Episodes(meta, next, item?.videoId, item?.progress ?: 0f) { v, resume -> nav.push(Route.Streams(meta.preview, v, resume)) } }
    }
    }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Episodes(meta: MetaDetail, next: Video?, watchingId: String?, watchingProgress: Float, open: (Video, Long) -> Unit) {
    val seasons = meta.seasons
    var season by rememberSaveable { mutableIntStateOf(next?.season ?: seasons.first()) }
    val episodes = remember(season) { meta.videos.filter { it.season == season } }
    val item = Library.items.collectAsState().value.firstOrNull { it.id == meta.preview.id }

    Column(Modifier.padding(top = 28.dp)) {
      CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot()) {
        LazyRow(contentPadding = PaddingValues(horizontal = Edge), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons) { s ->
                Button(onClick = { season = s }) { Text(if (s == 0) "Specials" else "Season $s", fontWeight = if (s == season) FontWeight.Bold else FontWeight.Normal, color = if (s == season) Gold else Color.Unspecified) }
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(state = rememberLazyListState(), contentPadding = PaddingValues(horizontal = Edge), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(episodes, key = { it.id }) { v ->
                val watching = v.id == watchingId
                Column(Modifier.width(240.dp)) {
                    Card(
                        onClick = { open(v, if (watching) item?.timeOffset ?: 0 else 0) },
                        modifier = Modifier.size(240.dp, 135.dp),
                        shape = CardDefaults.shape(RoundedCornerShape(6.dp)),
                        scale = CardDefaults.scale(focusedScale = 1.05f),
                        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Gold), shape = RoundedCornerShape(6.dp))),
                        colors = CardDefaults.colors(containerColor = Color(0xFF1E1E1E)),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            v.thumbnail?.let { AsyncImage(it, v.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            if (watching && watchingProgress > 0f) {
                                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x99000000))) {
                                    Box(Modifier.fillMaxWidth(watchingProgress).height(4.dp).background(Gold))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${v.episode}. ${v.title}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    v.overview?.let { Text(it, fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp) }
                }
            }
        }
      }
    }
}
