package com.abhilash.matinee.tv.data

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Http {
    lateinit var client: OkHttpClient
        private set

    fun init(context: Context) {
        client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 64L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun get(url: String): JSONObject = execute(Request.Builder().url(url).build())

    suspend fun post(url: String, body: JSONObject): JSONObject =
        execute(Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType())).build())

    // JSON is parsed on OkHttp's thread, never the main one
    private suspend fun execute(request: Request): JSONObject = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code} from ${request.url.host}")
                        cont.resume(JSONObject(it.body!!.string()))
                    }
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        })
    }
}

fun encodeComponent(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

fun JSONObject.strings(key: String): List<String> = optJSONArray(key).strings()

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { (opt(it) as? String)?.ifEmpty { null } }

fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T?): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(f) }
