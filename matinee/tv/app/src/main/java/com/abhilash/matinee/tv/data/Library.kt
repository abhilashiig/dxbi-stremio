package com.abhilash.matinee.tv.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Immutable
data class LibItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val removed: Boolean,
    val temp: Boolean,
    val timeOffset: Long,
    val duration: Long,
    val videoId: String?,
    val lastWatched: Long,
    val mtime: Long,
) {
    val inLibrary get() = !removed
    // stremio-core's rule, so Continue Watching matches the official apps
    val inContinueWatching get() = type != "other" && (!removed || temp) && timeOffset > 0
    val progress get() = if (duration > 0) (timeOffset.toFloat() / duration).coerceIn(0f, 1f) else 0f

    fun preview() = MetaPreview(id, type, name, poster, null, null, null, null, null, emptyList())

    companion object {
        fun from(j: JSONObject): LibItem? {
            val state = j.optJSONObject("state") ?: JSONObject()
            return LibItem(
                id = j.str("_id") ?: return null,
                type = j.str("type") ?: return null,
                name = j.str("name") ?: "",
                poster = j.str("poster")?.let { Images.poster(it) },
                removed = j.optBoolean("removed"),
                temp = j.optBoolean("temp"),
                timeOffset = state.optLong("timeOffset"),
                duration = state.optLong("duration"),
                videoId = state.str("video_id"),
                lastWatched = parseIso(state.str("lastWatched")),
                mtime = parseIso(j.str("_mtime")),
            )
        }
    }
}

// The user's Stremio library. Writes edit Stremio's own JSON in place, so fields Matinee doesn't know about
// survive. The `watched` episode bitfield is never written: a wrong one would corrupt the official apps' marks.
object Library {
    private lateinit var file: File
    private val lock = Mutex()
    private val raw = LinkedHashMap<String, JSONObject>()
    private val _items = MutableStateFlow<List<LibItem>>(emptyList())
    val items: StateFlow<List<LibItem>> = _items

    fun init(context: Context) {
        file = File(context.filesDir, "library.json")
        runCatching { JSONArray(file.readText()).mapObjects { it } }.getOrNull()?.forEach { j -> j.str("_id")?.let { raw[it] = j } }
        publish()
    }

    fun item(id: String): LibItem? = _items.value.firstOrNull { it.id == id }

    fun continueWatching(items: List<LibItem>) = items.filter { it.inContinueWatching }.sortedByDescending { it.lastWatched }

    suspend fun refresh() {
        val key = Session.authKey ?: return
        val remote = Stremio.library(key)
        lock.withLock {
            raw.clear()
            remote.forEach { j -> j.str("_id")?.let { raw[it] = j } }
            persist()
            publish()
        }
    }

    suspend fun setInLibrary(meta: MetaPreview, add: Boolean) = update(meta) { item, _ ->
        item.put("removed", !add).put("temp", false)
    }

    suspend fun saveProgress(meta: MetaPreview, videoId: String, positionMs: Long, durationMs: Long) = update(meta) { item, now ->
        val state = item.optJSONObject("state") ?: JSONObject().also { item.put("state", it) }
        if (state.str("video_id") != videoId) state.put("video_id", videoId).put("timeOffset", 0).put("timeWatched", 0)
        val watched = (positionMs - state.optLong("timeOffset")).coerceIn(0, 60_000)
        state.put("timeWatched", state.optLong("timeWatched") + watched)
            .put("overallTimeWatched", state.optLong("overallTimeWatched") + watched)
            .put("duration", durationMs)
            .put("lastWatched", now)
        if (durationMs > 0 && positionMs >= durationMs * 0.9) {
            state.put("timeOffset", 0).put("timesWatched", state.optInt("timesWatched") + 1).put("flaggedWatched", 1)
        } else {
            state.put("timeOffset", positionMs)
        }
    }

    private suspend fun update(meta: MetaPreview, change: (JSONObject, String) -> Unit) {
        val now = isoNow()
        val item = lock.withLock {
            val item = raw[meta.id]?.let { JSONObject(it.toString()) } ?: newItem(meta, now)
            change(item, now)
            item.put("_mtime", now)
            raw[meta.id] = item
            persist()
            publish()
            item
        }
        Session.authKey?.let { key -> runCatching { Stremio.putLibrary(key, listOf(item)) } }
    }

    // Same shape stremio-core writes; not yet in the library means removed + temp (it only shows in Continue Watching)
    private fun newItem(meta: MetaPreview, now: String) = JSONObject()
        .put("_id", meta.id).put("name", meta.name).put("type", meta.type)
        .put("poster", meta.poster ?: JSONObject.NULL).put("posterShape", "poster")
        .put("removed", true).put("temp", true).put("_ctime", now).put("_mtime", now)
        .put("state", JSONObject()
            .put("lastWatched", now).put("timeWatched", 0).put("timeOffset", 0).put("overallTimeWatched", 0)
            .put("timesWatched", 0).put("flaggedWatched", 0).put("duration", 0).put("video_id", JSONObject.NULL)
            .put("watched", JSONObject.NULL).put("noNotif", false))
        .put("behaviorHints", JSONObject()
            .put("defaultVideoId", JSONObject.NULL).put("featuredVideoId", JSONObject.NULL).put("hasScheduledVideos", false))

    private fun persist() = runCatching { file.writeText(JSONArray(raw.values.toList()).toString()) }

    // Called under the lock, or from init before anything else runs
    private fun publish() {
        _items.value = raw.values.mapNotNull { LibItem.from(it) }
    }
}

private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

fun isoNow(): String = isoFormat().format(System.currentTimeMillis())

fun parseIso(s: String?): Long {
    if (s == null) return 0
    return runCatching { isoFormat().parse(s)!!.time }.recoverCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)!!.time
    }.getOrDefault(0)
}
