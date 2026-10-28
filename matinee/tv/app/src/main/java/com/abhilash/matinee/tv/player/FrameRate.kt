package com.abhilash.matinee.tv.player

import kotlin.math.abs
import kotlin.math.roundToInt

data class ModeInfo(val id: Int, val width: Int, val height: Int, val refresh: Float)

// How many display refreshes each video frame gets, if it's a whole number (within 0.2%: 24 Hz plays
// 23.976 fps with one repeated frame every ~40 s, far smoother than the 3:2 judder of 60 Hz)
private fun cadence(refresh: Float, fps: Float): Pair<Int, Float>? {
    val k = (refresh / fps).roundToInt()
    if (k < 1) return null
    val error = abs(refresh - k * fps) / refresh
    return if (error < 0.002f) k to error else null
}

// The display mode (same resolution) whose refresh rate fits the video's frame rate, or null to stay
fun modeForFrameRate(fps: Float, current: ModeInfo, modes: List<ModeInfo>): ModeInfo? {
    if (fps < 10f) return null
    val now = cadence(current.refresh, fps)
    if (now != null && now.second < 0.0005f) return null
    val best = modes
        .filter { it.width == current.width && it.height == current.height }
        .mapNotNull { m -> cadence(m.refresh, fps)?.let { m to it } }
        .minWithOrNull(compareBy<Pair<ModeInfo, Pair<Int, Float>>> { it.second.second > 0.0005f }.thenBy { it.second.first }.thenBy { it.second.second })
        ?.first
    return best?.takeIf { it.id != current.id && (now == null || cadence(it.refresh, fps)!!.second < now.second) }
}
