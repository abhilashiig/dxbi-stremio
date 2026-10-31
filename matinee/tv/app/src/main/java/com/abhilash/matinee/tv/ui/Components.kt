package com.abhilash.matinee.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.darkColorScheme
import coil3.compose.AsyncImage
import com.abhilash.matinee.tv.data.MetaPreview

// The card that last had focus on a page, so coming back from the rail lands on it again
class LastFocus { var requester: FocusRequester? = null }
val LocalLastFocus = staticCompositionLocalOf { LastFocus() }

val Gold = Color(0xFFF2B441)
val Muted = Color(0xFFA8A8A8)
val Panel = Color(0xFF161616)
val Edge = 48.dp

@Composable
fun MatineeTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Gold, onPrimary = Color.Black, background = Color.Black, onBackground = Color.White,
        surface = Panel, onSurface = Color.White, surfaceVariant = Color(0xFF262626), onSurfaceVariant = Color.White,
        inverseSurface = Color.White, inverseOnSurface = Color.Black, border = Gold,
    ),
    content = content,
)

// Rows and lists scroll so the focused item sits at the start, like Netflix, instead of creeping along the edge
@OptIn(ExperimentalFoundationApi::class)
fun startPivot(leadingPx: Float) = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = offset - leadingPx
}

// Compose's TV default parks focus 30% down the screen; pages like Details only scroll when focus would be hidden,
// keeping a margin so a focused item's border and scale never touch the screen edge
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberMinimalScroll(margin: Dp = 36.dp): BringIntoViewSpec {
    val px = with(LocalDensity.current) { margin.toPx() }
    return remember(px) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val start = offset - px
                val end = offset + size + px
                return when {
                    start >= 0 && end <= containerSize -> 0f
                    start < 0 || end - start > containerSize -> start
                    else -> end - containerSize
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PosterRow(
    title: String,
    items: List<MetaPreview>,
    focusId: String?,
    rowKey: String,
    progress: (MetaPreview) -> Float? = { null },
    onFocus: (String, MetaPreview) -> Unit,
    onClick: (MetaPreview) -> Unit,
) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(start = Edge))
        // Vertical room so a focused card's scale and glow aren't clipped by the row
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = Edge, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            // Add-on catalogs can list the same title twice, and a repeated key crashes the LazyRow
            items(items.distinctBy { it.id }, key = { it.id }) { meta ->
                val key = "$rowKey/${meta.id}"
                PosterCard(meta, focusId == key, progress(meta), onFocus = { onFocus(key, meta) }, onClick = { onClick(meta) })
            }
        }
    }
}

@Composable
fun PosterCard(
    meta: MetaPreview,
    restoreFocus: Boolean,
    progress: Float?,
    width: Dp = 116.dp,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    val last = LocalLastFocus.current
    Card(
        onClick = onClick,
        // 2:3 at whatever width the row or grid gives it, so posters are never cropped out of shape
        modifier = Modifier.width(width).aspectRatio(2f / 3f).focusRequester(requester).onFocusChanged { if (it.isFocused) { last.requester = requester; onFocus() } },
        shape = CardDefaults.shape(RoundedCornerShape(6.dp)),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(6.dp))),
        glow = CardDefaults.glow(focusedGlow = Glow(Color(0x66F2B441), 14.dp)),
        colors = CardDefaults.colors(containerColor = Color(0xFF1E1E1E)),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Shows until the poster arrives, and stays when there is none
            Text(meta.name, fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            if (meta.poster != null) AsyncImage(meta.poster, meta.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x99000000))) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Gold))
                }
            }
        }
    }
    if (restoreFocus) LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
}

@Composable
fun MetaLine(meta: MetaPreview, extra: String? = null) {
    val parts = listOfNotNull(meta.releaseInfo, meta.imdbRating?.let { "★ $it" }, extra, meta.genres.take(3).joinToString(", ").ifEmpty { null })
    Text(parts.joinToString("  ·  "), fontSize = 14.sp, color = Color(0xFFD0D0D0), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Canvas(modifier.size(44.dp)) {
        rotate(angle) { drawArc(Gold, 0f, 270f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)) }
    }
}

// On TV a focused text field opens the keyboard, which then takes over the remote. So the field is a button until
// OK is pressed; then it edits with the keyboard up, and turns back into a button when done or left.
@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    // A new field reports "unfocused" before it gets focus: only leaving after having had focus ends editing
    var hadFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val field = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)
    if (!editing) {
        Surface(
            onClick = { hadFocus = false; editing = true },
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A1A), focusedContainerColor = Color(0xFF2A2A2A), contentColor = Color.White, focusedContentColor = Color.White),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Gold), shape = shape)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            val shown = if (password) "•".repeat(value.length) else value
            Text(shown.ifEmpty { placeholder }, fontSize = 18.sp, color = if (value.isEmpty()) Muted else Color.White, maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
        }
        return
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(Gold),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = {
            editing = false
            onAction()
            focusManager.moveFocus(FocusDirection.Down)
        }),
        modifier = modifier
            .focusRequester(field)
            .onFocusChanged { if (it.hasFocus) hadFocus = true else if (hadFocus) editing = false }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> focusManager.moveFocus(FocusDirection.Up)
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> focusManager.moveFocus(FocusDirection.Down)
                    else -> false
                }
            }
            .background(Color(0xFF2A2A2A), shape)
            .border(2.dp, Gold, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, fontSize = 18.sp, color = Muted)
                inner()
            }
        },
    )
    LaunchedEffect(Unit) {
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
}

@Composable
fun Chip(text: String, color: Color = Color(0xFF2C2C2C), textColor: Color = Color.White) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor,
        modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

// Each screen eases in instead of snapping, so moving between screens feels like one app
@Composable
fun FadeIn(content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(280)) }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) { content() }
}

// Placeholder rows that breathe while Home loads for the first time
@Composable
fun SkeletonRows(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Column(modifier.graphicsLayer { this.alpha = alpha }) {
        repeat(3) {
            Box(Modifier.padding(start = Edge, bottom = 12.dp).size(160.dp, 16.dp).background(Color(0xFF262626), RoundedCornerShape(4.dp)))
            Row(Modifier.padding(start = Edge, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(7) { Box(Modifier.width(116.dp).height(174.dp).background(Color(0xFF1C1C1C), RoundedCornerShape(6.dp))) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterPill(label: String, selected: Boolean, icon: Boolean = false, onClick: () -> Unit) {
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

