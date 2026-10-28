package com.abhilash.matinee.tv.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.abhilash.matinee.tv.R
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.Repo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// The M's two arches (brand/source/mark_transparent.svg), each measured so light can travel along it
private val ARCHES = listOf("M15,85L15,42A17.5,17.5 0 0,1 50,42L50,85", "M50,42A17.5,17.5 0 0,1 85,42L85,85")
    .map { PathParser().parsePathString(it).toPath() }
private val MEASURES = ARCHES.map { PathMeasure().apply { setPath(it, false) } }

// Same box as res/drawable/matinee_mark.xml (the launch-screen icon): 240 dp holding a 170-unit view with the M
// offset by 35, so the intro's first frame is pixel-for-pixel the launch screen it replaces
private val MarkBox = 240.dp
private const val VIEW = 170f
private const val OFFSET = 35f
private val LightGold = Color(0xFFFFE7B0)

// Start-up ident, timed to res/raw/intro.ogg ("Projector", brand/source/intro_sound.py). The M is already on screen from
// the launch screen; it flickers like a projector beam while the projector clatters, a streak of light runs along it
// with the "Ma-ti" notes (450 and 630 ms), it punches with a flash on the "NEE" (870 ms), the wordmark rises, then it
// zooms through
@Composable
fun Intro(sound: IntroSound, onDone: () -> Unit) {
    val sweep = remember { Animatable(0f) }
    val flicker = remember { Animatable(1f) }
    val land = remember { Animatable(1f) }
    val light = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val out = remember { Animatable(0f) }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    LaunchedEffect(Unit) {
        // Hold on the M (identical to the launch screen) while Home loads its saved rows and builds them underneath,
        // so that heavy first build happens before any motion instead of stuttering it
        withTimeoutOrNull(1_500) { Library.awaitLoaded(); Repo.awaitLoaded() }
        repeat(4) { withFrameNanos { } }
        sound.play()
        val start = System.nanoTime()
        // Projector beam: a slight frame-rate flicker that steadies as the projector winds down
        launch {
            val random = java.util.Random()
            while (System.nanoTime() - start < 820_000_000L) {
                flicker.snapTo(0.82f + 0.18f * random.nextFloat())
                delay(42)
            }
            flicker.animateTo(1f, tween(60))
        }
        launch { land.animateTo(0.94f, tween(870, easing = FastOutSlowInEasing)) }
        delay(450)
        launch { sweep.animateTo(1f, tween(420, easing = LinearEasing)) }
        delay(420)
        launch { land.snapTo(1.1f); land.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
        launch { light.animateTo(1f, tween(80)); light.animateTo(0.3f, tween(1000)) }
        delay(200)
        launch { word.animateTo(1f, tween(650, easing = LinearOutSlowInEasing)) }
        delay(950)
        out.animateTo(1f, tween(460, easing = FastOutLinearInEasing))
        onDone()
    }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - out.value }.background(Color.Black), contentAlignment = Alignment.Center) {
        Box(Modifier.size(760.dp).graphicsLayer { alpha = light.value }
            .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.22f), Color.Transparent))))
        Canvas(Modifier.size(MarkBox).graphicsLayer { val s = land.value * (1f + 6f * out.value); scaleX = s; scaleY = s; alpha = flicker.value }) {
            scale(size.minDimension / VIEW, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                translate(OFFSET, OFFSET) {
                    ARCHES.forEachIndexed { i, whole ->
                        val arch = MEASURES[i]
                        for ((width, alpha) in listOf(36f to 0.08f, 26f to 0.14f, 18f to 0.26f)) {
                            drawPath(whole, Gold.copy(alpha = alpha * light.value), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        drawPath(whole, Gold, style = Stroke(13f, join = StrokeJoin.Round))
                        // The streak: a bright stretch of stroke travelling from start to end, fading as it goes
                        val head = arch.length * sweep.value
                        if (sweep.value in 0.001f..0.999f) {
                            val streak = Path().also { arch.getSegment((head - arch.length * 0.3f).coerceAtLeast(0f), head, it, true) }
                            drawPath(streak, LightGold.copy(alpha = 0.9f * (1f - sweep.value * 0.4f)), style = Stroke(13f, join = StrokeJoin.Round))
                            drawPath(streak, LightGold.copy(alpha = 0.25f), style = Stroke(26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
        }
        Image(
            painterResource(R.drawable.wordmark), "Matinee",
            Modifier.offset(y = 120.dp).width(230.dp)
                .graphicsLayer { alpha = word.value * (1f - out.value); translationY = (1f - word.value) * 40f },
        )
    }
}

// Loaded as the activity starts, so the sound is ready by the time the ident plays
class IntroSound(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(1)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .build()
    private val loaded = CompletableDeferred<Boolean>()
    private val id: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status -> loaded.complete(status == 0) }
        id = pool.load(context, R.raw.intro, 1)
    }

    // If the sound isn't ready within 300 ms the ident plays silent rather than late
    suspend fun play() {
        if (withTimeoutOrNull(300) { loaded.await() } == true) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    // The hall tail outlasts the animation (and a skip): let it ring out before freeing the pool
    fun release() {
        Handler(Looper.getMainLooper()).postDelayed({ pool.release() }, 3_500)
    }
}
