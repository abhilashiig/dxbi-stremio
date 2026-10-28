package com.abhilash.matinee.tv.report

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.abhilash.matinee.tv.R

private val Gold = Color(0xFFF2B441)

// Every line is focusable so the remote's up/down scrolls through the whole report.
@Composable
fun ReportScreen(sections: List<Section>) {
    val first = remember { FocusRequester() }
    LazyColumn(
        Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 27.dp),
    ) {
        item {
            Image(painterResource(R.drawable.wordmark), contentDescription = "Matinee", modifier = Modifier.width(160.dp))
        }
        sections.forEachIndexed { s, section ->
            item {
                Text(section.title, color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            section.lines.forEachIndexed { l, line ->
                item {
                    Surface(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().then(if (s == 0 && l == 0) Modifier.focusRequester(first) else Modifier),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent, contentColor = Color(0xFFDDDDDD),
                            focusedContainerColor = Color(0xFF2A2A2A), focusedContentColor = Color.White,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        Text(line, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}
