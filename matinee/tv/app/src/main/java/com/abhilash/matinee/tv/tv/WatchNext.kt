package com.abhilash.matinee.tv.tv

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.abhilash.matinee.tv.data.LibItem
import com.abhilash.matinee.tv.data.encodeComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Continue Watching on the Google TV home screen ("Continue watching" row). Fire TV has no such provider.
object WatchNext {
    suspend fun publish(context: Context, items: List<LibItem>) = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences("watchNext", Context.MODE_PRIVATE)
            val resolver = context.contentResolver
            prefs.getStringSet("ids", emptySet())!!.forEach { id ->
                runCatching { resolver.delete(TvContractCompat.buildWatchNextProgramUri(id.toLong()), null, null) }
            }
            val ids = items.take(10).mapNotNull { item ->
                val program = WatchNextProgram.Builder()
                    .setType(if (item.type == "series") TvContractCompat.WatchNextPrograms.TYPE_TV_SERIES else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(item.lastWatched)
                    .setTitle(item.name)
                    .setPosterArtUri(item.poster?.let { Uri.parse(it) })
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER)
                    .setLastPlaybackPositionMillis(item.timeOffset.toInt())
                    .setDurationMillis(item.duration.toInt())
                    .setInternalProviderId(item.id)
                    .setIntentUri(Uri.parse("matinee://details/${item.type}/${encodeComponent(item.id)}"))
                    .build()
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())?.let { ContentUris.parseId(it).toString() }
            }
            prefs.edit().putStringSet("ids", ids.toSet()).apply()
        }
    }
}
