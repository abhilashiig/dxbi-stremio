package com.abhilash.matinee.tv.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class HomeRow(val key: String, val title: String, val items: List<MetaPreview>) {
    val type get() = key.split('|').getOrNull(1)
}

@Immutable
data class AddonStreams(val addon: String, val streams: List<StreamInfo>, val failed: Boolean)

@Immutable
data class SearchRow(val title: String, val items: List<MetaPreview>)

private const val CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"

object Repo {
    private lateinit var dir: File
    private val _addons = MutableStateFlow<List<Addon>>(emptyList())
    val addons: StateFlow<List<Addon>> = _addons
    private val _home = MutableStateFlow<List<HomeRow>>(emptyList())
    val home: StateFlow<List<HomeRow>> = _home

    private val catalogs = ConcurrentHashMap<String, JSONArray>()
    private val metas = ConcurrentHashMap<String, MetaDetail>()

    // Last-known addons and home rows load from disk, so the home screen shows instantly and refreshes behind
    fun init(context: Context) {
        dir = context.filesDir
        runCatching { JSONArray(File(dir, "addons.json").readText()).mapObjects { Addon.from(it) } }.getOrNull()?.let { _addons.value = it }
        runCatching {
            JSONArray(File(dir, "home.json").readText()).mapObjects { r ->
                HomeRow(r.getString("key"), r.getString("title"), r.optJSONArray("metas").mapObjects { MetaPreview.from(it, r.optString("type")) })
            }
        }.getOrNull()?.let { _home.value = it }
    }

    fun clear() {
        _addons.value = emptyList()
        _home.value = emptyList()
        catalogs.clear()
        metas.clear()
        File(dir, "addons.json").delete()
        File(dir, "home.json").delete()
    }

    suspend fun refreshAddons() {
        val key = Session.authKey
        val list = if (key != null) Stremio.addons(key) else listOf(Addon(CINEMETA, Http.get(CINEMETA)))
        if (list.isEmpty()) return
        _addons.value = list
        runCatching { File(dir, "addons.json").writeText(JSONArray(list.map { it.toJson() }).toString()) }
    }

    suspend fun refreshHome() = coroutineScope {
        val rows = _addons.value.flatMap { a -> a.catalogs.filter { it.forHome } }
        val fetched = rows.map { c -> async { runCatching { c to catalogJson(c, fresh = true) }.getOrNull() } }.awaitAll().filterNotNull()
        _home.value = fetched.mapNotNull { (c, metas) ->
            HomeRow(c.key, c.title, metas.mapObjects { MetaPreview.from(it, c.type) }).takeIf { it.items.isNotEmpty() }
        }
        runCatching {
            File(dir, "home.json").writeText(JSONArray(fetched.map { (c, metas) ->
                JSONObject().put("key", c.key).put("title", c.title).put("type", c.type).put("metas", metas)
            }).toString())
        }
    }

    private suspend fun catalogJson(c: Catalog, extra: List<Pair<String, String>> = emptyList(), fresh: Boolean = false): JSONArray {
        val url = c.addon.url("catalog", c.type, c.id, extra)
        if (!fresh) catalogs[url]?.let { return it }
        return (Http.get(url).optJSONArray("metas") ?: JSONArray()).also { catalogs[url] = it }
    }

    suspend fun meta(type: String, id: String): MetaDetail? {
        metas["$type/$id"]?.let { return it }
        for (addon in _addons.value.filter { it.supports("meta", type, id) }) {
            val meta = runCatching { Http.get(addon.url("meta", type, id)).optJSONObject("meta")?.let { MetaDetail.from(it) } }.getOrNull()
            if (meta != null) return meta.also { metas["$type/$id"] = it }
        }
        return null
    }

    // Each addon's streams arrive as soon as that addon answers
    fun streams(type: String, videoId: String): Flow<AddonStreams> = channelFlow {
        _addons.value.filter { it.supports("stream", type, videoId) }.forEach { addon ->
            launch {
                val result = runCatching { Http.get(addon.url("stream", type, videoId)).optJSONArray("streams").mapObjects { StreamInfo.from(addon.name, it) } }
                send(AddonStreams(addon.name, result.getOrDefault(emptyList()), result.isFailure))
            }
        }
    }

    // Each addon's subtitles as they arrive: a slow addon doesn't hold back the others
    fun subtitles(type: String, videoId: String): Flow<List<SubtitleTrack>> = channelFlow {
        _addons.value.filter { it.supports("subtitles", type, videoId) }.forEach { addon ->
            launch {
                val subs = withTimeoutOrNull(10_000) {
                    runCatching {
                        Http.get(addon.url("subtitles", type, videoId)).optJSONArray("subtitles").mapObjects { s ->
                            SubtitleTrack(
                                url = s.str("url") ?: return@mapObjects null, lang = s.str("lang") ?: "und", id = s.str("id") ?: s.optString("url"),
                                addon = addon.name, release = s.str("subtitleFileName") ?: s.str("movieReleaseName"),
                            )
                        }
                    }.getOrNull()
                }
                if (!subs.isNullOrEmpty()) send(subs)
            }
        }
    }

    suspend fun search(query: String): List<SearchRow> = coroutineScope {
        _addons.value.flatMap { a -> a.catalogs.filter { it.searchable } }.map { c ->
            async {
                runCatching { SearchRow(c.title, catalogJson(c, listOf("search" to query)).mapObjects { MetaPreview.from(it, c.type) }) }.getOrNull()
            }
        }.awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
    }
}
