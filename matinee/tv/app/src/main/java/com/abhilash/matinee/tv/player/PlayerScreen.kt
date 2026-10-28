package com.abhilash.matinee.tv.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.abhilash.matinee.tv.data.Settings
import com.abhilash.matinee.tv.data.SubtitleTrack
import com.abhilash.matinee.tv.ui.Gold
import com.abhilash.matinee.tv.ui.Icons
import com.abhilash.matinee.tv.ui.Muted
import com.abhilash.matinee.tv.ui.Spinner
import kotlinx.coroutines.delay

class PlayerUi {
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var started by mutableStateOf(false)
    var position by mutableLongStateOf(0)
    var duration by mutableLongStateOf(0)
    var buffered by mutableLongStateOf(0)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var badges by mutableStateOf(emptyList<String>())
    var subtitles by mutableStateOf(emptyList<SubtitleTrack>())
    var subtitlesLoading by mutableStateOf(false)
    var subtitleDelayMs by mutableLongStateOf(0)
    var styleVersion by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var dropWarning by mutableStateOf(false)
}

interface PlayerActions {
    fun togglePlay()
    fun seekTo(ms: Long)
    fun selectTrack(group: Tracks.Group, index: Int)
    fun subtitlesOff()
    fun useAddonSubtitle(track: SubtitleTrack)
    fun decodingForced(): Boolean
    fun toggleDecoding()
    fun retry()
    fun exit()
    fun infoLines(): List<String>
    fun liftSubtitles(lift: Boolean)
    fun nextSubtitleSize()
    fun toggleSubtitleColour()
    fun toggleSubtitleBackground()
    // 0 resets; otherwise added to the current shift (positive shows subtitles later)
    fun shiftSubtitles(ms: Long)
}

private enum class Panel { Tracks, Info }

private val Edge = 56.dp

