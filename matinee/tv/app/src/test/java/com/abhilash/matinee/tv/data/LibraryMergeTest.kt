package com.abhilash.matinee.tv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryMergeTest {
    private fun item(id: String, mtime: String, offset: Long) =
        JSONObject().put("_id", id).put("_mtime", mtime).put("state", JSONObject().put("timeOffset", offset))

    @Test
    fun newestCopyWins() {
        val local = mapOf(
            "tt1" to item("tt1", "2026-11-08T10:05:00.000Z", 900_000), // saved by the player, not yet on Stremio
            "tt2" to item("tt2", "2026-11-08T09:00:00.000Z", 100),     // changed since on another device
            "tt3" to item("tt3", "2026-11-08T10:00:00.000Z", 5_000),   // never reached Stremio
        )
        val remote = listOf(
            item("tt1", "2026-11-08T10:00:00.000Z", 300_000),
            item("tt2", "2026-11-08T09:30:00.000Z", 200),
            item("tt4", "2026-11-08T08:00:00.000Z", 0),
        )
        val (merged, unsynced) = mergeLibrary(local, remote)
        val offsets = merged.mapValues { it.value.getJSONObject("state").getLong("timeOffset") }
        assertEquals(mapOf("tt1" to 900_000L, "tt2" to 200L, "tt4" to 0L, "tt3" to 5_000L), offsets)
        assertEquals(listOf("tt1", "tt3"), unsynced.map { it.getString("_id") })
    }
}
