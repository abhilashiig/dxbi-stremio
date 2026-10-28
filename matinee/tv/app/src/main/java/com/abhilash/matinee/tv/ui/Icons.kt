package com.abhilash.matinee.tv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Material icons (Apache 2.0), as path data so there's no icon library to ship
object Icons {
    val Play = icon("M8,5v14l11,-7z")
    val Pause = icon("M6,19h4V5H6v14zm8,-14v14h4V5h-4z")
    val Replay = icon("M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z")
    val Subtitles = icon("M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM4,12h4v2H4v-2zm10,6H4v-2h10v2zm6,0h-4v-2h4v2zm0,-4H10v-2h10v2z")
    val Info = icon("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm1,15h-2v-6h2v6zm0,-8h-2V7h2v2z")
    val Check = icon("M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val Bolt = icon("M7,2v11h3v9l7,-12h-4l4,-8z")
    val Add = icon("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
}

// Navigation: Lucide outline icons (ISC licence), drawn as strokes so they stay crisp at any size and weight
object NavIcons {
    val Search = outline("M3,11a8,8 0 1,0 16,0a8,8 0 1,0 -16,0", "M21,21l-4.34,-4.34")
    val Home = outline(
        "M15,21v-8a1,1 0 0,0 -1,-1h-4a1,1 0 0,0 -1,1v8",
        "M3,10a2,2 0 0,1 0.709,-1.528l7,-5.999a2,2 0 0,1 2.582,0l7,5.999A2,2 0 0,1 21,10v9a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2z",
    )
    val Series = outline("M4,7h16a2,2 0 0,1 2,2v11a2,2 0 0,1 -2,2H4a2,2 0 0,1 -2,-2V9a2,2 0 0,1 2,-2z", "M17,2L12,7L7,2")
    val Movies = outline(
        "M20.2,6L3,11l-0.9,-2.4c-0.3,-1.1 0.3,-2.2 1.3,-2.5l13.5,-4c1.1,-0.3 2.2,0.3 2.5,1.3z",
        "M6.2,5.3l3.1,3.9", "M12.4,3.4l3.1,4", "M3,11h18v8a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2z",
    )
    val MyList = outline("M19,21l-7,-4l-7,4V5a2,2 0 0,1 2,-2h10a2,2 0 0,1 2,2v16z")
    val Settings = outline("M20,7h-9", "M14,17H5", "M14,17a3,3 0 1,0 6,0a3,3 0 1,0 -6,0", "M4,7a3,3 0 1,0 6,0a3,3 0 1,0 -6,0")
}

// The Matinee "M" from brand/source/mark_transparent.svg, in its own gold
val MatineeMark = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 100f, viewportHeight = 100f)
    .addPath(
        addPathNodes("M15,85L15,42A17.5,17.5 0 0,1 50,42L50,85M50,42A17.5,17.5 0 0,1 85,42L85,85"),
        fill = null, stroke = SolidColor(Color(0xFFF2B441)), strokeLineWidth = 13f, strokeLineJoin = StrokeJoin.Round,
    )
    .build()

private fun outline(vararg paths: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .apply {
        paths.forEach {
            addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }
    .build()

private fun icon(path: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.White))
    .build()
