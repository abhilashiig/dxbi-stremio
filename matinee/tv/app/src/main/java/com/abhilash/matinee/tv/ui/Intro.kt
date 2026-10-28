package com.abhilash.matinee.tv.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.abhilash.matinee.tv.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// The M's two arches (brand/source/mark_transparent.svg), drawn side by side as the ident plays
private val ARCHES = listOf("M15,85L15,42A17.5,17.5 0 0,1 50,42L50,85", "M50,42A17.5,17.5 0 0,1 85,42L85,85")
    .map { PathParser().parsePathString(it).toPath() }

// Start-up ident, timed to res/raw/intro.ogg: the M draws itself on the "ta", lands with a punch and a flash of light
// on the "DUM" (450 ms), the wordmark rises under it, then the M zooms through into the app
@Composable
fun Intro(onDone: () -> Unit) {
    val context = LocalContext.current
    val draw = remember { Animatable(0f) }
    val land = remember { Animatable(0.92f) }
    val light = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val out = remember { Animatable(0f) }
    val sound = remember { IntroSound(context) }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    LaunchedEffect(Unit) {
        sound.play()
        launch { draw.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        delay(450)
        launch { land.snapTo(1.1f); land.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        launch { light.animateTo(1f, tween(90)); light.animateTo(0.3f, tween(1100)) }
        delay(250)
        launch { word.animateTo(1f, tween(650, easing = LinearOutSlowInEasing)) }
        delay(1250)
        out.animateTo(1f, tween(480, easing = FastOutLinearInEasing))
        onDone()
    }

    Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer { alpha = 1f - out.value }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(720.dp).graphicsLayer { alpha = light.value }
            .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.22f), Color.Transparent))))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(150.dp).graphicsLayer { val s = land.value * (1f + 5f * out.value); scaleX = s; scaleY = s }) {
                scale(size.minDimension / 100f, pivot = Offset.Zero) {
                    ARCHES.forEach { arch ->
                        val measure = PathMeasure().apply { setPath(arch, false) }
                        val part = Path().also { measure.getSegment(0f, measure.length * draw.value, it, true) }
                        for ((width, alpha) in listOf(36f to 0.08f, 26f to 0.14f, 18f to 0.26f)) {
                            drawPath(part, Gold.copy(alpha = alpha * light.value), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        drawPath(part, Gold, style = Stroke(13f, join = StrokeJoin.Round))
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Image(
                painterResource(R.drawable.wordmark), "Matinee",
                Modifier.width(230.dp).graphicsLayer { alpha = word.value * (1f - out.value); translationY = (1f - word.value) * 40f },
            )
        }
    }
}

private class IntroSound(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(1)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .build()
    private val loaded = CompletableDeferred<Boolean>()
    private val id: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status -> loaded.complete(status == 0) }
        id = pool.load(context, R.raw.intro, 1)
    }

    // Starts the animation with the sound; if the sound isn't ready within 400 ms the ident plays silent
    suspend fun play() {
        if (withTimeoutOrNull(400) { loaded.await() } == true) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    // The hall tail outlasts the animation (and a skip): let it ring out before freeing the pool
    fun release() {
        Handler(Looper.getMainLooper()).postDelayed({ pool.release() }, 3_500)
    }
}
