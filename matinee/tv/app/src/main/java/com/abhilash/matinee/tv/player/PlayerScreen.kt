package com.abhilash.matinee.tv.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.abhilash.matinee.tv.data.SubtitleTrack
import com.abhilash.matinee.tv.ui.Gold
import com.abhilash.matinee.tv.ui.Spinner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerUi {
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var position by mutableLongStateOf(0)
    var duration by mutableLongStateOf(0)
    var buffered by mutableLongStateOf(0)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var error by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var dropWarning by mutableStateOf(false)
}

interface PlayerActions {
    fun togglePlay()
    fun seekBy(ms: Long)
    fun selectTrack(group: Tracks.Group, index: Int)
    fun subtitlesOff()
    suspend fun addonSubtitles(): List<SubtitleTrack>
    fun useAddonSubtitle(track: SubtitleTrack)
    fun decodingForced(): Boolean
    fun toggleDecoding()
    fun retry()
    fun exit()
    fun infoLines(): List<String>
}

private enum class Panel { Audio, Subtitles, Info }

@Composable
fun PlayerScreen(view: PlayerView, ui: PlayerUi, actions: PlayerActions, title: String, subtitle: String?) {
    var controls by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    val playButton = remember { FocusRequester() }

    fun show() { controls = true; interaction++ }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interaction++
                val key = e.nativeKeyEvent.keyCode
                val fast = e.nativeKeyEvent.repeatCount > 2
                when (key) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { actions.togglePlay(); show(); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { actions.seekBy(30_000); show(); true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { actions.seekBy(-30_000); show(); true }
                    else -> if (controls || panel != null || ui.error != null) false else when (key) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { actions.seekBy(if (fast) -30_000 else -10_000); show(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { actions.seekBy(if (fast) 30_000 else 10_000); show(); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { actions.togglePlay(); show(); true }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { show(); true }
                        else -> false
                    }
                }
            }
            .focusRequester(root)
            .focusable()
    ) {
        AndroidView({ view }, Modifier.fillMaxSize())

        if (ui.buffering && ui.error == null) Spinner(Modifier.align(Alignment.Center))

        AnimatedVisibility(controls && panel == null, enter = fadeIn(), exit = fadeOut()) {
            Controls(ui, actions, title, subtitle, playButton) { panel = it }
        }

        panel?.let { p -> SidePanel(p, ui, actions) { panel = null } }

        ui.error?.let { ErrorBox(it, actions) }

        Column(Modifier.align(Alignment.TopEnd).padding(32.dp), horizontalAlignment = Alignment.End) {
            ui.toast?.let { Toast(it) }
            if (ui.dropWarning) Toast("This stream is dropping frames on this device. A lighter version (lower bitrate or another codec) will play smoother.")
        }
    }

    LaunchedEffect(ui.toast) { if (ui.toast != null) { delay(4_000); ui.toast = null } }
    LaunchedEffect(ui.dropWarning) { if (ui.dropWarning) { delay(10_000); ui.dropWarning = false } }
    LaunchedEffect(controls, panel) {
        if (controls && panel == null) runCatching { playButton.requestFocus() } else if (!controls && panel == null) root.requestFocus()
    }
    LaunchedEffect(controls, interaction, ui.playing, panel) {
        if (controls && ui.playing && panel == null) { delay(5_000); controls = false }
    }
    BackHandler {
        when {
            panel != null -> panel = null
            controls -> controls = false
            else -> actions.exit()
        }
    }
}

@Composable
private fun Controls(ui: PlayerUi, actions: PlayerActions, title: String, subtitle: String?, playButton: FocusRequester, open: (Panel) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))))
        Column(Modifier.padding(horizontal = 48.dp, vertical = 32.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, fontSize = 15.sp, color = Color(0xFFCCCCCC), maxLines = 1) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            SeekBar(ui, actions)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { actions.togglePlay() }, modifier = Modifier.focusRequester(playButton)) { Text(if (ui.playing) "Pause" else "Play") }
                Button(onClick = { open(Panel.Audio) }) { Text("Audio") }
                Button(onClick = { open(Panel.Subtitles) }) { Text("Subtitles") }
                Button(onClick = { open(Panel.Info) }) { Text("Info") }
            }
        }
    }
}