@Composable
fun PlayerScreen(view: PlayerView, ui: PlayerUi, actions: PlayerActions, request: PlayRequest) {
    var controls by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    // Seeking moves this marker first and the player once the presses stop, so a debrid link isn't
    // re-requested for every 10 seconds skipped
    var scrub by remember { mutableStateOf<Long?>(null) }
    var focusSeekBar by remember { mutableStateOf(false) }
    var flashIcon by remember { mutableStateOf(Icons.Play) }
    var flashVisible by remember { mutableStateOf(false) }
    var flashing by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    val playButton = remember { FocusRequester() }
    val seekBar = remember { FocusRequester() }

    fun show(seeking: Boolean = false) {
        if (!controls) focusSeekBar = seeking
        controls = true
        interaction++
    }
    fun scrubBy(ms: Long) {
        if (ui.duration <= 0) return
        scrub = ((scrub ?: ui.position) + ms).coerceIn(0, ui.duration)
        show(seeking = true)
    }
    fun toggle() {
        flashIcon = if (ui.playing) Icons.Pause else Icons.Play
        flashVisible = true
        flashing++
        actions.togglePlay()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interaction++
                val key = e.nativeKeyEvent.keyCode
                when (key) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { toggle(); show(); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { scrubBy(30_000); true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { scrubBy(-30_000); true }
                    else -> if (controls || panel != null || ui.error != null || !ui.started) false else when (key) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { scrubBy(-seekStep(e.nativeKeyEvent)); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { scrubBy(seekStep(e.nativeKeyEvent)); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { toggle(); show(); true }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { show(); true }
                        else -> false
                    }
                }
            }
            .focusRequester(root)
            .focusable()
    ) {
        AndroidView({ view }, Modifier.fillMaxSize())

        if (ui.started && ui.buffering && ui.error == null) Spinner(Modifier.align(Alignment.Center))

        AnimatedVisibility(
            flashVisible, Modifier.align(Alignment.Center),
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.7f),
            exit = fadeOut(tween(380)) + scaleOut(tween(380), targetScale = 1.3f),
        ) {
            Box(Modifier.size(96.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                Icon(flashIcon, null, Modifier.size(52.dp), tint = Color.White)
            }
        }

        AnimatedVisibility(controls && panel == null, enter = fadeIn(tween(220)), exit = fadeOut(tween(300))) {
            Controls(ui, actions, request, scrub, playButton, seekBar, onScrub = ::scrubBy, onToggle = ::toggle) {
                focusSeekBar = false
                panel = it
            }
        }

        when (panel) {
            Panel.Tracks -> TracksPanel(ui, actions) { panel = null }
            Panel.Info -> InfoPanel(actions)
            null -> Unit
        }

        AnimatedVisibility(!ui.started && ui.error == null, enter = fadeIn(), exit = fadeOut(tween(600))) {
            Loading(request, ui.badges)
        }

        ui.error?.let { ErrorBox(it, actions) }

        Column(Modifier.align(Alignment.TopEnd).padding(32.dp), horizontalAlignment = Alignment.End) {
            ui.toast?.let { Toast(it) }
            if (ui.dropWarning) Toast("This stream is dropping frames on this device. A lighter version (lower bitrate or another codec) will play smoother.")
        }
    }

    LaunchedEffect(flashing) { if (flashing > 0) { delay(450); flashVisible = false } }
    LaunchedEffect(scrub) {
        val target = scrub ?: return@LaunchedEffect
        delay(700)
        actions.seekTo(target)
        scrub = null
    }
    LaunchedEffect(controls && panel == null) { actions.liftSubtitles(controls && panel == null) }
    LaunchedEffect(ui.toast) { if (ui.toast != null) { delay(4_000); ui.toast = null } }
    LaunchedEffect(ui.dropWarning) { if (ui.dropWarning) { delay(10_000); ui.dropWarning = false } }
    LaunchedEffect(controls, panel) {
        if (controls && panel == null) runCatching { (if (focusSeekBar) seekBar else playButton).requestFocus() }
        else if (!controls && panel == null) runCatching { root.requestFocus() }
    }
    LaunchedEffect(controls, interaction, ui.playing, panel, scrub) {
        if (controls && ui.playing && panel == null && scrub == null) { delay(5_000); controls = false }
    }
    BackHandler {
        when {
            panel != null -> panel = null
            controls -> controls = false
            else -> actions.exit()
        }
    }
}

// Holding a direction speeds up: 10 s steps, then 30 s, then a minute
private fun seekStep(e: KeyEvent) = when {
    e.repeatCount > 12 -> 60_000L
    e.repeatCount > 3 -> 30_000L
    else -> 10_000L
}

@Composable
private fun TitleBlock(request: PlayRequest, center: Boolean) {
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        if (request.logo != null) {
            AsyncImage(request.logo, request.title, contentScale = ContentScale.Fit,
                alignment = if (center) Alignment.Center else Alignment.CenterStart,
                modifier = Modifier.heightIn(max = if (center) 96.dp else 56.dp).widthIn(max = if (center) 420.dp else 320.dp))
        } else {
            Text(request.title, fontSize = if (center) 34.sp else 26.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (center) TextAlign.Center else TextAlign.Start)
        }
        request.subtitle?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 16.sp, color = Color(0xFFDDDDDD), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Badges(badges: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.forEach { b ->
            val premium = b.startsWith("DOLBY") || b.startsWith("HDR") || b == "DTS:X" || b == "HLG"
            val color = if (premium) Gold else Color.White
            Text(
                b, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = color,
                modifier = Modifier.border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun Loading(request: PlayRequest, badges: List<String>) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        request.background?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.4f })
        }
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x66000000), Color(0xF0000000)), radius = 1400f)))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            TitleBlock(request, center = true)
            Spacer(Modifier.height(32.dp))
            Spinner()
            Spacer(Modifier.height(24.dp))
            Box(Modifier.height(24.dp)) { Badges(badges) }
        }
    }
}

