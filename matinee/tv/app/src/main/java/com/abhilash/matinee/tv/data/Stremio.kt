package com.abhilash.matinee.tv.data

import org.json.JSONArray
import org.json.JSONObject

class StremioException(message: String) : Exception(message)

class Account(val authKey: String, val email: String?)

// Stremio's account API (api.strem.io) and its link-code sign-in (link.stremio.com)
object Stremio {
    private const val API = "https://api.strem.io/api/"
    private const val LINK = "https://link.stremio.com/api/v2/"
    private const val LINK_PENDING = 101

    private suspend fun api(method: String, body: JSONObject): Any {
        val response = Http.post(API + method, body)
        response.optJSONObject("error")?.let { throw StremioException(it.optString("message", "Stremio error")) }
        return response.get("result")
    }

    suspend fun login(email: String, password: String): Account {
        val result = api("login", JSONObject().put("type", "Login").put("email", email).put("password", password).put("facebook", false)) as JSONObject
        return Account(result.getString("authKey"), result.optJSONObject("user")?.str("email") ?: email)
    }

    suspend fun logout(authKey: String) {
        runCatching { api("logout", JSONObject().put("type", "Logout").put("authKey", authKey)) }
    }

    class LinkCode(val code: String, val link: String, val qrUrl: String)

    suspend fun createLink(): LinkCode {
        val result = Http.get(LINK + "create?type=Create").getJSONObject("result")
        return LinkCode(result.getString("code"), result.getString("link"), result.getString("qrcode"))
    }

    // null while the code hasn't been approved yet
    suspend fun readLink(code: String): Account? {
        val response = Http.get(LINK + "read?type=Read&code=" + encodeComponent(code))
        response.optJSONObject("error")?.let {
            if (it.optInt("code") == LINK_PENDING) return null
            throw StremioException(it.optString("message", "Stremio error"))
        }
        val result = response.getJSONObject("result")
        val authKey = result.str("authKey") ?: return null
        return Account(authKey, result.optJSONObject("user")?.str("email") ?: email(authKey))
    }

    private suspend fun email(authKey: String): String? =
        runCatching { (api("getUser", JSONObject().put("type", "GetUser").put("authKey", authKey)) as JSONObject).str("email") }.getOrNull()

    suspend fun addons(authKey: String): List<Addon> {
        val result = api("addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", authKey).put("update", true)) as JSONObject
        return result.optJSONArray("addons").mapObjects { Addon.from(it) }
    }

    suspend fun library(authKey: String): List<JSONObject> {
        val body = JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("ids", JSONArray()).put("all", true)
        return (api("datastoreGet", body) as JSONArray).mapObjects { it }
    }

    suspend fun putLibrary(authKey: String, items: List<JSONObject>) {
        api("datastorePut", JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("changes", JSONArray(items)))
    }
}

class Addon(val transportUrl: String, val manifest: JSONObject) {
    val base = transportUrl.substringBeforeLast("/manifest.json")
    val name = manifest.str("name") ?: base
    val catalogs: List<Catalog> = manifest.optJSONArray("catalogs").mapObjects { Catalog.from(this, it) }

    // Mirrors stremio-core: a short resource uses the manifest's types/idPrefixes, a full one its own
    fun supports(resource: String, type: String, id: String): Boolean {
        val resources = manifest.optJSONArray("resources") ?: return false
        for (i in 0 until resources.length()) {
            val r = resources.opt(i)
            val (name, types, prefixes) = when (r) {
                is String -> Triple(r, manifest.strings("types"), manifest.optJSONArray("idPrefixes"))
                is JSONObject -> Triple(r.str("name"), r.strings("types"), r.optJSONArray("idPrefixes"))
                else -> continue
            }
            if (name != resource || type !in types) continue
            if (prefixes == null || prefixes.strings().any { id.startsWith(it) }) return true
        }
        return false
    }

    fun url(resource: String, type: String, id: String, extra: List<Pair<String, String>> = emptyList()) = buildString {
        append(base).append('/').append(resource).append('/').append(type).append('/').append(encodeComponent(id))
        if (extra.isNotEmpty()) append('/').append(extra.joinToString("&") { "${it.first}=${encodeComponent(it.second)}" })
        append(".json")
    }

    fun toJson(): JSONObject = JSONObject().put("transportUrl", transportUrl).put("manifest", manifest)

    companion object {
        fun from(json: JSONObject): Addon? {
            val url = json.str("transportUrl") ?: return null
            return Addon(url, json.optJSONObject("manifest") ?: return null)
        }
    }
}

class Catalog(val addon: Addon, val type: String, val id: String, val name: String, val extras: Set<String>, val required: Set<String>) {
    val forHome get() = required.isEmpty()
    val searchable get() = "search" in extras && required.all { it == "search" }
    val key get() = "${addon.base}|$type|$id"
    val title get() = "$name · ${typeLabel(type)}"

    companion object {
        fun from(addon: Addon, json: JSONObject): Catalog? {
            val type = json.str("type") ?: return null
            val id = json.str("id") ?: return null
            val extra = json.optJSONArray("extra")
            val extras = extra?.mapObjects { it.str("name") }?.toSet() ?: json.strings("extraSupported").toSet()
            val required = extra?.mapObjects { e -> e.str("name")?.takeIf { e.optBoolean("isRequired") } }?.toSet()
                ?: json.strings("extraRequired").toSet()
            return Catalog(addon, type, id, json.str("name") ?: id, extras, required)
        }
    }
}

fun typeLabel(type: String) = when (type) {
    "movie" -> "Movies"
    "series" -> "Series"
    "tv" -> "TV"
    else -> type.replaceFirstChar { it.uppercase() }
}