@Composable
private fun SeekBar(ui: PlayerUi, actions: PlayerActions) {
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time(ui.position), fontSize = 14.sp, color = Color.White)
        Box(
            Modifier.weight(1f).padding(horizontal = 16.dp).height(if (focused) 8.dp else 4.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = if (e.nativeKeyEvent.repeatCount > 2) 30_000L else 10_000L
                    when (e.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { actions.seekBy(-step); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { actions.seekBy(step); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { actions.togglePlay(); true }
                        else -> false
                    }
                }
                .focusable()
                .background(Color(0x55FFFFFF), RoundedCornerShape(4.dp))
        ) {
            val d = ui.duration.coerceAtLeast(1)
            Box(Modifier.fillMaxHeight().fillMaxWidth((ui.buffered.toFloat() / d).coerceIn(0f, 1f)).background(Color(0x88FFFFFF), RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxHeight().fillMaxWidth((ui.position.toFloat() / d).coerceIn(0f, 1f)).background(Gold, RoundedCornerShape(4.dp)))
        }
        Text("-" + time((ui.duration - ui.position).coerceAtLeast(0)), fontSize = 14.sp, color = Color.White)
    }
}

@Composable
private fun SidePanel(panel: Panel, ui: PlayerUi, actions: PlayerActions, close: () -> Unit) {
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var addon by remember { mutableStateOf<List<SubtitleTrack>?>(null) }
    var loadingAddon by remember { mutableStateOf(false) }

    class Row(val label: String, val selected: Boolean, val onClick: (() -> Unit)?)

    val rows: List<Row> = when (panel) {
        Panel.Audio -> trackRows(ui.tracks, C.TRACK_TYPE_AUDIO).map { (g, i, label) -> Row(label, g.isTrackSelected(i)) { actions.selectTrack(g, i); close() } } +
            Row("No sound or crackling? Decode audio on this device: ${if (actions.decodingForced()) "On" else "Off"}", false) { actions.toggleDecoding(); close() }
        Panel.Subtitles -> {
            val text = trackRows(ui.tracks, C.TRACK_TYPE_TEXT)
            listOf(Row("Off", text.none { (g, i, _) -> g.isTrackSelected(i) }) { actions.subtitlesOff(); close() }) +
                text.map { (g, i, label) -> Row(label, g.isTrackSelected(i)) { actions.selectTrack(g, i); close() } } +
                when {
                    addon != null -> addon!!.map { s -> Row("${languageName(s.lang)} · addon", false) { actions.useAddonSubtitle(s); close() } }
                        .ifEmpty { listOf(Row("No addon subtitles found", false, null)) }
                    loadingAddon -> listOf(Row("Looking for addon subtitles…", false, null))
                    else -> listOf(Row("More from your subtitle addons…", false) {
                        loadingAddon = true
                        scope.launch { addon = runCatching { actions.addonSubtitles() }.getOrDefault(emptyList()); loadingAddon = false }
                    })
                }
        }
        Panel.Info -> actions.infoLines().map { Row(it, false, null) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.fillMaxHeight().width(440.dp).background(Color(0xF0101010)).padding(24.dp)) {
            Text(panel.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows.size) { i ->
                    val row = rows[i]
                    Surface(
                        onClick = { row.onClick?.invoke() },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0xFF2E2E2E), contentColor = Color.White, focusedContentColor = Color.White),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        Text((if (row.selected) "✓  " else "     ") + row.label, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(panel) { runCatching { first.requestFocus() } }
}

private fun trackRows(tracks: Tracks, type: Int): List<Triple<Tracks.Group, Int, String>> =
    tracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            val f = g.getTrackFormat(i)
            val label = if (type == C.TRACK_TYPE_AUDIO) audioLabel(f)
            else listOfNotNull(f.label ?: languageName(f.language), if ((f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) "forced" else null).joinToString(" · ")
            Triple(g, i, label)
        }
    }

@Composable
private fun ErrorBox(message: String, actions: PlayerActions) {
    val retry = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { actions.retry() }, modifier = Modifier.focusRequester(retry)) { Text("Try again") }
                Button(onClick = { actions.exit() }) { Text("Back") }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
}

@Composable
private fun Toast(text: String) {
    Text(
        text, fontSize = 14.sp, color = Color.White,
        modifier = Modifier.padding(bottom = 8.dp).width(420.dp)
            .background(Color(0xE0181818), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

private fun time(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