@Composable
private fun Controls(
    ui: PlayerUi, actions: PlayerActions, request: PlayRequest, scrub: Long?,
    playButton: FocusRequester, seekBar: FocusRequester, onScrub: (Long) -> Unit, onToggle: () -> Unit, open: (Panel) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent))))
        Row(Modifier.fillMaxWidth().padding(horizontal = Edge, vertical = 36.dp)) {
            Box(Modifier.weight(1f)) { TitleBlock(request, center = false) }
            Badges(ui.badges)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2000000))))
                .padding(start = Edge, end = Edge, top = 96.dp, bottom = 36.dp)
        ) {
            SeekBar(ui, scrub, seekBar, onScrub, onToggle)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Replay, "Back 10 seconds", label = "10") { onScrub(-10_000) }
                Spacer(Modifier.width(18.dp))
                RoundButton(if (ui.playing) Icons.Pause else Icons.Play, if (ui.playing) "Pause" else "Play", size = 64.dp,
                    modifier = Modifier.focusRequester(playButton)) { actions.togglePlay() }
                Spacer(Modifier.width(18.dp))
                RoundButton(Icons.Replay, "Forward 10 seconds", label = "10", mirrored = true) { onScrub(10_000) }
                Spacer(Modifier.weight(1f))
                PillButton(Icons.Subtitles, "Audio & Subtitles") { open(Panel.Tracks) }
                Spacer(Modifier.width(12.dp))
                PillButton(Icons.Info, "Info") { open(Panel.Info) }
            }
        }
    }
}

