package com.abhilash.matinee.tv.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject

@Immutable
data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val description: String?,
    val releaseInfo: String?,
    val imdbRating: String?,
    val genres: List<String>,
) {
    companion object {
        fun from(j: JSONObject, fallbackType: String? = null): MetaPreview? {
            val id = j.str("id") ?: j.str("imdb_id") ?: return null
            return MetaPreview(
                id = id,
                type = j.str("type") ?: fallbackType ?: return null,
                name = j.str("name") ?: return null,
                poster = j.str("poster")?.let { Images.poster(it) },
                background = j.str("background")?.let { Images.backdrop(it) },
                logo = j.str("logo"),
                description = j.str("description"),
                releaseInfo = j.str("releaseInfo") ?: j.str("year"),
                imdbRating = j.str("imdbRating"),
                genres = j.strings("genres").ifEmpty { j.strings("genre") },
            )
        }
    }
}

@Immutable
data class Video(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val released: String?,
    val thumbnail: String?,
    val overview: String?,
) {
    val label get() = if (season > 0 || episode > 0) "S$season:E$episode" else ""
}

@Immutable
data class MetaDetail(
    val preview: MetaPreview,
    val runtime: String?,
    val videos: List<Video>,
    val cast: List<String>,
    val director: List<String>,
) {
    val seasons: List<Int> get() = videos.map { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it }))

    companion object {
        fun from(j: JSONObject): MetaDetail? {
            val preview = MetaPreview.from(j) ?: return null
            val videos = j.optJSONArray("videos").mapObjects { v ->
                Video(
                    id = v.str("id") ?: return@mapObjects null,
                    title = v.str("name") ?: v.str("title") ?: "",
                    season = v.optInt("season"),
                    episode = v.optInt("episode", v.optInt("number")),
                    released = v.str("released") ?: v.str("firstAired"),
                    thumbnail = v.str("thumbnail"),
                    overview = v.str("overview") ?: v.str("description"),
                )
            }.sortedWith(compareBy({ it.season == 0 }, { it.season }, { it.episode }))
            return MetaDetail(preview, j.str("runtime"), videos, j.strings("cast"), j.strings("director"))
        }
    }
}

@Immutable
data class StreamInfo(
    val addon: String,
    val url: String,
    val name: String,
    val description: String,
    val headers: Map<String, String>,
    val filename: String?,
    val size: Long?,
) {
    // Everything the stream says about itself, for ranking and badges
    val text get() = listOfNotNull(name, description, filename).joinToString("\n")

    companion object {
        // Only direct http(s) streams: Matinee doesn't do torrents, debrid addons serve URLs
        fun from(addon: String, j: JSONObject): StreamInfo? {
            val url = j.str("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
            val hints = j.optJSONObject("behaviorHints")
            val request = hints?.optJSONObject("proxyHeaders")?.optJSONObject("request")
            return StreamInfo(
                addon = addon,
                url = url,
                name = j.str("name") ?: addon,
                description = j.str("description") ?: j.str("title") ?: "",
                headers = request?.keys()?.asSequence()?.associateWith { request.optString(it) } ?: emptyMap(),
                filename = hints?.str("filename"),
                size = hints?.optLong("videoSize")?.takeIf { it > 0 },
            )
        }
    }
}

@Immutable
data class SubtitleTrack(val url: String, val lang: String, val id: String, val addon: String = "", val release: String? = null)

// Cinemeta's image host serves several sizes; posters and backdrops are asked for at what the TV shows
object Images {
    fun poster(url: String) = url.replace("/poster/small/", "/poster/medium/")
    fun backdrop(url: String) = url.replace("/background/medium/", "/background/large/")
}