@Composable
private fun SeekBar(ui: PlayerUi, scrub: Long?, requester: FocusRequester, onScrub: (Long) -> Unit, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val position = scrub ?: ui.position
    val duration = ui.duration.coerceAtLeast(1)
    val played = (position.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (ui.buffered.toFloat() / duration).coerceIn(0f, 1f)
    val thickness by animateDpAsState(if (focused) 8.dp else 4.dp, label = "thickness")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time(position), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (scrub != null) Gold else Color.White,
            modifier = Modifier.widthIn(min = 64.dp))
        BoxWithConstraints(
            Modifier.weight(1f).height(28.dp).padding(horizontal = 14.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { onScrub(-seekStep(e.nativeKeyEvent)); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { onScrub(seekStep(e.nativeKeyEvent)); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onToggle(); true }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val track = RoundedCornerShape(4.dp)
            Box(Modifier.fillMaxWidth().height(thickness).background(Color(0x40FFFFFF), track))
            if (scrub == null) Box(Modifier.fillMaxWidth(buffered).height(thickness).background(Color(0x66FFFFFF), track))
            Box(Modifier.fillMaxWidth(played).height(thickness).background(Gold, track))
            if (focused) Box(Modifier.offset(x = maxWidth * played - 9.dp).size(18.dp).background(Color.White, CircleShape))
        }
        Text("-" + time((ui.duration - position).coerceAtLeast(0)), fontSize = 15.sp, color = Color.White, textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 64.dp))
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector, description: String, size: Dp = 52.dp, label: String? = null, mirrored: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(size * 0.56f).graphicsLayer { if (mirrored) scaleX = -1f })
            label?.let { Text(it, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = size * 0.08f)) }
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Netflix-style: audio, subtitles and how they look side by side. The bottom stays see-through, so subtitle
// size, colour and timing changes can be judged on the picture.
@Composable
private fun TracksPanel(ui: PlayerUi, actions: PlayerActions, close: () -> Unit) {
    val first = remember { FocusRequester() }
    val audio = trackRows(ui.tracks, C.TRACK_TYPE_AUDIO).map { (g, i, label) -> Choice(label, g.isTrackSelected(i)) { actions.selectTrack(g, i); close() } } +
        Choice("Decode audio on this device: ${if (actions.decodingForced()) "On" else "Off"}", false) { actions.toggleDecoding(); close() }
    val text = trackRows(ui.tracks, C.TRACK_TYPE_TEXT)
    val loaded = text.mapNotNull { (g, i, _) -> g.getTrackFormat(i).id }
    val subtitles = listOf(Choice("Off", text.none { (g, i, _) -> g.isTrackSelected(i) }) { actions.subtitlesOff(); close() }) +
        text.map { (g, i, label) -> Choice(label, g.isTrackSelected(i)) { actions.selectTrack(g, i); close() } } +
        ui.subtitles.filter { s -> loaded.none { it.contains("addon:${s.id}") } }.map { s ->
            Choice("${languageName(s.lang)} · ${s.addon}", false, s.release) { actions.useAddonSubtitle(s); close() }
        } +
        when {
            ui.subtitlesLoading -> listOf(Choice("Looking for more in your subtitle addons…", false, onClick = null))
            ui.subtitles.isEmpty() -> listOf(Choice("No addon subtitles for this title", false, onClick = null))
            else -> emptyList()
        }
    val delay = ui.subtitleDelayMs
    val style = remember(ui.styleVersion, delay) { listOf(
        Choice("Size: ${Settings.subtitleSizeLabel}", false) { actions.nextSubtitleSize() },
        Choice("Colour: ${if (Settings.subtitleYellow) "Yellow" else "White"}", false) { actions.toggleSubtitleColour() },
        Choice("Background: ${if (Settings.subtitleBackground) "On" else "Off"}", false) { actions.toggleSubtitleBackground() },
        Choice("Show earlier  −0.25 s", false) { actions.shiftSubtitles(-250) },
        Choice("Show later  +0.25 s", false) { actions.shiftSubtitles(250) },
        Choice(if (delay == 0L) "Timing: in sync" else "Timing: %+.2f s · reset".format(delay / 1000f), false) { actions.shiftSubtitles(0) },
    ) }
    val focusIndex = audio.indexOfFirst { it.selected }.coerceAtLeast(0)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xF2000000), 0.7f to Color(0xE6000000), 1f to Color(0x40000000)))) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight(0.78f).padding(top = 40.dp),
        ) {
            ChoiceColumn("Audio", audio, 260.dp, focusIndex, first)
            ChoiceColumn("Subtitles", subtitles, 330.dp, -1, null)
            ChoiceColumn("Subtitle style", style, 250.dp, -1, null)
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private class Choice(val label: String, val selected: Boolean, val detail: String? = null, val onClick: (() -> Unit)?)

@Composable
private fun ChoiceColumn(title: String, choices: List<Choice>, width: Dp, focusIndex: Int, requester: FocusRequester?) {
    Column(Modifier.width(width)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
        // Long lists fade out at the bottom instead of being cut mid-line
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 48.dp),
            modifier = Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.82f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
        ) {
            items(choices.size) { i ->
                val c = choices[i]
                Surface(
                    onClick = { c.onClick?.invoke() },
                    modifier = Modifier.fillMaxWidth().then(if (i == focusIndex && requester != null) Modifier.focusRequester(requester) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = if (c.onClick == null) Muted else Color(0xFFCCCCCC),
                        focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(26.dp)) { if (c.selected) Icon(Icons.Check, "Selected", Modifier.size(18.dp)) }
                        Column {
                            Text(c.label, fontSize = 15.sp, fontWeight = if (c.selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            c.detail?.let { Text(it, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.7f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(actions: PlayerActions) {
    val first = remember { FocusRequester() }
    val lines = actions.infoLines()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.fillMaxHeight().width(460.dp).background(Color(0xF0101010)).padding(24.dp)) {
            Text("Info", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(lines.size) { i ->
                    Surface(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0xFF2E2E2E), contentColor = Color.White, focusedContentColor = Color.White),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        Text(lines[i], fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private fun trackRows(tracks: Tracks, type: Int): List<Triple<Tracks.Group, Int, String>> =
    tracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            val f = g.getTrackFormat(i)
            val label = if (type == C.TRACK_TYPE_AUDIO) audioLabel(f)
            else {
                val name = f.label ?: languageName(f.language)
                val forced = (f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0 && !name.contains("forced", true)
                if (forced) "$name · forced" else name
            }
            Triple(g, i, label)
        }
    }

@Composable
private fun ErrorBox(message: String, actions: PlayerActions) {
    val retry = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Center)
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
